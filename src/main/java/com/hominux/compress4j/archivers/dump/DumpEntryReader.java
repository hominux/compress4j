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
package com.hominux.compress4j.archivers.dump;

import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.archive.ReaderContext;
import com.hominux.compress4j.utils.EntryValues;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.commons.compress.archivers.dump.DumpArchiveEntry;
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;

final class DumpEntryReader implements EntryReader {

    private final DumpArchiveInputStream archiveInputStream;
    private final ReaderContext context;

    DumpEntryReader(DumpArchiveInputStream archiveInputStream, ReaderContext context) {
        this.archiveInputStream = archiveInputStream;
        this.context = context;
    }

    @Override
    public Optional<Entry> next() throws IOException {
        while (true) {
            DumpArchiveEntry entry = archiveInputStream.getNextEntry();
            if (entry == null) {
                return Optional.empty();
            }
            if (isUnnamedDirectory(entry)) {
                continue;
            }
            Optional<Entry.Type> type = typeOf(entry.getType());
            if (type.isPresent()) {
                return Optional.of(toEntry(entry, type.orElseThrow()));
            }
            context.reportUnsupported(entry.getName(), kindOf(entry.getType()));
        }
    }

    @Override
    public InputStream open(Entry entry) {
        return CloseShieldInputStream.wrap(archiveInputStream);
    }

    @Override
    public void close() throws IOException {
        archiveInputStream.close();
    }

    static Optional<Entry.Type> typeOf(DumpArchiveEntry.TYPE type) {
        return switch (type) {
            case FILE -> Optional.of(Entry.Type.FILE);
            case DIRECTORY -> Optional.of(Entry.Type.DIR);
            default -> Optional.empty();
        };
    }

    static String kindOf(DumpArchiveEntry.TYPE type) {
        return switch (type) {
            case LINK -> "symbolic link";
            case CHRDEV -> "character device";
            case BLKDEV -> "block device";
            case FIFO -> "fifo";
            case SOCKET -> "socket";
            case WHITEOUT -> "whiteout";
            default -> "unknown type";
        };
    }

    private static boolean isUnnamedDirectory(DumpArchiveEntry entry) {
        return entry.getName().isEmpty() && entry.getType() == DumpArchiveEntry.TYPE.DIRECTORY;
    }

    private static Entry toEntry(DumpArchiveEntry entry, Entry.Type type) {
        return EntryValues.withMetadata(
                new Entry(entry.getName(), type, entry.getMode()),
                Optional.ofNullable(entry.getLastModifiedDate()),
                type == Entry.Type.FILE ? entry.getSize() : 0);
    }
}
