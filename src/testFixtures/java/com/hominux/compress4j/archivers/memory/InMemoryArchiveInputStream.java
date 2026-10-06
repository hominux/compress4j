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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.commons.compress.archivers.ArchiveInputStream;

/** A test input stream. */
public class InMemoryArchiveInputStream extends ArchiveInputStream<InMemoryArchiveEntry> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final CollectionType collectionType =
            mapper.getTypeFactory().constructCollectionType(List.class, InMemoryArchiveEntry.class);
    private final List<InMemoryArchiveEntry> entries;

    private InMemoryArchiveEntry currentEntry;
    private int pointer;

    public InMemoryArchiveInputStream(final List<InMemoryArchiveEntry> entries) {
        this.entries = entries;
    }

    public static InputStream toInputStream(final List<InMemoryArchiveEntry> entries) throws JsonProcessingException {
        return new ByteArrayInputStream(mapper.writeValueAsBytes(entries));
    }

    public static List<InMemoryArchiveEntry> from(final InputStream inputStream) throws IOException {
        return mapper.readValue(inputStream, collectionType);
    }

    @Override
    public InMemoryArchiveEntry getNextEntry() {
        if (pointer >= entries.size()) {
            currentEntry = null;
        } else {
            currentEntry = entries.get(pointer);
        }
        pointer++;
        return currentEntry;
    }

    @Override
    public int read() {
        return 0;
    }
}
