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
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings({"java:S1192", "unused"})
public class SnappyExamples {
    private SnappyExamples() {
        /* no-op */
    }

    /** Example for framed Snappy compression. */
    public static void framedCompressor() throws IOException {
        // tag::snappy-framed-compressor[]
        try (Compressor compressor = Compressor.builder(Path.of("example.sz"), Compression.snappyFramed())
                .build()) {
            compressor.write(Path.of("path/to/file.txt"));
        }
        // end::snappy-framed-compressor[]
    }

    /** Example for framed Snappy decompression. */
    public static void framedDecompressor() throws IOException {
        // tag::snappy-framed-decompressor[]
        try (Decompressor decompressor =
                Decompressor.builder(Path.of("example.sz")).build()) {
            decompressor.write(Path.of("path/to/file.txt"));
        }
        // end::snappy-framed-decompressor[]
    }

    /** Example for raw Snappy compression, which needs the uncompressed length up front. */
    public static void rawCompressor() throws IOException {
        // tag::snappy-raw-compressor[]
        Path source = Path.of("path/to/file.txt");
        Compression snappy = Compression.snappyRaw().uncompressedSize(Files.size(source));
        try (Compressor compressor =
                Compressor.builder(Path.of("example.snappy"), snappy).build()) {
            compressor.write(source);
        }
        // end::snappy-raw-compressor[]
    }

    /** Example for raw Snappy decompression. */
    public static void rawDecompressor() throws IOException {
        // tag::snappy-raw-decompressor[]
        try (Decompressor decompressor = Decompressor.builder(Path.of("example.snappy"), Compression.snappyRaw())
                .build()) {
            decompressor.write(Path.of("path/to/file.txt"));
        }
        // end::snappy-raw-decompressor[]
    }
}
