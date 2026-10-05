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
import com.hominux.compress4j.compressors.Decompressor;
import java.io.IOException;
import java.nio.file.Path;

@SuppressWarnings({"unused"})
public class Deflate64Examples {
    private Deflate64Examples() {
        /* no-op */
    }

    /** Example for Deflate64 decompression. */
    public static void decompressor() throws IOException {
        // tag::deflate64-decompressor[]
        try (Decompressor decompressor = Decompressor.builder(Path.of("example.deflate64"), Compression.deflate64())
                .build()) {
            decompressor.write(Path.of("path/to/file.txt"));
        }
        // end::deflate64-decompressor[]
    }
}
