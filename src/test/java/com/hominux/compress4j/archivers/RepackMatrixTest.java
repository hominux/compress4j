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

import static com.hominux.compress4j.archivers.catalog.Capability.DIRECTORIES;
import static com.hominux.compress4j.archivers.catalog.Capability.MODES;
import static com.hominux.compress4j.archivers.catalog.Capability.REQUIRES_SIZE;
import static com.hominux.compress4j.archivers.catalog.Capability.SYMLINKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.Entry.Type;
import com.hominux.compress4j.archivers.catalog.ArchiveFormat;
import com.hominux.compress4j.archivers.catalog.FormatCatalog;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveEntry;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveExtractor;
import com.hominux.compress4j.exceptions.UnsafeEntryException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RepackMatrixTest {

    private static final boolean POSIX =
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    @TempDir
    Path tmp;

    static Stream<Arguments> pairs() {
        return FormatCatalog.readable().flatMap(from -> FormatCatalog.writable().map(to -> Arguments.of(from, to)));
    }

    private Path sourceTree() throws IOException {
        Path src = Files.createDirectories(tmp.resolve("src"));
        Path dir = Files.createDirectories(src.resolve("d"));
        Path file = Files.writeString(dir.resolve("run.sh"), "echo hi", StandardCharsets.UTF_8);
        if (POSIX) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-x---"));
            Files.createSymbolicLink(src.resolve("link"), Path.of("d/run.sh"));
        }
        return src;
    }

    private Path sourceArchive(ArchiveFormat from) throws IOException {
        Path source = tmp.resolve("in." + from.name());
        try (var creator = FormatCatalog.writerOf(from).createAt().orElseThrow().apply(source)) {
            creator.addDirectoryRecursively(sourceTree());
        }
        return source;
    }

    private static boolean hasUnsizedFile(ArchiveFormat from, Path source) throws IOException {
        try (var extractor = from.readAt().apply(source)) {
            return extractor.stream()
                    .map(ArchiveItem::entry)
                    .anyMatch(e -> e.type() == Type.FILE && e.size().isEmpty());
        }
    }

    private static Map<String, Entry> entries(ArchiveFormat format, Path archive) throws IOException {
        try (var extractor = format.readAt().apply(archive)) {
            return extractor.stream().map(ArchiveItem::entry).collect(Collectors.toMap(Entry::name, e -> e));
        }
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("pairs")
    void repackKeepsWhatBothFormatsDeclare(ArchiveFormat from, ArchiveFormat to) throws IOException {
        Path source = sourceArchive(from);
        boolean rejected = hasUnsizedFile(from, source) && to.has(REQUIRES_SIZE);
        Path target = tmp.resolve("out." + to.name());
        try (var extractor = from.readAt().apply(source);
                var creator = to.createAt().orElseThrow().apply(target)) {
            assertThat(creator.requiresSize()).as("REQUIRES_SIZE").isEqualTo(to.has(REQUIRES_SIZE));
            if (rejected) {
                Stream<EntrySource> sources = extractor.stream().map(ArchiveItem::toSource);
                assertThatThrownBy(() -> creator.addAll(sources)).isInstanceOf(IllegalArgumentException.class);
                return;
            }
            creator.addAll(extractor.stream().map(ArchiveItem::toSource));
        }
        assertRepacked(from, to, target);
    }

    private void assertRepacked(ArchiveFormat from, ArchiveFormat to, Path target) throws IOException {
        var entries = entries(to, target);
        boolean modes = from.has(MODES) && to.has(MODES);
        boolean dirs = from.has(DIRECTORIES) && to.has(DIRECTORIES);
        assertThat(entries.containsKey("d") && entries.get("d").type() == Type.DIR)
                .as("directory")
                .isEqualTo(dirs);
        if (POSIX) {
            assertPosixDetails(entries, modes, dirs, from.has(SYMLINKS) && to.has(SYMLINKS));
        }
        Path out = Files.createDirectories(tmp.resolve("x"));
        try (var extractor = to.readAt().apply(target)) {
            extractor.extract(out);
        }
        assertThat(out.resolve("d/run.sh")).hasContent("echo hi");
    }

    private static void assertPosixDetails(Map<String, Entry> entries, boolean modes, boolean dirs, boolean symlinks) {
        assertThat((entries.get("d/run.sh").mode() & 0777) == 0755)
                .as("file mode")
                .isEqualTo(modes);
        if (dirs) {
            assertThat((entries.get("d").mode() & 0777) == 0750)
                    .as("directory mode")
                    .isEqualTo(modes);
        }
        var link = entries.get("link");
        assertThat(link.type() == Type.SYMLINK && link.linkTarget().equals(Optional.of("d/run.sh")))
                .as("symlink")
                .isEqualTo(symlinks);
    }

    static Stream<ArchiveFormat> sizeFirstTargets() {
        return FormatCatalog.writable().filter(f -> f.has(REQUIRES_SIZE));
    }

    @ParameterizedTest(name = "zip-streaming -> {0}")
    @MethodSource("sizeFirstTargets")
    void unsizedSourceIntoSizeFirstTargetIsRejected(ArchiveFormat to) throws IOException {
        var zip = new ByteArrayOutputStream();
        try (var creator =
                FormatCatalog.named("zip").createOnStream().orElseThrow().apply(zip)) {
            creator.add(EntrySource.file("a.txt", "alpha".getBytes(StandardCharsets.UTF_8)));
        }
        var streaming = FormatCatalog.named("zip-streaming").readFromStream().orElseThrow();
        try (var extractor = streaming.apply(new ByteArrayInputStream(zip.toByteArray()));
                var creator = to.createAt().orElseThrow().apply(tmp.resolve("out." + to.name()))) {
            Stream<EntrySource> sources = extractor.stream().map(ArchiveItem::toSource);
            assertThatThrownBy(() -> creator.addAll(sources))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("a.txt");
        }
    }

    @Test
    void repackingAHostileNameIsRejected() throws IOException {
        var tar = FormatCatalog.named("tar");
        var hostile = InMemoryArchiveExtractor.builder(List.of(InMemoryArchiveEntry.builder()
                        .name("../evil")
                        .content("x")
                        .build()))
                .build();
        try (hostile;
                var creator = tar.createAt().orElseThrow().apply(tmp.resolve("out.tar"))) {
            assertThatThrownBy(() -> creator.addAll(hostile.stream().map(ArchiveItem::toSource)))
                    .isInstanceOf(UnsafeEntryException.class);
        }
    }
}
