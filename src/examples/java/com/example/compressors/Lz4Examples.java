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
package com.example.compressors;

import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.compressors.Compressor;
import com.hominux.compress4j.compressors.Decompressor;
import java.io.IOException;
import java.nio.file.Path;

@SuppressWarnings({"java:S1192", "unused"})
public class Lz4Examples {
    private Lz4Examples() {
        /* no-op */
    }

    /** Example for framed LZ4 compression. */
    public static void framedCompressor() throws IOException {
        // tag::lz4-framed-compressor[]
        try (Compressor compressor = Compressor.builder(Path.of("example.lz4"), Compression.lz4Framed())
                .build()) {
            compressor.write(Path.of("path/to/file.txt"));
        }
        // end::lz4-framed-compressor[]
    }

    /** Example for framed LZ4 decompression. */
    public static void framedDecompressor() throws IOException {
        // tag::lz4-framed-decompressor[]
        Compression lz4 = Compression.lz4Framed().decompressConcatenated(true);
        try (Decompressor decompressor =
                Decompressor.builder(Path.of("example.lz4"), lz4).build()) {
            decompressor.write(Path.of("path/to/file.txt"));
        }
        // end::lz4-framed-decompressor[]
    }

    /** Example for block LZ4 compression. */
    public static void blockCompressor() throws IOException {
        // tag::lz4-block-compressor[]
        try (Compressor compressor = Compressor.builder(Path.of("example.block_lz4"), Compression.lz4Block())
                .build()) {
            compressor.write(Path.of("path/to/file.txt"));
        }
        // end::lz4-block-compressor[]
    }

    /** Example for block LZ4 decompression. */
    public static void blockDecompressor() throws IOException {
        // tag::lz4-block-decompressor[]
        try (Decompressor decompressor = Decompressor.builder(Path.of("example.block_lz4"), Compression.lz4Block())
                .build()) {
            decompressor.write(Path.of("path/to/file.txt"));
        }
        // end::lz4-block-decompressor[]
    }
}
