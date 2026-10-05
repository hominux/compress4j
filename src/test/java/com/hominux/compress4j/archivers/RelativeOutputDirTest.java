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

import com.hominux.compress4j.archivers.memory.InMemoryArchiveEntry;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelativeOutputDirTest {

    @Test
    void extractsATopLevelFileIntoTheEmptyRelativePath() throws IOException {
        String name = "relative-out-" + UUID.randomUUID() + ".txt";
        Path created = Path.of(name).toAbsolutePath();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(
                        InMemoryArchiveEntry.builder().name(name).content("x").build()))
                .build()) {
            extractor.extract(Path.of(""));

            assertThat(created).hasContent("x");
        } finally {
            Files.deleteIfExists(created);
        }
    }

    @Test
    void extractsIntoARelativeDirectory() throws IOException {
        Path relative = Path.of("build", "tmp", "relative-out-" + UUID.randomUUID());
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(InMemoryArchiveEntry.builder()
                        .name("a.txt")
                        .content("y")
                        .build()))
                .build()) {
            extractor.extract(relative);

            assertThat(relative.resolve("a.txt")).hasContent("y");
        } finally {
            Files.deleteIfExists(relative.resolve("a.txt"));
            Files.deleteIfExists(relative);
        }
    }
}
