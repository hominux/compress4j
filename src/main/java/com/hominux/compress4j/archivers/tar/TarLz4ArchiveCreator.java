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

import com.hominux.compress4j.compressors.lz4.Lz4FramedCompressor.Lz4FramedCompressorOutputStreamBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * The Tar framed LZ4 creator.
 *
 * @since 3.2
 */
public class TarLz4ArchiveCreator extends BaseTarArchiveCreator {

    /**
     * Create a new {@link TarLz4ArchiveCreator} with the given output stream.
     *
     * @param tarArchiveOutputStream the output Tar Archive Output Stream
     */
    public TarLz4ArchiveCreator(TarArchiveOutputStream tarArchiveOutputStream) {
        super(tarArchiveOutputStream);
    }

    /**
     * Create a new {@link TarLz4ArchiveCreator} with the given builder.
     *
     * @param builder the archive output stream builder
     * @throws IOException if an I/O error occurred
     */
    public TarLz4ArchiveCreator(TarLz4ArchiveCreatorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link TarLz4ArchiveCreatorBuilder} writing to the given path.
     *
     * @param path the path to write the archive to
     * @return a new {@link TarLz4ArchiveCreatorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static TarLz4ArchiveCreatorBuilder builder(Path path) throws IOException {
        return new TarLz4ArchiveCreatorBuilder(path);
    }

    /**
     * Creates a builder writing at the channel's current position. The creator closes the channel when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static TarLz4ArchiveCreatorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newOutputStream(channel));
    }

    /**
     * Creates a {@link TarLz4ArchiveCreatorBuilder} writing to the given stream.
     *
     * @param outputStream the output stream to write the archive to
     * @return a new {@link TarLz4ArchiveCreatorBuilder}
     */
    public static TarLz4ArchiveCreatorBuilder builder(OutputStream outputStream) {
        return new TarLz4ArchiveCreatorBuilder(outputStream);
    }

    /** Builder for creating a {@link TarLz4ArchiveCreator}. */
    public static class TarLz4ArchiveCreatorBuilder
            extends BaseTarArchiveCreatorBuilder<TarLz4ArchiveCreatorBuilder, TarLz4ArchiveCreator> {

        private final Lz4FramedCompressorOutputStreamBuilder<TarLz4ArchiveCreatorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link TarLz4ArchiveCreatorBuilder} with the given path.
         *
         * @param path the path to write the archive to
         * @throws IOException if an I/O error occurred
         */
        public TarLz4ArchiveCreatorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link TarLz4ArchiveCreatorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        protected TarLz4ArchiveCreatorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        @SuppressWarnings("this-escape")
        private TarLz4ArchiveCreatorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new Lz4FramedCompressorOutputStreamBuilder<>(this, this.outputStream);
        }

        /** {@inheritDoc} */
        @Override
        protected TarLz4ArchiveCreatorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveOutputStream buildArchiveOutputStream() throws IOException {
            return super.buildTarArchiveOutputStream(compressorOutputStreamBuilder.build());
        }

        /**
         * Returns the framed LZ4 compressor output stream builder.
         *
         * @return the framed LZ4 compressor output stream builder
         */
        public Lz4FramedCompressorOutputStreamBuilder<TarLz4ArchiveCreatorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public TarLz4ArchiveCreator build() throws IOException {
            return new TarLz4ArchiveCreator(this);
        }
    }
}
