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
package com.hominux.compress4j.archivers;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.archivers.ArchiveExtractor.Entry;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EntryPipelineExhaustionTest {

    @Test
    void readerIsNotAskedForEntriesAfterItReportedTheEnd() {
        // Given
        var calls = new AtomicInteger();
        EntryReader reader = new EntryReader() {
            @Override
            public Optional<Entry> next() throws IOException {
                return switch (calls.incrementAndGet()) {
                    case 1 -> Optional.of(new Entry("a", Entry.Type.DIR, 0));
                    case 2 -> Optional.empty();
                    default -> throw new IOException("read past the end of the archive");
                };
            }

            @Override
            public InputStream open(Entry entry) {
                return InputStream.nullInputStream();
            }

            @Override
            public void release(InputStream content) {
                // nothing to release
            }
        };
        var pipeline = new EntryPipeline(reader, 0, entry -> true, ExtractionLimits.noLimits());

        // When
        var names = pipeline.stream().map(item -> item.entry().name()).iterator();

        // Then
        assertThat(names.next()).isEqualTo("a");
        assertThat(names.hasNext()).isFalse();
        assertThat(calls).hasValue(2);
    }
}
