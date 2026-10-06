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

import java.nio.charset.StandardCharsets;
import java.util.Map;
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
        permits None,
                Gzip,
                Bzip2,
                Xz,
                Lzma,
                Lz4Block,
                Lz4Framed,
                Zstd,
                Deflate,
                Deflate64,
                SnappyRaw,
                SnappyFramed,
                Brotli,
                UnixZ,
                Pack200 {

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
}
