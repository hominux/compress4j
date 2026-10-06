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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.exceptions.UnsafeEntryException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class DotEntryExtractionTest {

    @TempDir
    Path out;

    @Test
    void leadingDotDirectoryEntryExtractsIntoTheOutputDirectory() throws IOException {
        byte[] archive = tar("./", "./a.txt");

        try (TarArchiveExtractor extractor =
                TarArchiveExtractor.builder(new ByteArrayInputStream(archive)).build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("a.txt")).hasContent("a");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void dotDirectoryEntryDoesNotChangeTheOutputDirectoryMode() throws IOException {
        byte[] archive = tar("./");
        var before = Files.getPosixFilePermissions(out);

        try (TarArchiveExtractor extractor =
                TarArchiveExtractor.builder(new ByteArrayInputStream(archive)).build()) {
            extractor.extract(out);
        }

        assertThat(out).isDirectory();
        assertThat(Files.getPosixFilePermissions(out)).isEqualTo(before);
    }

    @Test
    void traversalBehindADotSegmentStillFails() throws IOException {
        byte[] archive = tar("./", "./../x");

        try (TarArchiveExtractor extractor =
                TarArchiveExtractor.builder(new ByteArrayInputStream(archive)).build()) {
            assertThatThrownBy(() -> extractor.extract(out)).isInstanceOf(UnsafeEntryException.class);
        }
    }

    @Test
    void streamReportsTheDotEntryOnce() throws IOException {
        byte[] archive = tar("./", "./a.txt");

        try (TarArchiveExtractor extractor =
                TarArchiveExtractor.builder(new ByteArrayInputStream(archive)).build()) {
            List<String> names =
                    extractor.stream().map(item -> item.entry().name()).toList();
            assertThat(names).containsExactly(".", "./a.txt");
        }
    }

    private static byte[] tar(String... names) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(bytes)) {
            for (String name : names) {
                TarArchiveEntry entry = new TarArchiveEntry(name);
                byte[] data = name.endsWith("/") ? new byte[0] : "a".getBytes(StandardCharsets.UTF_8);
                entry.setSize(data.length);
                tar.putArchiveEntry(entry);
                tar.write(data);
                tar.closeArchiveEntry();
            }
        }
        return bytes.toByteArray();
    }
}
