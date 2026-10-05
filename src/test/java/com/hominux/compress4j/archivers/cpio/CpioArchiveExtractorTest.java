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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.archivers.EntrySource;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.internal.archive.ReaderContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class CpioArchiveExtractorTest {

    @TempDir
    Path tempDir;

    private byte[] sampleArchive;
    private byte[] directoryArchive;

    @BeforeEach
    void setUp() throws IOException {
        sampleArchive = createSampleArchive();
        directoryArchive = createDirectoryArchive();
    }

    @Test
    void testExtractCpioArchive() throws IOException {
        // given
        var extractDir = tempDir.resolve("extract");
        Files.createDirectories(extractDir);

        // when
        var archiveInput = new ByteArrayInputStream(sampleArchive);
        try (var extractor = CpioArchiveExtractor.builder(archiveInput).build()) {
            extractor.extract(extractDir);
        }

        // then
        assertThat(extractDir.resolve("file1.txt")).exists().hasContent("Content 1");
        assertThat(extractDir.resolve("file2.txt")).exists().hasContent("Content 2");
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void testSymlinkTargetLargerThanMaxEntrySize_throwsLimitExceededException() throws IOException {
        // given
        Path symlink = tempDir.resolve("huge-link");
        Files.createSymbolicLink(symlink, Path.of("a".repeat(200)));

        ByteArrayOutputStream archiveOutput = new ByteArrayOutputStream();
        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.add(EntrySource.of(tempDir, symlink));
        }

        var extractDir = tempDir.resolve("extract-huge-link");
        Files.createDirectories(extractDir);

        // when & then
        try (var extractor = CpioArchiveExtractor.builder(new ByteArrayInputStream(archiveOutput.toByteArray()))
                .maxEntrySize(50)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(extractDir))
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> {
                        assertThat(e.entryName()).contains("huge-link");
                        assertThat(e.limit()).isEqualTo(LimitExceededException.Limit.ENTRY_SIZE);
                        assertThat(e.maximum()).isEqualTo(50);
                    });
        }
    }

    @Test
    void testExtractCpioArchiveWithDirectories() throws IOException {
        // given
        var extractDir = tempDir.resolve("extract-dirs");
        Files.createDirectories(extractDir);

        // when
        var archiveInput = new ByteArrayInputStream(directoryArchive);
        try (var extractor = CpioArchiveExtractor.builder(archiveInput).build()) {
            extractor.extract(extractDir);
        }

        // then
        var extractedDir = extractDir.resolve("test_dir");
        var extractedFile = extractedDir.resolve("nested.txt");

        assertThat(extractedDir).exists().isDirectory();
        assertThat(extractedFile).exists().hasContent("Nested content");
    }

    @Test
    void testExtractFromFile() throws IOException {
        // given
        var archiveFile = tempDir.resolve("test.cpio");
        Files.write(archiveFile, sampleArchive);

        var extractDir = tempDir.resolve("extract-from-file");
        Files.createDirectories(extractDir);

        // when
        try (var extractor = CpioArchiveExtractor.builder(archiveFile).build()) {
            extractor.extract(extractDir);
        }

        // then
        assertThat(extractDir.resolve("file1.txt")).exists();
        assertThat(extractDir.resolve("file2.txt")).exists();
    }

    @Test
    void testExtractFromFileObject() throws IOException {
        // given
        var archiveFile = tempDir.resolve("test.cpio");
        Files.write(archiveFile, sampleArchive);

        var extractDir = tempDir.resolve("extract-from-file-obj");
        Files.createDirectories(extractDir);

        // when
        try (var extractor = CpioArchiveExtractor.builder(archiveFile).build()) {
            extractor.extract(extractDir);
        }

        // then
        assertThat(extractDir.resolve("file1.txt")).exists();
        assertThat(extractDir.resolve("file2.txt")).exists();
    }

    @Test
    void testExtractorBuilderConfiguration() throws IOException {
        // given
        var extractDir = tempDir.resolve("extract-config");
        Files.createDirectories(extractDir);

        // when
        var archiveInput = new ByteArrayInputStream(sampleArchive);
        try (var extractor = CpioArchiveExtractor.builder(archiveInput)
                .blockSize(1024)
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            extractor.extract(extractDir);
        }

        // then
        assertThat(extractDir.resolve("file1.txt")).exists();
        assertThat(extractDir.resolve("file2.txt")).exists();
    }

    @Test
    @SuppressWarnings("try")
    void testExtractEmptyArchive() throws IOException {
        // when
        var emptyArchiveOutput = new ByteArrayOutputStream();
        // noinspection EmptyTryBlock
        try (var ignored = CpioArchiveCreator.builder(emptyArchiveOutput).build()) {
            /* no-op */
        }
        byte[] emptyArchive = emptyArchiveOutput.toByteArray();

        var extractDir = tempDir.resolve("extract-empty");
        Files.createDirectories(extractDir);

        // when
        var archiveInput = new ByteArrayInputStream(emptyArchive);
        try (var extractor = CpioArchiveExtractor.builder(archiveInput).build()) {
            extractor.extract(extractDir);
        }

        // then
        assertThat(extractDir).isEmptyDirectory();
    }

    @Test
    void testExtractWithOverwrite() throws IOException {
        var extractDir = tempDir.resolve("extract-overwrite");
        Files.createDirectories(extractDir);

        Path existingFile = extractDir.resolve("file1.txt");
        Files.write(existingFile, "Existing content".getBytes());

        var archiveInput = new ByteArrayInputStream(sampleArchive);
        try (var extractor =
                CpioArchiveExtractor.builder(archiveInput).overwrite(true).build()) {
            extractor.extract(extractDir);
        }

        assertThat(Files.readString(existingFile)).isEqualTo("Content 1");
    }

    @Test
    void testExtractWithSpecialCharacterFiles() throws IOException {
        // given
        Path specialFile = tempDir.resolve("special-äöü.txt");
        Files.write(specialFile, "Special content".getBytes());

        ByteArrayOutputStream archiveOutput = new ByteArrayOutputStream();
        try (CpioArchiveCreator creator = CpioArchiveCreator.builder(archiveOutput)
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            creator.add(EntrySource.file("special-äöü.txt", specialFile));
        }

        // when
        var extractDir = tempDir.resolve("extract-special");
        Files.createDirectories(extractDir);

        var archiveInput = new ByteArrayInputStream(archiveOutput.toByteArray());
        try (var extractor = CpioArchiveExtractor.builder(archiveInput)
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            extractor.extract(extractDir);
        }

        // then
        assertThat(extractDir.resolve("special-äöü.txt")).exists().hasContent("Special content");
    }

    @Test
    void testExtractNonExistentFile() {
        // given
        var nonExistentFile = tempDir.resolve("does-not-exist.cpio");

        // when & then
        assertThatThrownBy(() -> CpioArchiveExtractor.builder(nonExistentFile).build())
                .isInstanceOf(IOException.class);
    }

    @Test
    void testExtractToNonExistentDirectory() throws IOException {
        // given
        var nonExistentDir = tempDir.resolve("does-not-exist/extract");

        var archiveInput = new ByteArrayInputStream(sampleArchive);
        try (var extractor = CpioArchiveExtractor.builder(archiveInput).build()) {
            extractor.extract(nonExistentDir);
        }

        // then
        assertThat(nonExistentDir)
                .exists()
                .isDirectory()
                .isDirectoryContaining(file -> file.endsWith("file1.txt"))
                .isDirectoryContaining(file -> file.endsWith("file2.txt"));
    }

    @Test
    void testExtractLargeFile() throws IOException {
        // given
        Path largeFile = tempDir.resolve("large-file.txt");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            content.append("This is line ").append(i).append(" of the large file.\n");
        }
        Files.write(largeFile, content.toString().getBytes());

        ByteArrayOutputStream archiveOutput = new ByteArrayOutputStream();
        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.add(EntrySource.file("large.txt", largeFile));
        }

        // when
        var extractDir = tempDir.resolve("extract-large");
        Files.createDirectories(extractDir);

        var archiveInput = new ByteArrayInputStream(archiveOutput.toByteArray());
        try (var extractor = CpioArchiveExtractor.builder(archiveInput).build()) {
            extractor.extract(extractDir);
        }

        // then
        assertThat(extractDir.resolve("large.txt")).exists().hasContent(content.toString());
    }

    @Test
    void symlinkTargetAboveTheCapIsRejected() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var writer = new CpioEntryWriter(new CpioArchiveOutputStream(bytes), CpioConstants.FORMAT_NEW)) {
            writer.writeSymlink("link", "t".repeat(4097), 0, FileTime.fromMillis(0));
        }

        try (var reader = new CpioEntryReader(
                new CpioArchiveInputStream(new ByteArrayInputStream(bytes.toByteArray())), context())) {
            assertThatThrownBy(reader::next).isInstanceOf(IOException.class).hasMessageContaining("4096");
        }
    }

    @Test
    void nextEntryShouldBeEmptyWhenStreamEndsWithoutTrailer() throws IOException {
        var stream = mock(CpioArchiveInputStream.class);
        given(stream.getNextEntry()).willReturn(null);
        try (var reader = new CpioEntryReader(stream, context())) {
            assertThat(reader.next()).isEmpty();
        }
    }

    @Test
    void nextEntryShouldBeEmptyAtTrailerEntry() throws IOException {
        var stream = mock(CpioArchiveInputStream.class);
        given(stream.getNextEntry()).willReturn(new CpioArchiveEntry("TRAILER!!!"));
        try (var reader = new CpioEntryReader(stream, context())) {
            assertThat(reader.next()).isEmpty();
        }
    }

    private static ReaderContext context() {
        return new ReaderContext(ExtractionLimits.defaults(), unsupported -> {});
    }

    private byte[] createSampleArchive() throws IOException {
        Path testFile1 = tempDir.resolve("setup-test1.txt");
        Path testFile2 = tempDir.resolve("setup-test2.txt");
        Files.write(testFile1, "Content 1".getBytes());
        Files.write(testFile2, "Content 2".getBytes());

        ByteArrayOutputStream archiveOutput = new ByteArrayOutputStream();
        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.add(EntrySource.file("file1.txt", testFile1));
            creator.add(EntrySource.file("file2.txt", testFile2));
        }
        return archiveOutput.toByteArray();
    }

    private byte[] createDirectoryArchive() throws IOException {
        Path subDir = tempDir.resolve("setup-subdir");
        Files.createDirectories(subDir);
        Path nestedFile = subDir.resolve("nested.txt");
        Files.write(nestedFile, "Nested content".getBytes());

        ByteArrayOutputStream archiveOutput = new ByteArrayOutputStream();
        try (CpioArchiveCreator creator =
                CpioArchiveCreator.builder(archiveOutput).build()) {
            creator.add(EntrySource.directory("test_dir/")
                    .withLastModified(FileTime.fromMillis(System.currentTimeMillis())));
            creator.add(EntrySource.file("test_dir/nested.txt", nestedFile));
        }
        return archiveOutput.toByteArray();
    }
}
