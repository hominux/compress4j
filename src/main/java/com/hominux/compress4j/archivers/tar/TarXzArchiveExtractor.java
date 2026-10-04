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

import com.hominux.compress4j.compressors.xz.XZDecompressor.XZDecompressorInputStreamBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Tar XZ ArchiveExtractor
 *
 * @since 2.2
 */
public class TarXzArchiveExtractor extends BaseTarArchiveExtractor {

    /**
     * Create a new {@link TarXzArchiveExtractor} with the given input stream.
     *
     * @param tarArchiveInputStream the input Tar Archive Input Stream
     */
    public TarXzArchiveExtractor(TarArchiveInputStream tarArchiveInputStream) {
        super(tarArchiveInputStream);
    }

    /**
     * Create a new {@link TarXzArchiveExtractor} with the given input stream and options.
     *
     * @param builder the archive input stream builder
     * @throws IOException if an I/O error occurred
     */
    public TarXzArchiveExtractor(TarXzArchiveExtractorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Helper static method to create an instance of the {@link TarXzArchiveExtractorBuilder}
     *
     * @param path the path to the archive to extract
     * @return An instance of the {@link TarXzArchiveExtractorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static TarXzArchiveExtractorBuilder builder(Path path) throws IOException {
        return new TarXzArchiveExtractorBuilder(path);
    }

    /**
     * Creates a builder reading from the channel's current position. The extractor closes the channel when it is
     * closed; a failed {@code build()} leaves it open.
     *
     * @param channel the channel holding the archive
     * @return the builder
     * @since 5.0
     */
    public static TarXzArchiveExtractorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newInputStream(channel));
    }

    /**
     * Helper static method to create an instance of the {@link TarXzArchiveExtractorBuilder}
     *
     * @param inputStream the input stream of the archive to extract
     * @return An instance of the {@link TarXzArchiveExtractorBuilder}
     */
    public static TarXzArchiveExtractorBuilder builder(InputStream inputStream) {
        return new TarXzArchiveExtractorBuilder(inputStream);
    }

    /** Builder for creating instances of {@link TarXzArchiveExtractor}. */
    public static class TarXzArchiveExtractorBuilder
            extends BaseTarArchiveExtractorBuilder<TarXzArchiveExtractorBuilder, TarXzArchiveExtractor> {

        private final XZDecompressorInputStreamBuilder<TarXzArchiveExtractorBuilder> xzInputStreamBuilder;

        /**
         * Create a new {@link TarXzArchiveExtractorBuilder} with the given path.
         *
         * @param path the path to the archive to extract
         * @throws IOException if an I/O error occurred
         */
        public TarXzArchiveExtractorBuilder(Path path) throws IOException {
            this(Files.newInputStream(path), true);
        }

        /**
         * Create a new {@link TarXzArchiveExtractorBuilder} with the given input stream.
         *
         * @param inputStream the input stream
         */
        public TarXzArchiveExtractorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private TarXzArchiveExtractorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.xzInputStreamBuilder = new XZDecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Access the XZ input stream builder for configuration, e.g. the memory limit or whether to decompress
         * concatenated streams.
         *
         * @return the XZ input stream builder
         */
        public XZDecompressorInputStreamBuilder<TarXzArchiveExtractorBuilder> xzInputStream() {
            return xzInputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public TarXzArchiveExtractorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveInputStream buildArchiveInputStream() throws IOException {
            return super.buildTarArchiveInputStream(xzInputStreamBuilder.buildInputStream());
        }

        /** {@inheritDoc} */
        @Override
        public TarXzArchiveExtractor build() throws IOException {
            return new TarXzArchiveExtractor(this);
        }
    }
}
