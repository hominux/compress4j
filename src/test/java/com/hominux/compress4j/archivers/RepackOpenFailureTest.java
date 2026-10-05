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
import static org.assertj.core.api.Assertions.catchThrowable;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveCreator.InMemoryArchiveCreatorBuilder;
import com.hominux.compress4j.internal.archive.EntryReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RepackOpenFailureTest {

    private static EntryReader readerFailingToOpen() {
        var served = new AtomicBoolean();
        return new EntryReader() {
            @Override
            public Optional<Entry> next() {
                return served.getAndSet(true) ? Optional.empty() : Optional.of(new Entry("a", Entry.Type.FILE, 0));
            }

            @Override
            public InputStream open(Entry entry) throws IOException {
                throw new IOException("cannot open " + entry.name());
            }

            @Override
            public void close() {
                throw new UnsupportedOperationException("the pipeline does not close its reader");
            }
        };
    }

    @Test
    void addAllSurfacesEntryOpenFailureAsIOException() throws IOException {
        // Given
        var pipeline = new EntryPipeline(
                readerFailingToOpen(), 0, entry -> true, ExtractionLimits.noLimits(), () -> Long.MAX_VALUE);

        try (var creator = new InMemoryArchiveCreatorBuilder(new ByteArrayOutputStream()).build()) {
            // When
            Throwable failure =
                    catchThrowable(() -> creator.addAll(pipeline.stream().map(ArchiveItem::toSource)));

            // Then
            assertThat(failure).isInstanceOf(IOException.class).hasMessage("cannot open a");
            assertThat(failure).isNotInstanceOf(UncheckedIOException.class);
        }
    }
}
