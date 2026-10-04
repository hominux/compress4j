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
package com.hominux.compress4j.archivers.sevenz;

import static com.hominux.compress4j.archivers.ArchiveExtractor.Entry.Type.DIR;
import static com.hominux.compress4j.archivers.ArchiveExtractor.Entry.Type.FILE;
import static com.hominux.compress4j.archivers.ArchiveExtractor.Entry.Type.SYMLINK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hominux.compress4j.archivers.ArchiveExtractor.Entry;
import com.hominux.compress4j.archivers.ArchiveExtractor.EscapingSymlinkPolicy;
import com.hominux.compress4j.archivers.ArchiveItem;
import com.hominux.compress4j.archivers.EntrySource;
import com.hominux.compress4j.exceptions.ArchiveLimitExceededException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Optional;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class SevenZArchiveTest {

    @TempDir
    Path tmp;

    private Path sourceTree() throws IOException {
        Path src = Files.createDirectories(tmp.resolve("src/sub"));
        Files.writeString(tmp.resolve("src/a.txt"), "alpha");
        Files.writeString(src.resolve("b.txt"), "bravo");
        return tmp.resolve("src");
    }

    private Path craft(String entryName, String content, char[] password) throws IOException {
        Path archive = tmp.resolve(password == null ? "crafted.7z" : "encrypted.7z");
        File file = archive.toFile();
        try (SevenZOutputFile out =
                password == null ? new SevenZOutputFile(file) : new SevenZOutputFile(file, password)) {
            SevenZArchiveEntry entry = new SevenZArchiveEntry();
            entry.setName(entryName);
            entry.setSize(content.length());
            out.putArchiveEntry(entry);
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }
        return archive;
    }

    @Test
    @DisplayName("Round-trips files and directories")
    void roundTrip() throws IOException {
        Path archive = tmp.resolve("out.7z");
        try (var creator = SevenZArchiveCreator.builder(archive).build()) {
            creator.addDirectoryRecursively(sourceTree());
        }

        Path out = tmp.resolve("out");
        try (var extractor = SevenZArchiveExtractor.builder(archive).build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("a.txt")).hasContent("alpha");
        assertThat(out.resolve("sub/b.txt")).hasContent("bravo");
    }

    @Test
    @DisplayName("Applies the entry filter")
    void filter() throws IOException {
        Path archive = tmp.resolve("out.7z");
        try (var creator = SevenZArchiveCreator.builder(archive).build()) {
            creator.addDirectoryRecursively(sourceTree());
        }

        Path out = tmp.resolve("out");
        try (var extractor = SevenZArchiveExtractor.builder(archive)
                .filter(e -> e.name().endsWith("a.txt"))
                .build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("a.txt")).exists();
        assertThat(out.resolve("sub/b.txt")).doesNotExist();
    }

    @Test
    @DisplayName("Stores a directory's mode in the Unix extension attribute bits")
    void directoryMode() throws IOException {
        Path archive = tmp.resolve("out.7z");
        try (var creator = SevenZArchiveCreator.builder(archive).build()) {
            creator.add(new EntrySource.Directory("d", 0750, FileTime.fromMillis(0)));
        }

        try (SevenZFile file = SevenZFile.builder().setPath(archive).get()) {
            SevenZArchiveEntry entry = file.getNextEntry();
            assertThat(entry.isDirectory()).isTrue();
            assertThat(entry.getWindowsAttributes() >>> 16).isEqualTo(0750);
            assertThat(entry.getWindowsAttributes() & SevenZArchiveExtractor.UNIX_EXTENSION)
                    .isNotZero();
        }
    }

    @Test
    @DisplayName("Round-trips a symbolic link as a symlink")
    @DisabledOnOs(OS.WINDOWS)
    void symlinkRoundTrip() throws IOException {
        Path src = Files.createDirectories(tmp.resolve("src"));
        Files.writeString(src.resolve("target.txt"), "t");
        Files.createSymbolicLink(src.resolve("link"), Path.of("target.txt"));
        Path archive = tmp.resolve("out.7z");
        try (var creator = SevenZArchiveCreator.builder(archive).build()) {
            creator.addDirectoryRecursively(src);
        }

        Path out = tmp.resolve("out");
        try (var extractor = SevenZArchiveExtractor.builder(archive).build()) {
            extractor.extract(out);
        }

        assertThat(Files.isSymbolicLink(out.resolve("link"))).isTrue();
        assertThat(Files.readSymbolicLink(out.resolve("link"))).isEqualTo(Path.of("target.txt"));
    }

    @Test
    @DisplayName("Rejects path traversal")
    void pathTraversal() throws IOException {
        Path archive = craft("../evil.txt", "x", null);

        try (var extractor = SevenZArchiveExtractor.builder(archive).build()) {
            assertThatThrownBy(() -> extractor.extract(tmp.resolve("out")))
                    .hasMessageStartingWith("Path traversal vulnerability detected!");
        }
        assertThat(tmp.resolve("evil.txt")).doesNotExist();
    }

    @Test
    @DisplayName("Rejects an escaping symlink when the policy is DISALLOW")
    @DisabledOnOs(OS.WINDOWS)
    void escapingSymlink() throws IOException {
        Path src = Files.createDirectories(tmp.resolve("src"));
        Files.createSymbolicLink(src.resolve("link"), Path.of("../outside"));
        Path archive = tmp.resolve("out.7z");
        try (var creator = SevenZArchiveCreator.builder(archive).build()) {
            creator.addDirectoryRecursively(src);
        }

        try (var extractor = SevenZArchiveExtractor.builder(archive)
                .escapingSymlinkPolicy(EscapingSymlinkPolicy.DISALLOW)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(tmp.resolve("out"))).hasMessageStartingWith("Invalid symlink");
        }
    }

    @Test
    @DisplayName("Enforces maxEntries")
    void maxEntries() throws IOException {
        Path archive = tmp.resolve("out.7z");
        try (var creator = SevenZArchiveCreator.builder(archive).build()) {
            creator.addDirectoryRecursively(sourceTree());
        }

        try (var extractor =
                SevenZArchiveExtractor.builder(archive).maxEntries(1).build()) {
            assertThatThrownBy(() -> extractor.extract(tmp.resolve("out")))
                    .isInstanceOf(ArchiveLimitExceededException.class);
        }
    }

    @Test
    @DisplayName("Enforces maxEntrySize")
    void maxEntrySize() throws IOException {
        Path archive = craft("big.txt", "0123456789", null);

        try (var extractor =
                SevenZArchiveExtractor.builder(archive).maxEntrySize(4).build()) {
            assertThatThrownBy(() -> extractor.extract(tmp.resolve("out")))
                    .isInstanceOf(ArchiveLimitExceededException.class);
        }
    }

    @Test
    @DisplayName("Extracts an encrypted archive with the right password")
    void password() throws IOException {
        Path archive = craft("secret.txt", "hush", "pw".toCharArray());

        Path out = tmp.resolve("out");
        try (var extractor = SevenZArchiveExtractor.builder(archive)
                .password("pw".toCharArray())
                .build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("secret.txt")).hasContent("hush");
    }

    private static void extract(Path archive, char[] password, Path out) throws IOException {
        try (var extractor =
                SevenZArchiveExtractor.builder(archive).password(password).build()) {
            extractor.extract(out);
        }
    }

    @Test
    @DisplayName("Fails on an encrypted archive with the wrong password")
    void wrongPassword() throws IOException {
        Path archive = craft("secret.txt", "hush", "pw".toCharArray());
        Path out = tmp.resolve("wrong");

        assertThatThrownBy(() -> extract(archive, "nope".toCharArray(), out)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Fails on an encrypted archive with no password")
    void missingPassword() throws IOException {
        Path archive = craft("secret.txt", "hush", "pw".toCharArray());
        Path out = tmp.resolve("none");

        assertThatThrownBy(() -> extract(archive, null, out)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Maps directory, symlink and plain file entries")
    void entryMapping() throws IOException {
        Path archive = tmp.resolve("mapped.7z");
        try (SevenZOutputFile out = new SevenZOutputFile(archive.toFile())) {
            put(out, "dir", true, SevenZArchiveExtractor.UNIX_EXTENSION | (0755 << 16), "");
            put(
                    out,
                    "link",
                    false,
                    SevenZArchiveExtractor.UNIX_EXTENSION | ((0777 | SevenZArchiveExtractor.S_IFLNK) << 16),
                    "target");
            put(out, "plain", false, -1, "p");
        }

        try (var extractor = SevenZArchiveExtractor.builder(archive).build()) {
            var entries = extractor.stream().map(ArchiveItem::entry).toList();

            assertThat(entries).extracting(Entry::type).containsExactly(DIR, SYMLINK, FILE);
            assertThat(entries.get(0).mode()).isEqualTo(0755);
            assertThat(entries.get(1).linkTarget()).contains("target");
            assertThat(entries.get(2).mode()).isZero();
        }
    }

    private static void put(SevenZOutputFile out, String name, boolean dir, int attributes, String content)
            throws IOException {
        SevenZArchiveEntry entry = new SevenZArchiveEntry();
        entry.setName(name);
        entry.setDirectory(dir);
        if (attributes != -1) {
            entry.setHasWindowsAttributes(true);
            entry.setWindowsAttributes(attributes);
        }
        entry.setSize(content.length());
        out.putArchiveEntry(entry);
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeArchiveEntry();
    }

    @Test
    @DisplayName("Creator stores a symlink entry as a Unix-mode entry holding the target")
    void creatorSymlinkEntry() throws IOException {
        Path archive = tmp.resolve("links.7z");
        try (var creator = SevenZArchiveCreator.builder(archive).build()) {
            creator.writeSymlink("a", "t1", 0, FileTime.fromMillis(0));
            creator.writeSymlink("b", "t2", 0644, FileTime.fromMillis(0));
        }

        try (var extractor = SevenZArchiveExtractor.builder(archive).build()) {
            var entries = extractor.stream().map(ArchiveItem::entry).toList();

            assertThat(entries).extracting(Entry::type).containsOnly(SYMLINK);
            assertThat(entries).extracting(e -> e.linkTarget().orElseThrow()).containsExactly("t1", "t2");
        }
    }

    @Test
    @DisplayName("Reads from a supplied seekable channel")
    void seekableChannel() throws IOException {
        Path archive = craft("c.txt", "chan", null);
        Path out = tmp.resolve("out");

        try (SeekableByteChannel channel = Files.newByteChannel(archive);
                var extractor = SevenZArchiveExtractor.builder(channel).build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("c.txt")).hasContent("chan");
    }

    @Test
    @DisplayName("Input stream reads single bytes, honours zero length and reports end of entry")
    void inputStreamReads() throws IOException {
        Path archive = craft("one.txt", "xy", null);

        try (var in = new SevenZFileArchiveInputStream(
                SevenZFile.builder().setPath(archive).get())) {
            in.getNextEntry();

            assertThat(in.read(new byte[1], 0, 0)).isZero();
            assertThat(in.read()).isEqualTo('x');
            assertThat(in.read(new byte[4], 0, 4)).isEqualTo(1);
            assertThat(in.read()).isEqualTo(-1);
            assertThat(in.read(new byte[4], 0, 4)).isEqualTo(-1);
        }
    }

    @Test
    @DisplayName("Output stream writes single bytes and creates entries from files")
    void outputStreamWrites() throws IOException {
        Path archive = tmp.resolve("raw.7z");
        Path source = Files.writeString(tmp.resolve("src.txt"), "z");

        try (var out = new SevenZFileArchiveOutputStream(new SevenZOutputFile(archive.toFile()))) {
            SevenZArchiveEntry entry = out.createArchiveEntry(source.toFile(), "named.txt");
            out.putArchiveEntry(entry);
            out.write('z');
            out.closeArchiveEntry();
            out.finish();
        }

        Path extracted = tmp.resolve("raw");
        extract(archive, null, extracted);
        assertThat(extracted.resolve("named.txt")).hasContent("z");
    }

    @Test
    @DisplayName("Skip consumes entry content and reports what it skipped")
    void inputStreamSkips() throws IOException {
        Path archive = craft("skip.txt", "abcd", null);

        try (var in = new SevenZFileArchiveInputStream(
                SevenZFile.builder().setPath(archive).get())) {
            in.getNextEntry();

            assertThat(in.skip(0)).isZero();
            assertThat(in.skip(-1)).isZero();
            assertThat(in.skip(1)).isEqualTo(1);
            assertThat(in.read()).isEqualTo('b');
            assertThat(in.skip(10)).isEqualTo(2);
            assertThat(in.skip(1)).isZero();
        }
    }

    @Test
    @DisplayName("Rejects a symlink entry whose target is larger than a path can be")
    void oversizedSymlinkTarget() throws IOException {
        Path archive = tmp.resolve("big-link.7z");
        try (SevenZOutputFile out = new SevenZOutputFile(archive.toFile())) {
            int attributes = SevenZArchiveExtractor.UNIX_EXTENSION | ((0777 | SevenZArchiveExtractor.S_IFLNK) << 16);
            put(out, "link", false, attributes, "x".repeat(5000));
        }

        try (var extractor = SevenZArchiveExtractor.builder(archive).build()) {
            assertThatThrownBy(extractor::nextEntry)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("exceeds");
        }
    }

    @Test
    @DisplayName("Reports an entry without a name instead of failing obscurely")
    void unnamedEntryIsRejected() throws IOException {
        var stream = mock(SevenZFileArchiveInputStream.class);
        when(stream.getNextEntry()).thenReturn(new SevenZArchiveEntry());

        var extractor = new SevenZArchiveExtractor.SevenZArchiveExtractorBuilder(Optional.empty(), Optional.empty()) {
            @Override
            public SevenZFileArchiveInputStream buildArchiveInputStream() {
                return stream;
            }
        }.build();

        assertThatThrownBy(extractor::nextEntry)
                .isInstanceOf(NullPointerException.class)
                .hasMessage("7z entry has no name");
    }

    @Test
    void roundTripsThroughInMemoryChannels() throws IOException {
        // Given
        var channel = new SeekableInMemoryByteChannel();
        try (var creator = SevenZArchiveCreator.builder(channel).build()) {
            creator.addFile("a.txt", "alpha".getBytes(StandardCharsets.UTF_8));
        }

        // When
        try (var extractor = SevenZArchiveExtractor.builder(new SeekableInMemoryByteChannel(channel.array()))
                .build()) {
            var item = extractor.stream().findFirst().orElseThrow();

            // Then
            assertThat(new String(item.content().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("alpha");
        }
    }

    @Test
    void creatorWritesFromOffsetZeroOverPrecedingBytes() throws IOException {
        // Given
        var channel = new SeekableInMemoryByteChannel("prefix-bytes".getBytes(StandardCharsets.UTF_8));
        channel.position(channel.size());

        // When
        try (var creator = SevenZArchiveCreator.builder(channel).build()) {
            creator.addFile("a.txt", "alpha".getBytes(StandardCharsets.UTF_8));
        }

        // Then
        try (var extractor = SevenZArchiveExtractor.builder(new SeekableInMemoryByteChannel(channel.array()))
                .build()) {
            assertThat(extractor.stream().map(item -> item.entry().name())).containsExactly("a.txt");
        }
    }

    @Test
    void offersNoStreamBuilders() {
        assertThat(Arrays.stream(SevenZArchiveExtractor.class.getMethods())
                        .filter(m -> m.getName().equals("builder"))
                        .flatMap(m -> Arrays.stream(m.getParameterTypes())))
                .doesNotContain(InputStream.class);
        assertThat(Arrays.stream(SevenZArchiveCreator.class.getMethods())
                        .filter(m -> m.getName().equals("builder"))
                        .flatMap(m -> Arrays.stream(m.getParameterTypes())))
                .doesNotContain(OutputStream.class);
    }
}
