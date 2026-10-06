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
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Snippets for the JArchiveLib migration page. */
@SuppressWarnings("unused")
public class JarchivelibMigrationExamples {
    private JarchivelibMigrationExamples() {
        /* no-op */
    }

    public static void create(Path sourceDir, Path destDir) throws IOException {
        // tag::create[]
        try (TarArchiveCreator creator = TarArchiveCreator.builder(destDir.resolve("example.tar.gz"))
                .compression(Compression.gzip())
                .build()) {
            creator.addDirectoryRecursively(sourceDir);
        }
        // end::create[]
    }

    public static void extract(Path archive, Path destDir) throws IOException {
        // tag::extract[]
        try (TarArchiveExtractor extractor =
                TarArchiveExtractor.builder(archive).build()) {
            extractor.extract(destDir);
        }
        // end::extract[]
    }

    public static List<String> stream(Path archive) throws IOException {
        // tag::stream[]
        try (TarArchiveExtractor extractor =
                TarArchiveExtractor.builder(archive).build()) {
            return extractor.stream().map(item -> item.entry().name()).toList();
        }
        // end::stream[]
    }
}
