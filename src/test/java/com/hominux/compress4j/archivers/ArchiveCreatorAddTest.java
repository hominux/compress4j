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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.ar.ArArchiveCreator;
import com.hominux.compress4j.archivers.catalog.ArchiveFormat;
import com.hominux.compress4j.archivers.catalog.FormatCatalog;
import com.hominux.compress4j.archivers.cpio.CpioArchiveCreator;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarArchiveCreator;
import com.hominux.compress4j.exceptions.UnsafeEntryException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.function.IOFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ArchiveCreatorAddTest {

    private static final FileTime T = FileTime.from(Instant.EPOCH);

    private static EntrySource.File unsized(String name) {
        return new EntrySource.File(
                name, 0, T, OptionalLong.empty(), () -> new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../evil", "a/../../evil", "a/..", "C:/evil", "..\u0000", "sub/..\u0000x", "C:evil.txt"})
    void rejectsUnsafeNames(String name) throws IOException {
        try (var creator =
                TarArchiveCreator.builder(new ByteArrayOutputStream()).build()) {
            var source = EntrySource.file(name, new byte[0]);
            assertThatThrownBy(() -> creator.add(source))
                    .isInstanceOf(UnsafeEntryException.class)
                    .hasMessageContaining(name);
        }
    }

    @Test
    void sanitisesLeadingSlashesAndBackslashes() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var creator = TarArchiveCreator.builder(out).build()) {
            creator.add(EntrySource.file("/a\\b.txt", new byte[0]));
        }
        assertThat(out.toString(StandardCharsets.ISO_8859_1)).contains("a/b.txt");
    }

    private static Stream<Arguments> sizeFirstFormats() {
        return Stream.of(
                Arguments.of("tar", (IOFunction<OutputStream, ArchiveCreator<?>>)
                        out -> TarArchiveCreator.builder(out).build()),
                Arguments.of("ar", (IOFunction<OutputStream, ArchiveCreator<?>>)
                        out -> ArArchiveCreator.builder(out).build()),
                Arguments.of("cpio", (IOFunction<OutputStream, ArchiveCreator<?>>)
                        out -> CpioArchiveCreator.builder(out).build()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sizeFirstFormats")
    void unknownSizeIntoSizeFirstFormatFailsWithBufferedHint(
            String format, IOFunction<OutputStream, ArchiveCreator<?>> factory) throws IOException {
        try (var creator = factory.apply(new ByteArrayOutputStream())) {
            var source = unsized("a.txt");
            assertThatThrownBy(() -> creator.add(source))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("a.txt")
                    .hasMessageContaining("EntrySource.buffered");
        }
    }

    @Test
    void unknownSizeRejectionLeavesTheCreatorUsable(@TempDir Path tempDir) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var creator = TarArchiveCreator.builder(out).build()) {
            var source = unsized("a.txt");
            assertThatThrownBy(() -> creator.add(source)).isInstanceOf(IllegalArgumentException.class);
            creator.add(EntrySource.buffered(source, tempDir));
        }
        assertThat(out.toString(StandardCharsets.ISO_8859_1)).contains("a.txt");
    }

    @Test
    void cpioWritesTheGivenSizeForAStreamThatReportsNothingAvailable() throws IOException {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        var stingy = new ByteArrayInputStream(content) {
            @Override
            public synchronized int available() {
                return 0;
            }
        };
        var out = new ByteArrayOutputStream();
        try (var creator = CpioArchiveCreator.builder(out).build()) {
            creator.addFile("a.txt", stingy, content.length);
        }

        try (var in = new CpioArchiveInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            var entry = in.getNextEntry();
            assertThat(entry.getName()).isEqualTo("a.txt");
            assertThat(entry.getSize()).isEqualTo(content.length);
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void unknownSizeIntoSizeFreeFormatIsWritten() throws IOException {
        try (var creator =
                new InMemoryArchiveCreator.InMemoryArchiveCreatorBuilder(new ByteArrayOutputStream()).build()) {
            var source = unsized("a.txt");
            assertThatCode(() -> creator.add(source)).doesNotThrowAnyException();
        }
    }

    @Test
    void creatorIsFailedAfterAWriteThrows() throws IOException {
        boolean[] sinkClosed = {false};
        var sink = new ByteArrayOutputStream() {
            @Override
            public void close() {
                sinkClosed[0] = true;
            }
        };
        var creator = TarArchiveCreator.builder(sink).build();
        var lying = new EntrySource.File("a", 0, T, OptionalLong.of(10), () -> new ByteArrayInputStream(new byte[2]));
        assertThatThrownBy(() -> creator.add(lying)).isInstanceOf(IOException.class);
        var next = EntrySource.file("b", new byte[0]);
        assertThatThrownBy(() -> creator.add(next)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(creator::close).isInstanceOf(IOException.class).hasMessageContaining("unclosed entries");
        assertThat(sinkClosed[0]).as("sink closed").isTrue();
    }

    @Test
    void writesDirectoriesAndSymlinksUnderTheirSanitisedNames() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var creator = TarArchiveCreator.builder(out).build()) {
            creator.add(new EntrySource.Directory("/a\\d", 0755, T));
            creator.add(new EntrySource.Symlink("/l", "a/d", 0777, T));
        }

        try (var in = new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            var dir = in.getNextEntry();
            assertThat(dir.getName()).isEqualTo("a/d/");
            assertThat(dir.isDirectory()).isTrue();
            var link = in.getNextEntry();
            assertThat(link.getName()).isEqualTo("l");
            assertThat(link.isSymbolicLink()).isTrue();
            assertThat(link.getLinkName()).isEqualTo("a/d");
        }
    }

    private static Closeable quietly(ArchiveCreator<?> creator) {
        return () -> IOUtils.closeQuietly(creator);
    }

    static Stream<ArchiveFormat> writable() {
        return FormatCatalog.writable();
    }

    private static EntrySource.File declaring(String name, long size, InputStream content) {
        return new EntrySource.File(name, 0, T, OptionalLong.of(size), () -> content);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writable")
    @SuppressWarnings("try")
    void contentShorterThanItsDeclaredSizeFails(ArchiveFormat format, @TempDir Path tempDir) throws IOException {
        var creator = format.createAt().orElseThrow().apply(tempDir.resolve("short." + format.name()));
        try (var ignored = quietly(creator)) {
            var source = declaring("short.txt", 10, new ByteArrayInputStream(new byte[2]));
            assertThatThrownBy(() -> creator.add(source))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("short.txt")
                    .hasMessageContaining("declared");
            var next = EntrySource.file("next", new byte[0]);
            assertThatThrownBy(() -> creator.add(next)).isInstanceOf(IllegalStateException.class);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writable")
    @SuppressWarnings("try")
    void contentLongerThanItsDeclaredSizeFailsWithoutReadingOn(ArchiveFormat format, @TempDir Path tempDir)
            throws IOException {
        var content = new ByteArrayInputStream(new byte[8]);
        var creator = format.createAt().orElseThrow().apply(tempDir.resolve("long." + format.name()));
        try (var ignored = quietly(creator)) {
            var source = declaring("long.txt", 2, content);
            assertThatThrownBy(() -> creator.add(source))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("long.txt")
                    .hasMessageContaining("declared");
            assertThat(content.available()).isGreaterThanOrEqualTo(8 - 3);
            var next = EntrySource.file("next", new byte[0]);
            assertThatThrownBy(() -> creator.add(next)).isInstanceOf(IllegalStateException.class);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writable")
    @SuppressWarnings("try")
    void emptyDeclaredContentWithExtraBytesFails(ArchiveFormat format, @TempDir Path tempDir) throws IOException {
        var creator = format.createAt().orElseThrow().apply(tempDir.resolve("empty." + format.name()));
        try (var ignored = quietly(creator)) {
            var source = declaring("empty.txt", 0, new ByteArrayInputStream(new byte[1]));
            assertThatThrownBy(() -> creator.add(source))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("empty.txt")
                    .hasMessageContaining("declared");
        }
    }

    @Test
    void filterSeesSanitisedSources() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var creator = TarArchiveCreator.builder(out)
                .filter(s -> !s.name().equals("skip.txt"))
                .build()) {
            creator.addAll(
                    Stream.of(EntrySource.file("/skip.txt", new byte[0]), EntrySource.file("keep.txt", new byte[0])));
        }
        assertThat(out.toString(StandardCharsets.ISO_8859_1))
                .contains("keep.txt")
                .doesNotContain("skip.txt");
    }

    @Test
    void addAllDoesNotCloseTheCallersStream() throws IOException {
        boolean[] closed = {false};
        try (var creator =
                TarArchiveCreator.builder(new ByteArrayOutputStream()).build()) {
            creator.addAll(Stream.of(EntrySource.file("a", new byte[0])).onClose(() -> closed[0] = true));
        }
        assertThat(closed[0]).isFalse();
    }

    @Test
    void addFileWithInputStreamRequiresASize() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var creator = TarArchiveCreator.builder(out).build()) {
            creator.addFile("a.txt", new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), 3);
        }
        assertThat(out.toString(StandardCharsets.ISO_8859_1)).contains("a.txt");
    }
}
