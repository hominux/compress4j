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

import com.hominux.compress4j.compressors.zstd.ZstdCompressor.ZstdCompressorOutputStreamBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * The Tar Zstandard creator. Requires {@code com.github.luben:zstd-jni} at runtime.
 *
 * @since 3.2
 */
public class TarZstdArchiveCreator extends BaseTarArchiveCreator {

    /**
     * Create a new {@link TarZstdArchiveCreator} with the given output stream.
     *
     * @param tarArchiveOutputStream the output Tar Archive Output Stream
     */
    public TarZstdArchiveCreator(TarArchiveOutputStream tarArchiveOutputStream) {
        super(tarArchiveOutputStream);
    }

    /**
     * Create a new {@link TarZstdArchiveCreator} with the given builder.
     *
     * @param builder the archive output stream builder
     * @throws IOException if an I/O error occurred
     */
    public TarZstdArchiveCreator(TarZstdArchiveCreatorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link TarZstdArchiveCreatorBuilder} writing to the given path.
     *
     * @param path the path to write the archive to
     * @return a new {@link TarZstdArchiveCreatorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static TarZstdArchiveCreatorBuilder builder(Path path) throws IOException {
        return new TarZstdArchiveCreatorBuilder(path);
    }

    /**
     * Creates a builder writing at the channel's current position. The creator closes the channel when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static TarZstdArchiveCreatorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newOutputStream(channel));
    }

    /**
     * Creates a {@link TarZstdArchiveCreatorBuilder} writing to the given stream.
     *
     * @param outputStream the output stream to write the archive to
     * @return a new {@link TarZstdArchiveCreatorBuilder}
     */
    public static TarZstdArchiveCreatorBuilder builder(OutputStream outputStream) {
        return new TarZstdArchiveCreatorBuilder(outputStream);
    }

    /** Builder for creating a {@link TarZstdArchiveCreator}. */
    public static class TarZstdArchiveCreatorBuilder
            extends BaseTarArchiveCreatorBuilder<TarZstdArchiveCreatorBuilder, TarZstdArchiveCreator> {

        private final ZstdCompressorOutputStreamBuilder<TarZstdArchiveCreatorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link TarZstdArchiveCreatorBuilder} with the given path.
         *
         * @param path the path to write the archive to
         * @throws IOException if an I/O error occurred
         */
        public TarZstdArchiveCreatorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link TarZstdArchiveCreatorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        protected TarZstdArchiveCreatorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        @SuppressWarnings("this-escape")
        private TarZstdArchiveCreatorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new ZstdCompressorOutputStreamBuilder<>(this, this.outputStream);
        }

        /** {@inheritDoc} */
        @Override
        protected TarZstdArchiveCreatorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveOutputStream buildArchiveOutputStream() throws IOException {
            return super.buildTarArchiveOutputStream(compressorOutputStreamBuilder.build());
        }

        /**
         * Returns the Zstandard compressor output stream builder.
         *
         * @return the Zstandard compressor output stream builder
         */
        public ZstdCompressorOutputStreamBuilder<TarZstdArchiveCreatorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public TarZstdArchiveCreator build() throws IOException {
            return new TarZstdArchiveCreator(this);
        }
    }
}
