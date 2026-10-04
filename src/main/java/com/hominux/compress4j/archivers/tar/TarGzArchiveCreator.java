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
package com.hominux.compress4j.archivers.tar;

import com.hominux.compress4j.compressors.gzip.GzipCompressor.GzipCompressorOutputStreamBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * The Tar Gz creator.
 *
 * @since 2.2
 */
public class TarGzArchiveCreator extends BaseTarArchiveCreator {

    /**
     * Create a new {@link TarGzArchiveCreator} with the given output stream.
     *
     * @param tarArchiveOutputStream the output Tar Archive Output Stream
     */
    public TarGzArchiveCreator(TarArchiveOutputStream tarArchiveOutputStream) {
        super(tarArchiveOutputStream);
    }

    /**
     * Create a new {@link TarGzArchiveCreator} with the given output stream and options.
     *
     * @param builder the archive output stream builder
     * @throws IOException if an I/O error occurred
     */
    public TarGzArchiveCreator(TarGzArchiveCreatorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Helper static method to create an instance of the {@link TarGzArchiveCreatorBuilder}
     *
     * @param path the path to write the archive to
     * @return An instance of the {@link TarGzArchiveCreatorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static TarGzArchiveCreatorBuilder builder(Path path) throws IOException {
        return new TarGzArchiveCreatorBuilder(path);
    }

    /**
     * Creates a builder writing at the channel's current position. The creator closes the channel when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static TarGzArchiveCreatorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newOutputStream(channel));
    }

    /**
     * Helper static method to create an instance of the {@link TarGzArchiveCreatorBuilder}
     *
     * @param outputStream the output stream to write the archive to
     * @return An instance of the {@link TarGzArchiveCreatorBuilder}
     */
    public static TarGzArchiveCreatorBuilder builder(OutputStream outputStream) {
        return new TarGzArchiveCreatorBuilder(outputStream);
    }

    /**
     * Builder for creating a {@link TarGzArchiveCreator}.
     *
     * @since 2.2
     */
    public static class TarGzArchiveCreatorBuilder
            extends BaseTarArchiveCreatorBuilder<TarGzArchiveCreatorBuilder, TarGzArchiveCreator> {

        private final GzipCompressorOutputStreamBuilder<TarGzArchiveCreatorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link TarGzArchiveCreatorBuilder} with the given path.
         *
         * @param path the path to write the archive to
         * @throws IOException if an I/O error occurred
         */
        public TarGzArchiveCreatorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link TarGzArchiveCreatorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        protected TarGzArchiveCreatorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        @SuppressWarnings("this-escape")
        private TarGzArchiveCreatorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new GzipCompressorOutputStreamBuilder<>(this, this.outputStream);
        }

        /** {@inheritDoc} */
        @Override
        protected TarGzArchiveCreatorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveOutputStream buildArchiveOutputStream() throws IOException {
            return super.buildTarArchiveOutputStream(compressorOutputStreamBuilder.build());
        }

        /**
         * Returns the Gzip compressor output stream builder.
         *
         * @return the Gzip compressor output stream builder
         */
        public GzipCompressorOutputStreamBuilder<TarGzArchiveCreatorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public TarGzArchiveCreator build() throws IOException {
            return new TarGzArchiveCreator(this);
        }
    }
}
