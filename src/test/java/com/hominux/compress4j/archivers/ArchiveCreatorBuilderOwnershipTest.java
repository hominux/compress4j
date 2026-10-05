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

import com.hominux.compress4j.archivers.memory.InMemoryArchiveOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ArchiveCreatorBuilderOwnershipTest {

    private final IllegalStateException failure = new IllegalStateException("build failed");

    private ProbeBuilder failingBuilder(OutputStream stream, boolean owned) {
        return new ProbeBuilder(stream, owned) {
            @Override
            public InMemoryArchiveOutputStream buildArchiveOutputStream() {
                throw failure;
            }
        };
    }

    @Test
    void shouldCloseOwnedStreamWhenBuildFails() throws IOException {
        var stream = mock(OutputStream.class);
        var builder = failingBuilder(stream, true);

        assertThatThrownBy(builder::build).isSameAs(failure);

        verify(stream).close();
    }

    @Test
    void shouldKeepCallerSuppliedStreamOpenWhenBuildFails() throws IOException {
        var stream = mock(OutputStream.class);
        var builder = failingBuilder(stream, false);

        assertThatThrownBy(builder::build).isSameAs(failure);

        verify(stream, never()).close();
    }

    private static final class Probe extends LegacyArchiveCreator<InMemoryArchiveOutputStream> {
        private Probe(ProbeBuilder builder) throws IOException {
            super(builder);
        }

        @Override
        protected void writeDirectory(String name, int mode, FileTime lastModified) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void writeFile(String name, InputStream content, OptionalLong size, int mode, FileTime lastModified) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void writeSymlink(String name, String target, int mode, FileTime lastModified) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean requiresSize() {
            return false;
        }
    }

    private static class ProbeBuilder
            extends LegacyArchiveCreator.ArchiveCreatorBuilder<InMemoryArchiveOutputStream, ProbeBuilder, Probe> {
        private ProbeBuilder(OutputStream stream, boolean owned) {
            super(stream, owned);
        }

        @Override
        protected ProbeBuilder getThis() {
            return this;
        }

        @Override
        public InMemoryArchiveOutputStream buildArchiveOutputStream() {
            return new InMemoryArchiveOutputStream(outputStream);
        }

        @Override
        public Probe build() throws IOException {
            return new Probe(this);
        }
    }
}
