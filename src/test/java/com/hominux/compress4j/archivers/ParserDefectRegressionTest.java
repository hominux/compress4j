/*
 * Copyright 2026 The Compress4J Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hominux.compress4j.archivers;

import static com.hominux.compress4j.archivers.ErrorHandlerChoice.SKIP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.cpio.CpioArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.archivers.zip.ZipArchiveExtractor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParserDefectRegressionTest {

    private static final String NUL_NAME = "a\0b";
    private static final byte[] OK = "ok".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    private Path zip(String name, int mode, byte[] content) throws IOException {
        Path zip = dir.resolve("nul.zip");
        try (var out = new ZipArchiveOutputStream(Files.newOutputStream(zip))) {
            put(out, name, mode, content);
            put(out, "ok.txt", UnixStat.FILE_FLAG | 0644, OK);
        }
        return zip;
    }

    private static void put(ZipArchiveOutputStream out, String name, int mode, byte[] content) throws IOException {
        var entry = new ZipArchiveEntry(name);
        entry.setUnixMode(mode);
        entry.setSize(content.length);
        out.putArchiveEntry(entry);
        out.write(content);
        out.closeArchiveEntry();
    }

    private Path cpio() throws IOException {
        Path cpio = dir.resolve("nul.cpio");
        try (var out = new CpioArchiveOutputStream(Files.newOutputStream(cpio))) {
            put(out, NUL_NAME);
            put(out, "ok.txt");
        }
        return cpio;
    }

    private static void put(CpioArchiveOutputStream out, String name) throws IOException {
        var entry = new CpioArchiveEntry(name);
        entry.setMode(CpioConstants.C_ISREG | 0644);
        entry.setSize(OK.length);
        out.putArchiveEntry(entry);
        out.write(OK);
        out.closeArchiveEntry();
    }

    @Test
    void zipEntryNamedWithNulFailsAsIOException() throws IOException {
        Path out = dir.resolve("out");
        try (var extractor = ZipArchiveExtractor.builder(zip(NUL_NAME, UnixStat.FILE_FLAG | 0644, OK))
                .build()) {
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOf(IOException.class)
                    .hasMessageStartingWith("Corrupt archive entry")
                    .hasMessageContaining("a\\u0000b")
                    .hasCauseInstanceOf(InvalidPathException.class);
        }
    }

    @Test
    void streamingZipEntryNamedWithNulFailsAsIOException() throws IOException {
        Path out = dir.resolve("out");
        try (var in = Files.newInputStream(zip(NUL_NAME, UnixStat.FILE_FLAG | 0644, OK));
                var extractor = ZipArchiveExtractor.streaming(in).build()) {
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOf(IOException.class)
                    .hasMessageStartingWith("Corrupt archive entry");
        }
    }

    @Test
    void cpioEntryNamedWithNulFailsAsIOException() throws IOException {
        Path out = dir.resolve("out");
        try (var extractor = CpioArchiveExtractor.builder(cpio()).build()) {
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOf(IOException.class)
                    .hasMessageStartingWith("Corrupt archive entry")
                    .hasCauseInstanceOf(InvalidPathException.class);
        }
    }

    @Test
    void errorHandlerSeesTheInvalidNameAndSkipContinuesTheExtraction() throws IOException {
        Path out = dir.resolve("out");
        List<IOException> seen = new ArrayList<>();
        try (var extractor = ZipArchiveExtractor.builder(zip(NUL_NAME, UnixStat.FILE_FLAG | 0644, OK))
                .errorHandler((entry, failure) -> {
                    seen.add(failure);
                    return SKIP;
                })
                .build()) {
            extractor.extract(out);
        }
        assertThat(seen).singleElement().satisfies(e -> assertThat(e)
                .hasMessageStartingWith("Corrupt archive entry")
                .hasCauseInstanceOf(InvalidPathException.class));
        assertThat(out.resolve("ok.txt")).hasContent("ok");
    }

    @Test
    void symlinkTargetWithNulFailsAsIOException() throws IOException {
        Path out = dir.resolve("out");
        byte[] target = "t\0x".getBytes(StandardCharsets.UTF_8);
        try (var extractor = ZipArchiveExtractor.builder(zip("link", UnixStat.LINK_FLAG | 0777, target))
                .build()) {
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOf(IOException.class)
                    .hasMessageStartingWith("Corrupt archive entry: invalid link target of 'link'")
                    .hasCauseInstanceOf(InvalidPathException.class);
        }
    }

    @Test
    void tarWhoseBytesDetectAsUnixZFailsAsIOException() {
        byte[] bytes = new byte[1024];
        bytes[0] = 0x1F;
        bytes[1] = (byte) 0x9D;
        bytes[2] = 0x08;
        var builder = TarArchiveExtractor.builder(new ByteArrayInputStream(bytes));
        assertThatThrownBy(builder::build)
                .isInstanceOf(IOException.class)
                .hasMessage("Corrupt archive")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tarFileWhoseBytesDetectAsUnixZFailsAsIOException() throws IOException {
        byte[] bytes = new byte[1024];
        bytes[0] = 0x1F;
        bytes[1] = (byte) 0x9D;
        bytes[2] = 0x08;
        var builder = TarArchiveExtractor.builder(Files.write(dir.resolve("x.tar.gz"), bytes));
        assertThatThrownBy(builder::build)
                .isInstanceOf(IOException.class)
                .hasMessage("Corrupt archive")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
