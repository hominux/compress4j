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
package com.example.archivers.cpio;

import static com.hominux.compress4j.archivers.ArchiveExtractor.ErrorHandlerChoice.RETRY;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.archivers.cpio.CpioArchiveCreator;
import com.hominux.compress4j.archivers.cpio.CpioArchiveExtractor;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.apache.commons.compress.archivers.cpio.CpioConstants;

@SuppressWarnings({"java:S1192", "unused"})
public class CpioExamples {

    private CpioExamples() {
        /* no-op */
    }

    public static void cpioCreator() throws IOException {
        // tag::cpio-creator[]
        try (CpioArchiveCreator cpioCreator = CpioArchiveCreator.builder(Path.of("example.cpio"))
                .cpioOutputStream()
                .format(CpioConstants.FORMAT_NEW)
                .blockSize(1024)
                .encoding(UTF_8.name())
                .and()
                .filter(s -> !s.name().endsWith("temp.txt"))
                .build()) {

            cpioCreator.addFile("document.txt", Path.of("path/to/document.txt"));
            cpioCreator.addDirectory("subdir/", FileTime.from(Instant.now()));
            cpioCreator.addFile("subdir/nested.txt", Path.of("path/to/nested.txt"));

            cpioCreator.addDirectoryRecursively(Path.of("sourceDir"));
        }
        // end::cpio-creator[]
    }

    public static void cpioExtractor() throws IOException {
        // tag::cpio-extractor[]
        try (CpioArchiveExtractor cpioExtractor = CpioArchiveExtractor.builder(Path.of("example.cpio"))
                .cpioInputStream()
                .blockSize(1024)
                .encoding(UTF_8.name())
                .and()
                .filter(entry -> !entry.name().startsWith("temp"))
                .errorHandler((entry, exception) -> RETRY)
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .postProcessor((entry, exception) -> {})
                .stripComponents(1)
                .overwrite(true)
                .build()) {
            cpioExtractor.extract(Path.of("outputDir"));
        }
        // end::cpio-extractor[]
    }
}
