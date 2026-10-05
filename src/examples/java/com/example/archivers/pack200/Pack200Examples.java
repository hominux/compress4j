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
package com.example.archivers.pack200;

import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.compressors.Compressor;
import com.hominux.compress4j.compressors.Decompressor;
import java.io.IOException;
import java.nio.file.Path;

@SuppressWarnings({"java:S1192", "unused"})
public class Pack200Examples {

    private Pack200Examples() {
        /* no-op */
    }

    public static void pack200Creator() throws IOException {
        // tag::pack200-creator[]
        Path sourceJar = Path.of("example.jar");
        Path targetPack = Path.of("example.pack");
        try (var compressor =
                Compressor.builder(targetPack, Compression.pack200()).build()) {
            compressor.write(sourceJar);
        }
        // end::pack200-creator[]
    }

    public static void pack200Extractor() throws IOException {
        // tag::pack200-extractor[]
        Path sourcePack = Path.of("example.pack");
        Path targetJar = Path.of("example.jar");
        try (var decompressor =
                Decompressor.builder(sourcePack, Compression.pack200()).build()) {
            decompressor.write(targetJar);
        }
        // end::pack200-extractor[]
    }
}
