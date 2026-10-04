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

import com.hominux.compress4j.compressors.xz.XZCompressor.XZCompressorOutputStreamBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * The Tar XZ creator.
 *
 * @since 2.2
 */
public class TarXzArchiveCreator extends BaseTarArchiveCreator {

    /**
     * Create a new {@link TarXzArchiveCreator} with the given output stream.
     *
     * @param tarArchiveOutputStream the output Tar Archive Output Stream
     */
    public TarXzArchiveCreator(TarArchiveOutputStream tarArchiveOutputStream) {
        super(tarArchiveOutputStream);
    }

    /**
     * Create a new {@link TarXzArchiveCreator} with the given output stream and options.
     *
     * @param builder the archive output stream builder
     * @throws IOException if an I/O error occurred
     */
    public TarXzArchiveCreator(TarXzArchiveCreatorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Helper static method to create an instance of the {@link TarXzArchiveCreatorBuilder}
     *
     * @param path the path to write the archive to
     * @return An instance of the {@link TarXzArchiveCreatorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static TarXzArchiveCreatorBuilder builder(Path path) throws IOException {
        return new TarXzArchiveCreatorBuilder(path);
    }

    /**
     * Creates a builder writing at the channel's current position. The creator closes the channel when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static TarXzArchiveCreatorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newOutputStream(channel));
    }

    /**
     * Helper static method to create an instance of the {@link TarXzArchiveCreatorBuilder}
     *
     * @param outputStream the output stream to write the archive to
     * @return An instance of the {@link TarXzArchiveCreatorBuilder}
     */
    public static TarXzArchiveCreatorBuilder builder(OutputStream outputStream) {
        return new TarXzArchiveCreatorBuilder(outputStream);
    }

    /**
     * Builder for creating a {@link TarXzArchiveCreator}.
     *
     * @since 2.2
     */
    public static class TarXzArchiveCreatorBuilder
            extends BaseTarArchiveCreatorBuilder<TarXzArchiveCreatorBuilder, TarXzArchiveCreator> {

        private final XZCompressorOutputStreamBuilder<TarXzArchiveCreatorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link TarXzArchiveCreatorBuilder} with the given path.
         *
         * @param path the path to write the archive to
         * @throws IOException if an I/O error occurred
         */
        public TarXzArchiveCreatorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link TarXzArchiveCreatorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        protected TarXzArchiveCreatorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        @SuppressWarnings("this-escape")
        private TarXzArchiveCreatorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new XZCompressorOutputStreamBuilder<>(this, this.outputStream);
        }

        /** {@inheritDoc} */
        @Override
        protected TarXzArchiveCreatorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveOutputStream buildArchiveOutputStream() throws IOException {
            return super.buildTarArchiveOutputStream(compressorOutputStreamBuilder.build());
        }

        /**
         * Returns the XZ compressor output stream builder.
         *
         * @return the XZ compressor output stream builder
         */
        public XZCompressorOutputStreamBuilder<TarXzArchiveCreatorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public TarXzArchiveCreator build() throws IOException {
            return new TarXzArchiveCreator(this);
        }
    }
}
