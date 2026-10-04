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
package com.hominux.compress4j.archivers.tar;

import com.hominux.compress4j.compressors.lzma.LZMACompressor.LZMACompressorOutputStreamBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * The Tar LZMA creator. Requires {@code org.tukaani:xz} at runtime.
 *
 * @since 3.2
 */
public class TarLzmaArchiveCreator extends BaseTarArchiveCreator {

    /**
     * Create a new {@link TarLzmaArchiveCreator} with the given output stream.
     *
     * @param tarArchiveOutputStream the output Tar Archive Output Stream
     */
    public TarLzmaArchiveCreator(TarArchiveOutputStream tarArchiveOutputStream) {
        super(tarArchiveOutputStream);
    }

    /**
     * Create a new {@link TarLzmaArchiveCreator} with the given builder.
     *
     * @param builder the archive output stream builder
     * @throws IOException if an I/O error occurred
     */
    public TarLzmaArchiveCreator(TarLzmaArchiveCreatorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link TarLzmaArchiveCreatorBuilder} writing to the given path.
     *
     * @param path the path to write the archive to
     * @return a new {@link TarLzmaArchiveCreatorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static TarLzmaArchiveCreatorBuilder builder(Path path) throws IOException {
        return new TarLzmaArchiveCreatorBuilder(path);
    }

    /**
     * Creates a builder writing at the channel's current position. The creator closes the channel when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static TarLzmaArchiveCreatorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newOutputStream(channel));
    }

    /**
     * Creates a {@link TarLzmaArchiveCreatorBuilder} writing to the given stream.
     *
     * @param outputStream the output stream to write the archive to
     * @return a new {@link TarLzmaArchiveCreatorBuilder}
     */
    public static TarLzmaArchiveCreatorBuilder builder(OutputStream outputStream) {
        return new TarLzmaArchiveCreatorBuilder(outputStream);
    }

    /** Builder for creating a {@link TarLzmaArchiveCreator}. */
    public static class TarLzmaArchiveCreatorBuilder
            extends BaseTarArchiveCreatorBuilder<TarLzmaArchiveCreatorBuilder, TarLzmaArchiveCreator> {

        private final LZMACompressorOutputStreamBuilder<TarLzmaArchiveCreatorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link TarLzmaArchiveCreatorBuilder} with the given path.
         *
         * @param path the path to write the archive to
         * @throws IOException if an I/O error occurred
         */
        public TarLzmaArchiveCreatorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link TarLzmaArchiveCreatorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        protected TarLzmaArchiveCreatorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        @SuppressWarnings("this-escape")
        private TarLzmaArchiveCreatorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new LZMACompressorOutputStreamBuilder<>(this, this.outputStream);
        }

        /** {@inheritDoc} */
        @Override
        protected TarLzmaArchiveCreatorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveOutputStream buildArchiveOutputStream() throws IOException {
            return super.buildTarArchiveOutputStream(compressorOutputStreamBuilder.build());
        }

        /**
         * Returns the LZMA compressor output stream builder.
         *
         * @return the LZMA compressor output stream builder
         */
        public LZMACompressorOutputStreamBuilder<TarLzmaArchiveCreatorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public TarLzmaArchiveCreator build() throws IOException {
            return new TarLzmaArchiveCreator(this);
        }
    }
}
