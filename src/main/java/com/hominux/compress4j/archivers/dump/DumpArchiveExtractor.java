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
package com.hominux.compress4j.archivers.dump;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.archivers.Entry;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.dump.DumpArchiveEntry;
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream;

/**
 * Read-only: Compress4J provides no creator for this format.
 *
 * <p>Extracts UNIX dump archives. The unnamed root directory entry is skipped. Directory and regular file entries are
 * extracted; every other entry type is skipped and reported to the builder's {@code unsupportedEntryHandler}.
 *
 * @since 3.2
 */
public class DumpArchiveExtractor extends ArchiveExtractor<DumpArchiveInputStream> {

    /**
     * Create a new {@link DumpArchiveExtractor} with the given input stream.
     *
     * @param archiveInputStream the dump archive input stream
     */
    protected DumpArchiveExtractor(DumpArchiveInputStream archiveInputStream) {
        super(archiveInputStream);
    }

    /**
     * Create a new {@link DumpArchiveExtractor} with the given builder.
     *
     * @param builder the extractor builder
     * @throws IOException if an I/O error occurred
     */
    public DumpArchiveExtractor(DumpArchiveExtractorBuilder builder) throws IOException {
        super(builder);
    }

    /** {@inheritDoc} */
    @Override
    protected Optional<Entry> nextEntry() throws IOException {
        while (true) {
            DumpArchiveEntry entry = archiveInputStream.getNextEntry();
            if (entry == null) {
                return Optional.empty();
            }
            if (isUnnamedDirectory(entry)) {
                continue;
            }
            Optional<Entry.Type> type = typeOf(entry.getType());
            if (type.isPresent()) {
                return Optional.of(toEntry(entry, type.orElseThrow()));
            }
            reportUnsupported(entry.getName(), kindOf(entry.getType()));
        }
    }

    static Optional<Entry.Type> typeOf(DumpArchiveEntry.TYPE type) {
        return switch (type) {
            case FILE -> Optional.of(Entry.Type.FILE);
            case DIRECTORY -> Optional.of(Entry.Type.DIR);
            default -> Optional.empty();
        };
    }

    static String kindOf(DumpArchiveEntry.TYPE type) {
        return switch (type) {
            case LINK -> "symbolic link";
            case CHRDEV -> "character device";
            case BLKDEV -> "block device";
            case FIFO -> "fifo";
            case SOCKET -> "socket";
            case WHITEOUT -> "whiteout";
            default -> "unknown type";
        };
    }

    /** {@inheritDoc} */
    @Override
    protected InputStream openEntryStream(Entry entry) {
        return archiveInputStream;
    }

    private static boolean isUnnamedDirectory(DumpArchiveEntry entry) {
        return entry.getName().isEmpty() && entry.getType() == DumpArchiveEntry.TYPE.DIRECTORY;
    }

    private static Entry toEntry(DumpArchiveEntry entry, Entry.Type type) {
        return new Entry(entry.getName(), type, entry.getMode())
                .withMetadata(entry.getLastModifiedDate(), type == Entry.Type.FILE ? entry.getSize() : 0);
    }

    /**
     * Creates a builder reading the archive at the given path.
     *
     * @param path the path to the archive
     * @return a new {@link DumpArchiveExtractorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static DumpArchiveExtractorBuilder builder(Path path) throws IOException {
        return new DumpArchiveExtractorBuilder(path);
    }

    /**
     * Creates a builder reading the archive at the given file.
     *
     * @param file the archive file
     * @return a new {@link DumpArchiveExtractorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static DumpArchiveExtractorBuilder builder(File file) throws IOException {
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
    public static DumpArchiveExtractorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newInputStream(channel));
    }

    /**
     * Creates a builder reading the archive from the given stream.
     *
     * @param inputStream the archive stream
     * @return a new {@link DumpArchiveExtractorBuilder}
     */
    public static DumpArchiveExtractorBuilder builder(InputStream inputStream) {
        return new DumpArchiveExtractorBuilder(inputStream);
    }

    /** Builder for creating a {@link DumpArchiveExtractor}. */
    public static class DumpArchiveExtractorBuilder
            extends ArchiveExtractorBuilder<DumpArchiveInputStream, DumpArchiveExtractorBuilder, DumpArchiveExtractor> {

        private final InputStream inputStream;
        private String encoding = "UTF-8";

        /**
         * Create a new builder reading the archive at the given path.
         *
         * @param path the path to the archive
         * @throws IOException if an I/O error occurred
         */
        public DumpArchiveExtractorBuilder(Path path) throws IOException {
            this(Files.newInputStream(path), true);
        }

        /**
         * Create a new builder reading the archive from the given stream.
         *
         * @param inputStream the archive stream
         */
        public DumpArchiveExtractorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        private DumpArchiveExtractorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStream = inputStream;
        }

        /**
         * Sets the character encoding of entry names.
         *
         * @param encoding the encoding name
         * @return this builder
         */
        public DumpArchiveExtractorBuilder encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public DumpArchiveExtractorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public DumpArchiveInputStream buildArchiveInputStream() throws IOException {
            try {
                return new DumpArchiveInputStream(inputStream, encoding);
            } catch (ArchiveException e) {
                throw new IOException(e.getMessage(), e);
            }
        }

        /** {@inheritDoc} */
        @Override
        public DumpArchiveExtractor build() throws IOException {
            return new DumpArchiveExtractor(this);
        }
    }
}
