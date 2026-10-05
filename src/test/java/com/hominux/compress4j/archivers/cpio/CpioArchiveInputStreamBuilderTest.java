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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CpioArchiveInputStreamBuilderTest {

    @TempDir
    Path tempDir;

    private byte[] sampleArchive;

    @BeforeEach
    void setUp() throws IOException {
        sampleArchive = createSampleArchive();
    }

    @Test
    void testBuilderWithDefaultConfiguration() throws IOException {
        // given
        var inputStream = new ByteArrayInputStream(sampleArchive);

        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder = extractorBuilder.cpioInputStream();

        // when
        var cpioStream = inputStreamBuilder.build();
        assertThat(cpioStream).isNotNull();

        // then
        var entry = cpioStream.getNextEntry();
        assertThat(entry).isNotNull();
        assertThat(entry.getName()).isEqualTo("test.txt");

        cpioStream.close();
    }

    @Test
    void testBuilderWithCustomBlockSize() throws IOException {
        // given
        var inputStream = new ByteArrayInputStream(sampleArchive);

        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder = extractorBuilder.cpioInputStream().blockSize(1024);

        // when
        var cpioStream = inputStreamBuilder.build();
        assertThat(cpioStream).isNotNull();

        // then
        var entry = cpioStream.getNextEntry();
        assertThat(entry).isNotNull();
        assertThat(entry.getName()).isEqualTo("test.txt");

        cpioStream.close();
    }

    @Test
    void testBuilderWithCustomEncoding() throws IOException {
        // given
        Path specialFile = tempDir.resolve("special-äöü.txt");
        Files.write(specialFile, "Special content".getBytes());

        ByteArrayOutputStream archiveOutput = new ByteArrayOutputStream();
        try (CpioArchiveCreator creator = CpioArchiveCreator.builder(archiveOutput)
                .cpioOutputStream()
                .encoding("UTF-8")
                .and()
                .build()) {
            creator.add(EntrySource.file("special-äöü.txt", specialFile));
        }

        var inputStream = new ByteArrayInputStream(archiveOutput.toByteArray());

        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder = extractorBuilder.cpioInputStream().encoding("UTF-8");

        // when
        var cpioStream = inputStreamBuilder.build();
        assertThat(cpioStream).isNotNull();

        // then
        var entry = cpioStream.getNextEntry();
        assertThat(entry).isNotNull();
        assertThat(entry.getName()).isEqualTo("special-äöü.txt");

        cpioStream.close();
    }

    @Test
    void testBuilderWithAllCustomOptions() throws IOException {
        // given
        var inputStream = new ByteArrayInputStream(sampleArchive);
        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder =
                extractorBuilder.cpioInputStream().blockSize(2048).encoding("ISO-8859-1");

        // when
        var cpioStream = inputStreamBuilder.build();
        assertThat(cpioStream).isNotNull();

        // then
        var entry = cpioStream.getNextEntry();
        assertThat(entry).isNotNull();
        assertThat(entry.getName()).isEqualTo("test.txt");

        cpioStream.close();
    }

    @Test
    void testBuilderMethodChaining() {
        // given
        var inputStream = new ByteArrayInputStream(sampleArchive);
        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder = extractorBuilder.cpioInputStream();

        // when & then
        var chainedBuilder1 = inputStreamBuilder.blockSize(1024);
        assertThat(chainedBuilder1).isSameAs(inputStreamBuilder);

        var chainedBuilder2 = inputStreamBuilder.encoding("UTF-8");
        assertThat(chainedBuilder2).isSameAs(inputStreamBuilder);

        var parentBuilder = inputStreamBuilder.and();
        assertThat(parentBuilder).isSameAs(extractorBuilder);
    }

    @Test
    void testBuilderAndMethod() throws IOException {
        // given
        var inputStream = new ByteArrayInputStream(sampleArchive);

        // when
        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder = extractorBuilder.cpioInputStream();
        var returnedParent = inputStreamBuilder.and();

        // then
        assertThat(returnedParent).isSameAs(extractorBuilder);

        try (CpioArchiveExtractor extractor = returnedParent.build()) {
            assertThat(extractor).isNotNull();
        }
    }

    @Test
    void testBuilderWithVariousBlockSizes() throws IOException {
        // given
        int[] blockSizes = {256, 512, 1024, 2048, 4096, 8192};

        for (int blockSize : blockSizes) {
            var inputStream = new ByteArrayInputStream(sampleArchive);

            var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
            var inputStreamBuilder = extractorBuilder.cpioInputStream().blockSize(blockSize);

            // when
            var cpioStream = inputStreamBuilder.build();
            assertThat(cpioStream).isNotNull();

            // then
            var entry = cpioStream.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("test.txt");

            cpioStream.close();
        }
    }

    @Test
    void testBuilderWithVariousEncodings() throws IOException {
        // given
        String[] encodings = {"UTF-8", "ISO-8859-1", "US-ASCII"};

        for (String encoding : encodings) {
            var inputStream = new ByteArrayInputStream(sampleArchive);

            var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
            var inputStreamBuilder = extractorBuilder.cpioInputStream().encoding(encoding);

            // when
            var cpioStream = inputStreamBuilder.build();
            assertThat(cpioStream).isNotNull();

            // then
            var entry = cpioStream.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("test.txt");

            cpioStream.close();
        }
    }

    @Test
    void testBuilderWithInvalidBlockSize() {
        // given
        var inputStream = new ByteArrayInputStream(sampleArchive);
        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder = extractorBuilder.cpioInputStream().blockSize(-1);

        // when & then
        assertThatThrownBy(inputStreamBuilder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("blockSize must be bigger than 0");
    }

    @Test
    void testBuilderWithZeroBlockSize() {
        // given
        var inputStream = new ByteArrayInputStream(sampleArchive);
        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder = extractorBuilder.cpioInputStream().blockSize(0);

        // when & then
        assertThatThrownBy(inputStreamBuilder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("blockSize must be bigger than 0");
    }

    @Test
    void testBuilderReusability() throws IOException {
        // given
        var inputStream = new ByteArrayInputStream(sampleArchive);
        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder =
                extractorBuilder.cpioInputStream().blockSize(1024).encoding("UTF-8");

        // when & then
        var stream1 = inputStreamBuilder.build();
        assertThat(stream1).isNotNull();

        var entry1 = stream1.getNextEntry();
        assertThat(entry1).isNotNull();
        stream1.close();

        var inputStream2 = new ByteArrayInputStream(sampleArchive);
        var extractorBuilder2 = CpioArchiveExtractor.builder(inputStream2);
        var inputStreamBuilder2 =
                extractorBuilder2.cpioInputStream().blockSize(1024).encoding("UTF-8");

        var stream2 = inputStreamBuilder2.build();
        assertThat(stream2).isNotNull();

        var entry2 = stream2.getNextEntry();
        assertThat(entry2).isNotNull();
        stream2.close();
    }

    @Test
    void testBuilderConfigurationPersistence() throws IOException {
        // given
        var inputStream = new ByteArrayInputStream(sampleArchive);
        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder = extractorBuilder.cpioInputStream();
        inputStreamBuilder.blockSize(2048);
        inputStreamBuilder.encoding("ISO-8859-1");

        // when
        var cpioStream = inputStreamBuilder.build();
        assertThat(cpioStream).isNotNull();

        // then
        var entry = cpioStream.getNextEntry();
        assertThat(entry).isNotNull();
        assertThat(entry.getName()).isEqualTo("test.txt");

        cpioStream.close();
    }

    @Test
    void testBuilderWithLargeBlockSize() throws IOException {
        // given
        var inputStream = new ByteArrayInputStream(sampleArchive);
        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder = extractorBuilder.cpioInputStream().blockSize(65536);

        // when
        var cpioStream = inputStreamBuilder.build();

        // then
        assertThat(cpioStream).isNotNull();
        var entry = cpioStream.getNextEntry();
        assertThat(entry).isNotNull();
        assertThat(entry.getName()).isEqualTo("test.txt");

        cpioStream.close();
    }

    @Test
    @SuppressWarnings("try")
    void testBuilderWithEmptyArchive() throws IOException {
        // given
        ByteArrayOutputStream emptyArchiveOutput = new ByteArrayOutputStream();
        // noinspection EmptyTryBlock
        try (var ignored = CpioArchiveCreator.builder(emptyArchiveOutput).build()) {
            /* no-op */
        }
        byte[] emptyArchive = emptyArchiveOutput.toByteArray();

        // when
        var inputStream = new ByteArrayInputStream(emptyArchive);
        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder = extractorBuilder.cpioInputStream();
        var cpioStream = inputStreamBuilder.build();

        // then
        assertThat(cpioStream).isNotNull();

        var entryAtEof = cpioStream.getNextEntry();
        assertThat(entryAtEof).isNull();

        cpioStream.close();
    }

    @Test
    void testBuilderWithMultipleFiles() throws IOException {
        // given
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.write(file1, "Content 1".getBytes());
        Files.write(file2, "Content 2".getBytes());

        ByteArrayOutputStream archiveOutput = new ByteArrayOutputStream();
        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.add(EntrySource.file("file1.txt", file1));
            creator.add(EntrySource.file("file2.txt", file2));
        }

        // when
        var inputStream = new ByteArrayInputStream(archiveOutput.toByteArray());
        var extractorBuilder = CpioArchiveExtractor.builder(inputStream);
        var inputStreamBuilder =
                extractorBuilder.cpioInputStream().blockSize(1024).encoding("UTF-8");

        var cpioStream = inputStreamBuilder.build();

        // then
        assertThat(cpioStream).isNotNull();

        var entry1 = cpioStream.getNextEntry();
        assertThat(entry1).isNotNull();
        assertThat(entry1.getName()).isEqualTo("file1.txt");

        var entry2 = cpioStream.getNextEntry();
        assertThat(entry2).isNotNull();
        assertThat(entry2.getName()).isEqualTo("file2.txt");

        cpioStream.close();
    }

    private byte[] createSampleArchive() throws IOException {
        Path testFile = tempDir.resolve("setup-test.txt");
        Files.write(testFile, "Sample content for testing".getBytes());

        ByteArrayOutputStream archiveOutput = new ByteArrayOutputStream();
        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.add(EntrySource.file("test.txt", testFile));
        }
        return archiveOutput.toByteArray();
    }
}
