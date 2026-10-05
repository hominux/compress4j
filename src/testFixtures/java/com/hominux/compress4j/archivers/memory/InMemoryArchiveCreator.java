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

import static com.hominux.compress4j.archivers.Entry.Type.DIR;
import static com.hominux.compress4j.archivers.Entry.Type.FILE;
import static com.hominux.compress4j.archivers.Entry.Type.SYMLINK;

import com.hominux.compress4j.archivers.ArchiveCreator;
import com.hominux.compress4j.internal.archive.EntryWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;

public class InMemoryArchiveCreator extends ArchiveCreator {

    public InMemoryArchiveCreator(InMemoryArchiveCreatorBuilder builder) {
        this(builder, new InMemoryEntryWriter(builder.outputStream));
    }

    public InMemoryArchiveCreator(InMemoryArchiveCreatorBuilder builder, InMemoryEntryWriter writer) {
        super(builder, writer);
    }

    /** Writes each entry as a JSON document to the output stream. */
    public static class InMemoryEntryWriter implements EntryWriter {
        private final InMemoryArchiveOutputStream out;

        public InMemoryEntryWriter(OutputStream outputStream) {
            this.out = new InMemoryArchiveOutputStream(outputStream);
        }

        @Override
        public boolean requiresSize() {
            return false;
        }

        @Override
        public void writeDirectory(String name, int mode, FileTime lastModified) throws IOException {
            out.putArchiveEntry(InMemoryArchiveEntry.builder()
                    .name(name)
                    .type(DIR)
                    .mode(mode)
                    .lastModifiedDate(lastModified)
                    .build());
        }

        @Override
        public void writeFile(String name, InputStream content, OptionalLong size, int mode, FileTime lastModified)
                throws IOException {
            out.putArchiveEntry(InMemoryArchiveEntry.builder()
                    .name(name)
                    .type(FILE)
                    .mode(mode)
                    .lastModifiedDate(lastModified)
                    .content(new String(content.readAllBytes(), StandardCharsets.UTF_8))
                    .build());
        }

        @Override
        public void writeSymlink(String name, String target, int mode, FileTime lastModified) throws IOException {
            out.putArchiveEntry(InMemoryArchiveEntry.builder()
                    .name(name)
                    .type(SYMLINK)
                    .mode(mode)
                    .linkName(target)
                    .lastModifiedDate(lastModified)
                    .build());
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    public static class InMemoryArchiveCreatorBuilder
            extends ArchiveCreator.Builder<InMemoryArchiveCreatorBuilder, InMemoryArchiveCreator> {
        private final OutputStream outputStream;

        public InMemoryArchiveCreatorBuilder(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        protected InMemoryArchiveCreatorBuilder getThis() {
            return this;
        }

        @Override
        public InMemoryArchiveCreator build() {
            return new InMemoryArchiveCreator(this);
        }
    }
}
