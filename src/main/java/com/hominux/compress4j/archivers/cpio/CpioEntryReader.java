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
package com.hominux.compress4j.archivers.cpio;

import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.archive.ReaderContext;
import com.hominux.compress4j.internal.util.EntryValues;
import com.hominux.compress4j.internal.util.UnixFileType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.apache.commons.io.input.CloseShieldInputStream;

final class CpioEntryReader implements EntryReader {

    private static final String HARD_LINK = "hard link without data";
    private static final String TRAILER = "TRAILER!!!";

    private final CpioArchiveInputStream archiveInputStream;
    private final ReaderContext context;

    CpioEntryReader(CpioArchiveInputStream archiveInputStream, ReaderContext context) {
        this.archiveInputStream = archiveInputStream;
        this.context = context;
    }

    @Override
    public Optional<Entry> next() throws IOException {
        CpioArchiveEntry ce;
        while ((ce = archiveInputStream.getNextEntry()) != null && !TRAILER.equals(ce.getName())) {
            UnixFileType fileType = UnixFileType.of((int) ce.getMode());
            Optional<Entry.Type> type = fileType.entryType();
            if (type.isPresent() && !isDatalessHardLink(ce)) {
                return Optional.of(toEntry(ce, type.orElseThrow()));
            }
            context.reportUnsupported(ce.getName(), type.isPresent() ? HARD_LINK : fileType.kind());
        }
        return Optional.empty();
    }

    private static boolean isDatalessHardLink(CpioArchiveEntry ce) {
        boolean newc = ce.getFormat() == CpioConstants.FORMAT_NEW || ce.getFormat() == CpioConstants.FORMAT_NEW_CRC;
        return newc && ce.isRegularFile() && ce.getNumberOfLinks() > 1 && ce.getSize() == 0;
    }

    @Override
    public InputStream open(Entry entry) {
        return CloseShieldInputStream.wrap(archiveInputStream);
    }

    @Override
    public void close() throws IOException {
        archiveInputStream.close();
    }

    private Entry toEntry(CpioArchiveEntry ce, Entry.Type type) throws IOException {
        Entry base = new Entry(ce.getName(), type, (int) ce.getMode());
        Entry entry = type == Entry.Type.SYMLINK ? base.withLinkTarget(readLinkTarget(ce)) : base;
        return EntryValues.withMetadata(entry, ce, type == Entry.Type.FILE ? ce.getSize() : 0);
    }

    private String readLinkTarget(CpioArchiveEntry ce) throws IOException {
        return context.readLinkTarget(ce.getName(), archiveInputStream, ce.getSize());
    }
}
