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
package com.example;

import com.hominux.compress4j.archivers.tar.TarArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.compressors.Compressor;
import com.hominux.compress4j.compressors.Decompressor;
import com.hominux.compress4j.exceptions.UnsafeInputException;
import java.io.IOException;
import java.nio.file.Path;

/** Keeps the snippets in README.adoc compiling; edit both together. */
@SuppressWarnings({"unused"})
public class ReadmeExamples {
    private ReadmeExamples() {
        /* no-op */
    }

    public static void create() throws IOException {
        // tag::readme-create[]
        try (TarArchiveCreator creator = TarArchiveCreator.builder(Path.of("example.tar.gz"))
                .compression(Compression.gzip())
                .build()) {
            creator.addDirectoryRecursively(Path.of("exampleDir"));
            creator.addFile(Path.of("path/to/file.txt"));
        }
        // end::readme-create[]
    }

    public static void extract() throws IOException {
        // tag::readme-extract[]
        try (TarArchiveExtractor extractor =
                TarArchiveExtractor.builder(Path.of("example.tar.gz")).build()) {
            extractor.extract(Path.of("outputDir"));
        }
        // end::readme-extract[]
    }

    public static void compress() throws IOException {
        // tag::readme-compress[]
        try (Compressor compressor = Compressor.builder(
                        Path.of("file.txt.zst"), Compression.zstd().level(6))
                .build()) {
            compressor.write(Path.of("file.txt"));
        }
        // end::readme-compress[]
    }

    public static void decompress() throws IOException {
        // tag::readme-decompress[]
        try (Decompressor decompressor =
                Decompressor.builder(Path.of("file.txt.zst")).build()) {
            decompressor.write(Path.of("copy.txt"));
        }
        // end::readme-decompress[]
    }

    public static void glance() throws IOException {
        // tag::readme-glance[]
        try (TarArchiveExtractor extractor = TarArchiveExtractor.builder(Path.of("untrusted.tar.gz"))
                .maxTotalSize(1024L * 1024 * 1024)
                .build()) {
            extractor.extract(Path.of("outputDir"));
        } catch (UnsafeInputException e) {
            System.err.println("Rejected: " + e.getMessage());
        }
        // end::readme-glance[]
    }

    public static void extractUntrusted() throws IOException {
        // tag::readme-extract-untrusted[]
        try (TarArchiveExtractor extractor = TarArchiveExtractor.builder(Path.of("untrusted.tar.gz"))
                .maxEntries(10_000)
                .maxEntrySize(100L * 1024 * 1024)
                .maxTotalSize(1024L * 1024 * 1024)
                .build()) {
            extractor.extract(Path.of("outputDir"));
        }
        // end::readme-extract-untrusted[]
    }
}
