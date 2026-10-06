/*
 * Copyright 2025-2026 The Compress4J Project
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
package com.hominux.compress4j.archivers.zip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hominux.compress4j.archivers.ArchiveItem;
import com.hominux.compress4j.archivers.EntrySource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipMethod;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipArchiveExtractorBuilderTest {

    private static final byte[] NOT_A_ZIP =
            "this is not a zip archive".repeat(8).getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    private Path zipWithOneFile() throws IOException {
        Path zip = dir.resolve("a.zip");
        try (var creator = ZipArchiveCreator.builder(zip).build()) {
            creator.add(EntrySource.file("a.txt", "hello".getBytes(StandardCharsets.UTF_8)));
        }
        return zip;
    }

    @Test
    void unbuiltPathBuilderHoldsNothingOpen() throws IOException {
        Path zip = zipWithOneFile();

        ZipArchiveExtractor.builder(zip).ignoreLocalFileHeader(true).maxNumberOfDisks(2);

        assertDoesNotThrow(() -> Files.delete(zip));
    }

    @Test
    void missingPathFailsOnlyInBuild() {
        var builder = ZipArchiveExtractor.builder(dir.resolve("missing.zip"));

        assertThatThrownBy(builder::build).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void corruptPathFailsInBuildAndReleasesTheFile() throws IOException {
        Path zip = Files.write(dir.resolve("corrupt.zip"), NOT_A_ZIP);
        var builder = ZipArchiveExtractor.builder(zip);

        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);

        assertDoesNotThrow(() -> Files.delete(zip));
    }

    @Test
    void corruptChannelFailsInBuildAndStaysOpen() {
        SeekableByteChannel channel = new SeekableInMemoryByteChannel(NOT_A_ZIP);
        var builder = ZipArchiveExtractor.builder(channel);

        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);

        assertThat(channel.isOpen()).isTrue();
    }

    @Test
    void builtExtractorClosesItsChannel() throws IOException {
        SeekableByteChannel channel = new SeekableInMemoryByteChannel(Files.readAllBytes(zipWithOneFile()));

        ZipArchiveExtractor.builder(channel).build().close();

        assertThat(channel.isOpen()).isFalse();
    }

    @Test
    void useUnicodeExtraFieldsDecidesWhichNameIsRead() throws IOException {
        Path zip = dir.resolve("unicode.zip");
        try (var creator = ZipArchiveCreator.builder(zip)
                .encoding(StandardCharsets.US_ASCII)
                .createUnicodeExtraFields(ZipUnicodeExtraFields.ALWAYS)
                .build()) {
            creator.add(EntrySource.file("café.txt", NOT_A_ZIP));
        }

        assertThat(namesRead(zip, true)).containsExactly("café.txt");
        assertThat(namesRead(zip, false)).doesNotContain("café.txt");
    }

    private static List<String> namesRead(Path zip, boolean useUnicodeExtraFields) throws IOException {
        try (var extractor = ZipArchiveExtractor.builder(zip)
                .useUnicodeExtraFields(useUnicodeExtraFields)
                .build()) {
            return extractor.stream().map(item -> item.entry().name()).toList();
        }
    }

    @Test
    void maxNumberOfDisksIsAccepted() throws IOException {
        try (var extractor = ZipArchiveExtractor.builder(zipWithOneFile())
                .ignoreLocalFileHeader(true)
                .maxNumberOfDisks(1)
                .build()) {
            assertThat(extractor.stream().map(item -> item.entry().name())).containsExactly("a.txt");
        }
    }

    @Test
    void zstdFactoryReadsZstdEntries() throws IOException {
        byte[] content = "zstd content".repeat(20).getBytes(StandardCharsets.UTF_8);
        var calls = new AtomicInteger();
        var bytes = zstdZip(content);

        try (var extractor = ZipArchiveExtractor.builder(new SeekableInMemoryByteChannel(bytes))
                .zstdInputStreamFactory(in -> {
                    calls.incrementAndGet();
                    return new ZstdCompressorInputStream(in);
                })
                .build()) {
            var item = extractor.stream().findFirst().orElseThrow();
            assertThat(item.content().readAllBytes()).isEqualTo(content);
        }

        assertThat(calls).hasValue(1);
    }

    @Test
    void nullZstdFactoryIsRejected() {
        var builder = ZipArchiveExtractor.builder(dir.resolve("a.zip"));

        assertThatThrownBy(() -> builder.zstdInputStreamFactory(null)).isInstanceOf(NullPointerException.class);
    }

    private static byte[] zstdZip(byte[] content) throws IOException {
        var compressed = new ByteArrayOutputStream();
        try (var zstd = new ZstdCompressorOutputStream(compressed)) {
            zstd.write(content);
        }
        var crc = new CRC32();
        crc.update(content);
        var entry = new ZipArchiveEntry("z.txt");
        entry.setMethod(ZipMethod.ZSTD.getCode());
        entry.setCrc(crc.getValue());
        entry.setSize(content.length);
        entry.setCompressedSize(compressed.size());
        var archive = new SeekableInMemoryByteChannel();
        try (var out = new ZipArchiveOutputStream(archive)) {
            out.addRawArchiveEntry(entry, new ByteArrayInputStream(compressed.toByteArray()));
        }
        return Arrays.copyOf(archive.array(), (int) archive.size());
    }

    @Test
    void streamingBuilderLeavesTheCallerStreamOpenUntilTheExtractorCloses() throws IOException {
        var closed = new boolean[1];
        var in = new ByteArrayInputStream(Files.readAllBytes(zipWithOneFile())) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };

        try (var extractor = ZipArchiveExtractor.streaming(in).build()) {
            assertThat(extractor.stream().map(ArchiveItem::entry)).hasSize(1);
            assertThat(closed[0]).isFalse();
        }

        assertThat(closed[0]).isTrue();
    }
}
