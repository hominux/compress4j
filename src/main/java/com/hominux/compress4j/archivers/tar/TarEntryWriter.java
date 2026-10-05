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

import com.hominux.compress4j.internal.archive.EntryWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.io.IOUtils;

final class TarEntryWriter implements EntryWriter {

    private final TarArchiveOutputStream archiveOutputStream;

    TarEntryWriter(TarArchiveOutputStream archiveOutputStream) {
        this.archiveOutputStream = archiveOutputStream;
    }

    @Override
    public boolean requiresSize() {
        return true;
    }

    @Override
    public void writeDirectory(String name, int mode, FileTime lastModified) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(name + '/');
        e.setModTime(lastModified);
        if (mode != 0) {
            e.setMode(UnixStat.DIR_FLAG | mode);
        }
        archiveOutputStream.putArchiveEntry(e);
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    public void writeFile(String name, InputStream content, OptionalLong size, int mode, FileTime lastModified)
            throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(name);
        e.setSize(size.orElseThrow());
        e.setModTime(lastModified);
        if (mode != 0) {
            e.setMode(mode);
        }
        archiveOutputStream.putArchiveEntry(e);
        IOUtils.copy(content, archiveOutputStream);
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    public void writeSymlink(String name, String target, int mode, FileTime lastModified) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(name, TarConstants.LF_SYMLINK);
        e.setLinkName(target);
        e.setSize(0);
        e.setModTime(lastModified);
        if (mode != 0) {
            e.setMode(mode);
        }
        archiveOutputStream.putArchiveEntry(e);
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        archiveOutputStream.close();
    }
}
