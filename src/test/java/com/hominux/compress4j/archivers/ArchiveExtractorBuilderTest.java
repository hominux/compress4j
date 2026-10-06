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
package com.hominux.compress4j.archivers;

import static com.hominux.compress4j.archivers.ErrorHandlerChoice.SKIP;
import static com.hominux.compress4j.archivers.EscapingSymlinkPolicy.DISALLOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.archivers.memory.InMemoryArchiveEntry;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveExtractor;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveExtractor.InMemoryArchiveExtractorBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveExtractorBuilderTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldBuildArchiveExtractor() throws IOException {
        // given
        Predicate<Entry> filter = entry -> !entry.name().contains("some");
        BiFunction<Entry, IOException, ErrorHandlerChoice> errorHandler = (entry, exception) -> SKIP;
        AtomicInteger counter = new AtomicInteger();
        BiConsumer<Entry, Path> postProcessor = (entry, path) -> counter.incrementAndGet();
        InMemoryArchiveExtractorBuilder builder = InMemoryArchiveExtractor.builder(List.of())
                .filter(filter)
                .errorHandler(errorHandler)
                .overwrite(true)
                .escapingSymlinkPolicy(DISALLOW)
                .stripComponents(5)
                .postProcessor(postProcessor);

        // when
        try (InMemoryArchiveExtractor extractor = builder.build()) {
            // then
            assertThat((Object) extractor)
                    .extracting("errorHandler", "overwrite", "escapingSymlinkPolicy", "postProcessor")
                    .containsExactly(errorHandler, true, DISALLOW, postProcessor);
        }
    }

    @Test
    void filterShouldControlWhichEntriesAreExtracted() throws IOException {
        // given
        var kept = InMemoryArchiveEntry.builder().name("kept").content("k").build();
        var skipped =
                InMemoryArchiveEntry.builder().name("some-skipped").content("s").build();

        // when
        try (InMemoryArchiveExtractor extractor = InMemoryArchiveExtractor.builder(List.of(kept, skipped))
                .filter(entry -> !entry.name().contains("some"))
                .build()) {
            extractor.extract(tempDir);
        }

        // then
        assertThat(tempDir.resolve("kept")).hasContent("k");
        assertThat(tempDir.resolve("some-skipped")).doesNotExist();
    }
}
