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
package com.hominux.compress4j.archivers.sevenz;

import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.archive.ReaderContext;
import com.hominux.compress4j.internal.util.EntryValues;
import com.hominux.compress4j.internal.util.UnixFileType;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

final class SevenZEntryReader implements EntryReader {

    static final int UNIX_EXTENSION = 0x8000;
    static final int S_IFLNK = 0120000;
    static final long MAX_SYMLINK_TARGET_BYTES = 4096;

    private final SevenZFile file;
    private final ReaderContext context;
    private Optional<String> current = Optional.empty();

    SevenZEntryReader(SevenZFile file, ReaderContext context) {
        this.file = file;
        this.context = context;
    }

    @Override
    public Optional<Entry> next() throws IOException {
        while (true) {
            drainCurrent();
            SevenZArchiveEntry entry = file.getNextEntry();
            if (entry == null) {
                return Optional.empty();
            }
            current = Optional.ofNullable(entry.getName());
            Optional<Entry> result = toEntry(entry);
            if (result.isPresent()) {
                return result;
            }
        }
    }

    private void drainCurrent() throws IOException {
        if (current.isPresent()) {
            context.meter().meter(new CurrentEntryStream(), current).transferTo(OutputStream.nullOutputStream());
            current = Optional.empty();
        }
    }

    @Override
    public InputStream open(Entry entry) {
        return new CurrentEntryStream();
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    private Optional<Entry> toEntry(SevenZArchiveEntry entry) throws IOException {
        String name = Objects.requireNonNull(entry.getName(), "7z entry has no name");
        int mode = unixMode(entry);
        UnixFileType fileType = UnixFileType.of(mode);
        Optional<Entry> result = typed(entry, name, mode, fileType);
        if (result.isEmpty()) {
            context.reportUnsupported(name, fileType.kind());
        }
        Optional<Date> modified =
                entry.getHasLastModifiedDate() ? Optional.of(entry.getLastModifiedDate()) : Optional.empty();
        return result.map(
                e -> EntryValues.withMetadata(e, modified, e.type() == Entry.Type.FILE ? entry.getSize() : 0));
    }

    private Optional<Entry> typed(SevenZArchiveEntry entry, String name, int mode, UnixFileType fileType)
            throws IOException {
        if (entry.isDirectory() || fileType == UnixFileType.DIRECTORY) {
            return Optional.of(new Entry(name, Entry.Type.DIR, mode));
        } else if (fileType == UnixFileType.SYMLINK) {
            return Optional.of(new Entry(name, Entry.Type.SYMLINK, mode).withLinkTarget(readSymlinkTarget(entry)));
        } else if (fileType == UnixFileType.FILE) {
            return Optional.of(new Entry(name, Entry.Type.FILE, mode));
        }
        return Optional.empty();
    }

    private String readSymlinkTarget(SevenZArchiveEntry entry) throws IOException {
        if (entry.getSize() > MAX_SYMLINK_TARGET_BYTES) {
            throw new IOException("Symlink target of '" + entry.getName() + "' exceeds " + MAX_SYMLINK_TARGET_BYTES
                    + " bytes: " + entry.getSize());
        }
        byte[] target = context.readDeclared(entry.getName(), new CurrentEntryStream(), entry.getSize());
        return new String(target, StandardCharsets.UTF_8);
    }

    private static int unixMode(SevenZArchiveEntry entry) {
        if (!entry.getHasWindowsAttributes() || (entry.getWindowsAttributes() & UNIX_EXTENSION) == 0) {
            return 0;
        }
        return entry.getWindowsAttributes() >>> 16;
    }

    private final class CurrentEntryStream extends InputStream {

        @Override
        public int read() throws IOException {
            return file.read();
        }

        @Override
        public int read(@Nonnull byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            return len == 0 ? 0 : file.read(b, off, len);
        }
    }
}
