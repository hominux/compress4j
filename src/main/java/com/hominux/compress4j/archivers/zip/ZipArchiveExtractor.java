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
import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.io.ParserFailures;
import com.hominux.compress4j.internal.io.Source;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.function.IOFunction;

/**
 * Reads zip archives through their central directory, or local headers from a forward-only stream.
 *
 * <p>Supported entries are regular files, directories and symbolic links; every other Unix file type is reported
 * through the unsupported-entry handler and skipped.
 *
 * @since 2.2
 */
public final class ZipArchiveExtractor extends ArchiveExtractor {

    private ZipArchiveExtractor(ArchiveExtractor.Builder<?, ?> builder, EntryReader reader, LongSupplier compressed) {
        super(builder, reader, compressed);
    }

    /**
     * Creates a builder reading the archive at the given path.
     *
     * @param path the path to the archive to extract
     * @return the builder
     */
    public static Builder builder(Path path) {
        return new Builder(new Source.OfPath(path));
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
    public static Builder builder(SeekableByteChannel channel) {
        return new Builder(new Source.OfChannel(channel));
    }

    /**
     * Creates a builder reading local headers from a forward-only stream. The extractor closes the stream when it is
     * closed; a failed {@code build()} leaves it open. See {@link StreamingBuilder} for what streaming mode loses.
     *
     * @param inputStream the stream holding the archive
     * @return the builder
     * @since 5.0
     */
    public static StreamingBuilder streaming(InputStream inputStream) {
        return new StreamingBuilder(new Source.OfStream(inputStream));
    }

    /**
     * Builder for {@link ZipArchiveExtractor} reading through the central directory.
     *
     * @since 5.0
     */
    public static final class Builder extends ArchiveExtractor.Builder<Builder, ZipArchiveExtractor> {

        private final Source.Seekable source;
        private boolean useUnicodeExtraFields = true;
        private boolean ignoreLocalFileHeader;
        private long maxNumberOfDisks = 1;
        private Optional<IOFunction<InputStream, InputStream>> zstdInputStreamFactory = Optional.empty();

        private Builder(Source.Seekable source) {
            this.source = source;
        }

        /**
         * Sets whether to ignore the information stored in local file headers. Defaults to {@code false}.
         *
         * @param ignoreLocalFileHeader whether to ignore the local file headers
         * @return this builder
         * @since 5.0
         */
        public Builder ignoreLocalFileHeader(boolean ignoreLocalFileHeader) {
            this.ignoreLocalFileHeader = ignoreLocalFileHeader;
            return this;
        }

        /**
         * Sets the maximum number of disks of a multi-disk archive. Defaults to 1, which accepts no multi-disk archive.
         *
         * @param maxNumberOfDisks the maximum number of disks
         * @return this builder
         * @since 5.0
         */
        public Builder maxNumberOfDisks(long maxNumberOfDisks) {
            this.maxNumberOfDisks = maxNumberOfDisks;
            return this;
        }

        /**
         * Sets whether InfoZIP Unicode extra fields, when present, set the entry names. Defaults to {@code true}.
         *
         * @param useUnicodeExtraFields whether to use the Unicode extra fields
         * @return this builder
         * @since 5.0
         */
        public Builder useUnicodeExtraFields(boolean useUnicodeExtraFields) {
            this.useUnicodeExtraFields = useUnicodeExtraFields;
            return this;
        }

        /**
         * Sets the factory for the stream that decompresses Zstandard entries, to plug in an alternate implementation.
         * Defaults to the commons-compress Zstandard stream.
         *
         * @param factory wraps the compressed entry content in a decompressing stream
         * @return this builder
         * @throws NullPointerException if the factory is null
         * @since 5.0
         */
        public Builder zstdInputStreamFactory(IOFunction<InputStream, InputStream> factory) {
            this.zstdInputStreamFactory = Optional.of(Objects.requireNonNull(factory, "zstdInputStreamFactory"));
            return this;
        }

        /** {@inheritDoc} */
        @Override
        protected Builder getThis() {
            return this;
        }

        /**
         * {@inheritDoc}
         *
         * @throws IOException if the archive cannot be opened or its central directory cannot be read
         */
        @Override
        public ZipArchiveExtractor build() throws IOException {
            Source.OpenedChannel opened = source.openChannel();
            try {
                ZipFile file = ParserFailures.call(() -> openFile(opened.channel()), ParserFailures.ARCHIVE);
                opened.built();
                EntryReader reader = new ZipEntryReader(file, readerContext());
                return new ZipArchiveExtractor(this, reader, opened.channel()::count);
            } catch (IOException e) {
                throw opened.closeIfOwned(e);
            } catch (RuntimeException e) {
                throw opened.closeIfOwned(e);
            }
        }

        private ZipFile openFile(SeekableByteChannel channel) throws IOException {
            ZipFile.Builder zip = ZipFile.builder()
                    .setIgnoreLocalFileHeader(ignoreLocalFileHeader)
                    .setMaxNumberOfDisks(maxNumberOfDisks)
                    .setUseUnicodeExtraFields(useUnicodeExtraFields)
                    .setSeekableByteChannel(channel);
            zstdInputStreamFactory.ifPresent(zip::setZstdInputStreamFactory);
            return zip.get();
        }
    }

    /**
     * Builder for {@link ZipArchiveExtractor} reading local headers from a forward-only stream.
     *
     * <p>A stream carries no central directory, where zip keeps Unix modes: entries report mode 0, and a symlink entry
     * surfaces as a {@link Entry.Type#FILE} whose content is the link target. Stored entries with a data descriptor are
     * supported. An entry's size may be empty until its content is read. Input that is not a zip archive fails on the
     * first read, not in {@code build()}; input too short to hold a signature reports no entries. Use
     * {@link #builder(Path)} or {@link #builder(SeekableByteChannel)} to keep modes and symlinks.
     *
     * @since 5.0
     */
    public static final class StreamingBuilder extends ArchiveExtractor.Builder<StreamingBuilder, ZipArchiveExtractor> {

        private final Source source;

        private StreamingBuilder(Source source) {
            this.source = source;
        }

        /** {@inheritDoc} */
        @Override
        protected StreamingBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public ZipArchiveExtractor build() throws IOException {
            Source.Opened opened = source.open();
            ZipArchiveInputStream zip =
                    new ZipArchiveInputStream(opened.in(), StandardCharsets.UTF_8.name(), true, true);
            return new ZipArchiveExtractor(this, new ZipStreamingEntryReader(zip, readerContext()), opened.in()::count);
        }
    }
}
