/*
 * Copyright 2024-2026 The Compress4J Project
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
package com.hominux.compress4j.archivers;

import static ch.qos.logback.classic.Level.TRACE;
import static com.hominux.compress4j.test.util.io.TestFileUtils.createFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.assertArg;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveCreator;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveCreator.InMemoryArchiveCreatorBuilder;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveCreator.InMemoryEntryWriter;
import com.hominux.compress4j.assertion.Compress4JAssertions;
import com.hominux.compress4j.internal.archive.EntryWriter;
import com.hominux.compress4j.test.util.log.InMemoryLogAppender;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class ArchiveCreatorTest {

    private static final String LOGGER_NAME = ArchiveCreator.class.getPackageName();
    private InMemoryLogAppender inMemoryLogAppender;

    @TempDir
    private Path tempDir;

    @Mock
    private OutputStream out;

    private InMemoryEntryWriter writer;

    @BeforeEach
    void setup() {
        Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        inMemoryLogAppender = new InMemoryLogAppender();
        inMemoryLogAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        logger.setLevel(TRACE);
        logger.addAppender(inMemoryLogAppender);
        inMemoryLogAppender.start();
    }

    @AfterEach
    void cleanUp() {
        inMemoryLogAppender.reset();
        inMemoryLogAppender.stop();
    }

    @Test
    void shouldAddFileWithPath() throws IOException {
        // given
        String fileName = "file_name.txt";
        var path = createFile(tempDir, fileName, "789");
        int fileMode = pinFileMode(path);

        try (InMemoryArchiveCreator archive = spyCreator()) {
            // when
            archive.addFile(path);

            // then
            BasicFileAttributes fileAttrs = Files.readAttributes(path, BasicFileAttributes.class);
            FileTime modTime = fileAttrs.lastModifiedTime();

            InOrder inOrder = inOrder(archive, writer);
            inOrder.verify(archive).addFile(path);
            inOrder.verify(archive).add(named(fileName));
            inOrder.verify(writer)
                    .writeFile(
                            eq(fileName), any(InputStream.class), eq(OptionalLong.of(3L)), eq(fileMode), eq(modTime));
        }
    }

    @Test
    void shouldAddFileWithPathAppliesFilterViaBuilder() throws IOException {
        // given
        String fileName = "file_name.txt";
        var path = createFile(tempDir, fileName, "789");

        try (InMemoryArchiveCreator archive = rejectingAll()) {
            // when
            archive.addFile(path);

            // then
            InOrder inOrder = inOrder(archive, writer);
            inOrder.verify(archive).addFile(path);
            inOrder.verify(archive).add(named(fileName));
            inOrder.verifyNoMoreInteractions();
        }
    }

    @Test
    void shouldAddFileWithNameAndPath() throws IOException {
        // given
        String fileName = "file_name.txt";
        var path = createFile(tempDir, fileName, "789");
        String entryName = "additional_name.txt";
        int fileMode = pinFileMode(path);

        try (InMemoryArchiveCreator archive = spyCreator()) {
            // when
            archive.add(EntrySource.file(entryName, path));

            // then
            BasicFileAttributes fileAttrs = Files.readAttributes(path, BasicFileAttributes.class);
            FileTime modTime = fileAttrs.lastModifiedTime();

            InOrder inOrder = inOrder(archive, writer);
            inOrder.verify(archive).add(named(entryName));
            inOrder.verify(writer)
                    .writeFile(
                            eq(entryName), any(InputStream.class), eq(OptionalLong.of(3L)), eq(fileMode), eq(modTime));
        }
    }

    @Test
    void shouldAddFileWithNamePathAndModTime() throws IOException {
        // given
        String fileName = "file_name.txt";
        var path = createFile(tempDir, fileName, "789");
        String entryName = "additional_name.txt";
        FileTime modTime = FileTime.from(Instant.now());
        int fileMode = pinFileMode(path);

        try (InMemoryArchiveCreator archive = spyCreator()) {
            // when
            archive.add(EntrySource.file(entryName, path).withLastModified(modTime));

            // then
            verify(archive).add(named(entryName));
            verify(writer)
                    .writeFile(
                            eq(entryName), any(InputStream.class), eq(OptionalLong.of(3L)), eq(fileMode), eq(modTime));
        }
    }

    @Test
    void shouldAddFileWithNameAndBytes() throws IOException {
        // given
        String entryName = "additional_name.txt";

        try (MockedStatic<Instant> mockedStaticInstant = mockStatic(Instant.class, CALLS_REAL_METHODS);
                InMemoryArchiveCreator archive = spyCreator()) {

            var mockedInstant = Instant.now();
            mockedStaticInstant.when(Instant::now).thenReturn(mockedInstant);
            byte[] content = "789".getBytes();

            // when
            archive.add(EntrySource.file(entryName, content));

            // then
            verify(archive).add(named(entryName));
            FileTime modTime = FileTime.from(mockedInstant);
            verify(writer)
                    .writeFile(eq(entryName), any(InputStream.class), eq(OptionalLong.of(3L)), eq(0), eq(modTime));
        }
    }

    @Test
    void shouldAddFileWithNameAndBytesAppliesFilter() throws IOException {
        // given
        String entryName = "additional_name.txt";

        try (InMemoryArchiveCreator archive = rejectingAll()) {
            byte[] content = "789".getBytes();

            // when
            archive.add(EntrySource.file(entryName, content));

            // then
            InOrder inOrder = inOrder(archive, writer);
            inOrder.verify(archive).add(named(entryName));
            inOrder.verifyNoMoreInteractions();
        }
    }

    @Test
    void shouldAddFileWithNameBytesAndModTime() throws IOException {
        // given
        String entryName = "additional_name.txt";
        FileTime modTime = FileTime.from(Instant.now());

        try (InMemoryArchiveCreator archive = spyCreator()) {

            byte[] content = "789".getBytes();

            // when
            archive.add(EntrySource.file(entryName, content).withLastModified(modTime));

            // then
            verify(archive).add(named(entryName));
            verify(writer)
                    .writeFile(eq(entryName), any(InputStream.class), eq(OptionalLong.of(3L)), eq(0), eq(modTime));
        }
    }

    @Test
    void shouldAddFileWithNameInputStreamAndSize() throws IOException {
        // given
        String entryName = "additional_name.txt";

        try (MockedStatic<Instant> mockedStaticInstant = mockStatic(Instant.class, CALLS_REAL_METHODS);
                InMemoryArchiveCreator archive = spyCreator()) {

            var mockedInstant = Instant.now();
            mockedStaticInstant.when(Instant::now).thenReturn(mockedInstant);
            FileTime modTime = FileTime.from(mockedInstant);
            var content = new ByteArrayInputStream("789".getBytes());

            // when
            archive.add(EntrySource.file(entryName, content, 3));

            // then
            verify(archive).add(named(entryName));
            verify(writer)
                    .writeFile(eq(entryName), any(InputStream.class), eq(OptionalLong.of(3L)), eq(0), eq(modTime));
        }
    }

    @Test
    void shouldAddFileWithNameInputStreamAndSizeAppliesFilter() throws IOException {
        // given
        String entryName = "additional_name.txt";

        try (InMemoryArchiveCreator archive = rejectingAll()) {
            var content = new ByteArrayInputStream("789".getBytes());

            // when
            archive.add(EntrySource.file(entryName, content, 3));

            // then
            InOrder inOrder = inOrder(archive, writer);
            inOrder.verify(archive).add(named(entryName));
            inOrder.verifyNoMoreInteractions();
        }
    }

    @Test
    void shouldAddFileWithNameInputStreamSizeAndModTime() throws IOException {
        // given
        String entryName = "additional_name.txt";
        FileTime modTime = FileTime.from(Instant.now());

        try (InMemoryArchiveCreator archive = spyCreator()) {

            var content = new ByteArrayInputStream("789".getBytes());

            // when
            archive.add(EntrySource.file(entryName, content, 3).withLastModified(modTime));

            // then
            verify(archive).add(named(entryName));
            verify(writer)
                    .writeFile(eq(entryName), any(InputStream.class), eq(OptionalLong.of(3L)), eq(0), eq(modTime));
        }
    }

    @Test
    void shouldNotCloseTheCallersInputStream() throws IOException {
        // given
        var closed = new boolean[] {false};
        var content = new ByteArrayInputStream("789".getBytes()) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };

        try (InMemoryArchiveCreator archive = new InMemoryArchiveCreator(new InMemoryArchiveCreatorBuilder(out))) {
            // when
            archive.add(EntrySource.file("a.txt", content, 3));
        }

        // then
        assertThat(closed[0]).isFalse();
    }

    @Test
    void shouldAddDirectoryWithName() throws IOException {
        // given
        String entryName = "dir_name";

        try (MockedStatic<Instant> mockedStaticInstant = mockStatic(Instant.class, CALLS_REAL_METHODS);
                InMemoryArchiveCreator archive = spyCreator()) {
            var mockedInstant = Instant.now();
            mockedStaticInstant.when(Instant::now).thenReturn(mockedInstant);
            FileTime modTime = FileTime.from(mockedInstant);

            // when
            archive.add(EntrySource.directory(entryName));

            // then
            verify(archive).add(named(entryName));
            verify(writer).writeDirectory(entryName, 0, modTime);
        }
    }

    @Test
    void shouldAddDirectoryWithNameAndAppliesFilter() throws IOException {
        // given
        String entryName = "dir_name";

        try (InMemoryArchiveCreator archive = rejectingAll()) {
            // when
            archive.add(EntrySource.directory(entryName));

            // then
            InOrder inOrder = inOrder(archive, writer);
            inOrder.verify(archive).add(named(entryName));
            inOrder.verifyNoMoreInteractions();
        }
    }

    @Test
    void shouldAddDirectoryWithNameAndModTime() throws IOException {
        // given
        String entryName = "dir_name";
        FileTime modTime = FileTime.from(Instant.now());

        try (InMemoryArchiveCreator archive = spyCreator()) {
            // when
            archive.add(EntrySource.directory(entryName).withLastModified(modTime));

            // then
            verify(archive).add(named(entryName));
            verify(writer).writeDirectory(entryName, 0, modTime);
        }
    }

    @Test
    void shouldStoreTopLevelDirectoryNameWithForwardSlashes() throws IOException {
        var base = tempDir.resolve("base");
        createFile(base, "file1", "1");

        try (InMemoryArchiveCreator archive = spyCreator()) {

            archive.addDirectoryRecursively("\\top\\inner\\", base);

            verify(archive).add(named("top/inner/file1"));
        }
    }

    @Test
    void shouldAddDirectoryRecursivelyWithPath() throws IOException {
        // given
        var base = tempDir.resolve("base");
        createFile(base, "file1", "1");
        var subDir1 = base.resolve("subDir1");
        var file11 = createFile(subDir1, "file11", "11");
        String file11RelativeName = base.relativize(file11).toString();
        int file11Mode = pinFileMode(file11);
        int subDir1Mode = pinDirectoryMode(subDir1);

        try (InMemoryArchiveCreator archive = spyCreator()) {

            // when
            archive.addDirectoryRecursively(base);

            // then
            verify(archive).addDirectoryRecursively("", base);
            verify(archive).add(named("subDir1"));
            FileTime subDir1ModTime = Files.getLastModifiedTime(subDir1);
            verify(writer).writeDirectory(eq("subDir1"), eq(subDir1Mode), assertArg(time -> assertThat(
                            time.toInstant().truncatedTo(ChronoUnit.SECONDS))
                    .isEqualTo(subDir1ModTime.toInstant().truncatedTo(ChronoUnit.SECONDS))));
            verify(archive).add(named("subDir1/file11"));
            FileTime file11ModTime = Files.getLastModifiedTime(file11);
            verify(writer)
                    .writeFile(
                            eq("subDir1/file11"),
                            any(InputStream.class),
                            eq(OptionalLong.of(2L)),
                            eq(file11Mode),
                            eq(file11ModTime));
            verify(archive).add(named("file1"));
            FileTime file1ModTime = Files.getLastModifiedTime(base.resolve("file1"));
            verify(writer)
                    .writeFile(
                            eq("file1"),
                            any(InputStream.class),
                            eq(OptionalLong.of(1L)),
                            eq(file11Mode),
                            eq(file1ModTime));
        }
    }

    @Test
    void shouldAddDirectoryRecursivelyWithPathAppliesFilter() throws IOException {
        // given
        var base = tempDir.resolve("base");
        createFile(base, "file1", "1");
        var subDir1 = base.resolve("subDir1");
        createFile(subDir1, "file11", "11");
        List<String> offered = new ArrayList<>();

        try (InMemoryArchiveCreator archive =
                spyCreator(new InMemoryArchiveCreatorBuilder(out).filter(s -> offered.add(s.name()) && false))) {

            // when
            archive.addDirectoryRecursively(base);

            // then
            verify(archive).addDirectoryRecursively("", base);
            verify(archive, never()).add(named("subDir1"));
            verify(archive, never()).add(named("subDir1/file11"));
            assertThat(offered).containsExactlyInAnyOrder("subDir1", "file1");
            verify(archive).add(named("file1"));
            verify(writer, never()).writeFile(anyString(), any(), any(OptionalLong.class), anyInt(), any());
        }
    }

    @Test
    void shouldAddDirectoryRecursivelyWithPathAndTopLevelDir() throws IOException {
        // given
        String top = "top";
        var base = tempDir.resolve("base");
        var file1 = createFile(base, "file1", "1");
        var subDir1 = base.resolve("subDir1");
        var file11 = createFile(subDir1, "file11", "11");
        String file11RelativeName = base.relativize(file11).toString();
        int file11Mode = pinFileMode(file11);
        int baseMode = pinDirectoryMode(base);
        int subDir1Mode = pinDirectoryMode(subDir1);

        try (InMemoryArchiveCreator archive = spyCreator()) {

            // when
            archive.addDirectoryRecursively(top, base);

            // then
            verify(archive).add(named(top));
            FileTime baseModTime = Files.getLastModifiedTime(base);
            verify(writer).writeDirectory("top", baseMode, baseModTime);
            verify(archive).add(named("top/subDir1"));
            FileTime subDir1ModTime = Files.getLastModifiedTime(subDir1);
            verify(writer).writeDirectory(eq("top/subDir1"), eq(subDir1Mode), assertArg(time -> assertThat(
                            time.toInstant().truncatedTo(ChronoUnit.SECONDS))
                    .isEqualTo(subDir1ModTime.toInstant().truncatedTo(ChronoUnit.SECONDS))));
            verify(archive).add(named("top/subDir1/file11"));
            FileTime file11ModTime = Files.getLastModifiedTime(file11);
            verify(writer)
                    .writeFile(
                            eq("top/subDir1/file11"),
                            any(InputStream.class),
                            eq(OptionalLong.of(2L)),
                            eq(file11Mode),
                            eq(file11ModTime));
            verify(archive).add(named("top/file1"));
            FileTime file1ModTime = Files.getLastModifiedTime(file1);
            verify(writer)
                    .writeFile(
                            eq("top/file1"),
                            any(InputStream.class),
                            eq(OptionalLong.of(1L)),
                            eq(file11Mode),
                            eq(file1ModTime));
        }
    }

    @Test
    void shouldAddDirectoryRecursivelyWithPathAndModTime() throws IOException {
        // given
        FileTime modTime = FileTime.from(Instant.now());
        var base = tempDir.resolve("base");
        createFile(base, "file1", "1");
        var subDir1 = base.resolve("subDir1");
        var file11 = createFile(subDir1, "file11", "11");
        String file11RelativeName = base.relativize(file11).toString();
        int file11Mode = pinFileMode(file11);
        int subDir1Mode = pinDirectoryMode(subDir1);

        try (InMemoryArchiveCreator archive = spyCreator()) {

            // when
            archive.addDirectoryRecursively("", base, modTime);

            // then
            verify(archive).addDirectoryRecursively("", base, modTime);
            verify(archive).add(named("subDir1"));
            verify(writer).writeDirectory("subDir1", subDir1Mode, modTime);
            verify(archive).add(named("subDir1/file11"));
            verify(writer)
                    .writeFile(
                            eq("subDir1/file11"),
                            any(InputStream.class),
                            eq(OptionalLong.of(2L)),
                            eq(file11Mode),
                            eq(modTime));
            verify(archive).add(named("file1"));
            verify(writer)
                    .writeFile(
                            eq("file1"), any(InputStream.class), eq(OptionalLong.of(1L)), eq(file11Mode), eq(modTime));
        }
    }

    @Test
    void shouldAddDirectoryRecursivelyWithPathTopLevelDirAndModTime() throws IOException {
        // given
        FileTime modTime = FileTime.from(Instant.now());
        String top = "top";
        var base = tempDir.resolve("base");
        createFile(base, "file1", "1");
        var subDir1 = base.resolve("subDir1");
        var file11 = createFile(subDir1, "file11", "11");
        String file11RelativeName = base.relativize(file11).toString();
        int file11Mode = pinFileMode(file11);
        int baseMode = pinDirectoryMode(base);
        int subDir1Mode = pinDirectoryMode(subDir1);

        try (InMemoryArchiveCreator archive = spyCreator()) {

            // when
            archive.addDirectoryRecursively(top, base, modTime);

            // then
            verify(archive).addDirectoryRecursively(top, base, modTime);
            verify(archive).add(named(top));
            verify(writer).writeDirectory("top", baseMode, modTime);
            verify(archive).add(named("top/subDir1"));
            verify(writer).writeDirectory("top/subDir1", subDir1Mode, modTime);
            verify(archive).add(named("top/subDir1/file11"));
            verify(writer)
                    .writeFile(
                            eq("top/subDir1/file11"),
                            any(InputStream.class),
                            eq(OptionalLong.of(2L)),
                            eq(file11Mode),
                            eq(modTime));
            verify(archive).add(named("top/file1"));
            verify(writer)
                    .writeFile(
                            eq("top/file1"),
                            any(InputStream.class),
                            eq(OptionalLong.of(1L)),
                            eq(file11Mode),
                            eq(modTime));
        }
    }

    @Test
    void addFile_whenSourcePathDoesNotExist_shouldThrowIOException() throws IOException {
        // Given
        Path nonExistentPath = tempDir.resolve("non_existent_file.txt");
        try (InMemoryArchiveCreator archive = new InMemoryArchiveCreatorBuilder(out).build()) {
            // When & Then
            assertThatThrownBy(() -> archive.addFile(nonExistentPath)).isInstanceOf(IOException.class);
        }
    }

    @Test
    void addFile_whenSourcePathIsNotReadable_shouldThrowIOException() throws IOException {
        // Given
        Path unreadablePath = createFile(tempDir, "unreadable.txt", "content");

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class, CALLS_REAL_METHODS);
                InMemoryArchiveCreator archive = new InMemoryArchiveCreatorBuilder(out).build()) {
            //noinspection resource
            mockedFiles
                    .when(() -> Files.newInputStream(eq(unreadablePath)))
                    .thenThrow(new AccessDeniedException("Simulated not readable"));

            // When & Then
            assertThatThrownBy(() -> archive.addFile(unreadablePath)).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void addDirectoryRecursively_whenSourceDirDoesNotExist_shouldThrowIOException() throws IOException {
        // Given
        Path nonExistentDir = tempDir.resolve("non_existent_dir");
        try (InMemoryArchiveCreator archive = new InMemoryArchiveCreatorBuilder(out).build()) {
            // When & Then
            assertThatThrownBy(() -> archive.addDirectoryRecursively(nonExistentDir))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Path is not a directory: %s", nonExistentDir.toString());
        }
    }

    @Test
    void addDirectoryRecursively_whenSourceIsNotDirectory_shouldThrowIOException() throws IOException {
        // Given
        Path filePath = createFile(tempDir, "iam_a_file.txt", "content");
        try (InMemoryArchiveCreator archive = new InMemoryArchiveCreatorBuilder(out).build()) {
            // When & Then
            assertThatThrownBy(() -> archive.addDirectoryRecursively(filePath))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Path is not a directory: %s", filePath.toString());
        }
    }

    @Test
    void close_shouldCloseTheWriter() throws IOException {
        // Given
        EntryWriter mockWriter = mock(EntryWriter.class);
        ArchiveCreator creator = new ArchiveCreator(new InMemoryArchiveCreatorBuilder(out), mockWriter) {};

        // When
        creator.close();

        // Then
        verify(mockWriter).close();
    }

    @Test
    void close_onClosedCreator_shouldHandleGracefully() throws IOException {
        // Given
        EntryWriter mockWriter = mock(EntryWriter.class);
        ArchiveCreator creator = new ArchiveCreator(new InMemoryArchiveCreatorBuilder(out), mockWriter) {};

        // when
        creator.close();
        creator.close();

        // then
        verify(mockWriter, times(2)).close();
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void addFile_whenPathIsSymbolicLink_shouldPassLinkTargetToWriter() throws IOException {
        // Given
        Path actualFile = createFile(tempDir, "actual_file.txt", "link content");
        Path symlinkPath = tempDir.resolve("symlink_to_file.txt");
        Files.createSymbolicLink(symlinkPath, actualFile.getFileName());

        try (InMemoryArchiveCreator archive = spyCreator()) {
            // When
            archive.addFile(symlinkPath);

            // Then
            verify(archive).addFile(symlinkPath);
            verify(archive).add(named("symlink_to_file.txt"));
            verify(writer)
                    .writeSymlink(
                            eq("symlink_to_file.txt"),
                            eq(actualFile.getFileName().toString()),
                            anyInt(),
                            any(FileTime.class));
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void addDirectoryRecursively_withSymbolicLinkToFile_shouldAddAsSymlink() throws IOException {
        // Given
        Path base = tempDir.resolve("base_dir_with_symlink");
        Files.createDirectories(base);
        createFile(base, "target.txt", "data");
        Path symlinkInDir = base.resolve("my_link.txt");
        Path target = Paths.get("target.txt");
        Files.createSymbolicLink(symlinkInDir, target);

        try (InMemoryArchiveCreator archive = spyCreator()) {
            // When
            archive.addDirectoryRecursively(base);

            // Then
            verify(archive).add(named("target.txt"));

            BasicFileAttributes linkAttrs =
                    Files.readAttributes(symlinkInDir, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            verify(archive).add(named("my_link.txt"));
            verify(writer)
                    .writeSymlink(eq("my_link.txt"), eq(target.toString()), anyInt(), eq(linkAttrs.lastModifiedTime()));
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void addDirectoryRecursively_withSymbolicLinkToDirectory_shouldAddAsSymlinkNotRecurseTarget() throws IOException {
        // Given
        Path base = tempDir.resolve("base_dir_with_dir_symlink");
        Files.createDirectories(base);
        Path targetDir = base.resolve("actual_dir");
        Files.createDirectories(targetDir);
        createFile(targetDir, "file_in_actual_dir.txt", "secret");

        Path symlinkToDir = base.resolve("link_to_actual_dir");
        Path actualDir = Paths.get("actual_dir");
        Files.createSymbolicLink(symlinkToDir, actualDir);

        try (InMemoryArchiveCreator archive = spyCreator()) {
            // When
            archive.addDirectoryRecursively(base);

            // Then
            verify(archive).add(named("actual_dir"));
            verify(archive).add(named("actual_dir/file_in_actual_dir.txt"));

            BasicFileAttributes linkAttrs =
                    Files.readAttributes(symlinkToDir, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            verify(archive).add(named("link_to_actual_dir"));
            verify(writer)
                    .writeSymlink(
                            eq("link_to_actual_dir"),
                            eq(actualDir.toString()),
                            anyInt(),
                            eq(linkAttrs.lastModifiedTime()));
            verify(archive, never()).add(named("link_to_actual_dir/file_in_actual_dir.txt"));
        }
    }

    @Test
    void addDirectoryRecursively_withEmptySourceDirectory() throws IOException {
        // Given
        Path emptyBaseDir = tempDir.resolve("empty_base");
        Files.createDirectories(emptyBaseDir);

        try (InMemoryArchiveCreator archive = spyCreator()) {
            // When
            archive.addDirectoryRecursively(emptyBaseDir);

            // Then
            verify(writer, never()).writeDirectory(anyString(), anyInt(), any(FileTime.class));
            verify(writer, never()).writeSymlink(anyString(), anyString(), anyInt(), any());
            verify(writer, never()).writeFile(anyString(), any(), any(OptionalLong.class), anyInt(), any());
            Compress4JAssertions.assertThat(inMemoryLogAppender)
                    .contains("dir=" + emptyBaseDir + " topLevelDir=", TRACE);
        }
    }

    @Test
    void addDirectoryRecursively_withEmptySourceDirectoryAndTopLevelDir() throws IOException {
        // Given
        Path emptyBaseDir = tempDir.resolve("empty_base_for_top");
        Files.createDirectories(emptyBaseDir);
        String topLevelDirName = "myArchiveDir";

        try (InMemoryArchiveCreator archive = spyCreator()) {
            // When
            archive.addDirectoryRecursively(topLevelDirName, emptyBaseDir);

            // Then
            FileTime expectedModTime = Files.getLastModifiedTime(emptyBaseDir);
            verify(archive).add(named(topLevelDirName));
            verify(writer).writeDirectory(eq(topLevelDirName), anyInt(), argThat(ft -> ft.toInstant()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .equals(expectedModTime.toInstant().truncatedTo(ChronoUnit.SECONDS))));
            verify(writer, times(1)).writeDirectory(anyString(), anyInt(), any(FileTime.class));
            verify(writer, never()).writeSymlink(anyString(), anyString(), anyInt(), any());
            verify(writer, never()).writeFile(anyString(), any(), any(OptionalLong.class), anyInt(), any());
        }
    }

    @Test
    void addDirectoryRecursively_filterSkipsSubtree() throws IOException {
        // Given
        Path base = tempDir.resolve("base_for_skip_subtree");
        createFile(base, "keep_this_file.txt", "content");
        Path dirToSkip = Files.createDirectory(base.resolve("skip_this_dir"));
        createFile(dirToSkip, "file_in_skipped_dir.txt", "secret");

        List<String> offered = new ArrayList<>();
        try (InMemoryArchiveCreator archive = spyCreator(new InMemoryArchiveCreatorBuilder(out)
                .filter(s -> offered.add(s.name()) && !s.name().equals("skip_this_dir")))) {
            // When
            archive.addDirectoryRecursively(base);

            // Then
            verify(archive).add(named("keep_this_file.txt"));
            verify(archive, never()).add(named("skip_this_dir"));
            verify(archive, never()).add(named("skip_this_dir/file_in_skipped_dir.txt"));
            assertThat(offered).containsExactlyInAnyOrder("keep_this_file.txt", "skip_this_dir");
        }
    }

    @Test
    void addDirectoryRecursively_filterAllowsDirButRejectsFileInside() throws IOException {
        // Given
        Path base = tempDir.resolve("base_for_partial_skip");
        Path subDir = Files.createDirectories(base.resolve("sub"));
        createFile(subDir, "allowed_file.txt", "content1");
        createFile(subDir, "denied_file.txt", "content2");

        try (InMemoryArchiveCreator archive = spyCreator(
                new InMemoryArchiveCreatorBuilder(out).filter(s -> !s.name().endsWith("denied_file.txt")))) {
            // When
            archive.addDirectoryRecursively(base);
            // Then
            verify(writer).writeDirectory(eq("sub"), anyInt(), any(FileTime.class));
            verify(writer).writeFile(eq("sub/allowed_file.txt"), any(InputStream.class), any(), anyInt(), any());
            verify(writer, never())
                    .writeFile(eq("sub/denied_file.txt"), any(InputStream.class), any(), anyInt(), any());
        }
    }

    @Test
    void addDirectoryRecursively_withOverridingModTime() throws IOException {
        // Given
        Path base = tempDir.resolve("base_override_modtime");
        createFile(base, "file.txt", "content");
        Path subDir = Files.createDirectory(base.resolve("subdir"));
        createFile(subDir, "file_in_sub.txt", "content2");
        int subDirMode = pinDirectoryMode(subDir);

        FileTime overrideModTime = FileTime.from(Instant.now().minus(1, ChronoUnit.DAYS));

        try (InMemoryArchiveCreator archive = spyCreator()) {
            // When
            archive.addDirectoryRecursively("", base, overrideModTime);

            // Then
            verify(archive).add(named("subdir"));
            verify(archive).add(named("file.txt"));
            verify(archive).add(named("subdir/file_in_sub.txt"));

            verify(writer, times(2))
                    .writeFile(
                            anyString(),
                            any(InputStream.class),
                            any(OptionalLong.class),
                            anyInt(),
                            eq(overrideModTime));
            verify(writer, times(1)).writeDirectory("subdir", subDirMode, overrideModTime);
        }
    }

    @Test
    void addDirectoryRecursively_visitorPreVisitDirectoryRootIsEmptyName() throws IOException {
        Path base = tempDir.resolve("visitor_root_test");
        createFile(base, "file.txt", "test");

        try (InMemoryArchiveCreator archive = spyCreator()) {
            archive.addDirectoryRecursively(base);

            verify(archive, never()).add(named(""));
            verify(archive).add(named("file.txt"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "/", "//", "\\"})
    void addDirectoryRecursively_whenTopLevelDirIsBlankOrAllSlashes_shouldThrowIllegalArgumentException(
            String topLevelDir) throws IOException {
        Path base = tempDir.resolve("blank_top_level");
        createFile(base, "file.txt", "test");

        try (InMemoryArchiveCreator archive = new InMemoryArchiveCreatorBuilder(out).build()) {
            assertThatThrownBy(() -> archive.addDirectoryRecursively(topLevelDir, base))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void fileSourceFromEmptyByteArray_shouldWriteZeroLengthEntry() throws IOException {
        // Given
        String entryName = "empty_from_bytes.txt";
        byte[] emptyContent = new byte[0];
        FileTime modTime = FileTime.from(Instant.now());

        try (InMemoryArchiveCreator archive = spyCreator()) {
            // When
            archive.add(EntrySource.file(entryName, emptyContent).withLastModified(modTime));

            // Then
            verify(writer)
                    .writeFile(eq(entryName), any(InputStream.class), eq(OptionalLong.of(0L)), eq(0), eq(modTime));
        }
    }

    @Test
    void builder_filterNull_isRejected() {
        InMemoryArchiveCreatorBuilder builder = new InMemoryArchiveCreatorBuilder(out);

        assertThatThrownBy(() -> builder.filter(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate");
    }

    private InMemoryArchiveCreator rejectingAll() {
        return spyCreator(new InMemoryArchiveCreatorBuilder(out).filter(s -> false));
    }

    private InMemoryArchiveCreator spyCreator() {
        return spyCreator(new InMemoryArchiveCreatorBuilder(out));
    }

    private InMemoryArchiveCreator spyCreator(InMemoryArchiveCreatorBuilder builder) {
        writer = spy(new InMemoryEntryWriter(out));
        return spy(new InMemoryArchiveCreator(builder, writer));
    }

    private static int pinFileMode(Path path) throws IOException {
        return pinMode(path, "rw-r--r--", 0644);
    }

    private static int pinDirectoryMode(Path path) throws IOException {
        return pinMode(path, "rwxr-xr-x", 0755);
    }

    private static int pinMode(Path path, String permissions, int mode) throws IOException {
        if (!path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return 0;
        }
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        return mode;
    }

    private static EntrySource named(String name) {
        return argThat(source -> source != null && source.name().equals(name));
    }
}
