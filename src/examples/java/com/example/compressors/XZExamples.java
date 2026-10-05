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
import java.io.IOException;
import java.nio.file.Path;

@SuppressWarnings({"unused"})
public class XZExamples {
    private XZExamples() {
        /* no-op */
    }

    /** Example for XZ compression. */
    public static void compressor() throws IOException {
        // tag::xz-compressor[]
        try (Compressor compressor = Compressor.builder(
                        Path.of("example.xz"), Compression.xz().preset(6))
                .build()) {
            compressor.write(Path.of("path/to/file.txt"));
        }
        // end::xz-compressor[]
    }

    /** Example for XZ decompression. */
    public static void decompressor() throws IOException {
        // tag::xz-decompressor[]
        Compression xz = Compression.xz().decompressConcatenated(true);
        try (Decompressor decompressor =
                Decompressor.builder(Path.of("example.xz"), xz).build()) {
            decompressor.write(Path.of("path/to/file.txt"));
        }
        // end::xz-decompressor[]
    }
}
