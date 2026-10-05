/*
 * Copyright 2024-2026 The Compress4J Project
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
package com.example.archivers.sevenz;

import static com.hominux.compress4j.archivers.ErrorHandlerChoice.SKIP;

import com.hominux.compress4j.archivers.EscapingSymlinkPolicy;
import com.hominux.compress4j.archivers.sevenz.SevenZArchiveCreator;
import com.hominux.compress4j.archivers.sevenz.SevenZArchiveExtractor;
import java.io.IOException;
import java.nio.file.Path;

@SuppressWarnings({"java:S1192", "unused"})
public class SevenZExamples {

    private SevenZExamples() {
        /* no-op */
    }

    public static void sevenZCreator() throws IOException {
        // tag::sevenz-creator[]
        try (SevenZArchiveCreator sevenZCreator =
                SevenZArchiveCreator.builder(Path.of("example.7z")).build()) {
            sevenZCreator.addFile(Path.of("path/to/file.txt"));
            sevenZCreator.addDirectoryRecursively(Path.of("sourceDir"));
        }
        // end::sevenz-creator[]
    }

    public static void sevenZExtractor() throws IOException {
        // tag::sevenz-extractor[]
        try (SevenZArchiveExtractor sevenZExtractor = SevenZArchiveExtractor.builder(Path.of("example.7z"))
                .overwrite(true)
                .stripComponents(1)
                .filter(entry -> entry.name().endsWith(".txt"))
                .errorHandler((entry, exception) -> SKIP)
                .escapingSymlinkPolicy(EscapingSymlinkPolicy.DISALLOW)
                .build()) {
            sevenZExtractor.extract(Path.of("outputDir"));
        }
        // end::sevenz-extractor[]
    }

    public static void sevenZEncryptedExtractor() throws IOException {
        // tag::sevenz-encrypted[]
        try (SevenZArchiveExtractor sevenZExtractor = SevenZArchiveExtractor.builder(Path.of("encrypted.7z"))
                .password("secret".toCharArray())
                .maxEntries(10_000)
                .maxTotalSize(1024L * 1024 * 1024)
                .build()) {
            sevenZExtractor.extract(Path.of("outputDir"));
        }
        // end::sevenz-encrypted[]
    }
}
