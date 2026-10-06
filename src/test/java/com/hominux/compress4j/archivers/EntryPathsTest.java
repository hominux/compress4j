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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntryPathsTest {

    @TempDir
    Path outputDir;

    @Test
    void entryFile_resolvesInsideOutputDir() throws IOException {
        assertThat(EntryPaths.entryFile(outputDir, "/a/b.txt")).isEqualTo(outputDir.resolve("a/b.txt"));
    }

    @Test
    void entryFile_rejectsTraversal() {
        assertThatThrownBy(() -> EntryPaths.entryFile(outputDir, "../evil.txt")).isInstanceOf(IOException.class);
    }

    @Test
    void makeDirectory_createsMissingParents() throws IOException {
        Path nested = outputDir.resolve("x/y/z");
        EntryPaths.makeDirectory(nested);
        assertThat(nested).isDirectory();
    }

    @Test
    void stripComponents_dropsLeadingComponents() {
        assertThat(EntryPaths.stripComponents("a/b/c", 1)).contains("b/c");
        assertThat(EntryPaths.stripComponents("/a/b/c", 2)).contains("c");
    }

    @Test
    void stripComponents_isEmptyWhenNothingRemains() {
        assertThat(EntryPaths.stripComponents("a/b", 2)).isEmpty();
        assertThat(EntryPaths.stripComponents("a", 3)).isEmpty();
    }

    @Test
    void requireParent_returnsTheParent() {
        assertThat(EntryPaths.requireParent(outputDir.resolve("a.txt"))).isEqualTo(outputDir);
    }

    @Test
    void requireParent_rejectsAPathWithoutParent() {
        Path noParent = Path.of("a.txt");

        assertThatThrownBy(() -> EntryPaths.requireParent(noParent)).isInstanceOf(IllegalArgumentException.class);
    }
}
