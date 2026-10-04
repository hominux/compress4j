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
import static com.hominux.compress4j.archivers.catalog.Capability.LAST_MODIFIED;
import static com.hominux.compress4j.archivers.catalog.Capability.MODES;
import static com.hominux.compress4j.archivers.catalog.Capability.STREAM_INPUT;
import static com.hominux.compress4j.archivers.catalog.Capability.STREAM_OUTPUT;
import static com.hominux.compress4j.archivers.catalog.Capability.SYMLINKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.hominux.compress4j.archivers.catalog.ArchiveFormat;
import com.hominux.compress4j.archivers.catalog.FormatCatalog;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisabledOnOs(OS.WINDOWS)
class FormatContractTest {

    private static final FileTime MODIFIED = FileTime.from(Instant.parse("2024-05-01T10:00:00Z"));

    @TempDir
    Path tmp;

    static Stream<ArchiveFormat> readable() {
        return FormatCatalog.readable();
    }

    private Path source() throws IOException {
        Path src = Files.createDirectories(tmp.resolve("src"));
        Path dir = Files.createDirectories(src.resolve("d"));
        Path file = Files.writeString(dir.resolve("run.sh"), "echo hi", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-x---"));
        Files.setLastModifiedTime(file, MODIFIED);
        Files.createSymbolicLink(src.resolve("link"), Path.of("d/run.sh"));
        return src;
    }

    private Path roundTrip(ArchiveFormat format) throws IOException {
        Path src = source();
        Path archive = tmp.resolve("archive." + format.name());
        try (var creator =
                FormatCatalog.writerOf(format).createAt().orElseThrow().apply(archive)) {
            creator.addDirectoryRecursively(src);
        }
        return archive;
    }

    private Map<String, ArchiveExtractor.Entry> entries(ArchiveFormat format, Path archive) throws IOException {
        try (var extractor = format.readAt().apply(archive)) {
            return extractor.stream()
                    .map(ArchiveItem::entry)
                    .collect(Collectors.toMap(ArchiveExtractor.Entry::name, e -> e));
        }
    }

    @ParameterizedTest
    @MethodSource("readable")
    void fileContentSurvivesRoundTrip(ArchiveFormat format) throws IOException {
        Path archive = roundTrip(format);
        Path out = tmp.resolve("out");
        try (var extractor = format.readAt().apply(archive)) {
            extractor.extract(out);
        }
        assertThat(out.resolve("d/run.sh")).hasContent("echo hi");
    }

    @ParameterizedTest
    @MethodSource("readable")
    void modesMatchDeclaration(ArchiveFormat format) throws IOException {
        var file = entries(format, roundTrip(format)).get("d/run.sh");
        if (format.has(MODES)) {
            assertThat(file.mode() & 0777).isEqualTo(0750);
        } else {
            assertThat(file.mode() & 0777).isNotEqualTo(0750);
        }
    }

    @ParameterizedTest
    @MethodSource("readable")
    void symlinksMatchDeclaration(ArchiveFormat format) throws IOException {
        var link = entries(format, roundTrip(format)).get("link");
        if (format.has(SYMLINKS)) {
            assertThat(link.type()).isEqualTo(ArchiveExtractor.Entry.Type.SYMLINK);
            assertThat(link.linkTarget()).contains("d/run.sh");
        } else {
            assertThat(link.type()).isNotEqualTo(ArchiveExtractor.Entry.Type.SYMLINK);
        }
    }

    @ParameterizedTest
    @MethodSource("readable")
    void directoriesMatchDeclaration(ArchiveFormat format) throws IOException {
        var names = entries(format, roundTrip(format));
        assertThat(names.containsKey("d") && names.get("d").type() == ArchiveExtractor.Entry.Type.DIR)
                .isEqualTo(format.has(DIRECTORIES));
    }

    @ParameterizedTest
    @MethodSource("readable")
    void lastModifiedMatchesDeclaration(ArchiveFormat format) throws IOException {
        assumeThat(format.has(LAST_MODIFIED)).isTrue();
        var file = entries(format, roundTrip(format)).get("d/run.sh");
        assertThat(file.lastModified()).isPresent();
        long deltaMillis = Math.abs(file.lastModified().orElseThrow().toMillis() - MODIFIED.toMillis());
        assertThat(deltaMillis).isLessThanOrEqualTo(2000);
    }

    static Stream<ArchiveFormat> channelCapable() {
        return FormatCatalog.writable().filter(f -> f.createOnChannel().isPresent());
    }

    @ParameterizedTest
    @MethodSource("channelCapable")
    void channelRoundTrip(ArchiveFormat format) throws IOException {
        Path archive = tmp.resolve("channel." + format.name());
        try (var channel = Files.newByteChannel(archive, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                var creator = format.createOnChannel().orElseThrow().apply(channel)) {
            creator.addFile("a.txt", "alpha".getBytes(StandardCharsets.UTF_8));
        }
        try (var channel = Files.newByteChannel(archive);
                var extractor = format.readFromChannel().orElseThrow().apply(channel)) {
            var item = extractor.stream().findFirst().orElseThrow();
            assertThat(new String(item.content().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("alpha");
        }
    }

    private static boolean declaresFactory(Class<?> owner, String name, Class<?> parameter) {
        return Arrays.stream(owner.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(name)
                        && Modifier.isPublic(m.getModifiers())
                        && Modifier.isStatic(m.getModifiers())
                        && Arrays.equals(m.getParameterTypes(), new Class<?>[] {parameter}));
    }

    @Test
    void everyWritableFormatHasChannelBuilders() {
        assertThat(FormatCatalog.writable()).allSatisfy(f -> {
            assertThat(declaresFactory(f.creator().orElseThrow(), "builder", SeekableByteChannel.class))
                    .as(f.name() + " create")
                    .isTrue();
            assertThat(declaresFactory(f.extractor(), "builder", SeekableByteChannel.class))
                    .as(f.name() + " read")
                    .isTrue();
            assertThat(f.createOnChannel()).as(f.name() + " create row").isPresent();
            assertThat(f.readFromChannel()).as(f.name() + " read row").isPresent();
        });
    }

    static Stream<ArchiveFormat> streamWriters() {
        return FormatCatalog.writable().filter(f -> f.has(STREAM_OUTPUT));
    }

    private static ArchiveFormat streamReaderOf(ArchiveFormat writer) {
        return FormatCatalog.all()
                .filter(f -> f.has(STREAM_INPUT))
                .filter(f -> FormatCatalog.writerOf(f).name().equals(writer.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(writer.name() + " writes streams but no row reads them"));
    }

    @ParameterizedTest
    @MethodSource("streamWriters")
    void streamRoundTrip(ArchiveFormat format) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var creator = format.createOnStream().orElseThrow().apply(bytes)) {
            creator.addFile("a.txt", "alpha".getBytes(StandardCharsets.UTF_8));
        }
        try (var extractor = streamReaderOf(format)
                .readFromStream()
                .orElseThrow()
                .apply(new ByteArrayInputStream(bytes.toByteArray()))) {
            var item = extractor.stream().findFirst().orElseThrow();
            assertThat(new String(item.content().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("alpha");
        }
    }

    @Test
    void streamBuildersExistExactlyWhereDeclared() {
        assertThat(FormatCatalog.all()).allSatisfy(f -> {
            assertThat(declaresFactory(f.extractor(), f.streamFactory(), InputStream.class))
                    .as(f.name() + " stream input")
                    .isEqualTo(f.has(STREAM_INPUT));
            assertThat(f.creator().filter(c -> declaresFactory(c, "builder", OutputStream.class)))
                    .as(f.name() + " stream output")
                    .matches(c -> c.isPresent() == f.has(STREAM_OUTPUT));
            assertThat(f.readFromStream().isPresent())
                    .as(f.name() + " stream input row")
                    .isEqualTo(f.has(STREAM_INPUT));
            assertThat(f.createOnStream().isPresent())
                    .as(f.name() + " stream output row")
                    .isEqualTo(f.has(STREAM_OUTPUT));
        });
    }
}
