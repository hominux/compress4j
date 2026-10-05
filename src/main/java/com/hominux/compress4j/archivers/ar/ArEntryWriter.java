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

import com.hominux.compress4j.internal.archive.EntryWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.io.IOUtils;

final class ArEntryWriter implements EntryWriter {

    @SuppressWarnings("OctalInteger")
    static final int S_IFLNK = 0120000;

    private final ArArchiveOutputStream archiveOutputStream;

    ArEntryWriter(ArArchiveOutputStream archiveOutputStream) {
        this.archiveOutputStream = archiveOutputStream;
    }

    @Override
    public boolean requiresSize() {
        return true;
    }

    @Override
    public void writeDirectory(String name, int mode, FileTime lastModified) {
        // AR has no directory entries.
    }

    @Override
    public void writeFile(String name, InputStream content, OptionalLong size, int mode, FileTime lastModified)
            throws IOException {
        writeEntry(name, content, size.orElseThrow(), lastModified, mode);
    }

    @Override
    @SuppressWarnings("OctalInteger")
    public void writeSymlink(String name, String target, int mode, FileTime lastModified) throws IOException {
        byte[] bytes = target.getBytes(StandardCharsets.UTF_8);
        writeEntry(name, new ByteArrayInputStream(bytes), bytes.length, lastModified, S_IFLNK | (mode & 0777));
    }

    private void writeEntry(String name, InputStream data, long length, FileTime modTime, int mode) throws IOException {
        archiveOutputStream.putArchiveEntry(new ArArchiveEntry(name, length, 0, 0, mode, modTime.toMillis() / 1000));
        if (length > 0) {
            IOUtils.copy(data, archiveOutputStream);
        }
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        archiveOutputStream.close();
    }
}
