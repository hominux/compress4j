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
package com.hominux.compress4j.archivers.ar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.EntrySource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class ArArchiveCreatorBuilderTest {

    @Test
    void testBuilderWithOutputStream() throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        var builder = ArArchiveCreator.builder(outputStream);

        // when
        try (ArArchiveCreator creator = builder.build()) {
            creator.addFile("builder-test.txt", "Builder with OutputStream".getBytes(StandardCharsets.UTF_8));
        }

        // then
        assertThat(outputStream.size()).isGreaterThan(0);
        verifyArchiveContains(outputStream.toByteArray(), "builder-test.txt", "Builder with OutputStream");
    }

    @Test
    void testBuilderWithPath(@TempDir Path tempDir) throws IOException {
        // given
        var archivePath = tempDir.resolve("builder-test.ar");
        var builder = ArArchiveCreator.builder(archivePath);

        // when
        try (ArArchiveCreator creator = builder.build()) {
            creator.addFile("path-builder.txt", "Builder with Path".getBytes(StandardCharsets.UTF_8));
        }

        // then
        assertThat(archivePath).exists();
        assertThat(Files.size(archivePath)).isGreaterThan(0);

        byte[] archiveBytes = Files.readAllBytes(archivePath);
        verifyArchiveContains(archiveBytes, "path-builder.txt", "Builder with Path");
    }

    @Test
    void testBuilderWithEntryFilter(@TempDir Path tempDir) throws IOException {
        // given
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("inc.txt"), "Include this");
        Files.writeString(sourceDir.resolve("exc.txt"), "Exclude this");
        Files.writeString(sourceDir.resolve("inc.log"), "Include this too");

        var outputStream = new ByteArrayOutputStream();

        var builder =
                ArArchiveCreator.builder(outputStream).filter(s -> s.name().startsWith("inc"));

        // when
        try (ArArchiveCreator creator = builder.build()) {
            creator.addDirectoryRecursively(sourceDir);
        }

        // then
        byte[] archiveBytes = outputStream.toByteArray();
        verifyArchiveContains(archiveBytes, "inc.txt", "Include this");
        verifyArchiveContains(archiveBytes, "inc.log", "Include this too");
        verifyArchiveDoesNotContain(archiveBytes, "exc.txt");
    }

    @Test
    void testBuilderRejectsNullFilter() {
        var builder = ArArchiveCreator.builder(new ByteArrayOutputStream());

        assertThatThrownBy(() -> builder.filter(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate");
    }

    @Test
    void testBuilderChaining(@TempDir Path tempDir) throws IOException {
        // given
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("chain1.txt"), "Chain test 1");
        Files.writeString(sourceDir.resolve("chain2.txt"), "Chain test 2");
        Files.writeString(sourceDir.resolve("other.txt"), "Other file");

        var outputStream = new ByteArrayOutputStream();

        // when
        var builder =
                ArArchiveCreator.builder(outputStream).filter(s -> s.name().contains("chain"));
        try (ArArchiveCreator creator = builder.build()) {
            creator.addDirectoryRecursively(sourceDir);
        }

        // then
        byte[] archiveBytes = outputStream.toByteArray();
        verifyArchiveContains(archiveBytes, "chain1.txt", "Chain test 1");
        verifyArchiveContains(archiveBytes, "chain2.txt", "Chain test 2");
        verifyArchiveDoesNotContain(archiveBytes, "other.txt");
    }

    @Test
    void testBuildArchiveOutputStream() throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        var builder = ArArchiveCreator.builder(outputStream);

        // when
        ArArchiveOutputStream archiveOutputStream = builder.buildArchiveOutputStream();

        // then
        assertThat(archiveOutputStream).isNotNull();
        archiveOutputStream.close();
    }

    @Test
    void testBuilderReturnsThis() {
        // given
        var outputStream = new ByteArrayOutputStream();
        var builder = ArArchiveCreator.builder(outputStream);

        // when
        var result1 = builder.filter(s -> true);

        // then
        assertThat(result1).isSameAs(builder);
    }

    @Test
    void testBuilderWithExistingPath(@TempDir Path tempDir) throws IOException {
        // given
        var existingPath = tempDir.resolve("existing.ar");
        Files.writeString(existingPath, "existing content");

        var builder = ArArchiveCreator.builder(existingPath);

        // when
        try (ArArchiveCreator creator = builder.build()) {
            creator.addFile("new-content.txt", "New content".getBytes(StandardCharsets.UTF_8));
        }

        // then
        byte[] archiveBytes = Files.readAllBytes(existingPath);
        verifyArchiveContains(archiveBytes, "new-content.txt", "New content");
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void testBuilderWithUnwritablePath(@TempDir Path tempDir) throws IOException {
        // given
        Assumptions.assumeFalse("root".equals(System.getProperty("user.name")), "root ignores POSIX permissions");
        var readOnlyDir = tempDir.resolve("readonly");
        Files.createDirectory(readOnlyDir);
        Files.setPosixFilePermissions(readOnlyDir, PosixFilePermissions.fromString("r-xr-xr-x"));
        var unwritablePath = readOnlyDir.resolve("unwritable.ar");

        try {
            // when & then
            assertThatThrownBy(() -> ArArchiveCreator.builder(unwritablePath)).isInstanceOf(IOException.class);
        } finally {
            Files.setPosixFilePermissions(readOnlyDir, PosixFilePermissions.fromString("rwxrwxrwx"));
        }
    }

    @SuppressWarnings("java:S5778")
    @Test
    void testBuilderWithNullOutputStream() {
        // given
        var builder = ArArchiveCreator.builder((OutputStream) null);

        // when & then
        assertThatThrownBy(() -> {
                    try (ArArchiveCreator creator = builder.build()) {
                        creator.addFile("test.txt", "test".getBytes());
                    }
                })
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testBuilderWithNullPath() {
        // when & then
        assertThatThrownBy(() -> ArArchiveCreator.builder((Path) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testBuilderBuildMultipleTimes() throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        var builder = ArArchiveCreator.builder(outputStream);

        // when
        var creator1 = builder.build();
        var creator2 = builder.build();

        // then
        assertThat(creator1).isNotNull();
        assertThat(creator2).isNotNull();
        assertThat(creator1).isNotSameAs(creator2);

        creator1.close();
        creator2.close();
    }

    @Test
    void testBuilderWithComplexFilter(@TempDir Path tempDir) throws IOException {
        // given
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("important.txt"), "Important file");
        Files.writeString(sourceDir.resolve("backup.txt"), "Backup file");
        Files.writeString(sourceDir.resolve("temp.tmp"), "Temporary file");

        var outputStream = new ByteArrayOutputStream();

        var builder = ArArchiveCreator.builder(outputStream)
                .filter(s -> s.name().endsWith(".txt") && !s.name().contains("backup"));

        // when
        try (ArArchiveCreator creator = builder.build()) {
            creator.addDirectoryRecursively(sourceDir);
        }

        // then
        byte[] archiveBytes = outputStream.toByteArray();

        if (archiveBytes.length > 8) {
            verifyArchiveContains(archiveBytes, "important.txt", "Important file");
            verifyArchiveDoesNotContain(archiveBytes, "backup.txt");
            verifyArchiveDoesNotContain(archiveBytes, "temp.tmp");
        } else {
            assertThat(archiveBytes).isEmpty();
        }
    }

    @Test
    void testBuilderWithFilterUsingEntrySize(@TempDir Path tempDir) throws IOException {
        // given
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        var file1 = sourceDir.resolve("file1.txt");
        var file2 = sourceDir.resolve("file2.txt");
        Files.writeString(file1, "File 1 content");
        Files.writeString(file2, "File 2 content");

        var outputStream = new ByteArrayOutputStream();

        var builder = ArArchiveCreator.builder(outputStream)
                .filter(s -> !(s instanceof EntrySource.File f) || f.size().orElse(0) < 20);

        // when
        try (ArArchiveCreator creator = builder.build()) {
            creator.addDirectoryRecursively(sourceDir);
        }

        // then
        byte[] archiveBytes = outputStream.toByteArray();
        verifyArchiveContains(archiveBytes, "file1.txt", "File 1 content");
        verifyArchiveContains(archiveBytes, "file2.txt", "File 2 content");
    }

    private void verifyArchiveContains(byte[] archiveBytes, String fileName, String expectedContent)
            throws IOException {
        if (archiveBytes.length == 0) {
            throw new AssertionError("File " + fileName + " not found in archive - archive is empty");
        }

        try (ArArchiveInputStream ais = new ArArchiveInputStream(new ByteArrayInputStream(archiveBytes))) {
            var entry = ais.getNextEntry();
            boolean found = false;

            while (entry != null) {
                if (entry.getName().equals(fileName)) {
                    found = true;
                    byte[] content = ais.readAllBytes();
                    assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo(expectedContent);
                    break;
                }
                entry = ais.getNextEntry();
            }

            assertThat(found).as("File %s not found in archive", fileName).isTrue();
        }
    }

    private void verifyArchiveDoesNotContain(byte[] archiveBytes, String fileName) throws IOException {
        if (archiveBytes.length == 0) {
            return;
        }

        try (ArArchiveInputStream ais = new ArArchiveInputStream(new ByteArrayInputStream(archiveBytes))) {
            var entry = ais.getNextEntry();

            while (entry != null) {
                assertThat(entry.getName())
                        .as("File %s should not be in archive", fileName)
                        .isNotEqualTo(fileName);
                entry = ais.getNextEntry();
            }
        }
    }
}
