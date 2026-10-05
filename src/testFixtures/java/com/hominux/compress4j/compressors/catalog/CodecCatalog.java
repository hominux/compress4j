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
package com.hominux.compress4j.compressors.catalog;

import com.hominux.compress4j.compressors.Compression;
import java.util.Optional;
import java.util.stream.Stream;

/** Every codec of the library with the facts the contract suites need. */
public final class CodecCatalog {

    private CodecCatalog() {}

    private static CodecFormat row(
            String name, Compression compression, boolean detectable, boolean reportsTruncation, String sample) {
        return new CodecFormat(name, compression, detectable, reportsTruncation, Optional.of(sample));
    }

    private static CodecFormat row(
            String name, Compression compression, boolean detectable, boolean reportsTruncation) {
        return new CodecFormat(name, compression, detectable, reportsTruncation, Optional.empty());
    }

    private static Stream<CodecFormat> sampled() {
        return Stream.of(
                row("gzip", Compression.gzip(), true, true, "compress.txt.gz"),
                row("bzip2", Compression.bzip2(), true, true, "compress.txt.bz2"),
                row("xz", Compression.xz(), true, true, "compress.txt.xz"),
                row("lzma", Compression.lzma(), false, true, "compress.txt.lzma"),
                row("lz4-block", Compression.lz4Block(), false, true, "compress.txt.block_lz4"),
                row("lz4-framed", Compression.lz4Framed(), true, true, "compress.txt.lz4"),
                row("zstd", Compression.zstd(), true, true, "compress.txt.zst"),
                row("deflate", Compression.deflate(), false, true, "compress.txt.deflate"),
                row("snappy-framed", Compression.snappyFramed(), true, true, "compress.txt.sz"));
    }

    private static Stream<CodecFormat> unsampled() {
        return Stream.of(
                row("snappy-raw", Compression.snappyRaw(), false, true),
                row("pack200", Compression.pack200(), false, true),
                row("deflate64", Compression.deflate64(), false, true),
                row("brotli", Compression.brotli(), false, true),
                row("z", Compression.unixZ(), true, false));
    }

    /**
     * Returns every codec.
     *
     * @return every codec
     */
    public static Stream<CodecFormat> all() {
        return Stream.concat(sampled(), unsampled());
    }

    /**
     * Returns the codecs that can write.
     *
     * @return the codecs that can write
     */
    public static Stream<CodecFormat> writable() {
        return all().filter(format -> format.compression().canWrite());
    }
}
