/*
 * Copyright 2026 The Compress4J Project
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

import static com.hominux.compress4j.archivers.Entry.Type.DIR;
import static com.hominux.compress4j.archivers.Entry.Type.SYMLINK;
import static com.hominux.compress4j.archivers.ErrorHandlerChoice.ABORT;
import static com.hominux.compress4j.archivers.ErrorHandlerChoice.SKIP;
import static com.hominux.compress4j.archivers.ErrorHandlerChoice.SKIP_ALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.memory.InMemoryArchiveEntry;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveExtractor;
import com.hominux.compress4j.exceptions.UnsafeEntryException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class DirectoryModesTest {

    @TempDir
    Path dir;

    private static InMemoryArchiveEntry directory(String name, int mode) {
        return InMemoryArchiveEntry.builder().name(name).type(DIR).mode(mode).build();
    }

    private static InMemoryArchiveEntry file(String name) {
        return InMemoryArchiveEntry.builder()
                .name(name)
                .content("x")
                .mode(0100644)
                .build();
    }

    @Test
    void readOnlyDirectoryKeepsItsContentsAndItsMode() throws IOException {
        try (var extractor = InMemoryArchiveExtractor.builder(
                        List.of(directory("d", 040555), directory("d/e", 040555), file("d/e/a.txt")))
                .build()) {
            extractor.extract(dir);
            assertThat(dir.resolve("d/e/a.txt")).hasContent("x");
            assertThat(Files.getPosixFilePermissions(dir.resolve("d")))
                    .isEqualTo(PosixFilePermissions.fromString("r-xr-xr-x"));
            assertThat(Files.getPosixFilePermissions(dir.resolve("d/e")))
                    .isEqualTo(PosixFilePermissions.fromString("r-xr-xr-x"));
        } finally {
            makeWritableIfPresent(dir.resolve("d/e"));
            makeWritableIfPresent(dir.resolve("d"));
        }
    }

    @Test
    void successfulExtractionEndsWithTheArchivesExactDirectoryMode() throws IOException {
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(directory("d", 040750), file("d/f")))
                .build()) {
            extractor.extract(dir);
        }
        assertThat(Files.getPosixFilePermissions(dir.resolve("d")))
                .isEqualTo(PosixFilePermissions.fromString("rwxr-x---"));
    }

    @Test
    void abortedExtractionKeepsTheDirectoryOwnerAccessibleAndClosedToOthers() throws IOException {
        try (var extractor = InMemoryArchiveExtractor.builder(
                        List.of(directory("d", 040700), file("d/f"), file("../evil")))
                .build()) {
            assertThatThrownBy(() -> extractor.extract(dir)).isInstanceOf(UnsafeEntryException.class);
            assertThat(Files.getPosixFilePermissions(dir.resolve("d")))
                    .isEqualTo(PosixFilePermissions.fromString("rwx------"));
        } finally {
            makeWritable(dir.resolve("d"));
        }
    }

    @Test
    void abortedExtractionGivesTheOwnerFullAccessToAReadOnlyArchiveDirectory() throws IOException {
        try (var extractor = InMemoryArchiveExtractor.builder(
                        List.of(directory("d", 040555), file("d/a.txt"), file("../evil")))
                .build()) {
            assertThatThrownBy(() -> extractor.extract(dir)).isInstanceOf(UnsafeEntryException.class);
            assertThat(Files.getPosixFilePermissions(dir.resolve("d")))
                    .isEqualTo(PosixFilePermissions.fromString("rwxr-xr-x"));
        } finally {
            makeWritable(dir.resolve("d"));
        }
    }

    @Test
    void overwritingAnExistingSymlinkInsideTheOutputFailsBeforeTouchingItsTarget() throws IOException {
        Path out = Files.createDirectories(dir.resolve("out"));
        Path target = Files.writeString(out.resolve("target.txt"), "original");
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
        Files.createSymbolicLink(out.resolve("f"), target.getFileName());
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(file("f")))
                .overwrite(true)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOf(IOException.class)
                    .isNotInstanceOf(UnsafeEntryException.class);
        }
        assertThat(target).hasContent("original");
        assertThat(Files.getPosixFilePermissions(target)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    void abortLeavesAPreExistingDirectoryUntouched() throws IOException {
        Path existing = Files.createDirectory(dir.resolve("d"));
        Files.setPosixFilePermissions(existing, PosixFilePermissions.fromString("rwx------"));
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(directory("d", 040777), file("../evil")))
                .build()) {
            assertThatThrownBy(() -> extractor.extract(dir)).isInstanceOf(UnsafeEntryException.class);
            assertThat(Files.getPosixFilePermissions(existing)).isEqualTo(PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void successGivesAPreExistingDirectoryTheArchivesFinalMode() throws IOException {
        Path existing = Files.createDirectory(dir.resolve("d"));
        Files.setPosixFilePermissions(existing, PosixFilePermissions.fromString("rwx------"));
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(directory("d", 040777)))
                .build()) {
            extractor.extract(dir);
        }
        assertThat(Files.getPosixFilePermissions(existing)).isEqualTo(PosixFilePermissions.fromString("rwxrwxrwx"));
    }

    @Test
    void directoryEntryOverAnInOutputSymlinkLeavesTheTargetUntouched() throws IOException {
        Path out = Files.createDirectories(dir.resolve("out"));
        Path sub = Files.createDirectory(out.resolve("sub"));
        Files.setPosixFilePermissions(sub, PosixFilePermissions.fromString("rwxr-x---"));
        InMemoryArchiveEntry link = InMemoryArchiveEntry.builder()
                .name("d")
                .type(SYMLINK)
                .linkName("sub")
                .build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(link, directory("d", 040777)))
                .build()) {
            extractor.extract(out);
        }
        assertThat(Files.isSymbolicLink(out.resolve("d"))).isTrue();
        assertThat(Files.getPosixFilePermissions(sub)).isEqualTo(PosixFilePermissions.fromString("rwxr-x---"));
    }

    @Test
    void modeIsNotAppliedThroughALinkThatReplacedTheDirectory() throws IOException {
        Path outside = Files.createDirectories(dir.resolve("outside"));
        Path out = Files.createDirectories(dir.resolve("out"));
        Files.setPosixFilePermissions(outside, PosixFilePermissions.fromString("rwxr-x---"));
        InMemoryArchiveEntry link = InMemoryArchiveEntry.builder()
                .name("d")
                .type(SYMLINK)
                .linkName(outside.toString())
                .build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(directory("d", 040777), link))
                .overwrite(true)
                .escapingSymlinkPolicy(EscapingSymlinkPolicy.ALLOW)
                .build()) {
            extractor.extract(out);
        }
        assertThat(Files.isSymbolicLink(out.resolve("d"))).isTrue();
        assertThat(Files.getPosixFilePermissions(outside)).isEqualTo(PosixFilePermissions.fromString("rwxr-x---"));
    }

    @Test
    void modesAreAppliedDeepestFirstAndInArchiveOrderAtEqualDepth() throws IOException {
        List<String> applied = new ArrayList<>();
        var builder = InMemoryArchiveExtractor.builder(List.of(
                directory("p", 040755),
                directory("p/c", 040755),
                directory("q", 040755),
                directory("p/c/g", 040755),
                directory("q/h", 040755)));
        try (var extractor = withModes(
                        builder,
                        (path, mode) -> applied.add(dir.relativize(path).toString()))
                .build()) {
            extractor.extract(dir);
        }
        assertThat(applied).containsExactly("p/c/g", "p/c", "q/h", "p", "q");
    }

    @Test
    void skipContinuesWithTheNextDirectoryAfterAFailingMode() throws IOException {
        List<Path> applied = new ArrayList<>();
        var builder = InMemoryArchiveExtractor.builder(List.of(directory("a", 040755), directory("b", 040755)))
                .errorHandler((entry, failure) -> SKIP);
        try (var extractor = withModes(builder, failing(applied)).build()) {
            extractor.extract(dir);
        }
        assertThat(applied).hasSize(2);
    }

    @Test
    void skipAllStopsOfferingLaterFailuresToTheHandler() throws IOException {
        List<Path> applied = new ArrayList<>();
        AtomicInteger offered = new AtomicInteger();
        var builder = InMemoryArchiveExtractor.builder(
                        List.of(directory("a", 040755), directory("b", 040755), directory("c", 040755)))
                .errorHandler((entry, failure) -> {
                    offered.incrementAndGet();
                    return SKIP_ALL;
                });
        try (var extractor = withModes(builder, failing(applied)).build()) {
            extractor.extract(dir);
        }
        assertThat(applied).hasSize(3);
        assertThat(offered).hasValue(1);
    }

    @Test
    void abortRethrowsAFailingMode() throws IOException {
        var builder = InMemoryArchiveExtractor.builder(List.of(directory("a", 040755)))
                .errorHandler((entry, failure) -> ABORT);
        try (var extractor = withModes(builder, failing(new ArrayList<>())).build()) {
            assertThatThrownBy(() -> extractor.extract(dir))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("chmod");
        }
    }

    private static ArchiveExtractor.DirectoryModeApplier failing(List<Path> applied) {
        return (path, mode) -> {
            applied.add(path.getFileName());
            throw new IOException("chmod " + path);
        };
    }

    private static InMemoryArchiveExtractor.InMemoryArchiveExtractorBuilder withModes(
            InMemoryArchiveExtractor.InMemoryArchiveExtractorBuilder builder,
            ArchiveExtractor.DirectoryModeApplier applier) {
        ((ArchiveExtractor.Builder<?, ?>) builder).directoryModeApplier(applier);
        return builder;
    }

    private static void makeWritableIfPresent(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            makeWritable(path);
        }
    }

    private static void makeWritable(Path path) throws IOException {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
    }
}
