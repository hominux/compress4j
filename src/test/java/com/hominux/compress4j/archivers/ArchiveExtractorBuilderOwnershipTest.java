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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hominux.compress4j.archivers.memory.InMemoryArchiveInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArchiveExtractorBuilderOwnershipTest {

    private final IOException failure = new IOException("build failed");

    private ProbeBuilder failingBuilder(InputStream stream, boolean owned) {
        return new ProbeBuilder(stream, owned) {
            @Override
            public InMemoryArchiveInputStream buildArchiveInputStream() throws IOException {
                throw failure;
            }
        };
    }

    @Test
    void shouldCloseOwnedStreamWhenBuildFails() throws IOException {
        var stream = mock(InputStream.class);
        var builder = failingBuilder(stream, true);

        assertThatThrownBy(builder::build).isSameAs(failure);

        verify(stream).close();
    }

    @Test
    void shouldKeepCallerSuppliedStreamOpenWhenBuildFails() throws IOException {
        var stream = mock(InputStream.class);
        var builder = failingBuilder(stream, false);

        assertThatThrownBy(builder::build).isSameAs(failure);

        verify(stream, never()).close();
    }

    private static final class Probe extends LegacyArchiveExtractor<InMemoryArchiveInputStream> {
        private Probe(ProbeBuilder builder) throws IOException {
            super(builder);
        }

        @Override
        protected Optional<Entry> nextEntry() {
            return Optional.empty();
        }

        @Override
        protected InputStream openEntryStream(Entry entry) {
            return InputStream.nullInputStream();
        }
    }

    private static class ProbeBuilder
            extends LegacyArchiveExtractor.ArchiveExtractorBuilder<InMemoryArchiveInputStream, ProbeBuilder, Probe> {
        private final InputStream stream;

        private ProbeBuilder(InputStream stream, boolean owned) {
            super(stream, owned);
            this.stream = stream;
        }

        @Override
        protected ProbeBuilder getThis() {
            return this;
        }

        @Override
        public InMemoryArchiveInputStream buildArchiveInputStream() throws IOException {
            return new InMemoryArchiveInputStream(stream);
        }

        @Override
        public Probe build() throws IOException {
            return new Probe(this);
        }
    }
}
