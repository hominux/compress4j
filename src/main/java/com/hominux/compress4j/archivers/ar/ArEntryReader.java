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
package com.hominux.compress4j.archivers.ar;

import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.archive.ReaderContext;
import com.hominux.compress4j.internal.util.EntryValues;
import com.hominux.compress4j.internal.util.UnixFileType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;

final class ArEntryReader implements EntryReader {

    private final ArArchiveInputStream archiveInputStream;
    private final ReaderContext context;

    ArEntryReader(ArArchiveInputStream archiveInputStream, ReaderContext context) {
        this.archiveInputStream = archiveInputStream;
        this.context = context;
    }

    @Override
    public Optional<Entry> next() throws IOException {
        ArArchiveEntry ae;
        while ((ae = archiveInputStream.getNextEntry()) != null) {
            UnixFileType fileType = UnixFileType.of(ae.getMode());
            Optional<Entry.Type> type = fileType.entryType();
            if (type.isPresent()) {
                return Optional.of(toEntry(ae, type.orElseThrow()));
            }
            context.reportUnsupported(ae.getName(), fileType.kind());
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

    private Entry toEntry(ArArchiveEntry ae, Entry.Type type) throws IOException {
        Entry base = new Entry(ae.getName(), type, ae.getMode());
        Entry entry = type == Entry.Type.SYMLINK
                ? base.withLinkTarget(context.readLinkTarget(ae.getName(), archiveInputStream, ae.getSize()))
                : base;
        return EntryValues.withMetadata(
                entry, Optional.ofNullable(ae.getLastModifiedDate()), type == Entry.Type.FILE ? ae.getSize() : 0);
    }
}
