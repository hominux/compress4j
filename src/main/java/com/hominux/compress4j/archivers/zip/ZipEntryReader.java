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
import com.hominux.compress4j.utils.EntryValues;
import com.hominux.compress4j.utils.UnixFileType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Optional;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.function.IOFunction;
import org.apache.commons.io.input.CloseShieldInputStream;

final class ZipEntryReader implements EntryReader {

    static final long MAX_SYMLINK_TARGET_BYTES = 4096;

    private final ZipFile file;
    private final ReaderContext context;
    private final Enumeration<ZipArchiveEntry> entries;
    private Optional<ZipArchiveEntry> current = Optional.empty();
    private Optional<InputStream> currentStream = Optional.empty();

    ZipEntryReader(ZipFile file, ReaderContext context) {
        this.file = file;
        this.context = context;
        this.entries = file.getEntriesInPhysicalOrder();
    }

    @Override
    public Optional<Entry> next() throws IOException {
        closeCurrentStream();
        current = Optional.empty();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry ze = entries.nextElement();
            Optional<Entry> entry = toEntry(ze, this::symlinkTarget, context);
            if (entry.isPresent()) {
                current = Optional.of(ze);
                return entry;
            }
        }
        return Optional.empty();
    }

    @Override
    public InputStream open(Entry entry) throws IOException {
        closeCurrentStream();
        ZipArchiveEntry ze = current.orElseThrow(() -> new IllegalStateException("no current entry"));
        InputStream stream = file.getInputStream(ze);
        currentStream = Optional.of(stream);
        return CloseShieldInputStream.wrap(stream);
    }

    @Override
    public void close() throws IOException {
        closeCurrentStream();
        file.close();
    }

    private void closeCurrentStream() {
        currentStream.ifPresent(IOUtils::closeQuietly);
        currentStream = Optional.empty();
    }

    private Optional<String> symlinkTarget(ZipArchiveEntry ze) throws IOException {
        if (!ze.isUnixSymlink()) {
            return Optional.empty();
        }
        if (ze.getSize() > MAX_SYMLINK_TARGET_BYTES) {
            throw new IOException("Symlink target of '" + ze.getName() + "' exceeds " + MAX_SYMLINK_TARGET_BYTES
                    + " bytes: " + ze.getSize());
        }
        try (InputStream in = file.getInputStream(ze)) {
            return Optional.of(
                    new String(context.readDeclared(ze.getName(), in, ze.getSize()), StandardCharsets.UTF_8));
        }
    }

    static Optional<Entry> toEntry(
            ZipArchiveEntry ze, IOFunction<ZipArchiveEntry, Optional<String>> symlinkTarget, ReaderContext context)
            throws IOException {
        Optional<Entry.Type> type = type(ze);
        if (type.isEmpty()) {
            context.reportUnsupported(
                    ze.getName(), UnixFileType.of(ze.getUnixMode()).kind());
            return Optional.empty();
        }
        Entry.Type t = type.orElseThrow();
        Entry base = EntryValues.withLinkTarget(new Entry(ze.getName(), t, ze.getUnixMode()), symlinkTarget.apply(ze));
        return Optional.of(EntryValues.withMetadata(
                base, Optional.ofNullable(ze.getLastModifiedDate()), t == Entry.Type.FILE ? ze.getSize() : 0));
    }

    private static Optional<Entry.Type> type(ZipArchiveEntry ze) {
        if (ze.isUnixSymlink()) {
            return Optional.of(Entry.Type.SYMLINK);
        } else if (ze.isDirectory()) {
            return Optional.of(Entry.Type.DIR);
        }
        return switch (UnixFileType.of(ze.getUnixMode())) {
            case FILE -> Optional.of(Entry.Type.FILE);
            case DIRECTORY -> Optional.of(Entry.Type.DIR);
            default -> Optional.empty();
        };
    }
}
