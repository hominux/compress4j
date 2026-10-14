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
package com.hominux.compress4j.test.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Proves a file is not held open by this process, which deletion alone only shows on Windows. */
public final class OpenFiles {

    private static final Path FD_DIR = Path.of("/proc/self/fd");

    private OpenFiles() {}

    /**
     * Asserts that no descriptor of this process refers to {@code file} (where the OS exposes them), then deletes the
     * file.
     *
     * @param file an existing file
     * @throws IOException if the file cannot be inspected or deleted
     */
    public static void assertReleased(Path file) throws IOException {
        Path real = file.toRealPath();
        assertThat(openTargets().stream().anyMatch(real::equals))
                .as("descriptor open on %s", real)
                .isFalse();
        Files.delete(file);
    }

    private static List<Path> openTargets() throws IOException {
        List<Path> targets = new ArrayList<>();
        if (!Files.isDirectory(FD_DIR)) {
            return targets;
        }
        try (DirectoryStream<Path> fds = Files.newDirectoryStream(FD_DIR)) {
            for (Path fd : fds) {
                try {
                    targets.add(Files.readSymbolicLink(fd));
                } catch (NoSuchFileException closedMeanwhile) {
                    // the directory stream's own descriptor, or one closed during the scan
                }
            }
        }
        return targets;
    }
}
