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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CpioArchiveCreatorBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void testBuilderWithPath() throws IOException {
        Path archivePath = tempDir.resolve("test-builder-path.cpio");
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Test content".getBytes());

        CpioArchiveCreator.Builder builder = CpioArchiveCreator.builder(archivePath);
        assertThat(builder).isNotNull();

        try (CpioArchiveCreator creator = builder.build()) {
            creator.add(EntrySource.file("test.txt", testFile));
        }

        assertThat(archivePath).exists();
        assertThat(Files.size(archivePath)).isGreaterThan(0);
    }

    @Test
    void testBuilderWithOutputStream() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Test content".getBytes());

        CpioArchiveCreator.Builder builder = CpioArchiveCreator.builder(outputStream);
        assertThat(builder).isNotNull();

        try (CpioArchiveCreator creator = builder.build()) {
            creator.add(EntrySource.file("test.txt", testFile));
        }

        assertThat(outputStream.size()).isGreaterThan(0);
    }

    @Test
    void testBuilderWithDefaultConfiguration() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Test content".getBytes());

        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(outputStream).build()) {
            creator.add(EntrySource.file("test.txt", testFile));
        }

        assertThat(outputStream.size()).isGreaterThan(0);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        try (CpioArchiveInputStream cpioInput = new CpioArchiveInputStream(inputStream)) {
            var entry = cpioInput.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("test.txt");
        }
    }

    @Test
    void testBuilderWithNewFormat() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Test content".getBytes());

        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(outputStream).format(CpioFormat.NEW).build()) {

            creator.add(EntrySource.file("test.txt", testFile));
        }

        assertThat(outputStream.size()).isGreaterThan(0);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        try (CpioArchiveInputStream cpioInput = new CpioArchiveInputStream(inputStream)) {
            var entry = cpioInput.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("test.txt");
        }
    }

    @Test
    void testBuilderWithOldAsciiFormat() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Test content".getBytes());

        try (CpioArchiveCreator creator = CpioArchiveCreator.builder(outputStream)
                .format(CpioFormat.OLD_ASCII)
                .build()) {
            creator.add(EntrySource.file("test.txt", testFile));
        }

        assertThat(outputStream.size()).isGreaterThan(0);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        try (CpioArchiveInputStream cpioInput = new CpioArchiveInputStream(inputStream)) {
            var entry = cpioInput.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("test.txt");
        }
    }

    @Test
    void testBuilderWithOldBinaryFormat() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Test content".getBytes());

        try (CpioArchiveCreator creator = CpioArchiveCreator.builder(outputStream)
                .format(CpioFormat.OLD_BINARY)
                .build()) {
            creator.add(EntrySource.file("test.txt", testFile));
        }

        assertThat(outputStream.size()).isGreaterThan(0);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        try (CpioArchiveInputStream cpioInput = new CpioArchiveInputStream(inputStream)) {
            var entry = cpioInput.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("test.txt");
        }
    }

    @Test
    void testBuilderWithCustomBlockSize() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Test content".getBytes());

        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(outputStream).blockSize(1024).build()) {

            creator.add(EntrySource.file("test.txt", testFile));
        }

        assertThat(outputStream.size()).isGreaterThan(0);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        try (CpioArchiveInputStream cpioInput = new CpioArchiveInputStream(inputStream, 1024)) {
            var entry = cpioInput.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("test.txt");
        }
    }

    @Test
    void testBuilderWithCustomEncoding() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Path testFile = tempDir.resolve("test-äöü.txt");
        Files.write(testFile, "Test content with special chars".getBytes());

        try (CpioArchiveCreator creator = CpioArchiveCreator.builder(outputStream)
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            creator.add(EntrySource.file("test-äöü.txt", testFile));
        }

        assertThat(outputStream.size()).isGreaterThan(0);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        try (CpioArchiveInputStream cpioInput = new CpioArchiveInputStream(inputStream, 512, "UTF-8")) {
            var entry = cpioInput.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("test-äöü.txt");
        }
    }

    @Test
    void testBuilderWithAllCustomOptions() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Path testFile = tempDir.resolve("test-complete.txt");
        Files.write(testFile, "Complete configuration test".getBytes());

        try (CpioArchiveCreator creator = CpioArchiveCreator.builder(outputStream)
                .format(CpioFormat.NEW)
                .blockSize(2048)
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            creator.add(EntrySource.file("test-complete.txt", testFile));
        }

        assertThat(outputStream.size()).isGreaterThan(0);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        try (CpioArchiveInputStream cpioInput = new CpioArchiveInputStream(inputStream, 2048, "UTF-8")) {
            var entry = cpioInput.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("test-complete.txt");
            assertThat(entry.getSize()).isEqualTo("Complete configuration test".length());
        }
    }

    @Test
    void testBuilderChaining() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        CpioArchiveCreator.Builder builder = CpioArchiveCreator.builder(outputStream)
                .format(CpioFormat.NEW)
                .blockSize(1024)
                .encoding(StandardCharsets.UTF_8);

        assertThat(builder).isNotNull();

        try (CpioArchiveCreator creator = builder.build()) {
            assertThat(creator).isNotNull();
        }
    }

    @Test
    void testBuilderWithInvalidPath() {
        Path invalidPath = tempDir.resolve("nonexistent/invalid.cpio");

        assertThatThrownBy(() -> CpioArchiveCreator.builder(invalidPath).build())
                .isInstanceOf(IOException.class);
    }

    @Test
    void testBuilderWithNullOutputStream() {
        assertThatThrownBy(() -> CpioArchiveCreator.builder((ByteArrayOutputStream) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testBuilderWithNullPath() {
        assertThatThrownBy(() -> CpioArchiveCreator.builder((Path) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testMultipleBuildsFromSameBuilder() throws IOException {
        ByteArrayOutputStream outputStream1 = new ByteArrayOutputStream();
        CpioArchiveCreator.Builder builder = CpioArchiveCreator.builder(outputStream1);

        try (CpioArchiveCreator creator1 = builder.build()) {
            assertThat(creator1).isNotNull();
        }

        try (CpioArchiveCreator creator2 = builder.build()) {
            assertThat(creator2).isNotNull();
        }
    }

    @Test
    void testBuilderOutputStreamConfiguration() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        var builderInstance = CpioArchiveCreator.builder(outputStream);
        var cpioOutputStreamBuilder = builderInstance;

        assertThat(cpioOutputStreamBuilder).isNotNull();

        var configuredBuilder =
                cpioOutputStreamBuilder.format(CpioFormat.NEW).blockSize(1024).encoding(StandardCharsets.UTF_8);

        assertThat(configuredBuilder).isSameAs(builderInstance);

        try (CpioArchiveCreator creator = configuredBuilder.build()) {
            assertThat(creator).isNotNull();
        }
    }

    @Test
    void testBuilderGetThis() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CpioArchiveCreator.Builder builder = CpioArchiveCreator.builder(outputStream);

        try (CpioArchiveCreator creator = builder.build()) {
            assertThat(creator).isNotNull();
        }
    }

    @Test
    void testBuilderWithDifferentBlockSizes() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Test content".getBytes());

        int[] blockSizes = {256, 512, 1024, 2048, 4096};

        for (int blockSize : blockSizes) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            try (CpioArchiveCreator creator = CpioArchiveCreator.builder(outputStream)
                    .blockSize(blockSize)
                    .build()) {
                creator.add(EntrySource.file("test.txt", testFile));
            }

            assertThat(outputStream.size()).isGreaterThan(0);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            try (CpioArchiveInputStream cpioInput = new CpioArchiveInputStream(inputStream, blockSize)) {
                var entry = cpioInput.getNextEntry();
                assertThat(entry).isNotNull();
                assertThat(entry.getName()).isEqualTo("test.txt");
            }
        }
    }

    @Test
    void testBuilderWithDifferentEncodings() throws IOException {
        Path testFile = tempDir.resolve("test-encoding.txt");
        Files.write(testFile, "Test content".getBytes());

        String[] encodings = {"UTF-8", "ISO-8859-1", "US-ASCII"};

        for (String encoding : encodings) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            try (CpioArchiveCreator creator = CpioArchiveCreator.builder(outputStream)
                    .encoding(Charset.forName(encoding))
                    .build()) {
                creator.add(EntrySource.file("test-encoding.txt", testFile));
            }

            assertThat(outputStream.size()).isGreaterThan(0);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            try (CpioArchiveInputStream cpioInput = new CpioArchiveInputStream(inputStream, 512, encoding)) {
                var entry = cpioInput.getNextEntry();
                assertThat(entry).isNotNull();
                assertThat(entry.getName()).isEqualTo("test-encoding.txt");
            }
        }
    }
}
