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
package com.hominux.compress4j.internal.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.compressors.Bzip2;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.compressors.DeflateStrategy;
import com.hominux.compress4j.compressors.Lz4Framed;
import com.hominux.compress4j.compressors.None;
import com.hominux.compress4j.compressors.Pack200;
import com.hominux.compress4j.compressors.UnixZ;
import com.hominux.compress4j.compressors.Xz;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Stream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CodecsTest {

    private static final byte[] TEXT = "hello hello hello hello".repeat(50).getBytes(StandardCharsets.UTF_8);

    static Stream<Compression> writable() {
        return Stream.of(
                Compression.none(),
                Compression.gzip(),
                Compression.gzip().deflateStrategy(DeflateStrategy.FILTERED),
                Compression.gzip().deflateStrategy(DeflateStrategy.HUFFMAN_ONLY),
                Compression.bzip2(),
                Compression.xz(),
                Compression.xz().memoryLimitKiB(64 * 1024),
                Compression.lzma(),
                Compression.lzma().memoryLimitKiB(64 * 1024),
                Compression.lz4Block(),
                Compression.lz4Framed(),
                Compression.zstd(),
                Compression.deflate(),
                Compression.deflate().zlibHeader(false),
                Compression.snappyRaw().uncompressedSize(TEXT.length),
                Compression.snappyFramed());
    }

    static Stream<Compression> detectable() {
        return Stream.of(
                Compression.gzip(),
                Compression.bzip2(),
                Compression.xz(),
                Compression.zstd(),
                Compression.lz4Framed(),
                Compression.snappyFramed());
    }

    private static byte[] compress(Compression compression) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (OutputStream out = Codecs.compressing(compression, bytes)) {
            out.write(TEXT);
        }
        return bytes.toByteArray();
    }

    @ParameterizedTest
    @MethodSource("writable")
    void roundTripsEveryWritableCodec(Compression compression) throws IOException {
        byte[] compressed = compress(compression);
        try (InputStream in = Codecs.decompressing(compression, new ByteArrayInputStream(compressed))) {
            assertThat(in.readAllBytes()).isEqualTo(TEXT);
        }
    }

    @Test
    void gzipHeaderOptionsReachTheHeader() throws IOException {
        var gzip = Compression.gzip()
                .fileName("a.txt")
                .comment("note")
                .modificationTime(Instant.ofEpochSecond(1_700_000_000L))
                .operatingSystem(3);
        byte[] compressed = compress(gzip);
        try (var in = (GzipCompressorInputStream) Codecs.decompressing(gzip, new ByteArrayInputStream(compressed))) {
            assertThat(in.readAllBytes()).isEqualTo(TEXT);
            assertThat(ByteBuffer.wrap(compressed, 4, 4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .getInt())
                    .isEqualTo(1_700_000_000);
            var meta = in.getMetaData();
            assertThat(meta.getFileName()).isEqualTo("a.txt");
            assertThat(meta.getComment()).isEqualTo("note");
            assertThat(meta.getOperatingSystem()).isEqualTo(3);
        }
    }

    @Test
    void gzipWithoutModificationTimeLeavesItZero() throws IOException {
        try (var in = (GzipCompressorInputStream)
                Codecs.decompressing(Compression.gzip(), new ByteArrayInputStream(compress(Compression.gzip())))) {
            assertThat(in.getMetaData().getModificationTime()).isZero();
        }
    }

    @Test
    void gzipReadsConcatenatedMembersWhenAsked() throws IOException {
        var gzip = Compression.gzip().decompressConcatenated(true);
        byte[] one = compress(gzip);
        var both = new ByteArrayOutputStream();
        both.write(one);
        both.write(one);
        try (InputStream in = Codecs.decompressing(gzip, new ByteArrayInputStream(both.toByteArray()))) {
            assertThat(in.readAllBytes()).hasSize(TEXT.length * 2);
        }
    }

    static Stream<Compression> concatenable() {
        return Stream.of(Compression.bzip2(), Compression.xz(), Compression.lz4Framed());
    }

    private static byte[] twoMembers(Compression compression) throws IOException {
        var both = new ByteArrayOutputStream();
        both.write(compress(compression));
        both.write(compress(compression));
        return both.toByteArray();
    }

    private static Compression withConcatenation(Compression compression) {
        return switch (compression) {
            case Bzip2 b -> b.decompressConcatenated(true);
            case Xz x -> x.decompressConcatenated(true);
            case Lz4Framed l -> l.decompressConcatenated(true);
            default -> throw new IllegalArgumentException(compression.toString());
        };
    }

    @ParameterizedTest
    @MethodSource("concatenable")
    void readsConcatenatedMembersWhenAsked(Compression compression) throws IOException {
        byte[] both = twoMembers(compression);
        try (InputStream in = Codecs.decompressing(withConcatenation(compression), new ByteArrayInputStream(both))) {
            assertThat(in.readAllBytes()).hasSize(TEXT.length * 2);
        }
    }

    @ParameterizedTest
    @MethodSource("concatenable")
    void readsOnlyTheFirstMemberByDefault(Compression compression) throws IOException {
        byte[] both = twoMembers(compression);
        try (InputStream in = Codecs.decompressing(compression, new ByteArrayInputStream(both))) {
            assertThat(in.readAllBytes()).hasSize(TEXT.length);
        }
    }

    @ParameterizedTest
    @MethodSource("detectable")
    void detectsStrongMagicNumbers(Compression compression) throws IOException {
        byte[] bytes = compress(compression);
        var in = new BufferedInputStream(new ByteArrayInputStream(bytes));
        assertThat(Codecs.detect(in)).isInstanceOf(compression.getClass());
        assertThat(in.read()).isEqualTo(bytes[0] & 0xFF);
    }

    @Test
    void detectsPack200() throws IOException {
        byte[] magic = {(byte) 0xCA, (byte) 0xFE, (byte) 0xD0, 0x0D, 0, 0, 0, 0};
        var in = new BufferedInputStream(new ByteArrayInputStream(magic));
        assertThat(Codecs.detect(in)).isInstanceOf(Pack200.class);
    }

    @Test
    void detectsUnixZ() throws IOException {
        var in =
                new BufferedInputStream(new ByteArrayInputStream(new byte[] {0x1f, (byte) 0x9d, (byte) 0x90, 0, 0, 0}));
        assertThat(Codecs.detect(in)).isInstanceOf(UnixZ.class);
    }

    @Test
    void detectionLeavesTheStreamAtItsStart() throws IOException {
        byte[] gz = compress(Compression.gzip());
        var in = new BufferedInputStream(new ByteArrayInputStream(gz));
        Codecs.detect(in);
        assertThat(in.readAllBytes()).isEqualTo(gz);
    }

    private static Compression detectBytes(byte... bytes) throws IOException {
        return Codecs.detect(new BufferedInputStream(new ByteArrayInputStream(bytes)));
    }

    @Test
    void oneByteOfInputDetectsNone() throws IOException {
        assertThat(detectBytes((byte) 'x')).isEqualTo(Compression.none());
    }

    @Test
    void truncatedGzipMagicDetectsNone() throws IOException {
        assertThat(detectBytes((byte) 0x1f)).isEqualTo(Compression.none());
    }

    @Test
    void plainTarHeaderDetectsNone() throws IOException {
        assertThat(detectBytes(tarHeaderNamed("hello.txt"))).isEqualTo(Compression.none());
    }

    @Test
    void tarWhoseFirstEntryNameStartsWithBZhIsNotBzip2() throws IOException {
        assertThat(detectBytes(tarHeaderNamed("BZh.txt"))).isInstanceOf(None.class);
        assertThat(detectBytes(tarHeaderNamed("BZh9.txt"))).isInstanceOf(None.class);
    }

    @Test
    void emptyBzip2StreamIsStillDetected() throws IOException {
        byte[] empty = compress(Compression.bzip2());
        assertThat(detectBytes(empty)).isInstanceOf(Bzip2.class);
    }

    private static byte[] tarHeaderNamed(String name) {
        byte[] header = new byte[512];
        byte[] bytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, 0, bytes.length);
        return header;
    }

    private static final class FailingStream extends InputStream {
        private final boolean failRead;
        private final boolean failReset;

        FailingStream(boolean failRead, boolean failReset) {
            this.failRead = failRead;
            this.failReset = failReset;
        }

        @Override
        public int read() throws IOException {
            if (failRead) {
                throw new IOException("read failed");
            }
            return 'x';
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public synchronized void mark(int readLimit) {
            // Detection only needs reset() to fail or succeed, so the mark position is not tracked.
        }

        @Override
        public synchronized void reset() throws IOException {
            if (failReset) {
                throw new IOException("reset failed");
            }
        }
    }

    @Test
    void failingReadDuringDetectionPropagates() {
        assertThatThrownBy(() -> Codecs.detect(new FailingStream(true, false)))
                .isInstanceOf(IOException.class)
                .hasMessage("read failed");
    }

    @Test
    void failingReadAndResetKeepsTheReadFailureAndSuppressesTheReset() {
        assertThatThrownBy(() -> Codecs.detect(new FailingStream(true, true)))
                .isInstanceOf(IOException.class)
                .hasMessage("read failed")
                .hasSuppressedException(new IOException("reset failed"));
    }

    @Test
    void failingResetAfterDetectionPropagates() {
        assertThatThrownBy(() -> Codecs.detect(new FailingStream(false, true)))
                .isInstanceOf(IOException.class)
                .hasMessage("reset failed");
    }

    @Test
    void gzipFileNameUsesTheConfiguredCharset() throws IOException {
        var gzip = Compression.gzip().fileNameCharset(StandardCharsets.UTF_8).fileName("é.txt");
        try (var in =
                (GzipCompressorInputStream) Codecs.decompressing(gzip, new ByteArrayInputStream(compress(gzip)))) {
            assertThat(in.getMetaData().getFileName()).isEqualTo("é.txt");
        }
    }

    @Test
    void emptyInputDetectsNone() throws IOException {
        assertThat(Codecs.detect(new BufferedInputStream(InputStream.nullInputStream())))
                .isEqualTo(Compression.none());
    }

    @Test
    void plainTarWithZlibLookingNameIsPlainTar() throws IOException {
        byte[] looksLikeZlib = {0x78, 0x5e, 'a', 'b', 'c', 0, 0, 0};
        assertThat(Codecs.detect(new BufferedInputStream(new ByteArrayInputStream(looksLikeZlib))))
                .isEqualTo(Compression.none());
    }

    @Test
    void lzmaIsNeverDetected() throws IOException {
        var in = new BufferedInputStream(new ByteArrayInputStream(compress(Compression.lzma())));
        assertThat(Codecs.detect(in)).isEqualTo(Compression.none());
    }

    @Test
    void detectionNeedsMark() {
        var withoutMark = new InputStream() {
            @Override
            public int read() {
                return -1;
            }
        };
        assertThatThrownBy(() -> Codecs.detect(withoutMark)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readOnlyCodecsCannotCompress() {
        for (Compression c : new Compression[] {Compression.unixZ(), Compression.brotli(), Compression.deflate64()}) {
            var sink = new ByteArrayOutputStream();
            assertThatThrownBy(() -> Codecs.compressing(c, sink))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("can only be read");
        }
    }

    @Test
    void rawSnappyNeedsUncompressedSize() {
        var raw = Compression.snappyRaw();
        var sink = new ByteArrayOutputStream();
        assertThatThrownBy(() -> Codecs.compressing(raw, sink))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uncompressedSize");
    }
}
