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

import com.hominux.compress4j.compressors.lzma.LZMADecompressor.LZMADecompressorInputStreamBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Tar LZMA archive extractor. Requires {@code org.tukaani:xz} at runtime.
 *
 * @since 3.2
 */
public class TarLzmaArchiveExtractor extends BaseTarArchiveExtractor {

    /**
     * Create a new {@link TarLzmaArchiveExtractor} with the given input stream.
     *
     * @param tarArchiveInputStream the input Tar Archive Input Stream
     */
    public TarLzmaArchiveExtractor(TarArchiveInputStream tarArchiveInputStream) {
        super(tarArchiveInputStream);
    }

    /**
     * Create a new {@link TarLzmaArchiveExtractor} with the given builder.
     *
     * @param builder the archive input stream builder
     * @throws IOException if an I/O error occurred
     */
    public TarLzmaArchiveExtractor(TarLzmaArchiveExtractorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link TarLzmaArchiveExtractorBuilder} reading the archive at the given path.
     *
     * @param path the path to the archive to extract
     * @return a new {@link TarLzmaArchiveExtractorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static TarLzmaArchiveExtractorBuilder builder(Path path) throws IOException {
        return new TarLzmaArchiveExtractorBuilder(path);
    }

    /**
     * Creates a builder reading from the channel's current position. The extractor closes the channel when it is
     * closed; a failed {@code build()} leaves it open.
     *
     * @param channel the channel holding the archive
     * @return the builder
     * @since 5.0
     */
    public static TarLzmaArchiveExtractorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newInputStream(channel));
    }

    /**
     * Creates a {@link TarLzmaArchiveExtractorBuilder} reading the archive from the given stream.
     *
     * @param inputStream the input stream of the archive to extract
     * @return a new {@link TarLzmaArchiveExtractorBuilder}
     */
    public static TarLzmaArchiveExtractorBuilder builder(InputStream inputStream) {
        return new TarLzmaArchiveExtractorBuilder(inputStream);
    }

    /** Builder for creating instances of {@link TarLzmaArchiveExtractor}. */
    public static class TarLzmaArchiveExtractorBuilder
            extends BaseTarArchiveExtractorBuilder<TarLzmaArchiveExtractorBuilder, TarLzmaArchiveExtractor> {

        private final LZMADecompressorInputStreamBuilder<TarLzmaArchiveExtractorBuilder> lzmaInputStreamBuilder;

        /**
         * Create a new {@link TarLzmaArchiveExtractorBuilder} with the given path.
         *
         * @param path the path to the archive to extract
         * @throws IOException if an I/O error occurred
         */
        public TarLzmaArchiveExtractorBuilder(Path path) throws IOException {
            this(Files.newInputStream(path), true);
        }

        /**
         * Create a new {@link TarLzmaArchiveExtractorBuilder} with the given input stream.
         *
         * @param inputStream the input stream
         */
        public TarLzmaArchiveExtractorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private TarLzmaArchiveExtractorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.lzmaInputStreamBuilder = new LZMADecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Access the LZMA input stream builder.
         *
         * @return the LZMA input stream builder
         */
        public LZMADecompressorInputStreamBuilder<TarLzmaArchiveExtractorBuilder> lzmaInputStream() {
            return lzmaInputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public TarLzmaArchiveExtractorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveInputStream buildArchiveInputStream() throws IOException {
            return super.buildTarArchiveInputStream(lzmaInputStreamBuilder.buildInputStream());
        }

        /** {@inheritDoc} */
        @Override
        public TarLzmaArchiveExtractor build() throws IOException {
            return new TarLzmaArchiveExtractor(this);
        }
    }
}
