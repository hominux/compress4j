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
package com.hominux.compress4j.archivers.tar;

import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.archive.ReaderContext;
import com.hominux.compress4j.internal.util.EntryValues;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.io.input.CloseShieldInputStream;

final class TarEntryReader implements EntryReader {

    private static final int RECORD_SIZE = TarConstants.DEFAULT_RCDSIZE;

    private final TarArchiveInputStream archiveInputStream;
    private final ReaderContext context;
    private boolean started;

    TarEntryReader(TarArchiveInputStream archiveInputStream, ReaderContext context) {
        this.archiveInputStream = archiveInputStream;
        this.context = context;
    }

    @Override
    public Optional<Entry> next() throws IOException {
        return getNextTarArchiveEntry().map(TarEntryReader::toEntry);
    }

    private static Entry toEntry(TarArchiveEntry te) {
        Entry base = EntryValues.withLinkTarget(
                new Entry(te.getName(), type(te), te.getMode()), Optional.ofNullable(te.getLinkName()));
        return EntryValues.withMetadata(base, te, base.type() == Entry.Type.FILE ? te.getRealSize() : 0);
    }

    @Override
    public InputStream open(Entry entry) {
        return CloseShieldInputStream.wrap(archiveInputStream);
    }

    @Override
    public void close() throws IOException {
        archiveInputStream.close();
    }

    private Optional<TarArchiveEntry> getNextTarArchiveEntry() throws IOException {
        Optional<TarArchiveEntry> next = nextSupported();
        if (next.isEmpty()) {
            requireNotTruncatedBeforeFirstEntry();
        }
        started = true;
        return next;
    }

    private Optional<TarArchiveEntry> nextSupported() throws IOException {
        for (TarArchiveEntry te = archiveInputStream.getNextEntry();
                te != null;
                te = archiveInputStream.getNextEntry()) {
            started = true;
            requireValidChecksum(te);
            if (te.isGlobalPaxHeader()) {
                continue;
            }
            Optional<String> unsupported = unsupportedKind(te);
            if (unsupported.isEmpty()) {
                return Optional.of(te);
            }
            context.reportUnsupported(te.getName(), unsupported.orElseThrow());
        }
        return Optional.empty();
    }

    private static void requireValidChecksum(TarArchiveEntry te) throws IOException {
        if (!te.isCheckSumOK()) {
            throw new IOException("not a tar archive: the header checksum of entry '" + te.getName() + "' is wrong");
        }
    }

    private void requireNotTruncatedBeforeFirstEntry() throws IOException {
        long read = archiveInputStream.getBytesRead();
        if (!started && read > 0 && read < RECORD_SIZE) {
            throw new IOException("not a tar archive: " + read + " bytes, shorter than one header record");
        }
    }

    private static Optional<String> unsupportedKind(TarArchiveEntry te) {
        if (te.isLink()) {
            return Optional.of("hard link");
        } else if (te.isCharacterDevice()) {
            return Optional.of("character device");
        } else if (te.isBlockDevice()) {
            return Optional.of("block device");
        } else if (te.isFIFO()) {
            return Optional.of("fifo");
        } else if (isSupported(te)) {
            return Optional.empty();
        }
        return Optional.of("unknown type");
    }

    private static boolean isSupported(TarArchiveEntry te) {
        return te.isDirectory() || te.isSymbolicLink() || isRegularFile(te);
    }

    private static boolean isRegularFile(TarArchiveEntry te) {
        byte flag = te.getLinkFlag();
        return flag == TarConstants.LF_OLDNORM
                || flag == TarConstants.LF_NORMAL
                || flag == TarConstants.LF_CONTIG
                || te.isGNUSparse();
    }

    private static Entry.Type type(TarArchiveEntry te) {
        if (te.isSymbolicLink()) {
            return Entry.Type.SYMLINK;
        } else if (te.isDirectory()) {
            return Entry.Type.DIR;
        } else {
            return Entry.Type.FILE;
        }
    }
}
