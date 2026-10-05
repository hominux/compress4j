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
package com.hominux.compress4j.archivers.zip;

import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.archive.ReaderContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;

final class ZipStreamingEntryReader implements EntryReader {

    private final ZipArchiveInputStream archiveInputStream;
    private final ReaderContext context;

    ZipStreamingEntryReader(ZipArchiveInputStream archiveInputStream, ReaderContext context) {
        this.archiveInputStream = archiveInputStream;
        this.context = context;
    }

    @Override
    public Optional<Entry> next() throws IOException {
        ZipArchiveEntry ze;
        while ((ze = archiveInputStream.getNextEntry()) != null) {
            Optional<Entry> entry = ZipEntryReader.toEntry(ze, e -> Optional.empty(), context);
            if (entry.isPresent()) {
                return entry;
            }
        }
        return Optional.empty();
    }

    @Override
    public InputStream open(Entry entry) {
        return CloseShieldInputStream.wrap(archiveInputStream);
    }

    @Override
    public void close() throws IOException {
        archiveInputStream.close();
    }
}
