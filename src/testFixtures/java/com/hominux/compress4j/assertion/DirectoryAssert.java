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
package com.hominux.compress4j.assertion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.AbstractPathAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;

public class DirectoryAssert extends AbstractPathAssert<DirectoryAssert> {

    protected DirectoryAssert(Path actual) {
        super(actual, DirectoryAssert.class);
    }

    public static DirectoryAssert assertThat(Path actual) {
        return new DirectoryAssert(actual);
    }

    private static Map<String, Path> directoryContents(final Path root) {
        final int rootPathLength = root.toAbsolutePath().toString().length();
        try (final Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> !root.equals(path))
                    .collect(Collectors.toMap(
                            path -> path.toAbsolutePath().toString().substring(rootPathLength + 1),
                            Function.identity(),
                            (oldPath, newPath) -> {
                                final String message =
                                        String.format("paths `%s` and `%s` have conflicting keys", oldPath, newPath);
                                throw new IllegalStateException(message);
                            },
                            TreeMap::new));
        } catch (final IOException error) {
            final String message = String.format("failed walking directory: `%s`", root);
            throw new UncheckedIOException(message, error);
        }
    }

    @SuppressWarnings({"UnusedReturnValue", "unused"})
    public DirectoryAssert containsAllEntriesOf(Map<String, String> expected) throws IOException {
        try (InputStream fi = Files.newInputStream(actual);
                InputStream bi = new BufferedInputStream(fi);
                TarArchiveInputStream o = new TarArchiveInputStream(bi)) {
            final Map<String, String> seen = new TreeMap<>();
            ArchiveEntry e;
            while ((e = o.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String previous = seen.put(e.getName(), new String(IOUtils.toByteArray(o), StandardCharsets.UTF_8));
                Assertions.assertThat(previous)
                        .as("duplicate entry %s", e.getName())
                        .isNull();
            }
            Assertions.assertThat(seen).containsAllEntriesOf(expected);
        }
        return this;
    }

    /**
     * Compares two directories and provides detailed information about differences. Reports missing paths, extra paths,
     * and content differences for common paths.
     *
     * @param expected the expected directory to compare against
     * @return this assertion object for method chaining
     */
    @SuppressWarnings({"UnusedReturnValue", "unused"})
    public DirectoryAssert hasSameStructureAndContentAs(final Path expected) {
        final SoftAssertions softly = new SoftAssertions();
        final Map<String, Path> actualContents = directoryContents(actual);
        final Map<String, Path> expectedContents = directoryContents(expected);

        final Set<String> actualPaths = actualContents.keySet();
        final Set<String> expectedPaths = expectedContents.keySet();

        final Set<String> missingPaths = new TreeSet<>(expectedPaths);
        missingPaths.removeAll(actualPaths);

        final Set<String> extraPaths = new TreeSet<>(actualPaths);
        extraPaths.removeAll(expectedPaths);

        final Set<String> commonPaths = new TreeSet<>(actualPaths);
        commonPaths.retainAll(expectedPaths);

        final StringBuilder errorMessage = new StringBuilder();
        boolean hasDifferences = false;

        if (!missingPaths.isEmpty()) {
            hasDifferences = true;
            errorMessage.append("\n\nMissing paths in actual directory:\n");
            missingPaths.forEach(
                    path -> errorMessage.append("  - ").append(path).append("\n"));
        }

        if (!extraPaths.isEmpty()) {
            hasDifferences = true;
            errorMessage.append("\n\nExtra paths in actual directory:\n");
            extraPaths.forEach(path -> errorMessage.append("  + ").append(path).append("\n"));
        }

        for (final String relativePath : commonPaths) {
            final Path actualPath = actualContents.get(relativePath);
            final Path expectedPath = expectedContents.get(relativePath);

            final boolean actualIsDir = Files.isDirectory(actualPath);
            final boolean expectedIsDir = Files.isDirectory(expectedPath);

            if (actualIsDir != expectedIsDir) {
                softly.assertThat(actualPath)
                        .as(String.format(
                                "%s: type mismatch: actual is %s, expected is %s",
                                relativePath, actualIsDir ? "directory" : "file", expectedIsDir ? "directory" : "file"))
                        .isEqualTo(expectedPath);
            } else if (!actualIsDir) {
                softly.assertThat(actualPath).as(relativePath).hasSameBinaryContentAs(expectedPath);
            }
        }

        try {
            softly.assertAll();
        } catch (AssertionError e) {
            errorMessage.append("\n\nContent differences:\n").append(e.getMessage());
            hasDifferences = true;
        }

        if (hasDifferences) {
            failWithMessage(
                    "Directory comparison failed for:\n  Actual:   %s\n  Expected: %s%s",
                    actual, expected, errorMessage);
        }

        return this;
    }
}
