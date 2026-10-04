/*
 * Copyright 2024-2026 The Compress4J Project
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

import static com.hominux.compress4j.utils.FileUtils.NO_MODE;

import com.hominux.compress4j.archivers.ArchiveCreator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.function.IOSupplier;

/**
 * The 7z archive creator. A symbolic link is stored the way p7zip stores it: a Unix-mode entry whose content is the
 * link target.
 *
 * @since 3.2
 */
public class SevenZArchiveCreator extends ArchiveCreator<SevenZFileArchiveOutputStream> {

    private static final int DOS_DIRECTORY = 0x10;

    /**
     * Create a new SevenZArchiveCreator with the given output stream.
     *
     * @param archiveOutputStream the output 7z Archive Output Stream
     */
    public SevenZArchiveCreator(SevenZFileArchiveOutputStream archiveOutputStream) {
        super(archiveOutputStream);
    }

    /**
     * Create a new SevenZArchiveCreator with the given builder.
     *
     * @param builder the archive output stream builder
     * @throws IOException if an I/O error occurred
     */
    public SevenZArchiveCreator(SevenZArchiveCreatorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Helper static method to create an instance of the {@link SevenZArchiveCreatorBuilder}.
     *
     * @param path the path to write the archive to
     * @return An instance of the {@link SevenZArchiveCreatorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static SevenZArchiveCreatorBuilder builder(Path path) throws IOException {
        return new SevenZArchiveCreatorBuilder(path);
    }

    /**
     * Creates a builder writing to the channel from absolute offset 0, whatever its current position, overwriting any
     * bytes already there; 7z rewrites its start header on close, so the channel must be seekable. The creator closes
     * the channel when it is closed; a failed {@code build()} leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static SevenZArchiveCreatorBuilder builder(SeekableByteChannel channel) {
        return new SevenZArchiveCreatorBuilder(() -> new SevenZOutputFile(channel));
    }

    /** {@inheritDoc} */
    @Override
    protected boolean requiresSize() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected void writeDirectory(String name, int mode, FileTime lastModified) throws IOException {
        SevenZArchiveEntry entry = newEntry(name, lastModified);
        entry.setDirectory(true);
        withWindowsAttributes(
                entry, DOS_DIRECTORY | (mode != NO_MODE ? SevenZArchiveExtractor.UNIX_EXTENSION | (mode << 16) : 0));
        archiveOutputStream.putArchiveEntry(entry);
        archiveOutputStream.closeArchiveEntry();
    }

    /** {@inheritDoc} */
    @Override
    protected void writeFile(String name, InputStream content, OptionalLong size, int mode, FileTime lastModified)
            throws IOException {
        SevenZArchiveEntry entry = newEntry(name, lastModified);
        size.ifPresent(entry::setSize);
        if (mode != NO_MODE) {
            withWindowsAttributes(entry, SevenZArchiveExtractor.UNIX_EXTENSION | (mode << 16));
        }
        archiveOutputStream.putArchiveEntry(entry);
        IOUtils.copy(content, archiveOutputStream);
        archiveOutputStream.closeArchiveEntry();
    }

    /** {@inheritDoc} */
    @Override
    protected void writeSymlink(String name, String target, int mode, FileTime lastModified) throws IOException {
        byte[] bytes = target.getBytes(StandardCharsets.UTF_8);
        SevenZArchiveEntry entry = newEntry(name, lastModified);
        entry.setSize(bytes.length);
        withWindowsAttributes(
                entry,
                SevenZArchiveExtractor.UNIX_EXTENSION
                        | ((SevenZArchiveExtractor.S_IFLNK | (mode != NO_MODE ? mode : 0777)) << 16));
        archiveOutputStream.putArchiveEntry(entry);
        archiveOutputStream.write(bytes);
        archiveOutputStream.closeArchiveEntry();
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

    /** 7z creator builder. */
    public static class SevenZArchiveCreatorBuilder
            extends ArchiveCreatorBuilder<
                    SevenZFileArchiveOutputStream, SevenZArchiveCreatorBuilder, SevenZArchiveCreator> {

        private final IOSupplier<SevenZOutputFile> file;

        /**
         * Create a new builder that writes to the given path. {@code build()} creates the file.
         *
         * <p>7z needs a seekable target to patch its header; for a channel, use {@code builder(SeekableByteChannel)}.
         *
         * @param path the path to write the archive to
         * @throws IOException never thrown; declared for compatibility with 4.x
         */
        public SevenZArchiveCreatorBuilder(Path path) throws IOException {
            this(() -> new SevenZOutputFile(path.toFile()));
        }

        private SevenZArchiveCreatorBuilder(IOSupplier<SevenZOutputFile> file) {
            super(OutputStream.nullOutputStream());
            this.file = file;
        }

        @Override
        protected SevenZArchiveCreatorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public SevenZFileArchiveOutputStream buildArchiveOutputStream() throws IOException {
            return new SevenZFileArchiveOutputStream(file.get());
        }

        /** {@inheritDoc} */
        @Override
        public SevenZArchiveCreator build() throws IOException {
            return new SevenZArchiveCreator(this);
        }
    }
}
