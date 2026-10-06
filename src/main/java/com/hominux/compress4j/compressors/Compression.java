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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * A compression codec and its options, used by Compressor, Decompressor and the tar archivers. Each option type is an
 * immutable final class created by a factory on this interface; its withers return a changed copy. Read-only codecs
 * report {@link #canWrite()} {@code false}.
 *
 * <p>New codecs may be added in minor releases; a switch over Compression needs a default branch.
 *
 * @since 5.0
 */
public sealed interface Compression
        permits Compression.None,
                Compression.Gzip,
                Compression.Bzip2,
                Compression.Xz,
                Compression.Lzma,
                Compression.Lz4Block,
                Compression.Lz4Framed,
                Compression.Zstd,
                Compression.Deflate,
                Compression.Deflate64,
                Compression.SnappyRaw,
                Compression.SnappyFramed,
                Compression.Brotli,
                Compression.UnixZ,
                Compression.Pack200 {

    /**
     * Whether Compress4J can write this codec.
     *
     * @return {@code true} unless the codec is read-only
     */
    default boolean canWrite() {
        return true;
    }

    /**
     * Returns no compression.
     *
     * @return the identity codec
     */
    static None none() {
        return new None();
    }

    /**
     * Returns gzip with the JDK's default level, no header metadata and unknown operating system.
     *
     * @return the default gzip options
     */
    static Gzip gzip() {
        return new Gzip(
                -1,
                512,
                DeflateStrategy.DEFAULT,
                false,
                new Gzip.Header(
                        Optional.empty(), Optional.empty(), Optional.empty(), 255, StandardCharsets.ISO_8859_1));
    }

    /**
     * Returns bzip2 with 900 KB blocks.
     *
     * @return the default bzip2 options
     */
    static Bzip2 bzip2() {
        return new Bzip2(9, false);
    }

    /**
     * Returns xz with preset 6 and the default 256 MiB reading memory limit.
     *
     * @return the default xz options
     */
    static Xz xz() {
        return new Xz(6, OptionalInt.empty(), false);
    }

    /**
     * Returns LZMA with the default 256 MiB reading memory limit; LZMA streams have no magic number, so readers never
     * detect it.
     *
     * @return the default LZMA options
     */
    static Lzma lzma() {
        return new Lzma(OptionalInt.empty());
    }

    /**
     * Returns the LZ4 block format.
     *
     * @return LZ4 block
     */
    static Lz4Block lz4Block() {
        return new Lz4Block();
    }

    /**
     * Returns the LZ4 frame format.
     *
     * @return the default LZ4 frame options
     */
    static Lz4Framed lz4Framed() {
        return new Lz4Framed(false);
    }

    /**
     * Returns Zstandard at level 3.
     *
     * @return the default Zstandard options
     */
    static Zstd zstd() {
        return new Zstd(3);
    }

    /**
     * Returns deflate with a zlib header at the default level.
     *
     * @return the default deflate options
     */
    static Deflate deflate() {
        return new Deflate(-1, true);
    }

    /**
     * Returns Deflate64, which is read-only.
     *
     * @return Deflate64
     */
    static Deflate64 deflate64() {
        return new Deflate64();
    }

    /**
     * Returns raw Snappy with an unknown size; writing needs {@link SnappyRaw#uncompressedSize(long)}.
     *
     * @return the default raw Snappy options
     */
    static SnappyRaw snappyRaw() {
        return new SnappyRaw(OptionalLong.empty());
    }

    /**
     * Returns framed Snappy.
     *
     * @return framed Snappy
     */
    static SnappyFramed snappyFramed() {
        return new SnappyFramed();
    }

    /**
     * Returns Brotli, which is read-only.
     *
     * @return Brotli
     */
    static Brotli brotli() {
        return new Brotli();
    }

    /**
     * Returns Unix compress ({@code .Z}), which is read-only.
     *
     * @return Unix compress
     */
    static UnixZ unixZ() {
        return new UnixZ();
    }

    /**
     * Returns Pack200 buffering in memory. Decompressor never detects Pack200, and its decode is not bounded by
     * extraction limits; select it explicitly only for trusted input.
     *
     * @return the default Pack200 options
     */
    static Pack200 pack200() {
        return new Pack200(Pack200.Strategy.IN_MEMORY, Map.of());
    }

    /** No compression. */
    final class None implements Compression {
        private None() {}

        @Override
        public boolean equals(Object other) {
            return other instanceof None;
        }

        @Override
        public int hashCode() {
            return "None".hashCode();
        }

        @Override
        public String toString() {
            return "None[]";
        }
    }

    /** Gzip and its header and compression options. Obtain the default with {@link Compression#gzip()}. */
    final class Gzip implements Compression {
        private final int level;
        private final int bufferSize;
        private final DeflateStrategy deflateStrategy;
        private final boolean decompressConcatenated;
        private final Header header;

        private record Header(
                Optional<String> fileName,
                Optional<String> comment,
                Optional<Instant> modificationTime,
                int operatingSystem,
                Charset fileNameCharset) {
            Header {
                Objects.requireNonNull(fileName, "fileName");
                Objects.requireNonNull(comment, "comment");
                Objects.requireNonNull(modificationTime, "modificationTime");
                Checks.gzipOperatingSystem("gzip operatingSystem", operatingSystem);
                Checks.gzipTime("gzip modificationTime", modificationTime);
                Objects.requireNonNull(fileNameCharset, "fileNameCharset");
            }

            Header fileName(Optional<String> value) {
                return new Header(value, comment, modificationTime, operatingSystem, fileNameCharset);
            }

            Header comment(Optional<String> value) {
                return new Header(fileName, value, modificationTime, operatingSystem, fileNameCharset);
            }

            Header modificationTime(Optional<Instant> value) {
                return new Header(fileName, comment, value, operatingSystem, fileNameCharset);
            }

            Header operatingSystem(int value) {
                return new Header(fileName, comment, modificationTime, value, fileNameCharset);
            }

            Header fileNameCharset(Charset value) {
                return new Header(fileName, comment, modificationTime, operatingSystem, value);
            }
        }

        private Gzip(
                int level,
                int bufferSize,
                DeflateStrategy deflateStrategy,
                boolean decompressConcatenated,
                Header header) {
            Checks.range("gzip level", level, -1, 9);
            Checks.positive("gzip bufferSize", bufferSize);
            Objects.requireNonNull(deflateStrategy, "deflateStrategy");
            this.level = level;
            this.bufferSize = bufferSize;
            this.deflateStrategy = deflateStrategy;
            this.decompressConcatenated = decompressConcatenated;
            this.header = header;
        }

        /**
         * Returns the compression level, from -1 (default) to 9.
         *
         * @return the compression level, from -1 (default) to 9
         */
        public int level() {
            return level;
        }

        /**
         * Returns a copy with the compression level, from -1 (default) to 9.
         *
         * @param value the compression level, from -1 (default) to 9
         * @return the changed copy
         * @throws IllegalArgumentException if the value is below -1 or above 9
         */
        public Gzip level(int value) {
            return new Gzip(value, bufferSize, deflateStrategy, decompressConcatenated, header);
        }

        /**
         * Returns the deflater buffer size in bytes.
         *
         * @return the deflater buffer size in bytes
         */
        public int bufferSize() {
            return bufferSize;
        }

        /**
         * Returns a copy with the deflater buffer size in bytes, above 0.
         *
         * @param value the deflater buffer size in bytes, above 0
         * @return the changed copy
         * @throws IllegalArgumentException if the value is not above 0
         */
        public Gzip bufferSize(int value) {
            return new Gzip(level, value, deflateStrategy, decompressConcatenated, header);
        }

        /**
         * Returns the original file name stored in the header, if any.
         *
         * @return the original file name stored in the header, if any
         */
        public Optional<String> fileName() {
            return header.fileName();
        }

        /**
         * Returns a copy with the original file name stored in the header, empty for none.
         *
         * @param value the original file name stored in the header, empty for none
         * @return the changed copy
         * @throws NullPointerException if the value is null
         */
        public Gzip fileName(Optional<String> value) {
            return new Gzip(level, bufferSize, deflateStrategy, decompressConcatenated, header.fileName(value));
        }

        /**
         * Returns a copy with the original file name stored in the header.
         *
         * @param value the original file name stored in the header
         * @return the changed copy
         * @throws NullPointerException if the value is null
         */
        public Gzip fileName(String value) {
            return fileName(Optional.of(value));
        }

        /**
         * Returns the comment stored in the header, if any.
         *
         * @return the comment stored in the header, if any
         */
        public Optional<String> comment() {
            return header.comment();
        }

        /**
         * Returns a copy with the comment stored in the header, empty for none.
         *
         * @param value the comment stored in the header, empty for none
         * @return the changed copy
         * @throws NullPointerException if the value is null
         */
        public Gzip comment(Optional<String> value) {
            return new Gzip(level, bufferSize, deflateStrategy, decompressConcatenated, header.comment(value));
        }

        /**
         * Returns a copy with the comment stored in the header.
         *
         * @param value the comment stored in the header
         * @return the changed copy
         * @throws NullPointerException if the value is null
         */
        public Gzip comment(String value) {
            return comment(Optional.of(value));
        }

        /**
         * Returns the deflate strategy.
         *
         * @return the deflate strategy
         */
        public DeflateStrategy deflateStrategy() {
            return deflateStrategy;
        }

        /**
         * Returns a copy with the deflate strategy.
         *
         * @param value the deflate strategy
         * @return the changed copy
         * @throws NullPointerException if the value is null
         */
        public Gzip deflateStrategy(DeflateStrategy value) {
            return new Gzip(level, bufferSize, value, decompressConcatenated, header);
        }

        /**
         * Returns the modification time stored in the header, if any.
         *
         * @return the modification time stored in the header, if any
         */
        public Optional<Instant> modificationTime() {
            return header.modificationTime();
        }

        /**
         * Returns a copy with the modification time stored in the header. The header holds whole seconds, so fractional
         * seconds are truncated toward the epoch second. Use an empty {@code Optional} to omit the time: the header
         * stores 0 as "no timestamp", so {@link Instant#EPOCH} is rejected rather than written like an empty time.
         *
         * @param value the modification time stored in the header, empty to omit it
         * @return the changed copy
         * @throws IllegalArgumentException if a present value lies outside 1970-01-01T00:00:01Z to
         *     2106-02-07T06:28:15Z, the range of a 32-bit unsigned count of seconds without 0
         * @throws NullPointerException if the value is null
         */
        public Gzip modificationTime(Optional<Instant> value) {
            return new Gzip(level, bufferSize, deflateStrategy, decompressConcatenated, header.modificationTime(value));
        }

        /**
         * Returns a copy with the modification time stored in the header, truncated to whole seconds.
         *
         * @param value the modification time stored in the header
         * @return the changed copy
         * @throws IllegalArgumentException if the value lies outside 1970-01-01T00:00:01Z to 2106-02-07T06:28:15Z
         * @throws NullPointerException if the value is null
         */
        public Gzip modificationTime(Instant value) {
            return modificationTime(Optional.of(value));
        }

        /**
         * Returns the RFC 1952 operating-system code stored in the header, 255 meaning unknown.
         *
         * @return the RFC 1952 operating-system code stored in the header, 255 meaning unknown
         */
        public int operatingSystem() {
            return header.operatingSystem();
        }

        /**
         * Returns a copy with the RFC 1952 operating-system code stored in the header: 0 to 13 for the assigned
         * systems, or 255 for unknown.
         *
         * @param value the RFC 1952 operating-system code stored in the header, 0 to 13 or 255
         * @return the changed copy
         * @throws IllegalArgumentException if the value is not 0 to 13 or 255
         */
        public Gzip operatingSystem(int value) {
            return new Gzip(level, bufferSize, deflateStrategy, decompressConcatenated, header.operatingSystem(value));
        }

        /**
         * Returns whether reading continues across concatenated gzip members.
         *
         * @return whether reading continues across concatenated gzip members
         */
        public boolean decompressConcatenated() {
            return decompressConcatenated;
        }

        /**
         * Returns a copy with whether reading continues across concatenated gzip members.
         *
         * @param value whether reading continues across concatenated gzip members
         * @return the changed copy
         */
        public Gzip decompressConcatenated(boolean value) {
            return new Gzip(level, bufferSize, deflateStrategy, value, header);
        }

        /**
         * Returns the charset of the stored file name and comment.
         *
         * @return the charset of the stored file name and comment
         */
        public Charset fileNameCharset() {
            return header.fileNameCharset();
        }

        /**
         * Returns a copy with the charset of the stored file name and comment.
         *
         * @param value the charset of the stored file name and comment
         * @return the changed copy
         * @throws NullPointerException if the value is null
         */
        public Gzip fileNameCharset(Charset value) {
            return new Gzip(level, bufferSize, deflateStrategy, decompressConcatenated, header.fileNameCharset(value));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Gzip that
                    && level == that.level
                    && bufferSize == that.bufferSize
                    && deflateStrategy == that.deflateStrategy
                    && decompressConcatenated == that.decompressConcatenated
                    && header.equals(that.header);
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, bufferSize, deflateStrategy, decompressConcatenated, header);
        }

        @Override
        public String toString() {
            return "Gzip[level=" + level + ", bufferSize=" + bufferSize + ", fileName=" + header.fileName
                    + ", comment=" + header.comment + ", deflateStrategy=" + deflateStrategy
                    + ", modificationTime=" + header.modificationTime + ", operatingSystem=" + header.operatingSystem
                    + Checks.DECOMPRESS_CONCATENATED + decompressConcatenated + ", fileNameCharset="
                    + header.fileNameCharset + "]";
        }
    }

    /** Bzip2. Obtain the default with {@link Compression#bzip2()}. */
    final class Bzip2 implements Compression {
        private final int blockSize;
        private final boolean decompressConcatenated;

        private Bzip2(int blockSize, boolean decompressConcatenated) {
            Checks.range("bzip2 blockSize", blockSize, 1, 9);
            this.blockSize = blockSize;
            this.decompressConcatenated = decompressConcatenated;
        }

        /**
         * Returns the block size in units of 100 KB, from 1 to 9.
         *
         * @return the block size in units of 100 KB, from 1 to 9
         */
        public int blockSize() {
            return blockSize;
        }

        /**
         * Returns a copy with the block size in units of 100 KB, from 1 to 9.
         *
         * @param value the block size in units of 100 KB, from 1 to 9
         * @return the changed copy
         * @throws IllegalArgumentException if the value is below 1 or above 9
         */
        public Bzip2 blockSize(int value) {
            return new Bzip2(value, decompressConcatenated);
        }

        /**
         * Returns whether reading continues across concatenated bzip2 streams.
         *
         * @return whether reading continues across concatenated bzip2 streams
         */
        public boolean decompressConcatenated() {
            return decompressConcatenated;
        }

        /**
         * Returns a copy with whether reading continues across concatenated bzip2 streams.
         *
         * @param value whether reading continues across concatenated bzip2 streams
         * @return the changed copy
         */
        public Bzip2 decompressConcatenated(boolean value) {
            return new Bzip2(blockSize, value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Bzip2 that
                    && blockSize == that.blockSize
                    && decompressConcatenated == that.decompressConcatenated;
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockSize, decompressConcatenated);
        }

        @Override
        public String toString() {
            return "Bzip2[blockSize=" + blockSize + Checks.DECOMPRESS_CONCATENATED + decompressConcatenated + "]";
        }
    }

    /** XZ. Obtain the default with {@link Compression#xz()}. */
    final class Xz implements Compression {
        private final int preset;
        private final OptionalInt memoryLimitKiB;
        private final boolean decompressConcatenated;

        private Xz(int preset, OptionalInt memoryLimitKiB, boolean decompressConcatenated) {
            Checks.range("xz preset", preset, 0, 9);
            Objects.requireNonNull(memoryLimitKiB, "memoryLimitKiB");
            Checks.limit("xz memoryLimitKiB", memoryLimitKiB);
            this.preset = preset;
            this.memoryLimitKiB = memoryLimitKiB;
            this.decompressConcatenated = decompressConcatenated;
        }

        /**
         * Returns the compression preset, from 0 to 9.
         *
         * @return the compression preset, from 0 to 9
         */
        public int preset() {
            return preset;
        }

        /**
         * Returns a copy with the compression preset, from 0 (fastest) to 9 (smallest).
         *
         * @param value the compression preset, from 0 (fastest) to 9 (smallest)
         * @return the changed copy
         * @throws IllegalArgumentException if the value is below 0 or above 9
         */
        public Xz preset(int value) {
            return new Xz(value, memoryLimitKiB, decompressConcatenated);
        }

        /**
         * Returns the memory limit for reading in KiB; empty means 256 MiB; {@link Integer#MAX_VALUE} KiB (about 2 TiB)
         * lifts it.
         *
         * @return the memory limit for reading in KiB, or empty for the default
         */
        public OptionalInt memoryLimitKiB() {
            return memoryLimitKiB;
        }

        /**
         * Returns a copy with the memory limit for reading in KiB; empty means 256 MiB; {@link Integer#MAX_VALUE} KiB
         * (about 2 TiB) lifts it.
         *
         * @param value the memory limit for reading in KiB, or empty for the default
         * @return the changed copy
         * @throws IllegalArgumentException if a present value is not above 0
         * @throws NullPointerException if the value is null
         */
        public Xz memoryLimitKiB(OptionalInt value) {
            return new Xz(preset, value, decompressConcatenated);
        }

        /**
         * Returns a copy with the memory limit for reading in KiB.
         *
         * @param value the memory limit for reading in KiB
         * @return the changed copy
         * @throws IllegalArgumentException if the value is not above 0
         */
        public Xz memoryLimitKiB(int value) {
            return memoryLimitKiB(OptionalInt.of(value));
        }

        /**
         * Returns whether reading continues across concatenated XZ streams.
         *
         * @return whether reading continues across concatenated XZ streams
         */
        public boolean decompressConcatenated() {
            return decompressConcatenated;
        }

        /**
         * Returns a copy with whether reading continues across concatenated XZ streams.
         *
         * @param value whether reading continues across concatenated XZ streams
         * @return the changed copy
         */
        public Xz decompressConcatenated(boolean value) {
            return new Xz(preset, memoryLimitKiB, value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Xz that
                    && preset == that.preset
                    && Objects.equals(memoryLimitKiB, that.memoryLimitKiB)
                    && decompressConcatenated == that.decompressConcatenated;
        }

        @Override
        public int hashCode() {
            return Objects.hash(preset, memoryLimitKiB, decompressConcatenated);
        }

        @Override
        public String toString() {
            return "Xz[preset=" + preset + ", memoryLimitKiB=" + memoryLimitKiB + Checks.DECOMPRESS_CONCATENATED
                    + decompressConcatenated + "]";
        }
    }

    /**
     * LZMA, whose streams have no magic number: readers never detect it, so name it explicitly. Obtain the default with
     * {@link Compression#lzma()}.
     */
    final class Lzma implements Compression {
        private final OptionalInt memoryLimitKiB;

        private Lzma(OptionalInt memoryLimitKiB) {
            Objects.requireNonNull(memoryLimitKiB, "memoryLimitKiB");
            Checks.limit("lzma memoryLimitKiB", memoryLimitKiB);
            this.memoryLimitKiB = memoryLimitKiB;
        }

        /**
         * Returns the memory limit for reading in KiB; empty means 256 MiB; {@link Integer#MAX_VALUE} KiB (about 2 TiB)
         * lifts it.
         *
         * @return the memory limit for reading in KiB, or empty for the default
         */
        public OptionalInt memoryLimitKiB() {
            return memoryLimitKiB;
        }

        /**
         * Returns a copy with the memory limit for reading in KiB; empty means 256 MiB; {@link Integer#MAX_VALUE} KiB
         * (about 2 TiB) lifts it.
         *
         * @param value the memory limit for reading in KiB, or empty for the default
         * @return the changed copy
         * @throws IllegalArgumentException if a present value is not above 0
         * @throws NullPointerException if the value is null
         */
        public Lzma memoryLimitKiB(OptionalInt value) {
            return new Lzma(value);
        }

        /**
         * Returns a copy with the memory limit for reading in KiB.
         *
         * @param value the memory limit for reading in KiB
         * @return the changed copy
         * @throws IllegalArgumentException if the value is not above 0
         */
        public Lzma memoryLimitKiB(int value) {
            return memoryLimitKiB(OptionalInt.of(value));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Lzma that && Objects.equals(memoryLimitKiB, that.memoryLimitKiB);
        }

        @Override
        public int hashCode() {
            return Objects.hash(memoryLimitKiB);
        }

        @Override
        public String toString() {
            return "Lzma[memoryLimitKiB=" + memoryLimitKiB + "]";
        }
    }

    /** LZ4 block format. */
    final class Lz4Block implements Compression {
        private Lz4Block() {}

        @Override
        public boolean equals(Object other) {
            return other instanceof Lz4Block;
        }

        @Override
        public int hashCode() {
            return "Lz4Block".hashCode();
        }

        @Override
        public String toString() {
            return "Lz4Block[]";
        }
    }

    /** LZ4 frame format. Obtain the default with {@link Compression#lz4Framed()}. */
    final class Lz4Framed implements Compression {
        private final boolean decompressConcatenated;

        private Lz4Framed(boolean decompressConcatenated) {
            this.decompressConcatenated = decompressConcatenated;
        }

        /**
         * Returns whether reading continues across concatenated LZ4 frames.
         *
         * @return whether reading continues across concatenated LZ4 frames
         */
        public boolean decompressConcatenated() {
            return decompressConcatenated;
        }

        /**
         * Returns a copy with whether reading continues across concatenated LZ4 frames.
         *
         * @param value whether reading continues across concatenated LZ4 frames
         * @return the changed copy
         */
        public Lz4Framed decompressConcatenated(boolean value) {
            return new Lz4Framed(value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Lz4Framed that && decompressConcatenated == that.decompressConcatenated;
        }

        @Override
        public int hashCode() {
            return Objects.hash(decompressConcatenated);
        }

        @Override
        public String toString() {
            return "Lz4Framed[decompressConcatenated=" + decompressConcatenated + "]";
        }
    }

    /**
     * Zstandard. Negative levels trade ratio for speed; 0 selects the default level; the accepted range is -131072 to
     * 22. Obtain the default with {@link Compression#zstd()}.
     */
    final class Zstd implements Compression {
        private final int level;

        private Zstd(int level) {
            Checks.range("zstd level", level, -131072, 22);
            this.level = level;
        }

        /**
         * Returns the compression level.
         *
         * @return the compression level
         */
        public int level() {
            return level;
        }

        /**
         * Returns a copy with the compression level.
         *
         * @param value the compression level, -131072 to 22
         * @return the changed copy
         * @throws IllegalArgumentException if the value lies outside -131072 to 22
         */
        public Zstd level(int value) {
            return new Zstd(value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Zstd that && level == that.level;
        }

        @Override
        public int hashCode() {
            return Objects.hash(level);
        }

        @Override
        public String toString() {
            return "Zstd[level=" + level + "]";
        }
    }

    /** Deflate with an optional zlib header. Obtain the default with {@link Compression#deflate()}. */
    final class Deflate implements Compression {
        private final int level;
        private final boolean zlibHeader;

        private Deflate(int level, boolean zlibHeader) {
            Checks.range("deflate level", level, -1, 9);
            this.level = level;
            this.zlibHeader = zlibHeader;
        }

        /**
         * Returns the compression level, from -1 (default) to 9.
         *
         * @return the compression level, from -1 (default) to 9
         */
        public int level() {
            return level;
        }

        /**
         * Returns a copy with the compression level, from -1 (default) to 9.
         *
         * @param value the compression level, from -1 (default) to 9
         * @return the changed copy
         * @throws IllegalArgumentException if the value is below -1 or above 9
         */
        public Deflate level(int value) {
            return new Deflate(value, zlibHeader);
        }

        /**
         * Returns whether the stream has a zlib header and trailer.
         *
         * @return whether the stream has a zlib header and trailer
         */
        public boolean zlibHeader() {
            return zlibHeader;
        }

        /**
         * Returns a copy with whether the stream has a zlib header and trailer.
         *
         * @param value whether the stream has a zlib header and trailer
         * @return the changed copy
         */
        public Deflate zlibHeader(boolean value) {
            return new Deflate(level, value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Deflate that && level == that.level && zlibHeader == that.zlibHeader;
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, zlibHeader);
        }

        @Override
        public String toString() {
            return "Deflate[level=" + level + ", zlibHeader=" + zlibHeader + "]";
        }
    }

    /** Deflate64; Compress4J reads it but cannot write it. */
    final class Deflate64 implements Compression {
        private Deflate64() {}

        @Override
        public boolean canWrite() {
            return false;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Deflate64;
        }

        @Override
        public int hashCode() {
            return "Deflate64".hashCode();
        }

        @Override
        public String toString() {
            return "Deflate64[]";
        }
    }

    /** Raw Snappy. Writing needs the uncompressed size. Obtain the default with {@link Compression#snappyRaw()}. */
    final class SnappyRaw implements Compression {
        private final OptionalLong uncompressedSize;

        private SnappyRaw(OptionalLong uncompressedSize) {
            Objects.requireNonNull(uncompressedSize, "uncompressedSize");
            if (uncompressedSize.isPresent() && uncompressedSize.getAsLong() < 0) {
                throw new IllegalArgumentException(
                        "snappy uncompressedSize must be at least 0: " + uncompressedSize.getAsLong());
            }
            this.uncompressedSize = uncompressedSize;
        }

        /**
         * Returns the uncompressed size in bytes, required for writing.
         *
         * @return the uncompressed size in bytes, required for writing
         */
        public OptionalLong uncompressedSize() {
            return uncompressedSize;
        }

        /**
         * Returns a copy with the uncompressed size in bytes, empty for unknown.
         *
         * @param value the uncompressed size in bytes, empty for unknown
         * @return the changed copy
         * @throws IllegalArgumentException if a present value is below 0
         * @throws NullPointerException if the value is null
         */
        public SnappyRaw uncompressedSize(OptionalLong value) {
            return new SnappyRaw(value);
        }

        /**
         * Returns a copy with the uncompressed size in bytes.
         *
         * @param value the uncompressed size in bytes
         * @return the changed copy
         * @throws IllegalArgumentException if the value is below 0
         */
        public SnappyRaw uncompressedSize(long value) {
            return uncompressedSize(OptionalLong.of(value));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SnappyRaw that && Objects.equals(uncompressedSize, that.uncompressedSize);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uncompressedSize);
        }

        @Override
        public String toString() {
            return "SnappyRaw[uncompressedSize=" + uncompressedSize + "]";
        }
    }

    /** Framed Snappy. */
    final class SnappyFramed implements Compression {
        private SnappyFramed() {}

        @Override
        public boolean equals(Object other) {
            return other instanceof SnappyFramed;
        }

        @Override
        public int hashCode() {
            return "SnappyFramed".hashCode();
        }

        @Override
        public String toString() {
            return "SnappyFramed[]";
        }
    }

    /** Brotli; Compress4J reads it but cannot write it. */
    final class Brotli implements Compression {
        private Brotli() {}

        @Override
        public boolean canWrite() {
            return false;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Brotli;
        }

        @Override
        public int hashCode() {
            return "Brotli".hashCode();
        }

        @Override
        public String toString() {
            return "Brotli[]";
        }
    }

    /** Unix compress ({@code .Z}); Compress4J reads it but cannot write it. */
    final class UnixZ implements Compression {
        private UnixZ() {}

        @Override
        public boolean canWrite() {
            return false;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof UnixZ;
        }

        @Override
        public int hashCode() {
            return "UnixZ".hashCode();
        }

        @Override
        public String toString() {
            return "UnixZ[]";
        }
    }

    /**
     * Pack200. Obtain the default with {@link Compression#pack200()}.
     *
     * <p>Pack200 is never selected by detection in Decompressor; select it explicitly. The packer loses the content of
     * deflated streamed JAR entries: write entries STORED with size and CRC.
     */
    final class Pack200 implements Compression {
        /** Where Pack200 buffers data. */
        public enum Strategy {
            /** Buffers in memory. */
            IN_MEMORY,
            /** Buffers in a temporary file. */
            TEMP_FILE
        }

        private final Pack200.Strategy strategy;
        private final Map<String, String> properties;

        private Pack200(Pack200.Strategy strategy, Map<String, String> properties) {
            Objects.requireNonNull(strategy, "strategy");
            properties = Map.copyOf(properties);
            this.strategy = strategy;
            this.properties = properties;
        }

        /**
         * Returns where Pack200 buffers data.
         *
         * @return where Pack200 buffers data
         */
        public Pack200.Strategy strategy() {
            return strategy;
        }

        /**
         * Returns a copy with where Pack200 buffers data.
         *
         * @param value where Pack200 buffers data
         * @return the changed copy
         * @throws NullPointerException if the value is null
         */
        public Pack200 strategy(Pack200.Strategy value) {
            return new Pack200(value, properties);
        }

        /**
         * Returns the Pack200 packer and unpacker properties.
         *
         * @return the Pack200 packer and unpacker properties
         */
        public Map<String, String> properties() {
            return properties;
        }

        /**
         * Returns a copy with the Pack200 packer and unpacker properties, copied.
         *
         * @param value the Pack200 packer and unpacker properties, copied
         * @return the changed copy
         * @throws NullPointerException if the map, or any of its keys or values, is null
         */
        public Pack200 properties(Map<String, String> value) {
            return new Pack200(strategy, value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Pack200 that
                    && Objects.equals(strategy, that.strategy)
                    && Objects.equals(properties, that.properties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(strategy, properties);
        }

        @Override
        public String toString() {
            return "Pack200[strategy=" + strategy + ", properties=" + properties + "]";
        }
    }
}
