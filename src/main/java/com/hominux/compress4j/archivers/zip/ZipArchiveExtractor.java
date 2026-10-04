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
package com.hominux.compress4j.archivers.zip;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.utils.BuildGatedChannel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.io.function.IOFunction;

/**
 * Zip Archive Extractor
 *
 * @since 2.2
 */
public class ZipArchiveExtractor extends ArchiveExtractor<ArchiveInputStream<ZipArchiveEntry>> {

    private final IOFunction<ZipArchiveEntry, String> symlinkTarget;

    private ZipArchiveExtractor(ZipArchiveExtractorBuilder builder) throws IOException {
        super(builder);
        ZipFileArchiveInputStream zip = (ZipFileArchiveInputStream) archiveInputStream;
        this.symlinkTarget = zip::getUnixSymlink;
    }

    private ZipArchiveExtractor(ZipStreamingExtractorBuilder builder) throws IOException {
        super(builder);
        this.symlinkTarget = entry -> null;
    }

    /**
     * Helper static method to create an instance of the {@link ZipArchiveExtractorBuilder}
     *
     * @param path the path to the archive to extract
     * @return An instance of the {@link ZipArchiveExtractorBuilder}
     */
    public static ZipArchiveExtractorBuilder builder(Path path) {
        return new ZipArchiveExtractorBuilder(Optional.of(path), Optional.empty());
    }

    /**
     * Creates a builder reading the whole channel, whatever its current position; zip keeps its central directory at
     * the end of the archive. The extractor closes the channel when it is closed; a failed {@code build()} leaves it
     * open.
     *
     * @param channel the channel holding the archive
     * @return the builder
     * @since 5.0
     */
    public static ZipArchiveExtractorBuilder builder(SeekableByteChannel channel) {
        return new ZipArchiveExtractorBuilder(Optional.empty(), Optional.of(channel));
    }

    /**
     * Creates a builder reading local headers from a forward-only stream.
     *
     * <p>A stream carries no central directory, where zip keeps Unix modes: entries report mode 0, and a symlink entry
     * surfaces as a {@link Entry.Type#FILE} whose content is the link target. An entry's size may be empty until its
     * content is read. Input that is not a zip archive fails on the first read, not in {@code build()}.
     *
     * @param inputStream the stream holding the archive
     * @return the builder
     * @since 5.0
     */
    public static ZipStreamingExtractorBuilder streaming(InputStream inputStream) {
        return new ZipStreamingExtractorBuilder(inputStream);
    }

    /** {@inheritDoc} */
    @Override
    protected Optional<Entry> nextEntry() throws IOException {
        ZipArchiveEntry ze = archiveInputStream.getNextEntry();
        if (ze == null) {
            return Optional.empty();
        }
        Entry.Type type = type(ze);
        Entry entry = new Entry(ze.getName(), type, ze.getUnixMode())
                .withLinkTarget(symlinkTarget.apply(ze))
                .withMetadata(ze.getLastModifiedDate(), type == Entry.Type.FILE ? ze.getSize() : 0);
        return Optional.of(entry);
    }

    private static Entry.Type type(ZipArchiveEntry ze) {
        if (ze.isUnixSymlink()) {
            return Entry.Type.SYMLINK;
        } else if (ze.isDirectory()) {
            return Entry.Type.DIR;
        } else {
            return Entry.Type.FILE;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected InputStream openEntryStream(Entry entry) {
        return archiveInputStream;
    }

    /**
     * Builder for creating a {@link ZipArchiveExtractor}.
     *
     * @since 2.2
     */
    public static class ZipArchiveExtractorBuilder
            extends ArchiveExtractorBuilder<
                    ArchiveInputStream<ZipArchiveEntry>, ZipArchiveExtractorBuilder, ZipArchiveExtractor> {

        private boolean useUnicodeExtraFields = true;
        private boolean ignoreLocalFileHeader;
        private long maxNumberOfDisks = 1;
        private IOFunction<InputStream, InputStream> zstdInputStreamFactory;

        private final Optional<Path> path;
        private final Optional<SeekableByteChannel> channel;

        ZipArchiveExtractorBuilder(Optional<Path> path, Optional<SeekableByteChannel> channel) {
            this.path = path;
            this.channel = channel;
        }

        /**
         * Sets whether to ignore information stored inside the local file header.
         *
         * @param ignoreLocalFileHeader whether to ignore information stored inside.
         * @return {@code this} instance.
         */
        public ZipArchiveExtractorBuilder setIgnoreLocalFileHeader(final boolean ignoreLocalFileHeader) {
            this.ignoreLocalFileHeader = ignoreLocalFileHeader;
            return this;
        }

        /**
         * Sets max number of multi archive disks, default is 1 (no multi archive).
         *
         * @param maxNumberOfDisks max number of multi archive disks.
         * @return {@code this} instance.
         */
        public ZipArchiveExtractorBuilder setMaxNumberOfDisks(final long maxNumberOfDisks) {
            this.maxNumberOfDisks = maxNumberOfDisks;
            return this;
        }

        /**
         * Sets whether to use InfoZIP Unicode Extra Fields (if present) to set the file names.
         *
         * @param useUnicodeExtraFields whether to use InfoZIP Unicode Extra Fields (if present) to set the file names.
         * @return {@code this} instance.
         */
        public ZipArchiveExtractorBuilder setUseUnicodeExtraFields(final boolean useUnicodeExtraFields) {
            this.useUnicodeExtraFields = useUnicodeExtraFields;
            return this;
        }

        /**
         * Sets the factory {@link IOFunction} to create a Zstd {@link InputStream}. Defaults to
         * {@link ZstdCompressorInputStream#ZstdCompressorInputStream(InputStream)}.
         *
         * <p>Call this method to plugin an alternate Zstd input stream implementation.
         *
         * @param zstdInpStreamFactory the factory {@link IOFunction} to create a Zstd {@link InputStream}; {@code null}
         *     resets to the default.
         * @return {@code this} instance.
         */
        public ZipArchiveExtractorBuilder setZstdInputStreamFactory(
                final IOFunction<InputStream, InputStream> zstdInpStreamFactory) {
            this.zstdInputStreamFactory = zstdInpStreamFactory;
            return this;
        }

        @Override
        public ZipArchiveExtractorBuilder getThis() {
            return this;
        }

        /**
         * Opens the zip archive.
         *
         * @return the entries of the archive, read through its central directory
         */
        @Override
        public ArchiveInputStream<ZipArchiveEntry> buildArchiveInputStream() throws IOException {
            var zip = ZipFile.builder()
                    .setIgnoreLocalFileHeader(ignoreLocalFileHeader)
                    .setMaxNumberOfDisks(maxNumberOfDisks)
                    .setUseUnicodeExtraFields(useUnicodeExtraFields)
                    .setZstdInputStreamFactory(zstdInputStreamFactory);
            if (channel.isEmpty()) {
                return new ZipFileArchiveInputStream(
                        zip.setPath(path.orElseThrow()).get());
            }
            var gated = new BuildGatedChannel(channel.orElseThrow());
            var stream = new ZipFileArchiveInputStream(
                    zip.setSeekableByteChannel(gated).get());
            gated.built();
            return stream;
        }

        /**
         * Build the ZipArchiveExtractor.
         *
         * @return the configured ZipArchiveExtractor
         */
        public ZipArchiveExtractor build() throws IOException {
            return new ZipArchiveExtractor(this);
        }
    }

    /**
     * Builds a zip extractor that reads local headers from a forward-only stream.
     *
     * <p>A stream carries no central directory, where zip keeps Unix modes: entries report mode 0, and a symlink entry
     * surfaces as a {@link Entry.Type#FILE} whose content is the link target. Stored entries with a data descriptor are
     * supported. Use {@link #builder(Path)} or {@link #builder(SeekableByteChannel)} to keep modes and symlinks.
     *
     * @since 5.0
     */
    public static final class ZipStreamingExtractorBuilder
            extends ArchiveExtractorBuilder<
                    ArchiveInputStream<ZipArchiveEntry>, ZipStreamingExtractorBuilder, ZipArchiveExtractor> {

        private final InputStream inputStream;

        private ZipStreamingExtractorBuilder(InputStream inputStream) {
            super(inputStream, false);
            this.inputStream = inputStream;
        }

        /** {@inheritDoc} */
        @Override
        protected ZipStreamingExtractorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public ArchiveInputStream<ZipArchiveEntry> buildArchiveInputStream() {
            return new ZipArchiveInputStream(inputStream, StandardCharsets.UTF_8.name(), true, true);
        }

        /** {@inheritDoc} */
        @Override
        public ZipArchiveExtractor build() throws IOException {
            return new ZipArchiveExtractor(this);
        }
    }
}
