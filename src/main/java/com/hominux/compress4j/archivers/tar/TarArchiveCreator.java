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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * The Tar creator.
 *
 * @since 2.2
 */
public class TarArchiveCreator extends BaseTarArchiveCreator {

    /**
     * Create a new TarArchiveCreator with the given output stream.
     *
     * @param tarArchiveOutputStream the output Tar Archive Output Stream
     */
    public TarArchiveCreator(TarArchiveOutputStream tarArchiveOutputStream) {
        super(tarArchiveOutputStream);
    }

    /**
     * Create a new TarArchiveCreator with the given output stream and options.
     *
     * @param builder the archive output stream builder
     * @throws IOException if an I/O error occurred
     */
    public TarArchiveCreator(TarArchiveCreatorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Helper static method to create an instance of the {@link TarArchiveCreatorBuilder}
     *
     * @param path the path to write the archive to
     * @return An instance of the {@link TarArchiveCreatorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static TarArchiveCreatorBuilder builder(Path path) throws IOException {
        return new TarArchiveCreatorBuilder(path);
    }

    /**
     * Creates a builder writing at the channel's current position. The creator closes the channel when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static TarArchiveCreatorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newOutputStream(channel));
    }

    /**
     * Helper static method to create an instance of the {@link TarArchiveCreatorBuilder}
     *
     * @param outputStream the output stream to write the archive to
     * @return An instance of the {@link TarArchiveCreatorBuilder}
     */
    public static TarArchiveCreatorBuilder builder(OutputStream outputStream) {
        return new TarArchiveCreatorBuilder(outputStream);
    }

    /** Tar creator builder */
    public static class TarArchiveCreatorBuilder
            extends BaseTarArchiveCreatorBuilder<TarArchiveCreatorBuilder, TarArchiveCreator> {
        /**
         * Create a new {@link TarArchiveCreator} with the given path.
         *
         * @param path the path to write the archive to
         * @throws IOException if an I/O error occurred
         */
        public TarArchiveCreatorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link TarArchiveCreator} with the given output stream.
         *
         * @param outputStream the output stream
         */
        public TarArchiveCreatorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        private TarArchiveCreatorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
        }

        /** {@inheritDoc} */
        @Override
        protected TarArchiveCreatorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveOutputStream buildArchiveOutputStream() {
            return buildTarArchiveOutputStream(outputStream);
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveCreator build() throws IOException {
            return new TarArchiveCreator(this);
        }
    }
}
