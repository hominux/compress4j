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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CpioArchiveCreatorTest {

    @TempDir
    Path tempDir;

    @Test
    void testCreateCpioArchiveWithFiles() throws IOException {
        // given
        var testFile1 = tempDir.resolve("test1.txt");
        var testFile2 = tempDir.resolve("test2.txt");
        Files.write(testFile1, "Hello World 1".getBytes());
        Files.write(testFile2, "Hello World 2".getBytes());

        // when
        var archiveOutput = new ByteArrayOutputStream();
        try (var archiveOutputStream = new CpioArchiveOutputStream(archiveOutput);
                var creator = new CpioArchiveCreator(archiveOutputStream)) {
            creator.addFile("test1.txt", testFile1);
            creator.addFile("test2.txt", testFile2);
        }

        // then
        assertThat(archiveOutput.size()).isGreaterThan(0);

        var archiveInput = new ByteArrayInputStream(archiveOutput.toByteArray());
        try (var cpioInput = new CpioArchiveInputStream(archiveInput)) {
            var entry1 = cpioInput.getNextEntry();
            assertThat(entry1).isNotNull();
            assertThat(entry1.getName()).isEqualTo("test1.txt");
            assertThat(entry1.getSize()).isEqualTo("Hello World 1".length());

            var entry2 = cpioInput.getNextEntry();
            assertThat(entry2).isNotNull();
            assertThat(entry2.getName()).isEqualTo("test2.txt");
            assertThat(entry2.getSize()).isEqualTo("Hello World 2".length());

            var trailer = cpioInput.getNextEntry();
            assertThat(trailer).isNull();
        }
    }

    @Test
    void testCreateCpioArchiveWithDirectories() throws IOException {
        // given
        var subDir = tempDir.resolve("subdir");
        Files.createDirectories(subDir);
        var testFile = subDir.resolve("nested.txt");
        Files.write(testFile, "Nested content".getBytes());

        // when
        var archiveOutput = new ByteArrayOutputStream();
        try (var creator = CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.addDirectory("subdir/", FileTime.fromMillis(System.currentTimeMillis()));
            creator.addFile("subdir/nested.txt", testFile);
        }

        // then
        assertThat(archiveOutput.size()).isGreaterThan(0);

        var archiveInput = new ByteArrayInputStream(archiveOutput.toByteArray());
        try (var cpioInput = new CpioArchiveInputStream(archiveInput)) {
            var dirEntry = cpioInput.getNextEntry();
            assertThat(dirEntry).isNotNull();
            assertThat(dirEntry.getName()).isEqualTo("subdir/");
            assertThat(dirEntry.isDirectory()).isTrue();

            var fileEntry = cpioInput.getNextEntry();
            assertThat(fileEntry).isNotNull();
            assertThat(fileEntry.getName()).isEqualTo("subdir/nested.txt");
            assertThat(fileEntry.getSize()).isEqualTo("Nested content".length());
        }
    }

    @Test
    void testCpioArchiveBuilderConfiguration() throws IOException {
        // given
        var archiveOutput = new ByteArrayOutputStream();

        // when
        try (var creator = CpioArchiveCreator.builder(archiveOutput)
                .cpioOutputStream()
                .format(CpioConstants.FORMAT_NEW)
                .blockSize(512)
                .encoding("UTF-8")
                .and()
                .build()) {

            var testFile = tempDir.resolve("config-test.txt");
            Files.write(testFile, "Configuration test".getBytes());
            creator.addFile("config-test.txt", testFile);
        }

        // then
        assertThat(archiveOutput.size()).isGreaterThan(0);
    }

    @Test
    void testCpioArchiveBuilderWithOldFormat() throws IOException {
        // given
        var archiveOutput = new ByteArrayOutputStream();

        // when
        try (var creator = CpioArchiveCreator.builder(archiveOutput)
                .cpioOutputStream()
                .format(CpioConstants.FORMAT_OLD_ASCII)
                .blockSize(1024)
                .encoding("UTF-8")
                .and()
                .build()) {

            var testFile = tempDir.resolve("old-format-test.txt");
            Files.write(testFile, "Old format test".getBytes());
            creator.addFile("old-format-test.txt", testFile);
        }

        // then
        assertThat(archiveOutput.size()).isGreaterThan(0);
    }

    @Test
    void testCreateCpioArchiveFromPath() throws IOException {
        // given
        var archivePath = tempDir.resolve("test.cpio");
        var testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Test content".getBytes());

        // when
        try (var creator = CpioArchiveCreator.builder(archivePath).build()) {
            creator.addFile("test.txt", testFile);
        }

        // then
        assertThat(archivePath).exists();
        assertThat(Files.size(archivePath)).isGreaterThan(0);
    }

    @Test
    void testAddEmptyFile() throws IOException {
        // given
        var emptyFile = tempDir.resolve("empty.txt");
        Files.createFile(emptyFile);

        // when
        var archiveOutput = new ByteArrayOutputStream();
        try (var creator = CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.addFile("empty.txt", emptyFile);
        }

        // then
        assertThat(archiveOutput.size()).isGreaterThan(0);

        var archiveInput = new ByteArrayInputStream(archiveOutput.toByteArray());
        try (var cpioInput = new CpioArchiveInputStream(archiveInput)) {
            var entry = cpioInput.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("empty.txt");
            assertThat(entry.getSize()).isZero();
        }
    }

    @Test
    void testAddMultipleDirectories() throws IOException {
        // given
        var dir2 = tempDir.resolve("dir1").resolve("dir2");
        Files.createDirectories(dir2);

        // when
        var archiveOutput = new ByteArrayOutputStream();
        try (var creator = CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.addDirectory("dir1/", FileTime.fromMillis(System.currentTimeMillis()));
            creator.addDirectory("dir1/dir2/", FileTime.fromMillis(System.currentTimeMillis()));
        }

        // then
        assertThat(archiveOutput.size()).isGreaterThan(0);

        var archiveInput = new ByteArrayInputStream(archiveOutput.toByteArray());
        try (var cpioInput = new CpioArchiveInputStream(archiveInput)) {
            var entry1 = cpioInput.getNextEntry();
            assertThat(entry1).isNotNull();
            assertThat(entry1.getName()).isEqualTo("dir1/");
            assertThat(entry1.isDirectory()).isTrue();

            var entry2 = cpioInput.getNextEntry();
            assertThat(entry2).isNotNull();
            assertThat(entry2.getName()).isEqualTo("dir1/dir2/");
            assertThat(entry2.isDirectory()).isTrue();
        }
    }

    @Test
    void testBuilderWithInvalidPath() {
        // given
        var invalidPath = tempDir.resolve("nonexistent/invalid.cpio");

        // when & then
        assertThatThrownBy(() -> CpioArchiveCreator.builder(invalidPath)).isInstanceOf(IOException.class);
    }

    @Test
    void testAddFileWithSpecialCharacters() throws IOException {
        // given
        var specialFile = tempDir.resolve("special-chars äöü.txt");
        Files.write(specialFile, "Special content".getBytes());

        // when
        var archiveOutput = new ByteArrayOutputStream();
        try (var creator = CpioArchiveCreator.builder(archiveOutput)
                .cpioOutputStream()
                .encoding("UTF-8")
                .and()
                .build()) {
            creator.addFile("special-chars äöü.txt", specialFile);
        }

        // then
        assertThat(archiveOutput.size()).isGreaterThan(0);

        var archiveInput = new ByteArrayInputStream(archiveOutput.toByteArray());
        try (var cpioInput = new CpioArchiveInputStream(archiveInput, 512, "UTF-8")) {
            var entry = cpioInput.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("special-chars äöü.txt");
        }
    }

    @Test
    void writeFileEntryShouldStoreSymlinkTargetAsContent() throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        var modTime = FileTime.from(Instant.parse("2023-01-01T00:00:00Z"));

        // when
        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(outputStream).build()) {
            creator.writeSymlink("link", "target.txt", 0, modTime);
        }

        // then
        try (var cis = new CpioArchiveInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            CpioArchiveEntry entry = cis.getNextEntry();
            assertThat(entry.getName()).isEqualTo("link");
            assertThat(entry.isSymbolicLink()).isTrue();
            assertThat(new String(cis.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("target.txt");
        }
    }
}
