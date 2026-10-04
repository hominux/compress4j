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
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/**
 * Tar BZip2 ArchiveExtractor
 *
 * @since 2.2
 */
public class TarBZip2ArchiveExtractor extends BaseTarArchiveExtractor {

    /**
     * Create a new {@link TarBZip2ArchiveExtractor} with the given input stream.
     *
     * @param tarArchiveInputStream the input Tar Archive Input Stream
     */
    @SuppressWarnings("unused")
    public TarBZip2ArchiveExtractor(TarArchiveInputStream tarArchiveInputStream) {
        super(tarArchiveInputStream);
    }

    /**
     * Create a new {@link TarBZip2ArchiveExtractor} with the given input stream and options.
     *
     * @param builder the archive input stream builder
     * @throws IOException if an I/O error occurred
     */
    public TarBZip2ArchiveExtractor(TarBZip2ArchiveExtractorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Helper static method to create an instance of the {@link TarBZip2ArchiveExtractorBuilder}
     *
     * @param path the path to the archive to extract
     * @return An instance of the {@link TarBZip2ArchiveExtractorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static TarBZip2ArchiveExtractorBuilder builder(Path path) throws IOException {
        return new TarBZip2ArchiveExtractorBuilder(path);
    }

    /**
     * Creates a builder reading from the channel's current position. The extractor closes the channel when it is
     * closed; a failed {@code build()} leaves it open.
     *
     * @param channel the channel holding the archive
     * @return the builder
     * @since 5.0
     */
    public static TarBZip2ArchiveExtractorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newInputStream(channel));
    }

    /**
     * Helper static method to create an instance of the {@link TarBZip2ArchiveExtractorBuilder}
     *
     * @param inputStream the input stream of the archive to extract
     * @return An instance of the {@link TarBZip2ArchiveExtractorBuilder}
     */
    public static TarBZip2ArchiveExtractorBuilder builder(InputStream inputStream) {
        return new TarBZip2ArchiveExtractorBuilder(inputStream);
    }

    /** Builder for creating instances of {@link TarBZip2ArchiveExtractor}. */
    public static class TarBZip2ArchiveExtractorBuilder
            extends BaseTarArchiveExtractorBuilder<TarBZip2ArchiveExtractorBuilder, TarBZip2ArchiveExtractor> {

        /**
         * Create a new {@link TarBZip2ArchiveExtractorBuilder} with the given path.
         *
         * @param path the path to the archive to extract
         * @throws IOException if an I/O error occurred
         */
        public TarBZip2ArchiveExtractorBuilder(Path path) throws IOException {
            this(Files.newInputStream(path), true);
        }

        /**
         * Create a new {@link TarBZip2ArchiveExtractorBuilder} with the given input stream.
         *
         * @param inputStream the input stream
         */
        public TarBZip2ArchiveExtractorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        private TarBZip2ArchiveExtractorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
        }

        /** {@inheritDoc} */
        @Override
        public TarBZip2ArchiveExtractorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveInputStream buildArchiveInputStream() throws IOException {
            return super.buildTarArchiveInputStream(new BZip2CompressorInputStream(inputStream));
        }

        /** {@inheritDoc} */
        @Override
        public TarBZip2ArchiveExtractor build() throws IOException {
            return new TarBZip2ArchiveExtractor(this);
        }
    }
}
