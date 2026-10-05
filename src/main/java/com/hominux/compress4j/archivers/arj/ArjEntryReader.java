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
package com.hominux.compress4j.archivers.arj;

import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.archive.ReaderContext;
import com.hominux.compress4j.utils.EntryValues;
import com.hominux.compress4j.utils.UnixFileType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.commons.compress.archivers.arj.ArjArchiveEntry;
import org.apache.commons.compress.archivers.arj.ArjArchiveInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;

final class ArjEntryReader implements EntryReader {

    private final ArjArchiveInputStream archiveInputStream;
    private final ReaderContext context;
    private Optional<ArjArchiveEntry> current = Optional.empty();

    ArjEntryReader(ArjArchiveInputStream archiveInputStream, ReaderContext context) {
        this.archiveInputStream = archiveInputStream;
        this.context = context;
    }

    @Override
    public Optional<Entry> next() throws IOException {
        ArjArchiveEntry arjEntry;
        while ((arjEntry = archiveInputStream.getNextEntry()) != null) {
            current = Optional.of(arjEntry);
            UnixFileType fileType = unixTypeOf(arjEntry);
            if (fileType == UnixFileType.FILE || fileType == UnixFileType.DIRECTORY) {
                return Optional.of(toEntry(arjEntry, fileType));
            }
            context.reportUnsupported(arjEntry.getName(), fileType.kind());
        }
        current = Optional.empty();
        return Optional.empty();
    }

    @Override
    public InputStream open(Entry entry) throws IOException {
        var arjEntry = current.orElseThrow(() -> new IllegalStateException("no current entry"));
        if (!archiveInputStream.canReadEntryData(arjEntry)) {
            throw new IOException(
                    "Cannot read ARJ entry data (encrypted or unsupported method): " + arjEntry.getName());
        }
        return CloseShieldInputStream.wrap(archiveInputStream);
    }

    @Override
    public void close() throws IOException {
        archiveInputStream.close();
    }

    private static UnixFileType unixTypeOf(ArjArchiveEntry entry) {
        if (!entry.isHostOsUnix()) {
            return entry.isDirectory() ? UnixFileType.DIRECTORY : UnixFileType.FILE;
        }
        UnixFileType fileType = UnixFileType.of(entry.getUnixMode());
        return fileType == UnixFileType.FILE && entry.isDirectory() ? UnixFileType.DIRECTORY : fileType;
    }

    private static Entry toEntry(ArjArchiveEntry entry, UnixFileType fileType) {
        var type = fileType == UnixFileType.DIRECTORY ? Entry.Type.DIR : Entry.Type.FILE;
        var mode = entry.isHostOsUnix() ? entry.getUnixMode() : 0;
        return EntryValues.withMetadata(
                new Entry(entry.getName(), type, mode),
                Optional.ofNullable(entry.getLastModifiedDate()),
                type == Entry.Type.FILE ? entry.getSize() : 0);
    }
}
