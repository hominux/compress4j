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

import com.hominux.compress4j.internal.archive.EntryWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;

final class ZipEntryWriter implements EntryWriter {

    private static final int DEFAULT_SYMLINK_PERMISSIONS = 0777;

    private final ZipArchiveOutputStream archiveOutputStream;

    ZipEntryWriter(ZipArchiveOutputStream archiveOutputStream) {
        this.archiveOutputStream = archiveOutputStream;
    }

    @Override
    public boolean requiresSize() {
        return false;
    }

    @Override
    public void writeDirectory(String name, int mode, FileTime lastModified) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name + '/');
        entry.setTime(lastModified);
        if (mode != 0) {
            entry.setUnixMode(UnixStat.DIR_FLAG | mode);
        }
        archiveOutputStream.putArchiveEntry(entry);
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    public void writeFile(String name, InputStream content, OptionalLong size, int mode, FileTime lastModified)
            throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setTime(lastModified);
        size.ifPresent(entry::setSize);
        if (mode != 0) {
            entry.setUnixMode(UnixStat.FILE_FLAG | mode);
        }
        archiveOutputStream.putArchiveEntry(entry);
        IOUtils.copy(content, archiveOutputStream);
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    public void writeSymlink(String name, String target, int mode, FileTime lastModified) throws IOException {
        byte[] bytes = target.getBytes(StandardCharsets.UTF_8);
        int permissions = mode == 0 ? DEFAULT_SYMLINK_PERMISSIONS : mode & UnixStat.PERM_MASK;
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setTime(lastModified);
        entry.setSize(bytes.length);
        entry.setUnixMode(UnixStat.LINK_FLAG | permissions);
        archiveOutputStream.putArchiveEntry(entry);
        archiveOutputStream.write(bytes);
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        archiveOutputStream.close();
    }
}
