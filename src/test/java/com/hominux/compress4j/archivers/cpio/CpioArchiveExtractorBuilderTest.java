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
package com.hominux.compress4j.archivers.cpio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.EntrySource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CpioArchiveExtractorBuilderTest {

    @TempDir
    Path tempDir;

    private byte[] sampleArchive;
    private byte[] multiFileArchive;

    @BeforeEach
    void setUp() throws IOException {
        sampleArchive = createSampleArchive();
        multiFileArchive = createMultiFileArchive();
    }

    @Test
    void testBuilderWithPath() throws IOException {
        // given
        var archiveFile = tempDir.resolve("test.cpio");
        Files.write(archiveFile, sampleArchive);

        // when
        var builder = CpioArchiveExtractor.builder(archiveFile);

        // then
        assertThat(builder).isNotNull();
        try (var extractor = builder.build()) {
            assertThat(extractor).isNotNull();
        }
    }

    @Test
    void testBuilderWithInputStream() throws IOException {
        // given
        InputStream inputStream = new ByteArrayInputStream(sampleArchive);

        // when
        var builder = CpioArchiveExtractor.builder(inputStream);

        // then
        assertThat(builder).isNotNull();
        try (var extractor = builder.build()) {
            assertThat(extractor).isNotNull();
        }
    }

    @Test
    void testBuilderWithDefaultConfiguration() throws IOException {
        // given
        InputStream inputStream = new ByteArrayInputStream(sampleArchive);

        // when & then
        try (var extractor = CpioArchiveExtractor.builder(inputStream).build()) {
            assertThat(extractor).isNotNull();

            var extractDir = tempDir.resolve("extract-default");
            Files.createDirectories(extractDir);
            extractor.extract(extractDir);

            assertThat(extractDir.resolve("test.txt")).exists();
        }
    }

    @Test
    void testBuilderWithCustomInputStreamConfiguration() throws IOException {
        // given
        InputStream inputStream = new ByteArrayInputStream(sampleArchive);

        // when & then
        try (var extractor = CpioArchiveExtractor.builder(inputStream)
                .blockSize(1024)
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            assertThat(extractor).isNotNull();

            var extractDir = tempDir.resolve("extract-custom");
            Files.createDirectories(extractDir);
            extractor.extract(extractDir);

            assertThat(extractDir.resolve("test.txt")).exists();
        }
    }

    @Test
    void testBuilderInputStreamAccess() throws IOException {
        // given
        InputStream inputStream = new ByteArrayInputStream(sampleArchive);

        // when
        var builder = CpioArchiveExtractor.builder(inputStream);
        var configuredBuilder = builder.blockSize(2048).encoding(StandardCharsets.ISO_8859_1);

        // then
        assertThat(configuredBuilder).isSameAs(builder);
        try (var extractor = builder.build()) {
            assertThat(extractor).isNotNull();
        }
    }

    @Test
    void testBuilderGetThis() throws IOException {
        // given
        InputStream inputStream = new ByteArrayInputStream(sampleArchive);
        var builder = CpioArchiveExtractor.builder(inputStream);

        // when & then
        try (var extractor = builder.build()) {
            assertThat(extractor).isNotNull();
        }
    }

    @Test
    void testBuilderBuildsReadableExtractor() throws IOException {
        // given
        InputStream inputStream = new ByteArrayInputStream(sampleArchive);

        // when
        try (var extractor = CpioArchiveExtractor.builder(inputStream).build()) {
            assertThat(extractor.stream().map(item -> item.entry().name())).contains("test.txt");
        }
    }

    @Test
    void testBuilderWithNonExistentFile() {
        // given
        var nonExistentFile = tempDir.resolve("does-not-exist.cpio");

        // when & then
        assertThatThrownBy(() -> CpioArchiveExtractor.builder(nonExistentFile).build())
                .isInstanceOf(IOException.class);
    }

    @Test
    void testBuilderWithNullPath() {
        // when & then
        assertThatThrownBy(() -> CpioArchiveExtractor.builder((Path) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testBuilderReusability() throws IOException {
        // given
        InputStream inputStream1 = new ByteArrayInputStream(sampleArchive);
        var builder = CpioArchiveExtractor.builder(inputStream1);

        // when & then
        try (var extractor1 = builder.build()) {
            assertThat(extractor1).isNotNull();
        }

        InputStream inputStream2 = new ByteArrayInputStream(sampleArchive);
        var builder2 = CpioArchiveExtractor.builder(inputStream2);

        try (var extractor2 = builder2.build()) {
            assertThat(extractor2).isNotNull();
        }
    }

    @Test
    void testBuilderWithVariousBlockSizes() throws IOException {
        // given
        int[] blockSizes = {256, 512, 1024, 2048, 4096};

        for (int blockSize : blockSizes) {
            InputStream inputStream = new ByteArrayInputStream(sampleArchive);

            // when
            try (var extractor = CpioArchiveExtractor.builder(inputStream)
                    .blockSize(blockSize)
                    .build()) {

                // then
                assertThat(extractor).isNotNull();

                var extractDir = tempDir.resolve("extract-block-" + blockSize);
                Files.createDirectories(extractDir);
                extractor.extract(extractDir);

                assertThat(extractDir.resolve("test.txt")).exists();
                assertThat(Files.readString(extractDir.resolve("test.txt"))).isEqualTo("Sample content for testing");
            }
        }
    }

    @Test
    void testBuilderWithVariousEncodings() throws IOException {
        // given
        String[] encodings = {"UTF-8", "ISO-8859-1", "US-ASCII"};

        for (String encoding : encodings) {
            InputStream inputStream = new ByteArrayInputStream(sampleArchive);

            // when
            try (var extractor = CpioArchiveExtractor.builder(inputStream)
                    .encoding(Charset.forName(encoding))
                    .build()) {

                // then
                assertThat(extractor).isNotNull();

                var extractDir = tempDir.resolve("extract-encoding-" + encoding.replace("-", ""));
                Files.createDirectories(extractDir);
                extractor.extract(extractDir);

                assertThat(extractDir.resolve("test.txt")).exists();
            }
        }
    }

    @Test
    void testBuilderWithComplexArchive() throws IOException {
        // given
        InputStream inputStream = new ByteArrayInputStream(multiFileArchive);

        // when & then
        try (var extractor = CpioArchiveExtractor.builder(inputStream)
                .blockSize(1024)
                .encoding(StandardCharsets.UTF_8)
                .build()) {

            assertThat(extractor).isNotNull();

            var extractDir = tempDir.resolve("extract-complex");
            Files.createDirectories(extractDir);
            extractor.extract(extractDir);

            assertThat(extractDir.resolve("file1.txt")).exists();
            assertThat(extractDir.resolve("file2.txt")).exists();
            assertThat(Files.readString(extractDir.resolve("file1.txt"))).isEqualTo("Content 1");
            assertThat(Files.readString(extractDir.resolve("file2.txt"))).isEqualTo("Content 2");
        }
    }

    @Test
    void testBuilderWithSpecialCharacterArchive() throws IOException {
        // given
        var specialFile = tempDir.resolve("special-äöü.txt");
        Files.write(specialFile, "Special content".getBytes());

        var archiveOutput = new ByteArrayOutputStream();
        try (var creator = CpioArchiveCreator.builder(archiveOutput)
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            creator.add(EntrySource.file("special-äöü.txt", specialFile));
        }

        InputStream inputStream = new ByteArrayInputStream(archiveOutput.toByteArray());

        // when & then
        try (var extractor = CpioArchiveExtractor.builder(inputStream)
                .encoding(StandardCharsets.UTF_8)
                .build()) {

            assertThat(extractor).isNotNull();

            var extractDir = tempDir.resolve("extract-special");
            Files.createDirectories(extractDir);
            extractor.extract(extractDir);

            var extractedFile = extractDir.resolve("special-äöü.txt");
            assertThat(extractedFile).exists();
            assertThat(Files.readString(extractedFile)).isEqualTo("Special content");
        }
    }

    @Test
    @SuppressWarnings("try")
    void testBuilderWithEmptyArchive() throws IOException {
        // given
        var emptyArchiveOutput = new ByteArrayOutputStream();
        // noinspection EmptyTryBlock
        try (var ignored = CpioArchiveCreator.builder(emptyArchiveOutput).build()) {
            /* no-op */
        }
        byte[] emptyArchive = emptyArchiveOutput.toByteArray();
        InputStream inputStream = new ByteArrayInputStream(emptyArchive);

        // when & then
        try (var extractor = CpioArchiveExtractor.builder(inputStream).build()) {
            assertThat(extractor).isNotNull();

            var extractDir = tempDir.resolve("extract-empty");
            Files.createDirectories(extractDir);
            extractor.extract(extractDir);

            assertThat(extractDir).isEmptyDirectory();
        }
    }

    @Test
    void testBuilderChainedConfiguration() throws IOException {
        // given
        InputStream inputStream = new ByteArrayInputStream(sampleArchive);

        // when
        var builder = CpioArchiveExtractor.builder(inputStream).blockSize(2048).encoding(StandardCharsets.UTF_8);

        // then
        assertThat(builder).isNotNull();

        try (var extractor = builder.build()) {
            assertThat(extractor).isNotNull();

            var extractDir = tempDir.resolve("extract-chained");
            Files.createDirectories(extractDir);
            extractor.extract(extractDir);

            assertThat(extractDir.resolve("test.txt")).exists();
        }
    }

    @Test
    void testBuilderWithInvalidConfiguration() {
        // given
        InputStream inputStream = new ByteArrayInputStream(sampleArchive);

        // when
        var builder = CpioArchiveExtractor.builder(inputStream);

        // then
        assertThatThrownBy(() -> builder.blockSize(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.blockSize(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBuilderWithLargeArchive() throws IOException {
        // given
        var largeFile = tempDir.resolve("large-file.txt");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            content.append("Line ").append(i).append(" of large file content.\n");
        }
        Files.write(largeFile, content.toString().getBytes());

        var archiveOutput = new ByteArrayOutputStream();
        try (var creator = CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.add(EntrySource.file("large.txt", largeFile));
        }

        InputStream inputStream = new ByteArrayInputStream(archiveOutput.toByteArray());

        // when & then
        try (var extractor =
                CpioArchiveExtractor.builder(inputStream).blockSize(4096).build()) {

            assertThat(extractor).isNotNull();

            var extractDir = tempDir.resolve("extract-large");
            Files.createDirectories(extractDir);
            extractor.extract(extractDir);

            var extractedFile = extractDir.resolve("large.txt");
            assertThat(extractedFile).exists();
            assertThat(Files.readString(extractedFile)).isEqualTo(content.toString());
        }
    }

    private byte[] createSampleArchive() throws IOException {
        var testFile = tempDir.resolve("setup-test.txt");
        Files.write(testFile, "Sample content for testing".getBytes());

        var archiveOutput = new ByteArrayOutputStream();
        try (var creator = CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.add(EntrySource.file("test.txt", testFile));
        }
        return archiveOutput.toByteArray();
    }

    private byte[] createMultiFileArchive() throws IOException {
        var file1 = tempDir.resolve("file1.txt");
        var file2 = tempDir.resolve("file2.txt");
        Files.write(file1, "Content 1".getBytes());
        Files.write(file2, "Content 2".getBytes());

        var archiveOutput = new ByteArrayOutputStream();
        try (var creator = CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.add(EntrySource.file("file1.txt", file1));
            creator.add(EntrySource.file("file2.txt", file2));
        }
        return archiveOutput.toByteArray();
    }
}
