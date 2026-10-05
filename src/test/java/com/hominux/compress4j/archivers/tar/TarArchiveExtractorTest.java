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
package com.hominux.compress4j.archivers.tar;

import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.assertion.Compress4JAssertions;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.internal.codec.Codecs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TarArchiveExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnNextFileEntry() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());
        var tarArchiveInputStream =
                spy(TarArchiveExtractor.builder(mockInputStream).buildArchiveInputStream());
        TarArchiveEntry mockTarEntry = mock(TarArchiveEntry.class);
        given(mockTarEntry.isCheckSumOK()).willReturn(true);
        given(mockTarEntry.getName()).willReturn("file.txt");
        //noinspection OctalInteger
        given(mockTarEntry.getMode()).willReturn(0400);
        given(mockTarEntry.isFile()).willReturn(true);
        given(mockTarEntry.getSize()).willReturn(10L);
        given(tarArchiveInputStream.getNextEntry()).willReturn(mockTarEntry, (TarArchiveEntry) null);

        try (TarArchiveExtractor tarDecompressor = new TarArchiveExtractor(tarArchiveInputStream)) {
            // when
            var result = tarDecompressor.nextEntry();

            // then
            Compress4JAssertions.assertThat(result.orElseThrow())
                    .hasName("file.txt")
                    .hasType(ArchiveExtractor.Entry.Type.FILE)
                    .hasMode(Set.of(OWNER_READ));
        }
    }

    @Test
    void shouldReturnNextSymlinkEntry() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());
        var tarArchiveInputStream =
                spy(TarArchiveExtractor.builder(mockInputStream).buildArchiveInputStream());
        TarArchiveEntry mockTarEntry = mock(TarArchiveEntry.class);
        given(mockTarEntry.isCheckSumOK()).willReturn(true);
        given(mockTarEntry.getName()).willReturn("file.txt");
        given(mockTarEntry.getLinkName()).willReturn("target.txt");
        @SuppressWarnings("OctalInteger")
        int value = 0644;
        given(mockTarEntry.getMode()).willReturn(value);
        given(mockTarEntry.isSymbolicLink()).willReturn(true);
        given(mockTarEntry.getSize()).willReturn(10L);
        given(tarArchiveInputStream.getNextEntry()).willReturn(mockTarEntry, (TarArchiveEntry) null);

        try (TarArchiveExtractor tarDecompressor = new TarArchiveExtractor(tarArchiveInputStream)) {
            // when
            var result = tarDecompressor.nextEntry();

            // then
            Compress4JAssertions.assertThat(result.orElseThrow())
                    .hasName("file.txt")
                    .hasMode(Set.of(OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ))
                    .hasLinkName("target.txt")
                    .hasType(ArchiveExtractor.Entry.Type.SYMLINK);
        }
    }

    @Test
    void shouldReturnNextDirectoryEntry() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());
        var tarArchiveInputStream =
                spy(TarArchiveExtractor.builder(mockInputStream).buildArchiveInputStream());
        TarArchiveEntry mockTarEntry = mock(TarArchiveEntry.class);
        given(mockTarEntry.isCheckSumOK()).willReturn(true);
        given(mockTarEntry.getName()).willReturn("file.txt");
        given(mockTarEntry.getLinkName()).willReturn("target.txt");
        @SuppressWarnings("OctalInteger")
        int value = 0400;
        given(mockTarEntry.getMode()).willReturn(value);
        given(mockTarEntry.isDirectory()).willReturn(true);
        given(mockTarEntry.getSize()).willReturn(10L);
        given(tarArchiveInputStream.getNextEntry()).willReturn(mockTarEntry, (TarArchiveEntry) null);

        try (TarArchiveExtractor tarDecompressor = new TarArchiveExtractor(tarArchiveInputStream)) {
            // when
            var result = tarDecompressor.nextEntry();

            // then
            Compress4JAssertions.assertThat(result.orElseThrow())
                    .hasName("file.txt")
                    .hasMode(Set.of(OWNER_READ))
                    .hasType(ArchiveExtractor.Entry.Type.DIR);
        }
    }

    @Test
    void shouldReturnEmptyWhenNoMoreEntries() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream(new byte[0]);

        try (TarArchiveExtractor tarDecompressor =
                TarArchiveExtractor.builder(mockInputStream).build()) {
            // when
            var result = tarDecompressor.nextEntry();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Test
    void shouldSkipEntryWhenNextEntryIsHardlink() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());
        var tarArchiveInputStream =
                spy(TarArchiveExtractor.builder(mockInputStream).buildArchiveInputStream());
        TarArchiveEntry mockTarEntry = mock(TarArchiveEntry.class);
        given(mockTarEntry.isCheckSumOK()).willReturn(true);
        given(mockTarEntry.isLink()).willReturn(true);
        given(mockTarEntry.getName()).willReturn("link");
        given(tarArchiveInputStream.getNextEntry()).willReturn(mockTarEntry, (TarArchiveEntry) null);

        try (TarArchiveExtractor tarDecompressor = new TarArchiveExtractor(tarArchiveInputStream)) {
            // when
            var result = tarDecompressor.nextEntry();

            // then
            //noinspection resource
            then(tarArchiveInputStream).should(times(3)).getNextEntry();
            then(mockTarEntry).should().isLink();
            assertThat(result).isEmpty();
        }
    }

    static Stream<Compression> explicitCodecs() {
        return Stream.of(Compression.lzma(), Compression.lz4Framed(), Compression.zstd());
    }

    @SuppressWarnings("OctalInteger")
    private static byte[] archiveOf(Compression compression, String name, byte[] content) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var creator =
                TarArchiveCreator.builder(bytes).compression(compression).build()) {
            creator.writeFile(
                    name,
                    new ByteArrayInputStream(content),
                    OptionalLong.of(content.length),
                    0644,
                    FileTime.from(Instant.now()));
        }
        return bytes.toByteArray();
    }

    private void extractAll(Compression compression, byte[] archive) throws IOException {
        try (var extractor = reader(compression, archive).build()) {
            extractor.extract(tempDir);
        }
    }

    private static TarArchiveExtractor.Builder reader(Compression compression, byte[] archive) {
        return TarArchiveExtractor.builder(new ByteArrayInputStream(archive)).compression(compression);
    }

    @ParameterizedTest
    @MethodSource("explicitCodecs")
    void shouldRoundTripThroughCreator(Compression compression) throws IOException {
        var archive = archiveOf(compression, "dir/file.txt", "payload".getBytes(StandardCharsets.UTF_8));

        try (var extractor = reader(compression, archive).build()) {
            extractor.extract(tempDir);
        }

        assertThat(tempDir.resolve("dir/file.txt")).hasContent("payload");
    }

    @ParameterizedTest
    @MethodSource("explicitCodecs")
    void shouldRejectPathTraversal(Compression compression) throws IOException {
        var archive = archiveOf(compression, "../escape.txt", "x".getBytes(StandardCharsets.UTF_8));
        var target = Files.createDirectory(tempDir.resolve("target"));

        try (var extractor = reader(compression, archive).build()) {
            assertThatThrownBy(() -> extractor.extract(target)).isInstanceOf(IOException.class);
        }
        assertThat(tempDir.resolve("escape.txt")).doesNotExist();
    }

    @ParameterizedTest
    @MethodSource("explicitCodecs")
    void shouldEnforceMaxEntrySizeOnHighlyCompressiblePayload(Compression compression) throws IOException {
        var payload = new byte[1_000_000];
        Arrays.fill(payload, (byte) 'a');
        var archive = archiveOf(compression, "bomb.txt", payload);
        assertThat(archive).hasSizeLessThan(payload.length / 10);

        try (var extractor = reader(compression, archive).maxEntrySize(1024).build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(LimitExceededException.class);
        }
    }

    @ParameterizedTest
    @MethodSource("explicitCodecs")
    void shouldEnforceMaxEntries(Compression compression) throws IOException {
        var archive = archiveOf(compression, "one.txt", "1".getBytes(StandardCharsets.UTF_8));

        try (var extractor = reader(compression, archive).maxEntries(0).build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(LimitExceededException.class);
        }
    }

    @ParameterizedTest
    @MethodSource("explicitCodecs")
    void shouldEnforceMaxTotalSize(Compression compression) throws IOException {
        var archive = archiveOf(compression, "big.txt", "x".repeat(4096).getBytes(StandardCharsets.UTF_8));

        try (var extractor = reader(compression, archive).maxTotalSize(1024).build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(LimitExceededException.class);
        }
    }

    @ParameterizedTest
    @MethodSource("explicitCodecs")
    void shouldRejectEscapingSymlinkWhenDisallowed(Compression compression) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(Codecs.compressing(compression, bytes))) {
            var link = new TarArchiveEntry("link", TarConstants.LF_SYMLINK);
            link.setLinkName("../outside");
            tar.putArchiveEntry(link);
            tar.closeArchiveEntry();
        }
        var target = Files.createDirectory(tempDir.resolve("target"));

        try (var extractor = reader(compression, bytes.toByteArray())
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(target)).isInstanceOf(IOException.class);
        }
        assertThat(target.resolve("link")).doesNotExist();
    }

    @ParameterizedTest
    @MethodSource("explicitCodecs")
    void shouldRejectTruncatedArchive(Compression compression) throws IOException {
        var archive = archiveOf(
                compression,
                "file.txt",
                "a longer payload to truncate".repeat(100).getBytes(StandardCharsets.UTF_8));
        var truncated = Arrays.copyOf(archive, archive.length / 2);

        assertThatThrownBy(() -> extractAll(compression, truncated)).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @MethodSource("explicitCodecs")
    void shouldRejectNonFormatInput(Compression compression) throws IOException {
        var gzip = new ByteArrayOutputStream();
        try (var out = new GZIPOutputStream(gzip)) {
            out.write("not the format".getBytes(StandardCharsets.UTF_8));
        }

        var notTheFormat = gzip.toByteArray();

        assertThatThrownBy(() -> extractAll(compression, notTheFormat)).isInstanceOf(IOException.class);
    }

    private static List<String> namesOf(byte[] bytes) throws IOException {
        try (var extractor =
                TarArchiveExtractor.builder(new ByteArrayInputStream(bytes)).build()) {
            return extractor.stream().map(i -> i.entry().name()).toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static byte[] plainTar() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var creator = TarArchiveCreator.builder(bytes)
                .longFileMode(TarLongFileMode.ERROR)
                .bigNumberMode(TarBigNumberMode.ERROR)
                .build()) {
            creator.addFile("a.txt", "alpha".getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    @Test
    void shouldReadAPlainTar() throws IOException {
        assertThat(namesOf(plainTar())).containsExactly("a.txt");
    }

    @Test
    void shouldRejectGarbageShorterThanOneRecord() {
        assertThatThrownBy(() -> namesOf(new byte[100]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a tar archive: 100 bytes");
        assertThatThrownBy(() -> namesOf("not a tar".repeat(10).getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("shorter than one header record");
    }

    @Test
    void shouldReadNothingAsAnEmptyArchive() throws IOException {
        assertThat(namesOf(new byte[0])).isEmpty();
        assertThat(namesOf(new byte[1024])).isEmpty();
    }

    @Test
    void shouldRejectACorruptedHeaderChecksum() throws IOException {
        byte[] tar = plainTar();
        tar[0] ^= 1;

        assertThatThrownBy(() -> namesOf(tar)).isInstanceOf(IOException.class).hasMessageContaining("checksum");
    }
}
