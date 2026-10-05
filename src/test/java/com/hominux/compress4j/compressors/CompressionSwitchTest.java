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

import java.util.List;
import org.junit.jupiter.api.Test;

class CompressionSwitchTest {

    @SuppressWarnings("java:S1479")
    private static String name(Compression compression) {
        return switch (compression) {
            case Compression.None c -> "none";
            case Compression.Gzip c -> "gzip";
            case Compression.Bzip2 c -> "bzip2";
            case Compression.Xz c -> "xz";
            case Compression.Lzma c -> "lzma";
            case Compression.Lz4Block c -> "lz4Block";
            case Compression.Lz4Framed c -> "lz4Framed";
            case Compression.Zstd c -> "zstd";
            case Compression.Deflate c -> "deflate";
            case Compression.Deflate64 c -> "deflate64";
            case Compression.SnappyRaw c -> "snappyRaw";
            case Compression.SnappyFramed c -> "snappyFramed";
            case Compression.Brotli c -> "brotli";
            case Compression.UnixZ c -> "unixZ";
            case Compression.Pack200 c -> "pack200";
        };
    }

    @Test
    void anExhaustiveSwitchNeedsNoDefault() {
        var all = List.of(
                Compression.none(),
                Compression.gzip(),
                Compression.bzip2(),
                Compression.xz(),
                Compression.lzma(),
                Compression.lz4Block(),
                Compression.lz4Framed(),
                Compression.zstd(),
                Compression.deflate(),
                Compression.deflate64(),
                Compression.snappyRaw(),
                Compression.snappyFramed(),
                Compression.brotli(),
                Compression.unixZ(),
                Compression.pack200());
        assertThat(all.stream().map(CompressionSwitchTest::name))
                .containsExactly(
                        "none",
                        "gzip",
                        "bzip2",
                        "xz",
                        "lzma",
                        "lz4Block",
                        "lz4Framed",
                        "zstd",
                        "deflate",
                        "deflate64",
                        "snappyRaw",
                        "snappyFramed",
                        "brotli",
                        "unixZ",
                        "pack200");
    }
}
