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
package com.hominux.compress4j.archivers;

import static ch.qos.logback.classic.Level.DEBUG;
import static ch.qos.logback.classic.Level.TRACE;
import static com.hominux.compress4j.archivers.ArchiveExtractor.Entry.Type.DIR;
import static com.hominux.compress4j.archivers.ArchiveExtractor.Entry.Type.FILE;
import static com.hominux.compress4j.archivers.ArchiveExtractor.Entry.Type.SYMLINK;
import static com.hominux.compress4j.archivers.ArchiveExtractor.ErrorHandlerChoice.ABORT;
import static com.hominux.compress4j.archivers.ArchiveExtractor.ErrorHandlerChoice.SKIP;
import static com.hominux.compress4j.archivers.ArchiveExtractor.ErrorHandlerChoice.SKIP_ALL;
import static com.hominux.compress4j.archivers.ArchiveExtractor.EscapingSymlinkPolicy.ALLOW;
import static com.hominux.compress4j.archivers.ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW;
import static com.hominux.compress4j.archivers.ArchiveExtractor.EscapingSymlinkPolicy.RELATIVIZE_ABSOLUTE;
import static com.hominux.compress4j.archivers.memory.InMemoryArchiveInputStream.toInputStream;
import static com.hominux.compress4j.test.util.io.TestFileUtils.createFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.archivers.ArchiveExtractor.Entry;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveEntry;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveExtractor;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveExtractor.InMemoryArchiveExtractorBuilder;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveInputStream;
import com.hominux.compress4j.assertion.Compress4JAssertions;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import com.hominux.compress4j.exceptions.UnsafeEntryException;
import com.hominux.compress4j.test.util.log.InMemoryLogAppender;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class ArchiveExtractorTest {

    private static final String LOGGER_NAME = ArchiveExtractor.class.getPackageName();
    private InMemoryLogAppender inMemoryLogAppender;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setup() {
        var logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
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
    void streamConstructorUsesDefaultLimits() throws IOException {
        try (var extractor =
                new InMemoryArchiveExtractor(new InMemoryArchiveInputStream(List.<InMemoryArchiveEntry>of()))) {
            ArchiveExtractor<?> base = extractor;
            assertThat(base.limits).isEqualTo(ExtractionLimits.defaults());
        }
    }

    @Test
    void limitsValueReplacesTheDefaults() throws IOException {
        var entries = List.of(
                InMemoryArchiveEntry.builder().name("a").content("").build(),
                InMemoryArchiveEntry.builder().name("b").content("").build());
        try (var extractor = InMemoryArchiveExtractor.builder(entries)
                .limits(ExtractionLimits.noLimits().withMaxEntries(1))
                .build()) {
            var stream = extractor.stream();
            assertThatThrownBy(() -> stream.forEach(item -> {}))
                    .isInstanceOf(UncheckedIOException.class)
                    .cause()
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> {
                        assertThat(e.limit()).isEqualTo(Limit.ENTRIES);
                        assertThat(e.maximum()).isEqualTo(1);
                    });
        }
    }

    @Test
    void builderRejectsOutOfRangeLimits() {
        var builder = InMemoryArchiveExtractor.builder(new ByteArrayInputStream(new byte[0]));
        assertThatThrownBy(() -> builder.maxRatio(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRatio");
        assertThatThrownBy(() -> builder.maxEntries(-2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxEntries");
    }

    @Test
    void shouldExtractFiles() throws IOException {
        // given
        var entry1 =
                InMemoryArchiveEntry.builder().name("test1").content("content1").build();
        var entry2 =
                InMemoryArchiveEntry.builder().name("test2").content("content2").build();
        try (var inMemoryDecompressor =
                InMemoryArchiveExtractor.builder(List.of(entry1, entry2)).build()) {
            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("test1")).hasContent("content1");
            assertThat(tempDir.resolve("test2")).hasContent("content2");
        }
    }

    @Test
    void shouldExtractFilesWithSubdirectories() throws IOException {
        // given
        var entry1 =
                InMemoryArchiveEntry.builder().name("test1").content("content1").build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("subdir/test2")
                .content("content2")
                .build();
        try (var inMemoryDecompressor =
                InMemoryArchiveExtractor.builder(List.of(entry1, entry2)).build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("test1")).hasContent("content1");
            assertThat(tempDir.resolve("subdir/test2")).hasContent("content2");
        }
    }

    @Test
    void shouldNotExtractFilesThatAlreadyExists() throws IOException {
        // given
        createFile(tempDir, "test1", "789");

        var entry1 =
                InMemoryArchiveEntry.builder().name("test1").content("content1").build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("subdir/test2")
                .content("content2")
                .build();
        try (var inMemoryDecompressor =
                InMemoryArchiveExtractor.builder(List.of(entry1, entry2)).build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("test1")).hasContent("789");
            assertThat(tempDir.resolve("subdir/test2")).hasContent("content2");
            Compress4JAssertions.assertThat(inMemoryLogAppender)
                    .contains("Skipping file entry: test1 (already exists)", DEBUG);
        }
    }

    @Test
    void shouldExtractFilesThatAlreadyExistsWhenOverwriteTrue() throws IOException {
        // given
        createFile(tempDir, "test1", "789");

        var entry1 =
                InMemoryArchiveEntry.builder().name("test1").content("content1").build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("subdir/test2")
                .content("content2")
                .build();
        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(entry1, entry2))
                .overwrite(true)
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("test1")).hasContent("content1");
            assertThat(tempDir.resolve("subdir/test2")).hasContent("content2");
        }
    }

    @Test
    void shouldExtractFilesWithSubdirectoriesAndStripZeroComponents() throws IOException {
        // given
        var entry1 =
                InMemoryArchiveEntry.builder().name("test1").content("content1").build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("subdir/test2")
                .content("content2")
                .build();
        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(entry1, entry2))
                .stripComponents(0)
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("test1")).hasContent("content1");
            assertThat(tempDir.resolve("subdir/test2")).hasContent("content2");
        }
    }

    @Test
    void shouldExtractFilesWithSubdirectoriesAndStripComponents() throws IOException {
        // given
        var entry1 =
                InMemoryArchiveEntry.builder().name("test1").content("content1").build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("subdir/test2")
                .content("content2")
                .build();
        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(entry1, entry2))
                .stripComponents(1)
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("test2")).hasContent("content2");
        }
    }

    @Test
    void shouldFailExtractFilesWithInvalidPaths() throws IOException {
        // given
        var entry1 = InMemoryArchiveEntry.builder()
                .name("../test1")
                .content("content1")
                .build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("subdir/test2")
                .content("content2")
                .build();
        try (var inMemoryDecompressor =
                InMemoryArchiveExtractor.builder(List.of(entry1, entry2)).build()) {

            // when && then
            assertThatThrownBy(() -> inMemoryDecompressor.extract(tempDir))
                    .isInstanceOf(IOException.class)
                    .hasMessageStartingWith("Path traversal vulnerability detected!");
        }
    }

    @Test
    void skipAllHandlerIsNotConsultedForTraversal() throws IOException {
        // given
        var entry1 = InMemoryArchiveEntry.builder()
                .name("../test1")
                .content("content1")
                .build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("subdir/test2")
                .content("content2")
                .build();
        var handlerCalls = new AtomicInteger();
        BiFunction<ArchiveExtractor.Entry, IOException, ArchiveExtractor.ErrorHandlerChoice> errorHandler =
                (entry, exception) -> {
                    handlerCalls.incrementAndGet();
                    return SKIP_ALL;
                };

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(entry1, entry2))
                .errorHandler(errorHandler)
                .build()) {

            // when
            assertThatThrownBy(() -> inMemoryDecompressor.extract(tempDir)).isInstanceOf(UnsafeEntryException.class);

            // then
            assertThat(handlerCalls).hasValue(0);
            assertThat(tempDir).isEmptyDirectory();
        }
    }

    @Test
    void shouldFailExtractFilesWithInvalidPathsAborts() throws IOException {
        // given
        var entry1 = InMemoryArchiveEntry.builder()
                .name("../test1")
                .content("content1")
                .build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("subdir/test2")
                .content("content2")
                .build();
        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(entry1, entry2))
                .errorHandler((entry, exception) -> ABORT)
                .build()) {

            // when
            assertThatThrownBy(() -> inMemoryDecompressor.extract(tempDir)).isInstanceOf(UnsafeEntryException.class);

            // then
            assertThat(tempDir).isEmptyDirectory();
        }
    }

    @Test
    void shouldFailExtractFilesWithInvalidPathsAbortsAfterSomeFilesExtractedAlready() throws IOException {
        // given
        var entry1 = InMemoryArchiveEntry.builder()
                .name("subdir/test1")
                .content("content1")
                .build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("../test2")
                .content("content2")
                .build();
        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(entry1, entry2))
                .errorHandler((entry, exception) -> ABORT)
                .build()) {

            // when
            assertThatThrownBy(() -> inMemoryDecompressor.extract(tempDir)).isInstanceOf(UnsafeEntryException.class);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void shouldFailExtractFilesWithInvalidPathsSkipsEntry() throws IOException {
        // given
        var entry1 = InMemoryArchiveEntry.builder()
                .name("subdir/../test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("subdir/some/../test1a")
                .content("content1a")
                .build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("subdir/test2")
                .content("content2")
                .build();
        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(entry1, entry1a, entry2))
                .errorHandler((entry, exception) -> SKIP)
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            Compress4JAssertions.assertThat(inMemoryLogAppender).contains("Skipped exception", DEBUG);
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("test1")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1a")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test2")).hasContent("content2");
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void shouldFailExtractFilesWithInvalidPathsSkipAllEntries() throws IOException {
        // given
        var entry1 = InMemoryArchiveEntry.builder()
                .name("subdir/../test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("subdir/some/../test1a")
                .content("content1a")
                .build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("subdir/test2")
                .content("content2")
                .build();
        var calls = new AtomicInteger();
        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(entry1, entry1a, entry2))
                .errorHandler((entry, exception) -> calls.getAndIncrement() == 0 ? SKIP_ALL : ABORT)
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            Compress4JAssertions.assertThat(inMemoryLogAppender).contains("SKIP_ALL is selected", DEBUG);
            Compress4JAssertions.assertThat(inMemoryLogAppender)
                    .contains("Skipped exception because SKIP_ALL was selected earlier", DEBUG);
            assertThat(calls).hasValue(1);
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("test1")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1a")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test2")).hasContent("content2");
        }
    }

    @Test
    void shouldApplyEntryFilters() throws IOException {
        // given
        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("subdir/test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("subdir/some/test1a")
                .content("content1a")
                .build();
        var entry2 = InMemoryArchiveEntry.builder()
                .name("subdir/test2")
                .content("content2")
                .build();

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a, entry2))
                .filter(entry -> !entry.name().contains("some"))
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/some")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
            assertThat(tempDir.resolve("subdir/test2")).hasContent("content2");
        }
    }

    @Test
    void shouldExtractSymlinksInAllowMode() throws IOException {
        // given
        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("subdir/test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("test1a")
                .type(SYMLINK)
                .linkName("subdir/test1")
                .build();

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a))
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/some")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
            assertThat(tempDir.resolve("test1a")).isSymbolicLink().hasContent("content1");
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void shouldExtractSymlinksInRelativizeAbsoluteMode() throws IOException {
        // given
        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("/subdir/test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("test1a")
                .type(SYMLINK)
                .linkName("/subdir/test1")
                .build();

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a))
                .escapingSymlinkPolicy(RELATIVIZE_ABSOLUTE)
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/some")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
            assertThat(tempDir.resolve("test1a")).isSymbolicLink().hasContent("content1");
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void shouldRejectEscapingRelativeSymlinkInRelativizeAbsoluteMode() throws IOException {
        // given
        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("/subdir/test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("test1a")
                .type(SYMLINK)
                .linkName("../subdir/test1")
                .build();

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a))
                .escapingSymlinkPolicy(RELATIVIZE_ABSOLUTE)
                .build()) {

            // then
            assertThatThrownBy(() -> inMemoryDecompressor.extract(tempDir)).isInstanceOf(UnsafeEntryException.class);
            assertThat(tempDir.resolve("test1a")).doesNotExist();
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void shouldExtractSymlinksInDisallowModeWithAbsoluteTargetPathWhenWithinTargetOutputDir() throws IOException {
        // given
        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("/subdir/test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("test1a")
                .type(SYMLINK)
                .linkName("subdir/test1")
                .build();

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a))
                .escapingSymlinkPolicy(DISALLOW)
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/some")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
            assertThat(tempDir.resolve("test1a")).isSymbolicLink().exists();
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void shouldNotExtractSymlinksInDisallowModeWithAbsoluteTargetPath() throws IOException {
        // given
        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("/subdir/test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("test1a")
                .type(SYMLINK)
                .linkName("/subdir/test1")
                .build();

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a))
                .escapingSymlinkPolicy(DISALLOW)
                .build()) {

            // when
            assertThatThrownBy(() -> inMemoryDecompressor.extract(tempDir))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Invalid symlink (absolute path): test1a -> /subdir/test1");

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/some")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
            assertThat(tempDir.resolve("test1a")).doesNotExist();
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void shouldNotExtractSymlinksInDisallowModeWithAbsoluteTargetPathTargetOutsideOutput() throws IOException {
        // given
        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("/subdir/test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("test1a")
                .type(SYMLINK)
                .linkName("../subdir2/test1")
                .build();

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a))
                .escapingSymlinkPolicy(DISALLOW)
                .build()) {

            // when
            assertThatThrownBy(() -> inMemoryDecompressor.extract(tempDir))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Invalid symlink (points outside of output directory): test1a -> ../subdir2/test1");

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/some")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
            assertThat(tempDir.resolve("test1a")).doesNotExist();
        }
    }

    @Test
    void shouldNotExtractSymlinksWhenTargetNull() throws IOException {
        // given
        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("/subdir/test1")
                .content("content1")
                .build();
        var entry1a =
                InMemoryArchiveEntry.builder().name("test1a").type(SYMLINK).build();

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a))
                .build()) {

            // when
            assertThatThrownBy(() -> inMemoryDecompressor.extract(tempDir))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Invalid symlink entry: test1a (empty target)");

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/some")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
            assertThat(tempDir.resolve("test1a")).doesNotExist();
        }
    }

    @Test
    void shouldNotExtractSymlinksWhenTargetBlank() throws IOException {
        // given
        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("/subdir/test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("test1a")
                .type(SYMLINK)
                .linkName("")
                .build();

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a))
                .build()) {

            // when
            assertThatThrownBy(() -> inMemoryDecompressor.extract(tempDir))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Invalid symlink entry: test1a (empty target)");

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/some")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
            assertThat(tempDir.resolve("test1a")).doesNotExist();
        }
    }

    @Test
    void shouldNotExtractSymlinksWhenTargetFileDoesExistsAndOverwriteFalse() throws IOException {
        // given
        createFile(tempDir, "test1a", "789");

        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("/subdir/test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("test1a")
                .type(SYMLINK)
                .linkName("some")
                .build();

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a))
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/some")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
            assertThat(tempDir.resolve("test1a")).isRegularFile().hasContent("789");
            Compress4JAssertions.assertThat(inMemoryLogAppender)
                    .contains("Skipping symlink entry: test1a -> some (already exists)", DEBUG);
        }
    }

    @Test
    void shouldExtractSymlinksWhenTargetFileDoesExistsAndOverwriteTrue() throws IOException {
        // given
        createFile(tempDir, "test1a", "789");

        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("subdir/test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("test1a")
                .type(SYMLINK)
                .linkName("subdir/test1")
                .build();

        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a))
                .overwrite(true)
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/some")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
            assertThat(tempDir.resolve("test1a")).isSymbolicLink().hasContent("content1");
        }
    }

    @Test
    void shouldRunBiConsumerPostProcessor() throws IOException {
        // given
        var subdir = InMemoryArchiveEntry.builder().name("subdir").type(DIR).build();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("subdir/test1")
                .content("content1")
                .build();
        var entry1a = InMemoryArchiveEntry.builder()
                .name("test1a")
                .type(SYMLINK)
                .linkName("subdir/test1")
                .build();

        var counter = new AtomicInteger();
        try (var inMemoryDecompressor = InMemoryArchiveExtractor.builder(List.of(subdir, entry1, entry1a))
                .postProcessor((entry, path) -> counter.incrementAndGet())
                .build()) {

            // when
            inMemoryDecompressor.extract(tempDir);

            // then
            assertThat(counter).hasValue(3);
            assertThat(tempDir).isDirectory();
            assertThat(tempDir.resolve("subdir/some")).doesNotExist();
            assertThat(tempDir.resolve("subdir/test1")).hasContent("content1");
            assertThat(tempDir.resolve("test1a")).isSymbolicLink().hasContent("content1");
        }
    }

    @Test
    void shouldExtractEmptyArchiveWithoutErrors() throws IOException {
        // given
        try (var extractor =
                InMemoryArchiveExtractor.builder(Collections.emptyList()).build()) {
            // when
            extractor.extract(tempDir);
            // then
            assertThat(tempDir).isEmptyDirectory();
            Compress4JAssertions.assertThat(inMemoryLogAppender).isEmpty();
        }
    }

    @Test
    void shouldExtractArchiveWithOnlyDirectories() throws IOException {
        // given
        var dirEntry =
                InMemoryArchiveEntry.builder().name("emptyDir/").type(DIR).build();
        var nestedDirEntry = InMemoryArchiveEntry.builder()
                .name("parentDir/childDir/")
                .type(DIR)
                .build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(dirEntry, nestedDirEntry))
                .build()) {
            // when
            extractor.extract(tempDir);
            // then
            assertThat(tempDir.resolve("emptyDir")).exists().isDirectory();
            assertThat(tempDir.resolve("parentDir/childDir")).exists().isDirectory();
        }
    }

    @Test
    void shouldCorrectlyStripAllComponentsLeavingNoFilesIfNameMatchesComponents() throws IOException {
        // given
        var entry1 = InMemoryArchiveEntry.builder()
                .name("a/b.txt")
                .content("content")
                .build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(entry1))
                .stripComponents(2)
                .build()) {
            // when
            extractor.extract(tempDir);
            // then
            assertThat(tempDir).isEmptyDirectory();
            Compress4JAssertions.assertThat(inMemoryLogAppender).isEmpty();
        }
    }

    @Test
    void shouldCorrectlyStripComponentsForDirectoryEntryMakingItTopLevel() throws IOException {
        // given
        var entry1 = InMemoryArchiveEntry.builder().name("a/b/").type(DIR).build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(entry1))
                .stripComponents(1)
                .build()) {
            // when
            extractor.extract(tempDir);
            // then
            assertThat(tempDir.resolve("b")).exists().isDirectory();
        }
    }

    @Test
    void shouldSkipEntryIfAllItsPathComponentsAreStripped() throws IOException {
        // given
        var entry =
                InMemoryArchiveEntry.builder().name("a/b/c.txt").content("test").build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(entry))
                .stripComponents(3)
                .build()) {
            // when
            extractor.extract(tempDir);
            // then
            assertThat(tempDir).isEmptyDirectory();
            Compress4JAssertions.assertThat(inMemoryLogAppender).isEmpty();
        }
    }

    @Test
    void shouldCorrectlyHandleStripComponentsGreaterThanPathDepth() throws IOException {
        // given
        var entry1 = InMemoryArchiveEntry.builder()
                .name("a/b.txt")
                .content("content")
                .build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(entry1))
                .stripComponents(3)
                .build()) {
            // when
            extractor.extract(tempDir);
            // then
            assertThat(tempDir).isEmptyDirectory();
            Compress4JAssertions.assertThat(inMemoryLogAppender).isEmpty();
        }
    }

    @Test
    void shouldCreateOutputDirIfItDoesNotExist() throws IOException {
        // given
        var newOutputDir = tempDir.resolve("new_output_dir");
        assertThat(newOutputDir).doesNotExist();
        var entry1 = InMemoryArchiveEntry.builder()
                .name("file.txt")
                .content("content")
                .build();

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(entry1)).build()) {
            // when
            extractor.extract(newOutputDir);
            // then
            assertThat(newOutputDir).exists().isDirectory();
            assertThat(newOutputDir.resolve("file.txt")).hasContent("content");
        }
    }

    @Test
    void shouldFailIfOutputDirIsAFile() throws IOException {
        // given
        var fileAsOutputDir = tempDir.resolve("iam_a_file.txt");
        Files.createFile(fileAsOutputDir);
        var entry1 = InMemoryArchiveEntry.builder()
                .name("file.txt")
                .content("content")
                .build();

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(entry1)).build()) {
            // when & then
            assertThatThrownBy(() -> extractor.extract(fileAsOutputDir))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining(fileAsOutputDir.getFileName().toString());
        }
    }

    @Test
    void shouldHandleRuntimeExceptionDuringNextEntryAndBailOut() throws IOException {
        var entry1 =
                InMemoryArchiveEntry.builder().name("file1.txt").content("abc").build();
        List<InMemoryArchiveEntry> entries = new ArrayList<>();
        entries.add(entry1);
        var inputStream = toInputStream(entries);
        var faultInjectingBuilder = new InMemoryArchiveExtractorBuilder(inputStream) {
            private int callCount = 0;

            @Override
            public InMemoryArchiveInputStream buildArchiveInputStream() {
                return new InMemoryArchiveInputStream(entries) {
                    @Override
                    public InMemoryArchiveEntry getNextEntry() {
                        callCount++;
                        if (callCount == 2) {
                            throw new RuntimeException("Simulated error reading next entry");
                        }
                        return super.getNextEntry();
                    }
                };
            }
        };

        try (var extractor =
                faultInjectingBuilder.errorHandler((entry, ex) -> ABORT).build()) {

            // when & then
            assertThatThrownBy(() -> extractor.extract(tempDir))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Simulated error reading next entry");

            assertThat(tempDir.resolve("file1.txt")).hasContent("abc");
        }
    }

    @SuppressWarnings("resource")
    @Test
    void shouldHandleIOExceptionDuringFileWriteAndUseSkipErrorHandler() throws IOException {
        // given
        var entryToFail = InMemoryArchiveEntry.builder()
                .name("file_to_fail.txt")
                .content("content")
                .build();
        var entryToSucceed =
                InMemoryArchiveEntry.builder().name("success.txt").content("ok").build();
        var simulatedException = new AccessDeniedException("Simulated permission error");

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class, CALLS_REAL_METHODS);
                var extractor = InMemoryArchiveExtractor.builder(List.of(entryToFail, entryToSucceed))
                        .errorHandler((entry, ex) -> {
                            if (entry.name().equals(entryToFail.getName()) && ex == simulatedException) {
                                return SKIP;
                            }
                            return ABORT;
                        })
                        .build()) {

            mockedFiles
                    .when(() ->
                            Files.newOutputStream(eq(tempDir.resolve(entryToFail.getName())), any(OpenOption[].class)))
                    .thenThrow(simulatedException);

            // when
            extractor.extract(tempDir);

            // then
            assertThat(tempDir.resolve(entryToFail.getName())).doesNotExist();
            assertThat(tempDir.resolve(entryToSucceed.getName())).hasContent("ok");
            Compress4JAssertions.assertThat(inMemoryLogAppender)
                    .contains("Skipped exception", DEBUG, simulatedException);
        }
    }

    @SuppressWarnings("resource")
    @Test
    void shouldHandleIOExceptionDuringFileWriteAndUseSkipAllErrorHandler() throws IOException {
        // given
        var entryToFail = InMemoryArchiveEntry.builder()
                .name("file_to_fail.txt")
                .content("content")
                .build();
        var anotherEntry = InMemoryArchiveEntry.builder()
                .name("another.txt")
                .content("another")
                .build();
        var thirdEntry = InMemoryArchiveEntry.builder()
                .name("third.txt")
                .content("third")
                .build();
        var simulatedException = new IOException("Simulated disk full");

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class, CALLS_REAL_METHODS);
                var extractor = InMemoryArchiveExtractor.builder(List.of(entryToFail, anotherEntry, thirdEntry))
                        .errorHandler((entry, ex) -> {
                            if (entry.name().equals(entryToFail.getName()) && ex == simulatedException) {
                                return SKIP_ALL;
                            }
                            return ABORT;
                        })
                        .build()) {

            mockedFiles
                    .when(() ->
                            Files.newOutputStream(eq(tempDir.resolve(entryToFail.getName())), any(OpenOption[].class)))
                    .thenThrow(simulatedException);

            // when
            extractor.extract(tempDir);

            // then
            assertThat(tempDir.resolve(entryToFail.getName())).doesNotExist();
            assertThat(tempDir.resolve(anotherEntry.getName())).hasContent("another");
            assertThat(tempDir.resolve(thirdEntry.getName())).hasContent("third");

            Compress4JAssertions.assertThat(inMemoryLogAppender)
                    .contains("SKIP_ALL is selected", DEBUG, simulatedException);
            mockedFiles.verify(
                    () -> Files.newOutputStream(eq(tempDir.resolve(entryToFail.getName())), any(OpenOption[].class)));
        }
    }

    @Test
    void shouldNotOverwriteExistingDirectoryWithFileWhenOverwriteTrue() throws IOException {
        // given
        var existingDir = tempDir.resolve("entryName");
        Files.createDirectories(existingDir);
        var fileEntry = InMemoryArchiveEntry.builder()
                .name("entryName")
                .content("file content")
                .build();

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(fileEntry))
                .overwrite(true)
                .build()) {
            // when
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(FileSystemException.class);

            // then
            assertThat(tempDir.resolve("entryName")).isDirectory();
        }
    }

    @Test
    void shouldFailToOverwriteExistingDirectoryWithFileWhenOverwriteFalse() throws IOException {
        // given
        var existingDir = tempDir.resolve("entryName");
        Files.createDirectories(existingDir);
        var fileEntry = InMemoryArchiveEntry.builder()
                .name("entryName")
                .content("file content")
                .build();

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(fileEntry))
                .overwrite(false)
                .build()) {
            // when
            extractor.extract(tempDir);

            // then
            assertThat(tempDir.resolve("entryName")).isDirectory();
            Compress4JAssertions.assertThat(inMemoryLogAppender)
                    .contains("Skipping file entry: entryName (already exists)", DEBUG);
        }
    }

    @Test
    void shouldReportDirectoryEntryClashingWithExistingFile() throws IOException {
        // given
        var existingFile = tempDir.resolve("entryName");
        Files.writeString(existingFile, "i am a file");
        var dirEntry =
                InMemoryArchiveEntry.builder().name("entryName").type(DIR).build();

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(dirEntry))
                .overwrite(true)
                .build()) {

            // when
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(FileAlreadyExistsException.class);

            // then
            assertThat(tempDir.resolve("entryName")).isRegularFile();
        }
    }

    @Test
    void shouldSkipDirectoryEntryClashingWithExistingFileWhenHandlerSkips() throws IOException {
        // given
        var existingFile = tempDir.resolve("entryName");
        Files.writeString(existingFile, "i am a file");
        var dirEntry =
                InMemoryArchiveEntry.builder().name("entryName").type(DIR).build();
        var nextEntry =
                InMemoryArchiveEntry.builder().name("next.txt").content("next").build();

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(dirEntry, nextEntry))
                .errorHandler((entry, exception) -> SKIP)
                .build()) {

            // when
            extractor.extract(tempDir);

            // then
            assertThat(tempDir.resolve("entryName")).isRegularFile();
            assertThat(tempDir.resolve("next.txt")).hasContent("next");
        }
    }

    @Test
    void shouldAllowSymlinkToAbsoluteExternalPathWhenPolicyAllow() throws IOException {
        // given
        var externalTarget = tempDir.resolve("external_target.txt");
        Files.writeString(externalTarget, "external content");
        var symlinkEntry = InMemoryArchiveEntry.builder()
                .name("myLink")
                .type(SYMLINK)
                .linkName(externalTarget.toAbsolutePath().toString())
                .build();

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(symlinkEntry))
                .escapingSymlinkPolicy(ALLOW)
                .build()) {
            // when
            extractor.extract(tempDir);
            // then
            var linkPath = tempDir.resolve("myLink");
            assertThat(linkPath).isSymbolicLink();
            assertThat(Files.readSymbolicLink(linkPath)).isEqualTo(externalTarget.toAbsolutePath());
            if (!OS.WINDOWS.isCurrentOs()) {
                assertThat(Files.readString(linkPath)).isEqualTo("external content");
            }
        } finally {
            Files.deleteIfExists(externalTarget);
        }
    }

    @Test
    void shouldExtractSymlinkToDirectory() throws IOException {
        // given
        var dirToLinkTo =
                InMemoryArchiveEntry.builder().name("actualDir").type(DIR).build();
        var symlinkEntry = InMemoryArchiveEntry.builder()
                .name("linkToDir")
                .type(SYMLINK)
                .linkName("actualDir")
                .build();

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(dirToLinkTo, symlinkEntry))
                .build()) {
            // when
            extractor.extract(tempDir);
            // then
            var linkPath = tempDir.resolve("linkToDir");
            assertThat(tempDir.resolve("actualDir")).isDirectory();
            assertThat(linkPath).isSymbolicLink();
            assertThat(Files.readSymbolicLink(linkPath).toString().replace('\\', '/'))
                    .isEqualTo("actualDir");

            var resolvedLinkPath = Files.readSymbolicLink(linkPath);
            assertThat(tempDir.resolve(resolvedLinkPath)).isDirectory();
        }
    }

    @Test
    void shouldExtractSymlinkToNonExistentTarget() throws IOException {
        // given
        var symlinkEntry = InMemoryArchiveEntry.builder()
                .name("linkToNowhere")
                .type(SYMLINK)
                .linkName("non_existent_target")
                .build();

        try (var extractor =
                InMemoryArchiveExtractor.builder(List.of(symlinkEntry)).build()) {
            // when
            extractor.extract(tempDir);
            // then
            var linkPath = tempDir.resolve("linkToNowhere");
            assertThat(linkPath).isSymbolicLink();
            assertThat(Files.readSymbolicLink(linkPath).toString().replace('\\', '/'))
                    .isEqualTo("non_existent_target");
            assertThat(Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS)).isTrue();
            assertThat(Files.exists(linkPath)).isFalse();
        }
    }

    @Test
    void entryConstructorShouldNormalizeAndTrimPaths() {
        assertThat(new Entry(" /a/b/ ", FILE, 0).name()).isEqualTo("a/b");
        assertThat(new Entry("\\a\\b\\", DIR, 0).name()).isEqualTo("a/b");
        assertThat(new Entry("a/b/", FILE, 0).name()).isEqualTo("a/b");
        assertThat(new Entry("a/b/", DIR, 0).name()).isEqualTo("a/b");
        assertThat(new Entry("///", DIR, 0).name()).isEmpty();
        assertThat(new Entry("", FILE, 0).name()).isEmpty();
    }

    @Test
    void builderShouldCorrectlySetNullEntryFilter() throws IOException {
        InMemoryArchiveExtractor.ArchiveExtractorBuilder<?, ?, ?> builder =
                InMemoryArchiveExtractor.builder(Collections.emptyList());
        builder.filter(null);

        var entry1 = InMemoryArchiveEntry.builder().name("test1").content("c1").build();
        try (var extractor =
                InMemoryArchiveExtractor.builder(List.of(entry1)).filter(null).build()) {
            extractor.extract(tempDir);
            assertThat(tempDir.resolve("test1")).exists();
        }
    }

    @Test
    void shouldNotExtractSymlinksInDisallowModeWhenTargetIsAbsoluteEvenIfResolvesInside() throws IOException {
        var entry1 = InMemoryArchiveEntry.builder()
                .name("file.txt")
                .content("content")
                .build();
        var symlinkEntry = InMemoryArchiveEntry.builder()
                .name("absLink")
                .type(SYMLINK)
                .linkName(tempDir.resolve("file.txt").toAbsolutePath().toString())
                .build();

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(entry1, symlinkEntry))
                .escapingSymlinkPolicy(DISALLOW)
                .build()) {

            assertThatThrownBy(() -> extractor.extract(tempDir))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Invalid symlink (absolute path): absLink -> ");

            assertThat(tempDir.resolve("file.txt")).hasContent("content");
            assertThat(tempDir.resolve("absLink")).doesNotExist();
        }
    }

    @Test
    void shouldExtractMixedEntriesWithAllTypesAndPolicies() throws IOException {
        var dirEntry = InMemoryArchiveEntry.builder().name("adir").type(DIR).build();
        var fileEntry = InMemoryArchiveEntry.builder()
                .name("adir/file.txt")
                .content("f")
                .build();
        var symlinkEntry = InMemoryArchiveEntry.builder()
                .name("adir/link")
                .type(SYMLINK)
                .linkName("file.txt")
                .build();
        var absSymlink = InMemoryArchiveEntry.builder()
                .name("absLink")
                .type(SYMLINK)
                .linkName(tempDir.resolve("adir/file.txt").toAbsolutePath().toString())
                .build();

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(dirEntry, fileEntry, symlinkEntry, absSymlink))
                .escapingSymlinkPolicy(ALLOW)
                .build()) {
            extractor.extract(tempDir);
            assertThat(tempDir.resolve("adir")).isDirectory();
            assertThat(tempDir.resolve("adir/file.txt")).hasContent("f");
            assertThat(tempDir.resolve("adir/link")).isSymbolicLink();
            assertThat(tempDir.resolve("absLink")).isSymbolicLink();
        }

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(dirEntry, fileEntry, absSymlink))
                .escapingSymlinkPolicy(DISALLOW)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Invalid symlink (absolute path)");
        }
    }

    @Test
    void shouldNotOverwriteSymlinkWithFileOrDirWhenOverwriteFalse() throws IOException {
        var dirEntry = InMemoryArchiveEntry.builder().name("adir").type(DIR).build();
        var fileEntry = InMemoryArchiveEntry.builder()
                .name("adir/file.txt")
                .content("f")
                .build();
        var symlinkEntry = InMemoryArchiveEntry.builder()
                .name("adir/link")
                .type(SYMLINK)
                .linkName("file.txt")
                .build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(dirEntry, fileEntry, symlinkEntry))
                .build()) {
            extractor.extract(tempDir);
        }
        var fileAtSymlink =
                InMemoryArchiveEntry.builder().name("adir/link").content("new").build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(fileAtSymlink))
                .overwrite(false)
                .build()) {
            extractor.extract(tempDir);
            assertThat(tempDir.resolve("adir/link")).isSymbolicLink();
        }
    }

    @Test
    void shouldNotWriteThroughAnExistingSymlinkWhenOverwriteTrue() throws IOException {
        var dirEntry = InMemoryArchiveEntry.builder().name("adir").type(DIR).build();
        var fileEntry = InMemoryArchiveEntry.builder()
                .name("adir/file.txt")
                .content("f")
                .build();
        var symlinkEntry = InMemoryArchiveEntry.builder()
                .name("adir/link")
                .type(SYMLINK)
                .linkName("file.txt")
                .build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(dirEntry, fileEntry, symlinkEntry))
                .build()) {
            extractor.extract(tempDir);
        }
        var fileAtSymlink =
                InMemoryArchiveEntry.builder().name("adir/link").content("new").build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(fileAtSymlink))
                .overwrite(true)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(IOException.class);
        }
        assertThat(tempDir.resolve("adir/file.txt")).hasContent("f");
    }

    @Test
    void shouldHandleNullAndAlwaysFalseEntryFilter() throws IOException {
        var entry1 = InMemoryArchiveEntry.builder().name("test1").content("c1").build();
        var entry2 = InMemoryArchiveEntry.builder().name("test2").content("c2").build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(entry1, entry2))
                .filter(null)
                .build()) {
            extractor.extract(tempDir);
            assertThat(tempDir.resolve("test1")).hasContent("c1");
            assertThat(tempDir.resolve("test2")).hasContent("c2");
        }
        var temp2 = Files.createDirectory(tempDir.resolve("temp1"));
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(entry1, entry2))
                .filter(e -> false)
                .build()) {
            extractor.extract(temp2);
            assertThat(temp2).isEmptyDirectory();
        }
    }

    @Test
    void nullEntryFilterShouldExtractEverything() throws IOException {
        var entry1 = InMemoryArchiveEntry.builder().name("test1").content("c1").build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(entry1))
                .filter(entry -> false)
                .filter(null)
                .build()) {

            extractor.extract(tempDir);

            assertThat(tempDir.resolve("test1")).hasContent("c1");
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void shouldApplyModeToExtractedDirectory() throws IOException {
        // given
        @SuppressWarnings("OctalInteger")
        var mode = 0750;
        var dirEntry = InMemoryArchiveEntry.builder()
                .name("restricted")
                .type(DIR)
                .mode(mode)
                .build();

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(dirEntry)).build()) {
            // when
            extractor.extract(tempDir);

            // then
            var extracted = tempDir.resolve("restricted");
            assertThat(extracted).isDirectory();
            assertThat(Files.getPosixFilePermissions(extracted))
                    .isEqualTo(PosixFilePermissions.fromString("rwxr-x---"));
        }
    }

    @Nested
    class ExtractionLimitTests {

        @Test
        void shouldRejectArchiveWithTooManyEntries() throws IOException {
            // given
            var entries = List.of(
                    InMemoryArchiveEntry.builder().name("a").content("a").build(),
                    InMemoryArchiveEntry.builder().name("b").content("b").build(),
                    InMemoryArchiveEntry.builder().name("c").content("c").build());

            try (var extractor =
                    InMemoryArchiveExtractor.builder(entries).maxEntries(2).build()) {

                // when
                assertThatThrownBy(() -> extractor.extract(tempDir))
                        .isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                                .isEqualTo(Limit.ENTRIES));

                // then
                assertThat(tempDir.resolve("a")).hasContent("a");
                assertThat(tempDir.resolve("c")).doesNotExist();
            }
        }

        @Test
        void shouldRejectEntryLargerThanMaxEntrySize() throws IOException {
            // given
            var entry = InMemoryArchiveEntry.builder()
                    .name("big.txt")
                    .content("0123456789")
                    .build();

            try (var extractor = InMemoryArchiveExtractor.builder(List.of(entry))
                    .maxEntrySize(4)
                    .build()) {

                // when / then
                assertThatThrownBy(() -> extractor.extract(tempDir))
                        .isInstanceOfSatisfying(LimitExceededException.class, e -> {
                            assertThat(e.limit()).isEqualTo(Limit.ENTRY_SIZE);
                            assertThat(e.maximum()).isEqualTo(4);
                            assertThat(e.entryName()).contains("big.txt");
                        });
            }
        }

        @Test
        void shouldReleaseEntryStreamAfterSuccessfulWrite() throws IOException {
            // given
            var entry = InMemoryArchiveEntry.builder().name("a").content("a").build();
            var released = new AtomicInteger();

            try (var extractor = new InMemoryArchiveExtractor(InMemoryArchiveExtractor.builder(List.of(entry))) {
                @Override
                protected void closeEntryStream(InputStream stream) {
                    released.incrementAndGet();
                }
            }) {
                // when
                extractor.extract(tempDir);

                // then
                assertThat(released).hasValue(1);
            }
        }

        @Test
        void shouldReleaseEntryStreamWhenWriteFails() throws IOException {
            // given
            var entry = InMemoryArchiveEntry.builder()
                    .name("big.txt")
                    .content("0123456789")
                    .build();
            var released = new AtomicInteger();

            try (var extractor =
                    new InMemoryArchiveExtractor(
                            InMemoryArchiveExtractor.builder(List.of(entry)).maxEntrySize(4)) {
                        @Override
                        protected void closeEntryStream(InputStream stream) {
                            released.incrementAndGet();
                        }
                    }) {

                // when
                assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(LimitExceededException.class);

                // then
                assertThat(released).hasValue(1);
            }
        }

        @Test
        void shouldKeepWriteFailureAsPrimaryWhenReleasingEntryStreamAlsoFails() throws IOException {
            // given
            var entry = InMemoryArchiveEntry.builder()
                    .name("big.txt")
                    .content("0123456789")
                    .build();
            var releaseFailure = new IOException("release failed");

            try (var extractor =
                    new InMemoryArchiveExtractor(
                            InMemoryArchiveExtractor.builder(List.of(entry)).maxEntrySize(4)) {
                        @Override
                        protected void closeEntryStream(InputStream stream) throws IOException {
                            throw releaseFailure;
                        }
                    }) {

                // when / then
                assertThatThrownBy(() -> extractor.extract(tempDir))
                        .isInstanceOf(LimitExceededException.class)
                        .hasSuppressedException(releaseFailure);
            }
        }

        @Test
        void shouldRejectArchiveExceedingMaxTotalSize() throws IOException {
            // given
            var entries = List.of(
                    InMemoryArchiveEntry.builder()
                            .name("one.txt")
                            .content("12345")
                            .build(),
                    InMemoryArchiveEntry.builder()
                            .name("two.txt")
                            .content("12345")
                            .build());

            try (var extractor =
                    InMemoryArchiveExtractor.builder(entries).maxTotalSize(8).build()) {

                // when
                assertThatThrownBy(() -> extractor.extract(tempDir))
                        .isInstanceOfSatisfying(LimitExceededException.class, e -> {
                            assertThat(e.limit()).isEqualTo(Limit.TOTAL_SIZE);
                            assertThat(e.maximum()).isEqualTo(8);
                        });

                // then
                assertThat(tempDir.resolve("one.txt")).hasContent("12345");
            }
        }

        @Test
        void shouldNotLetTheErrorHandlerOverrideABreachedLimit() throws IOException {
            // given
            var entries = List.of(
                    InMemoryArchiveEntry.builder().name("a").content("a").build(),
                    InMemoryArchiveEntry.builder().name("b").content("b").build());

            try (var extractor = InMemoryArchiveExtractor.builder(entries)
                    .maxEntries(1)
                    .errorHandler((entry, exception) -> SKIP_ALL)
                    .build()) {

                // when / then
                assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(LimitExceededException.class);
            }
        }

        @Test
        void shouldExtractEverythingWhenLimitsAreUnlimited() throws IOException {
            // given
            var entries = List.of(
                    InMemoryArchiveEntry.builder().name("a").content("a").build(),
                    InMemoryArchiveEntry.builder().name("b").content("b").build());

            try (var extractor = InMemoryArchiveExtractor.builder(entries)
                    .maxEntries(ExtractionLimits.UNLIMITED)
                    .maxEntrySize(ExtractionLimits.UNLIMITED)
                    .maxTotalSize(ExtractionLimits.UNLIMITED)
                    .build()) {
                // when
                extractor.extract(tempDir);

                // then
                assertThat(tempDir.resolve("a")).hasContent("a");
                assertThat(tempDir.resolve("b")).hasContent("b");
            }
        }

        @Test
        void eachExtractorHasItsOwnBudget() throws IOException {
            // given
            var entries = List.of(
                    InMemoryArchiveEntry.builder().name("a").content("a").build(),
                    InMemoryArchiveEntry.builder().name("b").content("b").build());
            var secondDir = Files.createDirectory(tempDir.resolve("second"));

            try (var extractor =
                    InMemoryArchiveExtractor.builder(entries).maxEntries(2).build()) {
                extractor.extract(tempDir);
            }

            // when a fresh extractor runs again, the counters start from zero
            try (var extractor =
                    InMemoryArchiveExtractor.builder(entries).maxEntries(2).build()) {
                extractor.extract(secondDir);

                // then
                assertThat(secondDir.resolve("a")).hasContent("a");
                assertThat(secondDir.resolve("b")).hasContent("b");
            }
        }
    }

    @Test
    void errorHandlerCannotSkipTraversalEntry(@TempDir Path out) throws IOException {
        // Given
        var entries = List.of(
                InMemoryArchiveEntry.builder().name("../evil.txt").content("x").build());

        try (var extractor = InMemoryArchiveExtractor.builder(entries)
                .errorHandler((entry, e) -> ArchiveExtractor.ErrorHandlerChoice.SKIP)
                .build()) {
            // Then
            assertThatThrownBy(() -> extractor.extract(out)).isInstanceOf(UnsafeEntryException.class);
        }
        assertThat(out.resolveSibling("evil.txt")).doesNotExist();
    }

    @Test
    void ioExceptionCatchStillCatchesUnsafeEntry(@TempDir Path out) throws IOException {
        // Given
        var entries = List.of(
                InMemoryArchiveEntry.builder().name("../evil.txt").content("x").build());

        try (var extractor = InMemoryArchiveExtractor.builder(entries).build()) {
            // When
            IOException caught = null;
            try {
                extractor.extract(out);
            } catch (IOException e) {
                caught = e;
            }

            // Then
            assertThat(caught).isInstanceOf(UnsafeEntryException.class);
        }
    }

    @Test
    void escapingSymlinkIsRejectedByDefault(@TempDir Path out) throws IOException {
        // Given
        var entries = List.of(InMemoryArchiveEntry.builder()
                .name("link")
                .type(ArchiveExtractor.Entry.Type.SYMLINK)
                .linkName("../../outside")
                .build());

        try (var extractor = InMemoryArchiveExtractor.builder(entries).build()) {
            // Then
            assertThatThrownBy(() -> extractor.extract(out)).isInstanceOf(UnsafeEntryException.class);
        }
    }

    @Test
    void escapingSymlinkIsExtractedWhenAllowed(@TempDir Path out) throws IOException {
        // Given
        var entries = List.of(InMemoryArchiveEntry.builder()
                .name("link")
                .type(ArchiveExtractor.Entry.Type.SYMLINK)
                .linkName("../../outside")
                .build());

        try (var extractor = InMemoryArchiveExtractor.builder(entries)
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.ALLOW)
                .build()) {
            // When
            extractor.extract(out);
        }

        // Then
        assertThat(Files.isSymbolicLink(out.resolve("link"))).isTrue();
    }

    @Test
    void extractAfterStreamIsRejected(@TempDir Path out) throws IOException {
        var entries =
                List.of(InMemoryArchiveEntry.builder().name("a").content("1").build());
        try (var extractor = InMemoryArchiveExtractor.builder(entries).build()) {
            // When
            extractor.stream().toList();

            // Then
            assertThatThrownBy(() -> extractor.extract(out)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void extractTwiceIsRejected(@TempDir Path out) throws IOException {
        var entries =
                List.of(InMemoryArchiveEntry.builder().name("a").content("1").build());
        try (var extractor = InMemoryArchiveExtractor.builder(entries).build()) {
            // When
            extractor.extract(out);

            // Then
            assertThatThrownBy(() -> extractor.extract(out)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void extractFiltersOnStrippedName(@TempDir Path out) throws IOException {
        var entries = List.of(
                InMemoryArchiveEntry.builder()
                        .name("root/keep.txt")
                        .content("k")
                        .build(),
                InMemoryArchiveEntry.builder()
                        .name("root/skip.txt")
                        .content("s")
                        .build());
        try (var extractor = InMemoryArchiveExtractor.builder(entries)
                .stripComponents(1)
                .filter(e -> !e.name().equals("skip.txt"))
                .build()) {
            // When
            extractor.extract(out);
        }

        // Then
        assertThat(out.resolve("keep.txt")).hasContent("k");
        assertThat(out.resolve("skip.txt")).doesNotExist();
    }

    @Test
    void releaseFailureOnAdvanceIsReportedOnceAndStreamReleasedOnce(@TempDir Path out) throws IOException {
        var entries = List.of(
                InMemoryArchiveEntry.builder().name("a").content("1").build(),
                InMemoryArchiveEntry.builder().name("b").content("2").build());
        var releaseFailure = new IOException("release failed");
        var releases = new AtomicInteger();

        try (var extractor = new InMemoryArchiveExtractor(InMemoryArchiveExtractor.builder(entries)) {
            @Override
            protected void closeEntryStream(InputStream stream) throws IOException {
                releases.incrementAndGet();
                throw releaseFailure;
            }
        }) {
            // When / Then
            assertThatThrownBy(() -> extractor.extract(out)).isSameAs(releaseFailure);
            assertThat(releases).hasValue(1);
            assertThat(out.resolve("a")).hasContent("1");
        }
    }

    @Test
    void abortRethrowsTheWriteFailureAndStopsExtraction() throws IOException {
        var blocked = InMemoryArchiveEntry.builder().name("a.txt").content("a").build();
        var later = InMemoryArchiveEntry.builder().name("b.txt").content("b").build();
        Files.createDirectory(tempDir.resolve("a.txt"));

        var received = new AtomicReference<IOException>();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(blocked, later))
                .overwrite(true)
                .errorHandler((entry, failure) -> {
                    received.set(failure);
                    return ABORT;
                })
                .build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir))
                    .isInstanceOf(IOException.class)
                    .isSameAs(received.get());
        }
        assertThat(tempDir.resolve("b.txt")).doesNotExist();
    }

    @Test
    void defaultErrorHandlerAborts() throws IOException {
        var blocked = InMemoryArchiveEntry.builder().name("a.txt").content("a").build();
        Files.createDirectory(tempDir.resolve("a.txt"));

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(blocked))
                .overwrite(true)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(IOException.class);
        }
    }

    @Test
    void skipContinuesWithTheNextEntry() throws IOException {
        var blocked = InMemoryArchiveEntry.builder().name("a.txt").content("a").build();
        var later = InMemoryArchiveEntry.builder().name("b.txt").content("b").build();
        Files.createDirectory(tempDir.resolve("a.txt"));

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(blocked, later))
                .overwrite(true)
                .errorHandler((entry, failure) -> SKIP)
                .build()) {
            extractor.extract(tempDir);
        }
        assertThat(tempDir.resolve("b.txt")).hasContent("b");
    }

    @Test
    void skipAllStillPropagatesSecurityFailures() throws IOException {
        var blocked = InMemoryArchiveEntry.builder().name("a.txt").content("a").build();
        var escaping =
                InMemoryArchiveEntry.builder().name("../evil").content("x").build();
        Files.createDirectory(tempDir.resolve("a.txt"));

        try (var extractor = InMemoryArchiveExtractor.builder(List.of(blocked, escaping))
                .overwrite(true)
                .errorHandler((entry, failure) -> SKIP_ALL)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(UnsafeEntryException.class);
        }
    }

    @Test
    void errorHandlerMustNotBeNull() {
        var builder = InMemoryArchiveExtractor.builder(new ByteArrayInputStream(new byte[0]));
        assertThatThrownBy(() -> builder.errorHandler(null)).isInstanceOf(NullPointerException.class);
    }
}
