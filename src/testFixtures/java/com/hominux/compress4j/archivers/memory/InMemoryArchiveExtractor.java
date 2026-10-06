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
package com.hominux.compress4j.archivers.memory;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.util.EntryValues;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class InMemoryArchiveExtractor extends ArchiveExtractor {

    public InMemoryArchiveExtractor(InMemoryArchiveExtractorBuilder builder) throws IOException {
        super(builder, builder.reader(), () -> Long.MAX_VALUE);
    }

    /**
     * Helper static method to create an instance of the {@link InMemoryArchiveExtractorBuilder}
     *
     * @param inputStream the input stream
     * @return An instance of the {@link InMemoryArchiveExtractorBuilder}
     */
    public static InMemoryArchiveExtractorBuilder builder(InputStream inputStream) {
        return new InMemoryArchiveExtractorBuilder(inputStream);
    }

    /**
     * Creates a new {@code InMemoryArchiveExtractorBuilder}.
     *
     * @param entries the {@code List} of {@code InMemoryArchiveEntry}
     * @throws IOException if an I/O error occurs
     */
    public static InMemoryArchiveExtractorBuilder builder(final List<InMemoryArchiveEntry> entries) throws IOException {
        return new InMemoryArchiveExtractorBuilder(InMemoryArchiveInputStream.toInputStream(entries));
    }

    private static final class InMemoryEntryReader implements EntryReader {
        private final List<InMemoryArchiveEntry> entries;
        private int pointer;
        private Optional<InMemoryArchiveEntry> current = Optional.empty();

        private InMemoryEntryReader(List<InMemoryArchiveEntry> entries) {
            this.entries = entries;
        }

        @Override
        public Optional<Entry> next() {
            current = pointer < entries.size() ? Optional.of(entries.get(pointer)) : Optional.empty();
            pointer++;
            return current.map(InMemoryEntryReader::entryOf);
        }

        @Override
        public InputStream open(Entry entry) {
            return new ByteArrayInputStream(current.orElseThrow().getContent().getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            /* nothing to release */
        }

        private static Entry entryOf(InMemoryArchiveEntry next) {
            return EntryValues.withMetadata(
                    EntryValues.withLinkTarget(
                            new Entry(next.getName(), next.getType(), next.getMode()),
                            Optional.ofNullable(next.getLinkName())),
                    next,
                    next.getSize());
        }
    }

    public static class InMemoryArchiveExtractorBuilder
            extends ArchiveExtractor.Builder<InMemoryArchiveExtractorBuilder, InMemoryArchiveExtractor> {

        private final InputStream inputStream;
        private UnaryOperator<EntryReader> readerDecorator = UnaryOperator.identity();

        public InMemoryArchiveExtractorBuilder(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        /**
         * Wraps the reader the extractor reads from, so a test can inject faults.
         *
         * @param decorator wraps the reader
         * @return this builder
         */
        public InMemoryArchiveExtractorBuilder readerDecorator(UnaryOperator<EntryReader> decorator) {
            this.readerDecorator = decorator;
            return this;
        }

        EntryReader reader() throws IOException {
            return readerDecorator.apply(new InMemoryEntryReader(InMemoryArchiveInputStream.from(inputStream)));
        }

        @Override
        protected InMemoryArchiveExtractorBuilder getThis() {
            return this;
        }

        @Override
        public InMemoryArchiveExtractor build() throws IOException {
            return new InMemoryArchiveExtractor(this);
        }
    }
}
