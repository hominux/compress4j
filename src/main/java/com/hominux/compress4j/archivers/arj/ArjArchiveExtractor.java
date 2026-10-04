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
package com.hominux.compress4j.archivers.arj;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.arj.ArjArchiveEntry;
import org.apache.commons.compress.archivers.arj.ArjArchiveInputStream;

/**
 * Read-only: Compress4J provides no creator for this format.
 *
 * <p>Extracts ARJ archives. Directory and regular file entries are extracted; entries that are Unix symbolic links, or
 * whose data cannot be read (encrypted or using an unsupported method), fail with an {@link IOException} that the error
 * handler receives. Such entries are listed as regular files; their real type is visible in the Unix file type bits of
 * {@link Entry#mode()}.
 *
 * @since 3.2
 */
public class ArjArchiveExtractor extends ArchiveExtractor<ArjArchiveInputStream> {
    private static final int UNIX_FILE_TYPE_MASK = 0170000;
    private static final int UNIX_SYMLINK = 0120000;

    private Optional<ArjArchiveEntry> current = Optional.empty();

    /**
     * Create a new {@link ArjArchiveExtractor} with the given input stream.
     *
     * @param archiveInputStream the ARJ archive input stream
     */
    protected ArjArchiveExtractor(ArjArchiveInputStream archiveInputStream) {
        super(archiveInputStream);
    }

    /**
     * Create a new {@link ArjArchiveExtractor} with the given builder.
     *
     * @param builder the extractor builder
     * @throws IOException if an I/O error occurred
     */
    public ArjArchiveExtractor(ArjArchiveExtractorBuilder builder) throws IOException {
        super(builder);
    }

    /** {@inheritDoc} */
    @Override
    protected Optional<Entry> nextEntry() throws IOException {
        current = Optional.ofNullable(archiveInputStream.getNextEntry());
        return current.map(ArjArchiveExtractor::toEntry);
    }

    /** {@inheritDoc} */
    @Override
    protected InputStream openEntryStream(Entry entry) throws IOException {
        var arjEntry = current.orElseThrow(() -> new IOException("No current ARJ entry"));
        if (isSymlink(arjEntry)) {
            throw new IOException("Unsupported ARJ entry type: symlink: " + arjEntry.getName());
        }
        if (!archiveInputStream.canReadEntryData(arjEntry)) {
            throw new IOException(
                    "Cannot read ARJ entry data (encrypted or unsupported method): " + arjEntry.getName());
        }
        return archiveInputStream;
    }

    private static Entry toEntry(ArjArchiveEntry entry) {
        var type = entry.isDirectory() ? Entry.Type.DIR : Entry.Type.FILE;
        var mode = entry.isHostOsUnix() ? entry.getUnixMode() : 0;
        return new Entry(entry.getName(), type, mode)
                .withMetadata(entry.getLastModifiedDate(), type == Entry.Type.FILE ? entry.getSize() : 0);
    }

    private static boolean isSymlink(ArjArchiveEntry entry) {
        return entry.isHostOsUnix() && (entry.getUnixMode() & UNIX_FILE_TYPE_MASK) == UNIX_SYMLINK;
    }

    /**
     * Creates a builder reading the archive at the given path.
     *
     * @param path the path to the archive
     * @return a new {@link ArjArchiveExtractorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static ArjArchiveExtractorBuilder builder(Path path) throws IOException {
        return new ArjArchiveExtractorBuilder(path);
    }

    /**
     * Creates a builder reading the archive at the given file.
     *
     * @param file the archive file
     * @return a new {@link ArjArchiveExtractorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static ArjArchiveExtractorBuilder builder(File file) throws IOException {
        return builder(file.toPath());
    }

    /**
     * Creates a builder reading from the channel's current position. The extractor closes the channel when it is
     * closed; a failed {@code build()} leaves it open.
     *
     * @param channel the channel holding the archive
     * @return the builder
     * @since 5.0
     */
    public static ArjArchiveExtractorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newInputStream(channel));
    }

    /**
     * Creates a builder reading the archive from the given stream.
     *
     * @param inputStream the archive stream
     * @return a new {@link ArjArchiveExtractorBuilder}
     */
    public static ArjArchiveExtractorBuilder builder(InputStream inputStream) {
        return new ArjArchiveExtractorBuilder(inputStream);
    }

    /** Builder for creating an {@link ArjArchiveExtractor}. */
    public static class ArjArchiveExtractorBuilder
            extends ArchiveExtractorBuilder<ArjArchiveInputStream, ArjArchiveExtractorBuilder, ArjArchiveExtractor> {

        private final InputStream inputStream;
        private String encoding = "UTF-8";

        /**
         * Create a new builder reading the archive at the given path.
         *
         * @param path the path to the archive
         * @throws IOException if an I/O error occurred
         */
        public ArjArchiveExtractorBuilder(Path path) throws IOException {
            this(Files.newInputStream(path), true);
        }

        /**
         * Create a new builder reading the archive from the given stream.
         *
         * @param inputStream the archive stream
         */
        public ArjArchiveExtractorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        private ArjArchiveExtractorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStream = inputStream;
        }

        /**
         * Sets the character encoding of entry names.
         *
         * @param encoding the encoding name
         * @return this builder
         */
        public ArjArchiveExtractorBuilder encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public ArjArchiveExtractorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public ArjArchiveInputStream buildArchiveInputStream() throws IOException {
            try {
                return new ArjArchiveInputStream(inputStream, encoding);
            } catch (ArchiveException e) {
                throw new IOException(e.getMessage(), e);
            }
        }

        /** {@inheritDoc} */
        @Override
        public ArjArchiveExtractor build() throws IOException {
            return new ArjArchiveExtractor(this);
        }
    }
}
