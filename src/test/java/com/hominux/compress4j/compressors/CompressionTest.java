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
package com.hominux.compress4j.compressors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class CompressionTest {

    private static final Instant GZIP_MAX = Instant.parse("2106-02-07T06:28:15Z");
    private static final Instant ONE_SECOND = Instant.parse("1970-01-01T00:00:01Z");

    @Test
    void defaultsMatchTheCodecDefaults() {
        var gzip = Compression.gzip();
        assertThat(gzip.level()).isEqualTo(-1);
        assertThat(gzip.bufferSize()).isEqualTo(512);
        assertThat(gzip.fileName()).isEmpty();
        assertThat(gzip.comment()).isEmpty();
        assertThat(gzip.deflateStrategy()).isEqualTo(DeflateStrategy.DEFAULT);
        assertThat(gzip.modificationTime()).isEmpty();
        assertThat(gzip.operatingSystem()).isEqualTo(255);
        assertThat(gzip.decompressConcatenated()).isFalse();
        assertThat(gzip.fileNameCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(Compression.bzip2().blockSize()).isEqualTo(9);
        assertThat(Compression.bzip2().decompressConcatenated()).isFalse();
        assertThat(Compression.xz().preset()).isEqualTo(6);
        assertThat(Compression.xz().memoryLimitKiB()).isEmpty();
        assertThat(Compression.xz().decompressConcatenated()).isFalse();
        assertThat(Compression.lzma().memoryLimitKiB()).isEmpty();
        assertThat(Compression.lz4Framed().decompressConcatenated()).isFalse();
        assertThat(Compression.zstd().level()).isEqualTo(3);
        assertThat(Compression.deflate().level()).isEqualTo(-1);
        assertThat(Compression.deflate().zlibHeader()).isTrue();
        assertThat(Compression.snappyRaw().uncompressedSize()).isEqualTo(OptionalLong.empty());
        assertThat(Compression.pack200().strategy()).isEqualTo(Pack200.Strategy.IN_MEMORY);
        assertThat(Compression.pack200().properties()).isEmpty();
    }

    @Test
    void gzipWithersKeepEveryOtherComponent() {
        var base = Compression.gzip()
                .level(5)
                .bufferSize(1024)
                .fileName("a.txt")
                .comment("c")
                .deflateStrategy(DeflateStrategy.FILTERED)
                .modificationTime(Instant.ofEpochSecond(7))
                .operatingSystem(3)
                .decompressConcatenated(true)
                .fileNameCharset(StandardCharsets.UTF_8);
        assertGzip(base.level(6), 6, 1024, "a.txt", "c", DeflateStrategy.FILTERED, 7, 3, true);
        assertGzip(base.bufferSize(2048), 5, 2048, "a.txt", "c", DeflateStrategy.FILTERED, 7, 3, true);
        assertGzip(base.fileName("b"), 5, 1024, "b", "c", DeflateStrategy.FILTERED, 7, 3, true);
        assertGzip(base.comment("d"), 5, 1024, "a.txt", "d", DeflateStrategy.FILTERED, 7, 3, true);
        assertGzip(
                base.deflateStrategy(DeflateStrategy.HUFFMAN_ONLY),
                5,
                1024,
                "a.txt",
                "c",
                DeflateStrategy.HUFFMAN_ONLY,
                7,
                3,
                true);
        assertGzip(
                base.modificationTime(Instant.ofEpochSecond(9)),
                5,
                1024,
                "a.txt",
                "c",
                DeflateStrategy.FILTERED,
                9,
                3,
                true);
        assertGzip(base.operatingSystem(4), 5, 1024, "a.txt", "c", DeflateStrategy.FILTERED, 7, 4, true);
        assertGzip(base.decompressConcatenated(false), 5, 1024, "a.txt", "c", DeflateStrategy.FILTERED, 7, 3, false);
        assertThat(base.fileNameCharset(StandardCharsets.US_ASCII).fileNameCharset())
                .isEqualTo(StandardCharsets.US_ASCII);
        assertThat(base.fileNameCharset(StandardCharsets.US_ASCII).fileName()).contains("a.txt");
        assertThat(Compression.gzip().level()).isEqualTo(-1);
    }

    private static void assertGzip(
            Gzip gzip,
            int level,
            int bufferSize,
            String fileName,
            String comment,
            DeflateStrategy strategy,
            long seconds,
            int os,
            boolean concatenated) {
        assertThat(gzip.level()).isEqualTo(level);
        assertThat(gzip.bufferSize()).isEqualTo(bufferSize);
        assertThat(gzip.fileName()).contains(fileName);
        assertThat(gzip.comment()).contains(comment);
        assertThat(gzip.deflateStrategy()).isEqualTo(strategy);
        assertThat(gzip.modificationTime()).contains(Instant.ofEpochSecond(seconds));
        assertThat(gzip.operatingSystem()).isEqualTo(os);
        assertThat(gzip.decompressConcatenated()).isEqualTo(concatenated);
        assertThat(gzip.fileNameCharset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void clearableOptionalsAcceptEmpty() {
        var gzip = Compression.gzip().fileName("a").comment("b").modificationTime(ONE_SECOND);
        assertThat(gzip.fileName(Optional.empty()).fileName()).isEmpty();
        assertThat(gzip.comment(Optional.empty()).comment()).isEmpty();
        assertThat(gzip.modificationTime(Optional.empty()).modificationTime()).isEmpty();
        assertThat(gzip.fileName(Optional.empty()).comment()).contains("b");
        assertThat(Compression.snappyRaw()
                        .uncompressedSize(5)
                        .uncompressedSize(OptionalLong.empty())
                        .uncompressedSize())
                .isEmpty();
        assertThat(Compression.snappyRaw().uncompressedSize(5).uncompressedSize())
                .hasValue(5);
        assertThat(Compression.xz()
                        .memoryLimitKiB(4)
                        .memoryLimitKiB(OptionalInt.empty())
                        .memoryLimitKiB())
                .isEmpty();
        assertThat(Compression.lzma()
                        .memoryLimitKiB(4)
                        .memoryLimitKiB(OptionalInt.empty())
                        .memoryLimitKiB())
                .isEmpty();
    }

    @Test
    void otherWithersKeepEveryOtherComponent() {
        var bzip2 = Compression.bzip2().blockSize(3).decompressConcatenated(true);
        assertThat(bzip2.blockSize(4).decompressConcatenated()).isTrue();
        assertThat(bzip2.decompressConcatenated(false).blockSize()).isEqualTo(3);
        var xz = Compression.xz().preset(2).memoryLimitKiB(8).decompressConcatenated(true);
        assertThat(xz.preset(3).memoryLimitKiB()).hasValue(8);
        assertThat(xz.preset(3).decompressConcatenated()).isTrue();
        assertThat(xz.memoryLimitKiB(9).preset()).isEqualTo(2);
        assertThat(xz.memoryLimitKiB(9).decompressConcatenated()).isTrue();
        assertThat(xz.decompressConcatenated(false).preset()).isEqualTo(2);
        assertThat(xz.decompressConcatenated(false).memoryLimitKiB()).hasValue(8);
        assertThat(Compression.lzma().memoryLimitKiB(4).memoryLimitKiB()).hasValue(4);
        assertThat(Compression.lz4Framed().decompressConcatenated(true).decompressConcatenated())
                .isTrue();
        assertThat(Compression.zstd().level(19).level()).isEqualTo(19);
        var deflate = Compression.deflate().level(1).zlibHeader(false);
        assertThat(deflate.level(2).zlibHeader()).isFalse();
        assertThat(deflate.zlibHeader(true).level()).isEqualTo(1);
        var pack = Compression.pack200().strategy(Pack200.Strategy.TEMP_FILE).properties(Map.of("k", "v"));
        assertThat(pack.strategy(Pack200.Strategy.IN_MEMORY).properties()).containsOnlyKeys("k");
        assertThat(pack.properties(Map.of()).strategy()).isEqualTo(Pack200.Strategy.TEMP_FILE);
    }

    @Test
    void readOnlyCodecsCannotWrite() {
        assertThat(Compression.deflate64().canWrite()).isFalse();
        assertThat(Compression.brotli().canWrite()).isFalse();
        assertThat(Compression.unixZ().canWrite()).isFalse();
        assertThat(Compression.gzip().canWrite()).isTrue();
        assertThat(Compression.none().canWrite()).isTrue();
        assertThat(Compression.lz4Block().canWrite()).isTrue();
        assertThat(Compression.snappyFramed().canWrite()).isTrue();
        assertThat(Compression.lzma().canWrite()).isTrue();
        assertThat(Compression.pack200().canWrite()).isTrue();
    }

    @Test
    void boundariesAreAccepted() {
        assertThat(Compression.gzip().level(-1).level(9).level()).isEqualTo(9);
        assertThat(Compression.gzip()
                        .operatingSystem(0)
                        .operatingSystem(13)
                        .operatingSystem(255)
                        .operatingSystem())
                .isEqualTo(255);
        assertThat(Compression.gzip().modificationTime(ONE_SECOND).modificationTime())
                .contains(ONE_SECOND);
        assertThat(Compression.gzip().modificationTime(GZIP_MAX).modificationTime())
                .contains(GZIP_MAX);
        assertThat(Compression.zstd().level(22).level()).isEqualTo(22);
        assertThat(Compression.zstd().level(-131072).level()).isEqualTo(-131072);
        assertThat(Compression.bzip2().blockSize(1).blockSize(9).blockSize()).isEqualTo(9);
        assertThat(Compression.xz().preset(0).preset(9).preset()).isEqualTo(9);
        assertThat(Compression.xz().memoryLimitKiB(1).memoryLimitKiB()).hasValue(1);
        assertThat(Compression.lzma().memoryLimitKiB(1).memoryLimitKiB()).hasValue(1);
        assertThat(Compression.deflate().level(-1).level(9).level()).isEqualTo(9);
        assertThat(Compression.snappyRaw().uncompressedSize(0).uncompressedSize())
                .hasValue(0);
    }

    @Test
    void invalidOptionsAreRejected() {
        assertRejected(() -> Compression.gzip().level(10));
        assertRejected(() -> Compression.gzip().level(-2));
        assertRejected(() -> Compression.gzip().bufferSize(0));
        assertRejected(() -> Compression.gzip().operatingSystem(256));
        assertRejected(() -> Compression.gzip().operatingSystem(-1));
        assertRejected(() -> Compression.gzip().operatingSystem(14));
        assertRejected(() -> Compression.gzip().operatingSystem(254));
        assertRejected(() -> Compression.gzip().modificationTime(Instant.EPOCH));
        assertRejected(() -> Compression.gzip().modificationTime(Instant.ofEpochSecond(0, 999_999_999)));
        assertRejected(() -> Compression.gzip().modificationTime(Instant.EPOCH.minusSeconds(1)));
        assertRejected(() -> Compression.gzip().modificationTime(GZIP_MAX.plusNanos(1)));
        assertRejected(() -> Compression.gzip().modificationTime(GZIP_MAX.plusSeconds(1)));
        assertRejected(() -> Compression.bzip2().blockSize(0));
        assertRejected(() -> Compression.bzip2().blockSize(10));
        assertRejected(() -> Compression.xz().preset(10));
        assertRejected(() -> Compression.xz().preset(-1));
        assertRejected(() -> Compression.xz().memoryLimitKiB(0));
        assertRejected(() -> Compression.lzma().memoryLimitKiB(0));
        assertRejected(() -> Compression.deflate().level(-2));
        assertRejected(() -> Compression.deflate().level(10));
        assertRejected(() -> Compression.snappyRaw().uncompressedSize(-1));
        assertRejected(() -> Compression.zstd().level(23));
        assertRejected(() -> Compression.zstd().level(-131073));
    }

    private static void assertRejected(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullOptionsAreRejected() {
        assertNpe(() -> Compression.gzip().fileNameCharset(null));
        assertNpe(() -> Compression.gzip().deflateStrategy(null));
        assertNpe(() -> Compression.gzip().fileName((String) null));
        assertNpe(() -> Compression.gzip().fileName((Optional<String>) null));
        assertNpe(() -> Compression.gzip().comment((Optional<String>) null));
        assertNpe(() -> Compression.gzip().modificationTime((Optional<Instant>) null));
        assertNpe(() -> Compression.xz().memoryLimitKiB((OptionalInt) null));
        assertNpe(() -> Compression.lzma().memoryLimitKiB((OptionalInt) null));
        assertNpe(() -> Compression.snappyRaw().uncompressedSize((OptionalLong) null));
        assertNpe(() -> Compression.pack200().strategy(null));
        assertNpe(() -> Compression.pack200().properties(null));
    }

    private static void assertNpe(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(NullPointerException.class);
    }

    @Test
    void pack200PropertiesAreCopied() {
        Map<String, String> props = new HashMap<>();
        props.put("k", "v");
        var pack = Compression.pack200().properties(props);
        props.put("k2", "v2");
        assertThat(pack.properties()).containsOnlyKeys("k");
    }

    @Test
    void equalsAndHashCodeFollowTheOptions() {
        assertSame(Compression.none(), Compression.none());
        assertSame(Compression.gzip().level(4), Compression.gzip().level(4));
        assertDifferent(Compression.gzip().level(4), Compression.gzip().level(5));
        assertDifferent(Compression.gzip().fileName("a"), Compression.gzip());
        assertGzipComponentsMatterToEquality();
        assertSame(Compression.bzip2().blockSize(2), Compression.bzip2().blockSize(2));
        assertDifferent(Compression.bzip2().blockSize(2), Compression.bzip2().blockSize(3));
        assertSame(Compression.xz().memoryLimitKiB(3), Compression.xz().memoryLimitKiB(3));
        assertDifferent(Compression.xz().preset(1), Compression.xz().preset(2));
        assertSame(Compression.lzma().memoryLimitKiB(3), Compression.lzma().memoryLimitKiB(3));
        assertDifferent(Compression.lzma(), Compression.lzma().memoryLimitKiB(3));
        assertSame(Compression.lz4Block(), Compression.lz4Block());
        assertDifferent(Compression.lz4Framed(), Compression.lz4Framed().decompressConcatenated(true));
        assertDifferent(Compression.zstd(), Compression.zstd().level(4));
        assertDifferent(Compression.deflate(), Compression.deflate().zlibHeader(false));
        assertSame(Compression.deflate64(), Compression.deflate64());
        assertDifferent(Compression.snappyRaw(), Compression.snappyRaw().uncompressedSize(1));
        assertSame(Compression.snappyFramed(), Compression.snappyFramed());
        assertSame(Compression.brotli(), Compression.brotli());
        assertSame(Compression.unixZ(), Compression.unixZ());
        assertDifferent(Compression.pack200(), Compression.pack200().properties(Map.of("k", "v")));
        assertDifferent(Compression.none(), Compression.snappyFramed());
        assertThat(Compression.gzip()).isNotEqualTo("gzip");
    }

    private static void assertGzipComponentsMatterToEquality() {
        var base = Compression.gzip();
        assertDifferent(base, base.bufferSize(1024));
        assertDifferent(base, base.comment("c"));
        assertDifferent(base, base.deflateStrategy(DeflateStrategy.FILTERED));
        assertDifferent(base, base.modificationTime(ONE_SECOND));
        assertDifferent(base, base.operatingSystem(3));
        assertDifferent(base, base.decompressConcatenated(true));
        assertDifferent(base, base.fileNameCharset(StandardCharsets.UTF_8));
        assertDifferent(Compression.bzip2(), Compression.bzip2().decompressConcatenated(true));
        assertDifferent(Compression.xz(), Compression.xz().memoryLimitKiB(3));
        assertDifferent(Compression.xz(), Compression.xz().decompressConcatenated(true));
        assertDifferent(Compression.deflate(), Compression.deflate().level(2));
        assertDifferent(Compression.pack200(), Compression.pack200().strategy(Pack200.Strategy.TEMP_FILE));
    }

    private static void assertSame(Compression left, Compression right) {
        assertThat(left).isEqualTo(right).hasSameHashCodeAs(right);
    }

    private static void assertDifferent(Compression left, Compression right) {
        assertThat(left).isNotEqualTo(right);
    }

    @Test
    void toStringNamesTheClassAndOptions() {
        assertThat(Compression.gzip().level(9).fileName("a.txt").toString())
                .startsWith("Gzip[level=9, ")
                .contains("fileName=Optional[a.txt]")
                .contains("deflateStrategy=DEFAULT");
        assertThat(Compression.bzip2()).hasToString("Bzip2[blockSize=9, decompressConcatenated=false]");
        assertThat(Compression.xz().toString()).contains("Xz[preset=6", "memoryLimitKiB=OptionalInt.empty");
        assertThat(Compression.lzma().toString()).startsWith("Lzma[");
        assertThat(Compression.lz4Framed().toString()).startsWith("Lz4Framed[");
        assertThat(Compression.zstd()).hasToString("Zstd[level=3]");
        assertThat(Compression.deflate()).hasToString("Deflate[level=-1, zlibHeader=true]");
        assertThat(Compression.snappyRaw().toString()).startsWith("SnappyRaw[");
        assertThat(Compression.pack200().toString()).startsWith("Pack200[strategy=IN_MEMORY");
        assertThat(Compression.none()).hasToString("None[]");
        assertThat(Compression.lz4Block()).hasToString("Lz4Block[]");
        assertThat(Compression.deflate64()).hasToString("Deflate64[]");
        assertThat(Compression.snappyFramed()).hasToString("SnappyFramed[]");
        assertThat(Compression.brotli()).hasToString("Brotli[]");
        assertThat(Compression.unixZ()).hasToString("UnixZ[]");
    }
}
