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
package com.hominux.compress4j.archivers.cpio;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.internal.io.Source;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;

/**
 * Reads CPIO archives. Regular files, directories and symbolic links are extracted; every other entry type is reported
 * through the unsupported-entry handler and skipped. In the newc formats a hard-linked file keeps its data on the last
 * name; every earlier name has no data, so it is reported as unsupported, and so is a hard-linked empty file. The
 * expansion ratio is measured against the bytes consumed from the source.
 *
 * @since 2.2
 */
public final class CpioArchiveExtractor extends ArchiveExtractor {

    private CpioArchiveExtractor(Builder builder, CpioEntryReader reader, LongSupplier compressedBytes) {
        super(builder, reader, compressedBytes);
    }

    /**
     * Creates a builder reading the archive at the given path.
     *
     * @param path the path to read the archive from
     * @return the builder
     */
    public static Builder builder(Path path) {
        return new Builder(new Source.OfPath(path));
    }

    /**
     * Creates a builder reading from the channel's current position. The extractor closes the channel when it is
     * closed; a failed {@code build()} leaves it open.
     *
     * @param channel the channel holding the archive
     * @return the builder
     * @since 5.0
     */
    public static Builder builder(SeekableByteChannel channel) {
        return new Builder(new Source.OfChannel(channel));
    }

    /**
     * Creates a builder reading the archive from the given stream. The extractor closes the stream when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param inputStream the input stream to read the archive from
     * @return the builder
     */
    public static Builder builder(InputStream inputStream) {
        return new Builder(new Source.OfStream(inputStream));
    }

    /**
     * Builder for {@link CpioArchiveExtractor}.
     *
     * @since 5.0
     */
    public static final class Builder extends ArchiveExtractor.Builder<Builder, CpioArchiveExtractor> {
        private final Source source;
        private int blockSize = CpioConstants.BLOCK_SIZE;
        private Charset encoding = StandardCharsets.UTF_8;

        private Builder(Source source) {
            this.source = source;
        }

        /**
         * Sets the block size. Defaults to 512 bytes.
         *
         * @param blockSize the block size in bytes
         * @return this builder
         * @throws IllegalArgumentException if the block size is not positive
         * @since 5.0
         */
        public Builder blockSize(int blockSize) {
            this.blockSize = CpioFormat.requireBlockSize(blockSize);
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

        /** {@inheritDoc} */
        @Override
        protected Builder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public CpioArchiveExtractor build() throws IOException {
            Source.Opened opened = source.open();
            try {
                CpioArchiveInputStream cpio = new CpioArchiveInputStream(opened.in(), blockSize, encoding.name());
                return new CpioArchiveExtractor(this, new CpioEntryReader(cpio, readerContext()), opened.in()::count);
            } catch (RuntimeException e) {
                throw opened.closeIfOwned(e);
            }
        }
    }
}
