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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class InMemoryArchiveExtractor extends ArchiveExtractor<InMemoryArchiveInputStream> {

    @SuppressWarnings("unused")
    public InMemoryArchiveExtractor(InMemoryArchiveInputStream inputStream) {
        super(inputStream);
    }

    public InMemoryArchiveExtractor(InMemoryArchiveExtractorBuilder inputStreamBuilder) throws IOException {
        super(inputStreamBuilder);
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

    @Override
    protected Optional<Entry> nextEntry() {
        return Optional.ofNullable(archiveInputStream.getNextEntry())
                .map(next -> new Entry(next.getName(), next.getType(), next.getMode())
                        .withLinkTarget(next.getLinkName())
                        .withMetadata(next.getLastModifiedDate(), next.getSize()));
    }

    @Override
    protected InputStream openEntryStream(Entry entry) {
        return new ByteArrayInputStream(archiveInputStream.readString().getBytes(StandardCharsets.UTF_8));
    }

    public static class InMemoryArchiveExtractorBuilder
            extends ArchiveExtractor.ArchiveExtractorBuilder<
                    InMemoryArchiveInputStream, InMemoryArchiveExtractorBuilder, InMemoryArchiveExtractor> {

        private final InputStream inputStream;

        public InMemoryArchiveExtractorBuilder(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        public InMemoryArchiveExtractorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStream = inputStream;
        }

        @Override
        protected InMemoryArchiveExtractorBuilder getThis() {
            return this;
        }

        @Override
        public InMemoryArchiveInputStream buildArchiveInputStream() throws IOException {
            return new InMemoryArchiveInputStream(inputStream);
        }

        @Override
        public InMemoryArchiveExtractor build() throws IOException {
            return new InMemoryArchiveExtractor(this);
        }
    }
}
