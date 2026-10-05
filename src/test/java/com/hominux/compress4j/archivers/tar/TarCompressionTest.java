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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.ArchiveItem;
import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.archivers.EntrySource;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.internal.codec.Codecs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class TarCompressionTest {

    @TempDir
    Path dir;

    static Stream<Compression> detectable() {
        return Stream.of(
                Compression.none(),
                Compression.gzip(),
                Compression.bzip2(),
                Compression.xz(),
                Compression.zstd(),
                Compression.lz4Framed(),
                Compression.snappyFramed());
    }

    private Path write(Compression compression) throws IOException {
        Path tar = dir.resolve("a.tar");
        try (var creator =
                TarArchiveCreator.builder(tar).compression(compression).build()) {
            creator.add(EntrySource.file("a.txt", "alpha".getBytes(StandardCharsets.UTF_8)));
        }
        return tar;
    }

    private static List<String> names(TarArchiveExtractor extractor) {
        return extractor.stream().map(ArchiveItem::entry).map(Entry::name).toList();
    }

    @ParameterizedTest
    @MethodSource("detectable")
    void readsBackWithoutBeingToldTheCompression(Compression compression) throws IOException {
        try (var extractor = TarArchiveExtractor.builder(write(compression)).build()) {
            assertThat(names(extractor)).containsExactly("a.txt");
        }
    }

    @ParameterizedTest
    @MethodSource("detectable")
    void readsFromAStreamToo(Compression compression) throws IOException {
        try (InputStream in = Files.newInputStream(write(compression));
                var extractor = TarArchiveExtractor.builder(in).build()) {
            assertThat(names(extractor)).containsExactly("a.txt");
        }
    }

    @Test
    void lzmaNeedsExplicitCompression() throws IOException {
        Path tar = write(Compression.lzma());
        try (var extractor = TarArchiveExtractor.builder(tar).build()) {
            assertThatThrownBy(() -> extractor.extract(dir.resolve("out"))).isInstanceOf(IOException.class);
        }
        try (var extractor =
                TarArchiveExtractor.builder(tar).compression(Compression.lzma()).build()) {
            assertThat(names(extractor)).containsExactly("a.txt");
        }
    }

    @Test
    void readOnlyCompressionIsRejectedForWriting() throws IOException {
        Path tar = dir.resolve("never.tar.Z");
        try (var out = Files.newOutputStream(tar)) {
            var builder = TarArchiveCreator.builder(out);
            var unixZ = Compression.unixZ();
            assertThatThrownBy(() -> builder.compression(unixZ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("can only be read");
        }
    }

    @Test
    void longFileModeIsApplied() throws IOException {
        Path tar = dir.resolve("long.tar");
        String longName = "d/".repeat(60) + "f.txt";
        try (var creator = TarArchiveCreator.builder(tar)
                .longFileMode(TarLongFileMode.POSIX)
                .build()) {
            creator.add(EntrySource.file(longName, new byte[0]));
        }
        try (var extractor = TarArchiveExtractor.builder(tar).build()) {
            assertThat(names(extractor)).containsExactly(longName);
        }
    }

    @Test
    void aPack200SignatureIsReadAsPlainTarNotDecompressed() {
        byte[] pack200Magic = {(byte) 0xCA, (byte) 0xFE, (byte) 0xD0, 0x0D, 0, 0, 0, 0, 0, 0, 0, 0};
        assertThatThrownBy(() -> extractAll(pack200Magic, dir.resolve("pack-out")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a tar archive");
    }

    private static void extractAll(byte[] archive, Path out) throws IOException {
        try (var extractor =
                TarArchiveExtractor.builder(new ByteArrayInputStream(archive)).build()) {
            extractor.extract(out);
        }
    }

    @Test
    void pack200IsRejectedForWritingAndForExplicitReading() {
        var message = "Pack200 compresses JAR files, not tar streams";
        var pack200 = Compression.pack200();
        var creator = TarArchiveCreator.builder(new ByteArrayOutputStream());
        assertThatThrownBy(() -> creator.compression(pack200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
        var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(new byte[0]));
        assertThatThrownBy(() -> extractor.compression(pack200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    private static byte[] plainTarOf(String... fileNames) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var creator = TarArchiveCreator.builder(bytes).build()) {
            for (String name : fileNames) {
                creator.add(EntrySource.file(name, name.getBytes(StandardCharsets.UTF_8)));
            }
        }
        return bytes.toByteArray();
    }

    static Stream<Compression> concatenable() {
        return Stream.of(Compression.gzip(), Compression.bzip2(), Compression.xz(), Compression.lz4Framed());
    }

    @ParameterizedTest
    @MethodSource("concatenable")
    void detectedMultiStreamArchivesAreReadToTheEnd(Compression compression) throws IOException {
        byte[] tar = plainTarOf("a.txt", "b.txt", "c.txt");
        int split = 512 * 3;
        var joined = new ByteArrayOutputStream();
        for (byte[] part : List.of(Arrays.copyOfRange(tar, 0, split), Arrays.copyOfRange(tar, split, tar.length))) {
            try (var out = Codecs.compressing(compression, joined)) {
                out.write(part);
            }
        }
        try (var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(joined.toByteArray()))
                .build()) {
            assertThat(names(extractor)).containsExactly("a.txt", "b.txt", "c.txt");
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = TarLongFileMode.class,
            names = {"GNU", "POSIX"})
    void longNamesRoundTripInEveryStoringMode(TarLongFileMode mode) throws IOException {
        String longName = "d/".repeat(60) + "f.txt";
        Path tar = dir.resolve("modes.tar");
        try (var creator = TarArchiveCreator.builder(tar).longFileMode(mode).build()) {
            creator.add(EntrySource.file(longName, new byte[0]));
        }
        try (var extractor = TarArchiveExtractor.builder(tar).build()) {
            assertThat(names(extractor)).containsExactly(longName);
        }
    }

    @Test
    void defaultsStoreLongAndNonAsciiNames() throws IOException {
        String longName = "d/".repeat(75) + "f.txt";
        String nonAscii = "résumé-日本語.txt";
        Path tar = dir.resolve("defaults.tar");
        try (var creator = TarArchiveCreator.builder(tar).build()) {
            creator.add(EntrySource.file(longName, new byte[0]));
            creator.add(EntrySource.file(nonAscii, new byte[0]));
        }
        try (var extractor = TarArchiveExtractor.builder(tar).build()) {
            assertThat(names(extractor)).containsExactly(longName, nonAscii);
        }
    }

    @Test
    void errorModeStillRejectsALongName() throws IOException {
        String longName = "d/".repeat(75) + "f.txt";
        try (var creator = TarArchiveCreator.builder(new ByteArrayOutputStream())
                .longFileMode(TarLongFileMode.ERROR)
                .build()) {
            var source = EntrySource.file(longName, new byte[0]);
            assertThatThrownBy(() -> creator.add(source))
                    .isInstanceOfAny(IOException.class, IllegalArgumentException.class);
        }
    }

    @Test
    void readsPaxHeadersAndModesWrittenByCommons() throws IOException {
        String longName = "d/".repeat(60) + "f.txt";
        var bytes = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(bytes)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            var entry = new TarArchiveEntry(longName);
            entry.setSize(3);
            entry.setMode(0755);
            entry.addPaxHeader("comment", "pax");
            tar.putArchiveEntry(entry);
            tar.write(new byte[] {1, 2, 3});
            tar.closeArchiveEntry();
        }
        try (var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(bytes.toByteArray()))
                .build()) {
            var entries = extractor.stream().map(ArchiveItem::entry).toList();
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).name()).isEqualTo(longName);
            assertThat(entries.get(0).mode() & 0777).isEqualTo(0755);
        }
    }

    @Test
    void aFailedBuildLeavesACallersStreamOpen() {
        var closed = new AtomicBoolean();
        var notGzip = new ByteArrayInputStream("not gzip at all".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        var builder = TarArchiveExtractor.builder(notGzip).compression(Compression.gzip());
        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);
        assertThat(closed).isFalse();
    }

    private byte[] archive(Compression compression) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var payload = Stream.iterate(0, i -> i + 1)
                .limit(2000)
                .map(i -> "line " + i + " value " + (i * 31 % 97) + "\n")
                .collect(Collectors.joining())
                .getBytes(StandardCharsets.UTF_8);
        try (var creator =
                TarArchiveCreator.builder(bytes).compression(compression).build()) {
            creator.add(EntrySource.file("data.txt", payload));
        }
        return bytes.toByteArray();
    }

    @Test
    void zstdLevelReachesTheCodec() throws IOException {
        byte[] fastest = archive(Compression.zstd().level(1));
        byte[] strongest = archive(Compression.zstd().level(19));
        assertThat(strongest).hasSizeLessThan(fastest.length);
    }

    @Test
    void gzipLevelReachesTheCodec() throws IOException {
        byte[] stored = archive(Compression.gzip().level(0));
        byte[] strongest = archive(Compression.gzip().level(9));
        assertThat(strongest).hasSizeLessThan(stored.length);
    }

    @Test
    void aTarNamedLikeBzip2IsStillReadAsPlainTar() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var creator = TarArchiveCreator.builder(bytes).build()) {
            creator.add(EntrySource.file("BZh9.txt", "x".getBytes(StandardCharsets.UTF_8)));
        }
        try (var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(bytes.toByteArray()))
                .build()) {
            assertThat(names(extractor)).containsExactly("BZh9.txt");
        }
    }

    @Test
    void defaultTarsCarryNoPaxHeaders() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var creator = TarArchiveCreator.builder(bytes).build()) {
            for (String name : List.of("a.txt", "b.txt", "c.txt")) {
                creator.add(EntrySource.file(name, name.getBytes(StandardCharsets.UTF_8))
                        .withLastModified(FileTime.fromMillis(1_700_000_000_123L)));
            }
        }
        byte[] tar = bytes.toByteArray();
        for (int block = 0; block * 512 < tar.length; block++) {
            assertThat(tar[block * 512 + 156]).isNotEqualTo((byte) 'x');
        }
        assertThat(tar[0]).isEqualTo((byte) 'a');
        assertThat(tar[1024]).isEqualTo((byte) 'b');
        assertThat(tar[2048]).isEqualTo((byte) 'c');
    }

    @Test
    void bigNumbersDefaultToStar() throws IOException {
        try (var out = TarArchiveCreator.builder(new ByteArrayOutputStream()).buildArchiveOutputStream()) {
            assertThat(out)
                    .extracting("bigNumberMode", "longFileMode")
                    .containsExactly(TarArchiveOutputStream.BIGNUMBER_STAR, TarArchiveOutputStream.LONGFILE_POSIX);
        }
    }
}
