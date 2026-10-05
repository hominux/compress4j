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
package com.example.archivers.zip;

import static com.hominux.compress4j.archivers.ErrorHandlerChoice.SKIP;

import com.hominux.compress4j.archivers.EscapingSymlinkPolicy;
import com.hominux.compress4j.archivers.zip.ZipArchiveCreator;
import com.hominux.compress4j.archivers.zip.ZipArchiveExtractor;
import com.hominux.compress4j.archivers.zip.ZipCompressionMethod;
import com.hominux.compress4j.archivers.zip.ZipUnicodeExtraFields;
import com.hominux.compress4j.archivers.zip.ZipZip64Mode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@SuppressWarnings({"java:S1192", "unused"})
public class ZipExamples {

    private ZipExamples() {
        /* no-op */
    }

    public static void zipCreator() throws IOException {
        // tag::zip-creator[]
        try (ZipArchiveCreator zipCreator = ZipArchiveCreator.builder(Path.of("example.zip"))
                .compressionLevel(9)
                .compressionMethod(ZipCompressionMethod.DEFLATED)
                .encoding(StandardCharsets.UTF_8)
                .zip64(ZipZip64Mode.AS_NEEDED)
                .comment("This is a zip comment")
                .createUnicodeExtraFields(ZipUnicodeExtraFields.ALWAYS)
                .build()) {
            zipCreator.addFile(Path.of("path/to/file.txt"));
            zipCreator.addDirectoryRecursively(Path.of("sourceDir"));
        }
        // end::zip-creator[]
    }

    public static void zipExtractor() throws IOException {
        // tag::zip-extractor[]
        try (ZipArchiveExtractor zipExtractor = ZipArchiveExtractor.builder(Path.of("example.zip"))
                .overwrite(true)
                .stripComponents(1)
                .ignoreLocalFileHeader(true)
                .useUnicodeExtraFields(true)
                .filter(entry -> entry.name().endsWith(".txt"))
                .errorHandler((entry, exception) -> SKIP)
                .escapingSymlinkPolicy(EscapingSymlinkPolicy.DISALLOW)
                .build()) {
            zipExtractor.extract(Path.of("outputDir"));
        }
        // end::zip-extractor[]
    }
}
