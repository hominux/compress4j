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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArArchiveExtractorBuilderTest {

    @Test
    void testBuilderWithInputStream(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.addFile("test.txt", "Builder test".getBytes(StandardCharsets.UTF_8));
        }

        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        var builder = ArArchiveExtractor.builder(bais);

        // when
        try (var extractor = builder.build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("test.txt")).exists().hasContent("Builder test");
    }

    @Test
    void testBuilderWithPath(@TempDir Path tempDir) throws IOException {
        // given
        var archivePath = tempDir.resolve("test.ar");
        try (var creator = ArArchiveCreator.builder(archivePath).build()) {
            creator.addFile("path-test.txt", "Path builder test".getBytes(StandardCharsets.UTF_8));
        }

        var builder = ArArchiveExtractor.builder(archivePath);

        var extractDir = tempDir.resolve("extract");
        Files.createDirectories(extractDir);

        // when
        try (var extractor = builder.build()) {
            extractor.extract(extractDir);
        }

        // then
        assertThat(extractDir.resolve("path-test.txt")).exists().hasContent("Path builder test");
    }

    @Test
    void testBuilderWithEntryFilter(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.addFile("include.txt", "Include this".getBytes(StandardCharsets.UTF_8));
            creator.addFile("exclude.txt", "Exclude this".getBytes(StandardCharsets.UTF_8));
            creator.addFile("include.log", "Include this too".getBytes(StandardCharsets.UTF_8));
        }

        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        var builder =
                ArArchiveExtractor.builder(bais).filter(entry -> entry.name().startsWith("include"));

        // when
        try (var extractor = builder.build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("include.txt")).exists();
        assertThat(tempDir.resolve("exclude.txt")).doesNotExist();
        assertThat(tempDir.resolve("include.log")).exists();
    }

    @Test
    void testBuilderWithErrorHandler(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.addFile("error-test.txt", "Error handling test".getBytes(StandardCharsets.UTF_8));
        }

        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        var builder = ArArchiveExtractor.builder(bais)
                .errorHandler((entry, exception) -> ArArchiveExtractor.ErrorHandlerChoice.SKIP);

        // when
        try (var extractor = builder.build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("error-test.txt")).exists();
    }

    @Test
    void testBuilderWithOverwriteOption(@TempDir Path tempDir) throws IOException {
        // given
        var existingFile = tempDir.resolve("existing.txt");
        Files.writeString(existingFile, "Original content");

        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.addFile("existing.txt", "New content".getBytes(StandardCharsets.UTF_8));
        }

        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        var builder = ArArchiveExtractor.builder(bais).overwrite(true);

        try (var extractor = builder.build()) {
            extractor.extract(tempDir);
        }

        assertThat(existingFile).hasContent("New content");
    }

    @Test
    void testBuilderWithStripComponents(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.addFile("file.txt", "Nested file".getBytes(StandardCharsets.UTF_8));
        }

        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        var builder = ArArchiveExtractor.builder(bais).stripComponents(0);

        // when
        try (var extractor = builder.build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("file.txt")).exists().hasContent("Nested file");
    }

    @Test
    void testBuilderWithPostProcessor(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.addFile("process.txt", "Process me".getBytes(StandardCharsets.UTF_8));
        }

        var processLog = new StringBuilder();
        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        var builder = ArArchiveExtractor.builder(bais).postProcessor((entry, path) -> processLog
                .append("Processed: ")
                .append(entry.name())
                .append(" at ")
                .append(path)
                .append("\n"));

        // when
        try (var extractor = builder.build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(processLog.toString()).contains("Processed: process.txt").contains(tempDir.toString());
    }

    @Test
    void testBuilderChaining(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.addFile("chain1.txt", "Chain test 1".getBytes(StandardCharsets.UTF_8));
            creator.addFile("chain2.txt", "Chain test 2".getBytes(StandardCharsets.UTF_8));
        }

        var log = new StringBuilder();
        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        var builder = ArArchiveExtractor.builder(bais)
                .filter(entry -> entry.name().contains("chain"))
                .overwrite(true)
                .postProcessor((entry, path) ->
                        log.append("Processed: ").append(entry.name()).append("\n"))
                .errorHandler((entry, ex) -> ArArchiveExtractor.ErrorHandlerChoice.SKIP);

        // when
        try (var extractor = builder.build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("chain1.txt")).exists();
        assertThat(tempDir.resolve("chain2.txt")).exists();
        assertThat(log.toString()).contains("Processed: chain1.txt").contains("Processed: chain2.txt");
    }

    @Test
    void testBuildArchiveInputStream() throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.addFile("stream-test.txt", "Stream test".getBytes(StandardCharsets.UTF_8));
        }

        // when
        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        var builder = ArArchiveExtractor.builder(bais);
        ArArchiveInputStream archiveInputStream = builder.buildArchiveInputStream();

        // then
        assertThat(archiveInputStream).isNotNull();
        assertThat(archiveInputStream.getNextEntry()).isNotNull();
        archiveInputStream.close();
    }

    @Test
    void testBuilderReturnsThis() {
        // given
        var bais = new ByteArrayInputStream(new byte[0]);

        // when
        var builder = ArArchiveExtractor.builder(bais);

        // then
        assertThat(builder)
                .isSameAs(builder.filter(entry -> true))
                .isSameAs(builder.overwrite(true))
                .isSameAs(builder.errorHandler((entry, ex) -> ArArchiveExtractor.ErrorHandlerChoice.SKIP));
    }

    @Test
    void testBuilderWithNonExistentPath() {
        // given
        var nonExistentPath = Path.of("/this/path/does/not/exist.ar");

        // when & then
        assertThatThrownBy(() -> ArArchiveExtractor.builder(nonExistentPath)).isInstanceOf(IOException.class);
    }

    @SuppressWarnings("java:S5778")
    @Test
    void testBuilderWithNullInputStream() {
        // given
        var builder = ArArchiveExtractor.builder((InputStream) null);

        // when & then
        assertThatThrownBy(() -> {
                    try (var extractor = builder.build()) {
                        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
                        extractor.extract(tempDir);
                    }
                })
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testBuilderWithNullPath() {
        // when & then
        assertThatThrownBy(() -> ArArchiveExtractor.builder((Path) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testBuilderWithEscapingSymlinkPolicy(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.addFile("policy-test.txt", "Symlink policy test".getBytes(StandardCharsets.UTF_8));
        }

        // when
        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        var builder = ArArchiveExtractor.builder(bais)
                .escapingSymlinkPolicy(ArArchiveExtractor.EscapingSymlinkPolicy.DISALLOW);

        try (var extractor = builder.build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("policy-test.txt")).exists();
    }

    @Test
    void testBuilderWithEscapingSymlinkPolicyRejectsEscapingTarget(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.writeSymlink("escape-link", "../../etc/passwd", 0, FileTime.from(Instant.now()));
        }

        // when
        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        try (var extractor = ArArchiveExtractor.builder(bais)
                .escapingSymlinkPolicy(ArArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .build()) {

            // then
            assertThatThrownBy(() -> extractor.extract(tempDir))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Invalid symlink (points outside of output directory): escape-link");

            assertThat(tempDir.resolve("escape-link")).doesNotExist();
        }
    }
}
