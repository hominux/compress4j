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

import com.hominux.compress4j.internal.archive.EntryWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;

final class SevenZEntryWriter implements EntryWriter {

    private static final int DOS_DIRECTORY = 0x10;
    private static final int DEFAULT_SYMLINK_PERMISSIONS = 0777;

    private final SevenZOutputFile file;

    SevenZEntryWriter(SevenZOutputFile file) {
        this.file = file;
    }

    @Override
    public boolean requiresSize() {
        return false;
    }

    @Override
    public void writeDirectory(String name, int mode, FileTime lastModified) throws IOException {
        SevenZArchiveEntry entry = newEntry(name, lastModified);
        entry.setDirectory(true);
        withWindowsAttributes(entry, DOS_DIRECTORY | (mode != 0 ? SevenZEntryReader.UNIX_EXTENSION | (mode << 16) : 0));
        file.putArchiveEntry(entry);
        file.closeArchiveEntry();
    }

    @Override
    public void writeFile(String name, InputStream content, OptionalLong size, int mode, FileTime lastModified)
            throws IOException {
        SevenZArchiveEntry entry = newEntry(name, lastModified);
        size.ifPresent(entry::setSize);
        if (mode != 0) {
            withWindowsAttributes(entry, SevenZEntryReader.UNIX_EXTENSION | (mode << 16));
        }
        file.putArchiveEntry(entry);
        file.write(content);
        file.closeArchiveEntry();
    }

    @Override
    public void writeSymlink(String name, String target, int mode, FileTime lastModified) throws IOException {
        byte[] bytes = target.getBytes(StandardCharsets.UTF_8);
        SevenZArchiveEntry entry = newEntry(name, lastModified);
        entry.setSize(bytes.length);
        int permissions = mode != 0 ? mode : DEFAULT_SYMLINK_PERMISSIONS;
        withWindowsAttributes(
                entry, SevenZEntryReader.UNIX_EXTENSION | ((SevenZEntryReader.S_IFLNK | permissions) << 16));
        file.putArchiveEntry(entry);
        file.write(bytes);
        file.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    private static void withWindowsAttributes(SevenZArchiveEntry entry, int attributes) {
        entry.setHasWindowsAttributes(true);
        entry.setWindowsAttributes(attributes);
    }

    private static SevenZArchiveEntry newEntry(String name, FileTime modTime) {
        SevenZArchiveEntry entry = new SevenZArchiveEntry();
        entry.setName(name);
        entry.setLastModifiedTime(modTime);
        return entry;
    }
}
