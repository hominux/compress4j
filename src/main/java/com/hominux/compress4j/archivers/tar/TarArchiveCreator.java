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

import com.hominux.compress4j.archivers.ArchiveCreator;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.internal.archive.EntryWriter;
import com.hominux.compress4j.internal.codec.Codecs;
import com.hominux.compress4j.internal.io.Sink;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

/**
 * Writes tar archives, plain or compressed with any codec {@link Compression} can write except Pack200.
 *
 * @since 2.2
 */
public final class TarArchiveCreator extends ArchiveCreator {

    private TarArchiveCreator(Builder builder, EntryWriter writer) {
        super(builder, writer);
    }

    /**
     * Creates a builder writing the archive to the given path.
     *
     * @param path the path to write the archive to
     * @return the builder
     */
    public static Builder builder(Path path) {
        return new Builder(new Sink.OfPath(path));
    }

    /**
     * Creates a builder writing at the channel's current position. The creator closes the channel when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static Builder builder(SeekableByteChannel channel) {
        return new Builder(new Sink.OfChannel(channel));
    }

    /**
     * Creates a builder writing the archive to the given stream. The creator closes the stream when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param outputStream the output stream to write the archive to
     * @return the builder
     */
    public static Builder builder(OutputStream outputStream) {
        return new Builder(new Sink.OfStream(outputStream));
    }

    /**
     * Builder for {@link TarArchiveCreator}; writes uncompressed tar unless a compression is set.
     *
     * @since 5.0
     */
    public static final class Builder extends ArchiveCreator.Builder<Builder, TarArchiveCreator> {
        private static final int DEFAULT_BLOCK_SIZE = -511;

        private Compression compression = Compression.none();
        private Charset encoding = StandardCharsets.UTF_8;
        private int blockSize = DEFAULT_BLOCK_SIZE;
        private TarLongFileMode longFileMode = TarLongFileMode.POSIX;
        private TarBigNumberMode bigNumberMode = TarBigNumberMode.STAR;
        private boolean addPaxHeadersForNonAsciiNames;

        private final Sink sink;

        private Builder(Sink sink) {
            this.sink = sink;
        }

        /**
         * Sets the compression applied to the whole tar stream. Defaults to none.
         *
         * @param compression the compression
         * @return this builder
         * @throws IllegalArgumentException if the codec can only be read, or is Pack200, which compresses JAR files,
         *     not tar streams
         * @throws NullPointerException if the compression is null
         * @since 5.0
         */
        public Builder compression(Compression compression) {
            Objects.requireNonNull(compression, "compression");
            if (compression instanceof Compression.Pack200) {
                throw new IllegalArgumentException("Pack200 compresses JAR files, not tar streams");
            }
            if (!compression.canWrite()) {
                throw new IllegalArgumentException(compression.getClass().getSimpleName() + " can only be read");
            }
            this.compression = compression;
            return this;
        }

        /**
         * Sets the encoding of entry names. Defaults to UTF-8.
         *
         * @param encoding the encoding
         * @return this builder
         * @throws NullPointerException if the encoding is null
         * @since 5.0
         */
        public Builder encoding(Charset encoding) {
            this.encoding = Objects.requireNonNull(encoding, "encoding");
            return this;
        }

        /**
         * Sets the block size. Defaults to {@code -511}, the commons-compress default.
         *
         * @param blockSize {@code -511}, or a positive multiple of 512 bytes
         * @return this builder
         * @throws IllegalArgumentException if the value is neither {@code -511} nor a positive multiple of 512
         * @since 5.0
         */
        public Builder blockSize(int blockSize) {
            if (blockSize != DEFAULT_BLOCK_SIZE && (blockSize <= 0 || blockSize % TarConstants.DEFAULT_RCDSIZE != 0)) {
                throw new IllegalArgumentException(
                        "blockSize must be " + DEFAULT_BLOCK_SIZE + " or a positive multiple of 512: " + blockSize);
            }
            this.blockSize = blockSize;
            return this;
        }

        /**
         * Sets how names longer than 100 bytes are stored. Defaults to {@link TarLongFileMode#POSIX}, which stores long
         * names in PAX extended headers.
         *
         * @param mode the mode
         * @return this builder
         * @throws NullPointerException if the mode is null
         * @since 5.0
         */
        public Builder longFileMode(TarLongFileMode mode) {
            this.longFileMode = Objects.requireNonNull(mode, "longFileMode");
            return this;
        }

        /**
         * Sets how numbers too large for the classic header are stored. Defaults to {@link TarBigNumberMode#STAR},
         * which uses the GNU binary encoding only for numbers that do not fit and so adds no PAX headers to ordinary
         * entries; GNU tar, bsdtar and star read it.
         *
         * @param mode the mode
         * @return this builder
         * @throws NullPointerException if the mode is null
         * @since 5.0
         */
        public Builder bigNumberMode(TarBigNumberMode mode) {
            this.bigNumberMode = Objects.requireNonNull(mode, "bigNumberMode");
            return this;
        }

        /**
         * Sets whether non-ASCII names also get a PAX extension header. Defaults to {@code false}.
         *
         * @param add whether to add the header
         * @return this builder
         * @since 5.0
         */
        public Builder addPaxHeadersForNonAsciiNames(boolean add) {
            this.addPaxHeadersForNonAsciiNames = add;
            return this;
        }

        /** {@inheritDoc} */
        @Override
        protected Builder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveCreator build() throws IOException {
            Sink.Opened opened = sink.open();
            try {
                return new TarArchiveCreator(this, new TarEntryWriter(tarStream(opened.out())));
            } catch (IOException e) {
                throw opened.closeIfOwned(e);
            } catch (RuntimeException e) {
                throw opened.closeIfOwned(e);
            }
        }

        private TarArchiveOutputStream tarStream(OutputStream target) throws IOException {
            OutputStream compressed = Codecs.compressing(compression, target);
            TarArchiveOutputStream out = new TarArchiveOutputStream(compressed, blockSize, encoding.name());
            out.setAddPaxHeadersForNonAsciiNames(addPaxHeadersForNonAsciiNames);
            out.setLongFileMode(longFileMode.value);
            out.setBigNumberMode(bigNumberMode.value);
            return out;
        }
    }
}
