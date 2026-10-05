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
package com.hominux.compress4j.archivers;

import static com.hominux.compress4j.internal.util.FileUtils.checkValidPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/** Output-path helpers for {@link ArchiveExtractor}. */
final class EntryPaths {

    private EntryPaths() {}

    /**
     * Validates entry and returns the path using the output directory. This method protects against path traversal
     * vulnerabilities.
     *
     * @param outputDir the directory to extract the archive to
     * @param entryName the name of the entry
     * @return the path to the extracted entry
     * @throws IOException if an I/O error occurs or a path traversal vulnerability is detected
     */
    static Path entryFile(Path outputDir, String entryName) throws IOException {
        Path destinationFile = outputDir.resolve(StringUtils.stripStart(entryName, "/"));
        checkValidPath(destinationFile, outputDir);
        return destinationFile;
    }

    /**
     * Creates the directory for the given path, including any necessary but nonexistent parent directories. Note that
     * if this operation fails it may have succeeded in creating some of the necessary parent directories.
     *
     * @param path the directory to be created
     * @throws IOException if the directory, or one of its parents, could not be created
     */
    static void makeDirectory(Path path) throws IOException {
        Files.createDirectories(path);
    }

    static Optional<String> stripComponents(String entryName, int count) {
        List<String> ourPathSplit = splitPath(entryName);
        if (ourPathSplit.size() <= count) {
            return Optional.empty();
        }
        return Optional.of(String.join("/", ourPathSplit.subList(count, ourPathSplit.size())));
    }

    private static List<String> splitPath(String canonicalPath) {
        return Arrays.asList(StringUtils.stripStart(canonicalPath, "/").split("/"));
    }
}
