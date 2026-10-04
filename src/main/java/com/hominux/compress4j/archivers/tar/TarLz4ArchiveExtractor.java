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

import com.hominux.compress4j.compressors.lz4.Lz4FramedDecompressor.Lz4FramedDecompressorInputStreamBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Tar framed LZ4 archive extractor.
 *
 * @since 3.2
 */
public class TarLz4ArchiveExtractor extends BaseTarArchiveExtractor {

    /**
     * Create a new {@link TarLz4ArchiveExtractor} with the given input stream.
     *
     * @param tarArchiveInputStream the input Tar Archive Input Stream
     */
    public TarLz4ArchiveExtractor(TarArchiveInputStream tarArchiveInputStream) {
        super(tarArchiveInputStream);
    }

    /**
     * Create a new {@link TarLz4ArchiveExtractor} with the given builder.
     *
     * @param builder the archive input stream builder
     * @throws IOException if an I/O error occurred
     */
    public TarLz4ArchiveExtractor(TarLz4ArchiveExtractorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link TarLz4ArchiveExtractorBuilder} reading the archive at the given path.
     *
     * @param path the path to the archive to extract
     * @return a new {@link TarLz4ArchiveExtractorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static TarLz4ArchiveExtractorBuilder builder(Path path) throws IOException {
        return new TarLz4ArchiveExtractorBuilder(path);
    }

    /**
     * Creates a builder reading from the channel's current position. The extractor closes the channel when it is
     * closed; a failed {@code build()} leaves it open.
     *
     * @param channel the channel holding the archive
     * @return the builder
     * @since 5.0
     */
    public static TarLz4ArchiveExtractorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newInputStream(channel));
    }

    /**
     * Creates a {@link TarLz4ArchiveExtractorBuilder} reading the archive from the given stream.
     *
     * @param inputStream the input stream of the archive to extract
     * @return a new {@link TarLz4ArchiveExtractorBuilder}
     */
    public static TarLz4ArchiveExtractorBuilder builder(InputStream inputStream) {
        return new TarLz4ArchiveExtractorBuilder(inputStream);
    }

    /** Builder for creating instances of {@link TarLz4ArchiveExtractor}. */
    public static class TarLz4ArchiveExtractorBuilder
            extends BaseTarArchiveExtractorBuilder<TarLz4ArchiveExtractorBuilder, TarLz4ArchiveExtractor> {

        private final Lz4FramedDecompressorInputStreamBuilder<TarLz4ArchiveExtractorBuilder> lz4InputStreamBuilder;

        /**
         * Create a new {@link TarLz4ArchiveExtractorBuilder} with the given path.
         *
         * @param path the path to the archive to extract
         * @throws IOException if an I/O error occurred
         */
        public TarLz4ArchiveExtractorBuilder(Path path) throws IOException {
            this(Files.newInputStream(path), true);
        }

        /**
         * Create a new {@link TarLz4ArchiveExtractorBuilder} with the given input stream.
         *
         * @param inputStream the input stream
         */
        public TarLz4ArchiveExtractorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private TarLz4ArchiveExtractorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.lz4InputStreamBuilder = new Lz4FramedDecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Access the framed LZ4 input stream builder.
         *
         * @return the framed LZ4 input stream builder
         */
        public Lz4FramedDecompressorInputStreamBuilder<TarLz4ArchiveExtractorBuilder> lz4InputStream() {
            return lz4InputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public TarLz4ArchiveExtractorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveInputStream buildArchiveInputStream() throws IOException {
            return super.buildTarArchiveInputStream(lz4InputStreamBuilder.buildInputStream());
        }

        /** {@inheritDoc} */
        @Override
        public TarLz4ArchiveExtractor build() throws IOException {
            return new TarLz4ArchiveExtractor(this);
        }
    }
}
