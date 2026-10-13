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
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
        var outputStream = new ByteArrayOutputStream();
        Path testFile = writeSource();

        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(outputStream).build()) {
            creator.add(EntrySource.file("test.txt", testFile));
        }

        CpioArchiveEntry entry = firstEntry(outputStream.toByteArray(), 512);
        assertThat(entry.getName()).isEqualTo("test.txt");
        assertThat(entry.getFormat()).isEqualTo(CpioConstants.FORMAT_NEW);
    }

    static Stream<Arguments> formats() {
        return Stream.of(
                Arguments.of(CpioFormat.NEW, CpioConstants.FORMAT_NEW),
                Arguments.of(CpioFormat.OLD_ASCII, CpioConstants.FORMAT_OLD_ASCII),
                Arguments.of(CpioFormat.OLD_BINARY, CpioConstants.FORMAT_OLD_BINARY));
    }

    @ParameterizedTest
    @MethodSource("formats")
    void testBuilderWritesRequestedFormat(CpioFormat format, short expected) throws IOException {
        var outputStream = new ByteArrayOutputStream();
        Path testFile = writeSource();

        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(outputStream).format(format).build()) {
            creator.add(EntrySource.file("test.txt", testFile));
        }

        CpioArchiveEntry entry = firstEntry(outputStream.toByteArray(), 512);
        assertThat(entry.getName()).isEqualTo("test.txt");
        assertThat(entry.getFormat()).isEqualTo(expected);
    }

    @Test
    void testBuilderWithCustomBlockSize() throws IOException {
        var outputStream = new ByteArrayOutputStream();
        Path testFile = writeSource();

        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(outputStream).blockSize(1024).build()) {

            creator.add(EntrySource.file("test.txt", testFile));
        }

        assertThat(outputStream.size()).isGreaterThan(0);
        assertThat(firstEntry(outputStream.toByteArray(), 1024).getName()).isEqualTo("test.txt");
    }

    @Test
    void testBuilderWithCustomEncoding() throws IOException {
        var outputStream = new ByteArrayOutputStream();
        Path testFile = tempDir.resolve("source.txt");
        Files.write(testFile, "Test content with special chars".getBytes());

        try (CpioArchiveCreator creator = CpioArchiveCreator.builder(outputStream)
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            creator.add(EntrySource.file("test-äöü.txt", testFile));
        }

        assertThat(firstEntry(outputStream.toByteArray(), 512, "UTF-8").getName())
                .isEqualTo("test-äöü.txt");
    }

    @Test
    void testBuilderWithAllCustomOptions() throws IOException {
        var outputStream = new ByteArrayOutputStream();
        Path testFile = tempDir.resolve("test-complete.txt");
        Files.write(testFile, "Complete configuration test".getBytes());

        try (CpioArchiveCreator creator = CpioArchiveCreator.builder(outputStream)
                .format(CpioFormat.OLD_ASCII)
                .blockSize(2048)
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            creator.add(EntrySource.file("test-complete.txt", testFile));
        }

        CpioArchiveEntry entry = firstEntry(outputStream.toByteArray(), 2048, "UTF-8");
        assertThat(entry.getName()).isEqualTo("test-complete.txt");
        assertThat(entry.getSize()).isEqualTo("Complete configuration test".length());
        assertThat(entry.getFormat()).isEqualTo(CpioConstants.FORMAT_OLD_ASCII);
    }

    @Test
    void testBuilderChaining() throws IOException {
        var outputStream = new ByteArrayOutputStream();
        Path testFile = writeSource();

        CpioArchiveCreator.Builder builder = CpioArchiveCreator.builder(outputStream)
                .format(CpioFormat.OLD_BINARY)
                .blockSize(1024)
                .encoding(StandardCharsets.UTF_8);

        try (CpioArchiveCreator creator = builder.build()) {
            creator.add(EntrySource.file("test.txt", testFile));
        }

        CpioArchiveEntry entry = firstEntry(outputStream.toByteArray(), 1024, "UTF-8");
        assertThat(entry.getFormat()).isEqualTo(CpioConstants.FORMAT_OLD_BINARY);
        assertThat(entry.getName()).isEqualTo("test.txt");
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
        Path testFile = writeSource();
        var first = new ByteArrayOutputStream();
        var builder = CpioArchiveCreator.builder(first).format(CpioFormat.OLD_ASCII);

        try (CpioArchiveCreator creator1 = builder.build()) {
            creator1.add(EntrySource.file("test.txt", testFile));
        }

        try (CpioArchiveCreator creator2 = builder.build()) {
            creator2.add(EntrySource.file("again.txt", testFile));
        }

        var bytes = first.toByteArray();
        assertThat(firstEntry(bytes, 512).getName()).isEqualTo("test.txt");
        assertThat(firstEntry(bytes, 512).getFormat()).isEqualTo(CpioConstants.FORMAT_OLD_ASCII);
    }

    @Test
    void testBuilderSettersReturnSameBuilder() {
        var builder = CpioArchiveCreator.builder(new ByteArrayOutputStream());

        assertThat(builder.format(CpioFormat.NEW)).isSameAs(builder);
        assertThat(builder.blockSize(1024)).isSameAs(builder);
        assertThat(builder.encoding(StandardCharsets.UTF_8)).isSameAs(builder);
    }

    @ParameterizedTest
    @ValueSource(ints = {256, 512, 1024, 2048, 4096})
    void testBuilderWithDifferentBlockSizes(int blockSize) throws IOException {
        Path testFile = writeSource();
        var outputStream = new ByteArrayOutputStream();

        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(outputStream).blockSize(blockSize).build()) {
            creator.add(EntrySource.file("test.txt", testFile));
        }

        assertThat(outputStream.size()).isGreaterThan(0);
        assertThat(firstEntry(outputStream.toByteArray(), blockSize).getName()).isEqualTo("test.txt");
    }

    static Stream<Arguments> encodings() {
        return Stream.of(
                Arguments.of("UTF-8", "test-äöü.txt"),
                Arguments.of("ISO-8859-1", "test-äöü.txt"),
                Arguments.of("US-ASCII", "test-encoding.txt"));
    }

    @ParameterizedTest
    @MethodSource("encodings")
    void testBuilderWithDifferentEncodings(String encoding, String entryName) throws IOException {
        Path testFile = writeSource();
        var outputStream = new ByteArrayOutputStream();
        Charset charset = Charset.forName(encoding);

        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(outputStream).encoding(charset).build()) {
            creator.add(EntrySource.file(entryName, testFile));
        }

        assertThat(firstEntry(outputStream.toByteArray(), 512, encoding).getName())
                .isEqualTo(entryName);
        assertThat(new String(outputStream.toByteArray(), StandardCharsets.ISO_8859_1))
                .contains(new String(entryName.getBytes(charset), StandardCharsets.ISO_8859_1));
    }

    private Path writeSource() throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Test content".getBytes());
        return testFile;
    }

    private static CpioArchiveEntry firstEntry(byte[] archive, int blockSize) throws IOException {
        try (var in = new CpioArchiveInputStream(new ByteArrayInputStream(archive), blockSize)) {
            return in.getNextEntry();
        }
    }

    private static CpioArchiveEntry firstEntry(byte[] archive, int blockSize, String encoding) throws IOException {
        try (var in = new CpioArchiveInputStream(new ByteArrayInputStream(archive), blockSize, encoding)) {
            return in.getNextEntry();
        }
    }
}
