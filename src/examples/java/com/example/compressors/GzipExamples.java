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
package com.example.compressors;

import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.compressors.Compressor;
import com.hominux.compress4j.compressors.Decompressor;
import com.hominux.compress4j.compressors.DeflateStrategy;
import java.io.IOException;
import java.nio.file.Path;

@SuppressWarnings({"unused"})
public class GzipExamples {
    private GzipExamples() {
        /* no-op */
    }

    public static void compressor() throws IOException {
        // tag::gzip-compressor[]
        Compression gzip = Compression.gzip()
                .bufferSize(1024)
                .level(9)
                .comment("comment")
                .deflateStrategy(DeflateStrategy.HUFFMAN_ONLY)
                .operatingSystem(0);
        try (Compressor compressor =
                Compressor.builder(Path.of("example.gz"), gzip).build()) {
            compressor.write(Path.of("path/to/file.txt"));
        }
        // end::gzip-compressor[]
    }

    public static void decompressor() throws IOException {
        // tag::gzip-decompressor[]
        try (Decompressor decompressor =
                Decompressor.builder(Path.of("example.gz")).build()) {
            decompressor.write(Path.of("path/to/file.txt"));
        }
        // end::gzip-decompressor[]
    }
}
