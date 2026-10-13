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
package com.hominux.compress4j.fuzz;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.archivers.ArchiveItem;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class FuzzSupport {

    static final ExtractionLimits LIMITS = ExtractionLimits.defaults().withMaxTotalSize(16L << 20);

    private FuzzSupport() {}

    @FunctionalInterface
    interface Parse {
        void run() throws IOException;
    }

    @FunctionalInterface
    interface Open {
        ArchiveExtractor open() throws IOException;
    }

    static void onlyIOException(Parse parse) {
        try {
            parse.run();
        } catch (IOException | UncheckedIOException malformedInput) {
            // Only malformed input may fail; any other exception escapes and fails the test.
        }
    }

    static void drain(Open open) {
        onlyIOException(() -> {
            try (ArchiveExtractor extractor = open.open()) {
                extractor.stream().forEach(FuzzSupport::consume);
            }
        });
        onlyIOException(() -> extractToTempDir(open));
    }

    private static void extractToTempDir(Open open) throws IOException {
        Path outputDir = Files.createTempDirectory("compress4j-fuzz");
        try (ArchiveExtractor extractor = open.open()) {
            extractor.extract(outputDir);
        } finally {
            deleteRecursively(outputDir);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        final List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.delete(path);
        }
    }

    static void consume(InputStream in) throws IOException {
        try (in) {
            in.transferTo(OutputStream.nullOutputStream());
        }
    }

    private static void consume(ArchiveItem item) {
        try {
            consume(item.content());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
