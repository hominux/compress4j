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

import com.hominux.compress4j.archivers.ArchiveCreator;
import com.hominux.compress4j.internal.io.Sink;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;

/**
 * Writes CPIO archives. Symbolic links are stored with the {@code C_ISLNK} mode bits and the target path as content.
 *
 * @since 2.2
 */
public final class CpioArchiveCreator extends ArchiveCreator {

    private CpioArchiveCreator(Builder builder, CpioEntryWriter writer) {
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
     * Builder for {@link CpioArchiveCreator}.
     *
     * @since 5.0
     */
    public static final class Builder extends ArchiveCreator.Builder<Builder, CpioArchiveCreator> {
        private final Sink sink;
        private CpioFormat format = CpioFormat.NEW;
        private int blockSize = CpioConstants.BLOCK_SIZE;
        private Charset encoding = StandardCharsets.UTF_8;

        private Builder(Sink sink) {
            this.sink = sink;
        }

        /**
         * Sets the header format. Defaults to {@link CpioFormat#NEW}.
         *
         * @param format the format
         * @return this builder
         * @throws NullPointerException if the format is null
         * @since 5.0
         */
        public Builder format(CpioFormat format) {
            this.format = Objects.requireNonNull(format, "format");
            return this;
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
        public CpioArchiveCreator build() throws IOException {
            Sink.Opened opened = sink.open();
            try {
                var cpio = new CpioArchiveOutputStream(opened.out(), format.value, blockSize, encoding.name());
                return new CpioArchiveCreator(this, new CpioEntryWriter(cpio, format.value));
            } catch (RuntimeException e) {
                throw opened.closeIfOwned(e);
            }
        }
    }
}
