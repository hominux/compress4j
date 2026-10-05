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

import com.hominux.compress4j.internal.archive.EntryWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.apache.commons.io.IOUtils;

@SuppressWarnings("OctalInteger")
final class CpioEntryWriter implements EntryWriter {

    private final CpioArchiveOutputStream archiveOutputStream;
    private final short format;

    CpioEntryWriter(CpioArchiveOutputStream archiveOutputStream, short format) {
        this.archiveOutputStream = archiveOutputStream;
        this.format = format;
    }

    @Override
    public boolean requiresSize() {
        return true;
    }

    @Override
    public void writeDirectory(String name, int mode, FileTime lastModified) throws IOException {
        String directoryName = name.endsWith("/") ? name : name + "/";
        CpioArchiveEntry entry = newEntry(directoryName, 0, lastModified);
        entry.setMode(CpioConstants.C_ISDIR | (mode != 0 ? mode : 0755));
        archiveOutputStream.putArchiveEntry(entry);
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    public void writeFile(String name, InputStream content, OptionalLong size, int mode, FileTime lastModified)
            throws IOException {
        long length = size.orElseThrow();
        CpioArchiveEntry entry = newEntry(name, length, lastModified);
        setRegularMode(entry, mode);
        archiveOutputStream.putArchiveEntry(entry);
        if (length > 0) {
            IOUtils.copy(content, archiveOutputStream);
        }
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    public void writeSymlink(String name, String target, int mode, FileTime lastModified) throws IOException {
        byte[] bytes = target.getBytes(StandardCharsets.UTF_8);
        CpioArchiveEntry entry = newEntry(name, bytes.length, lastModified);
        entry.setMode(CpioConstants.C_ISLNK | (mode != 0 ? mode : 0777));
        archiveOutputStream.putArchiveEntry(entry);
        archiveOutputStream.write(bytes);
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        archiveOutputStream.close();
    }

    private CpioArchiveEntry newEntry(String name, long length, FileTime modTime) {
        CpioArchiveEntry entry;
        if (format != CpioConstants.FORMAT_NEW) {
            entry = new CpioArchiveEntry(format, name, length);
        } else {
            entry = new CpioArchiveEntry(name);
            entry.setSize(length);
        }
        entry.setTime(modTime.toMillis() / 1000L);
        return entry;
    }

    private static void setRegularMode(CpioArchiveEntry entry, int mode) {
        if (mode == 0) {
            entry.setMode(CpioConstants.C_ISREG | 0644);
            return;
        }
        try {
            entry.setMode(CpioConstants.C_ISREG | (mode & 0777));
        } catch (IllegalArgumentException e) {
            entry.setMode(CpioConstants.C_ISREG | 0644);
        }
    }
}
