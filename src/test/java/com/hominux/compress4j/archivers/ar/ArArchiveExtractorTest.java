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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArArchiveExtractorTest {

    @Test
    void testExtractSingleFile(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        var content = "Hello, AR Archive!";

        try (var archiveOutputStream = new ArArchiveOutputStream(outputStream);
                var creator = new ArArchiveCreator(archiveOutputStream)) {
            creator.add(EntrySource.file("test.txt", content.getBytes(StandardCharsets.UTF_8)));
        }

        // when
        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        try (var archiveInputStream = new ArArchiveInputStream(bais);
                var extractor = new ArArchiveExtractor(archiveInputStream)) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("test.txt")).exists().hasContent(content);
    }

    @Test
    void testExtractMultipleFiles(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();

        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.add(EntrySource.file("file1.txt", "Content 1".getBytes(StandardCharsets.UTF_8)));
            creator.add(EntrySource.file("file2.txt", "Content 2".getBytes(StandardCharsets.UTF_8)));
            creator.add(EntrySource.file("file3.txt", "Content 3".getBytes(StandardCharsets.UTF_8)));
        }

        // when
        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        try (var extractor = ArArchiveExtractor.builder(bais).build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("file1.txt")).exists().hasContent("Content 1");
        assertThat(tempDir.resolve("file2.txt")).exists().hasContent("Content 2");
        assertThat(tempDir.resolve("file3.txt")).exists().hasContent(("Content 3"));
    }

    @Test
    void testExtractEmptyFile(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();

        // when
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.add(EntrySource.file("empty.txt", new byte[0]));
        }

        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        try (var extractor = ArArchiveExtractor.builder(bais).build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("empty.txt")).exists().isEmptyFile();
    }

    @Test
    void testExtractBinaryFile(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        byte[] binaryData = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryData[i] = (byte) i;
        }

        // when
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.add(EntrySource.file("binary.dat", binaryData));
        }

        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        try (var extractor = ArArchiveExtractor.builder(bais).build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("binary.dat")).exists().hasBinaryContent(binaryData);
    }

    @Test
    void testExtractFromPath(@TempDir Path tempDir) throws IOException {
        // given
        var archivePath = tempDir.resolve("test.ar");
        var content = "File from path test";

        try (var creator = ArArchiveCreator.builder(archivePath).build()) {
            creator.add(EntrySource.file("path-test.txt", content.getBytes(StandardCharsets.UTF_8)));
        }

        var extractDir = tempDir.resolve("extract");
        Files.createDirectories(extractDir);

        // when
        try (var extractor = ArArchiveExtractor.builder(archivePath).build()) {
            extractor.extract(extractDir);
        }

        // then
        assertThat(extractDir.resolve("path-test.txt")).exists().hasContent(content);
    }

    @Test
    void testExtractWithFilter(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();

        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.add(EntrySource.file("keep.txt", "Keep this".getBytes(StandardCharsets.UTF_8)));
            creator.add(EntrySource.file("skip.txt", "Skip this".getBytes(StandardCharsets.UTF_8)));
            creator.add(EntrySource.file("keep.log", "Keep this too".getBytes(StandardCharsets.UTF_8)));
        }

        // when
        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        try (var extractor = ArArchiveExtractor.builder(bais)
                .filter(entry -> entry.name().startsWith("keep"))
                .build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("keep.txt")).exists();
        assertThat(tempDir.resolve("skip.txt")).doesNotExist();
        assertThat(tempDir.resolve("keep.log")).exists();
    }

    @Test
    void testExtractToSpecificPath(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        var content = "Specific path test";

        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.add(EntrySource.file("specific.txt", content.getBytes(StandardCharsets.UTF_8)));
        }

        // when
        var specificDir = tempDir.resolve("specific").resolve("subdir");
        Files.createDirectories(specificDir);

        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        try (var extractor = ArArchiveExtractor.builder(bais).build()) {
            extractor.extract(specificDir);
        }

        // then
        assertThat(specificDir.resolve("specific.txt")).exists().hasContent(content);
    }

    @Test
    void testExtractEmptyArchive(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.add(EntrySource.file("placeholder", new byte[0]));
        }

        // when
        var bais = new ByteArrayInputStream(outputStream.toByteArray());
        try (var extractor = ArArchiveExtractor.builder(bais).build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("placeholder")).exists().isEmptyFile();
    }

    @SuppressWarnings("java:S5783")
    @Test
    void testExtractWithInvalidInputStream(@TempDir Path tempDir) {
        // given
        InputStream invalidStream = new ByteArrayInputStream("not an ar archive".getBytes());

        // when & then
        assertThatThrownBy(() -> {
                    try (var extractor =
                            ArArchiveExtractor.builder(invalidStream).build()) {
                        extractor.extract(tempDir);
                    }
                })
                .isInstanceOf(IOException.class);
    }

    @Test
    void testExtractorAutoCloseable(@TempDir Path tempDir) throws IOException {
        // given
        var outputStream = new ByteArrayOutputStream();
        try (var creator = ArArchiveCreator.builder(outputStream).build()) {
            creator.add(EntrySource.file("closeable.txt", "test".getBytes(StandardCharsets.UTF_8)));
        }

        // when
        try (var bais = new ByteArrayInputStream(outputStream.toByteArray());
                var extractor = ArArchiveExtractor.builder(bais).build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("closeable.txt")).exists();
    }

    @Test
    void nextEntryShouldBeEmptyForEmptyArchive() throws IOException {
        var emptyArchive = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);
        try (var extractor = new ArArchiveExtractor(new ArArchiveInputStream(new ByteArrayInputStream(emptyArchive)))) {
            assertThat(extractor.nextEntry()).isEmpty();
        }
    }
}
