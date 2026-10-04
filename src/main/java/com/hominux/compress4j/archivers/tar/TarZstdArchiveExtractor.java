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

import com.hominux.compress4j.compressors.zstd.ZstdDecompressor.ZstdDecompressorInputStreamBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Tar Zstandard archive extractor. Requires {@code com.github.luben:zstd-jni} at runtime.
 *
 * @since 3.2
 */
public class TarZstdArchiveExtractor extends BaseTarArchiveExtractor {

    /**
     * Create a new {@link TarZstdArchiveExtractor} with the given input stream.
     *
     * @param tarArchiveInputStream the input Tar Archive Input Stream
     */
    public TarZstdArchiveExtractor(TarArchiveInputStream tarArchiveInputStream) {
        super(tarArchiveInputStream);
    }

    /**
     * Create a new {@link TarZstdArchiveExtractor} with the given builder.
     *
     * @param builder the archive input stream builder
     * @throws IOException if an I/O error occurred
     */
    public TarZstdArchiveExtractor(TarZstdArchiveExtractorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link TarZstdArchiveExtractorBuilder} reading the archive at the given path.
     *
     * @param path the path to the archive to extract
     * @return a new {@link TarZstdArchiveExtractorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static TarZstdArchiveExtractorBuilder builder(Path path) throws IOException {
        return new TarZstdArchiveExtractorBuilder(path);
    }

    /**
     * Creates a builder reading from the channel's current position. The extractor closes the channel when it is
     * closed; a failed {@code build()} leaves it open.
     *
     * @param channel the channel holding the archive
     * @return the builder
     * @since 5.0
     */
    public static TarZstdArchiveExtractorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newInputStream(channel));
    }

    /**
     * Creates a {@link TarZstdArchiveExtractorBuilder} reading the archive from the given stream.
     *
     * @param inputStream the input stream of the archive to extract
     * @return a new {@link TarZstdArchiveExtractorBuilder}
     */
    public static TarZstdArchiveExtractorBuilder builder(InputStream inputStream) {
        return new TarZstdArchiveExtractorBuilder(inputStream);
    }

    /** Builder for creating instances of {@link TarZstdArchiveExtractor}. */
    public static class TarZstdArchiveExtractorBuilder
            extends BaseTarArchiveExtractorBuilder<TarZstdArchiveExtractorBuilder, TarZstdArchiveExtractor> {

        private final ZstdDecompressorInputStreamBuilder<TarZstdArchiveExtractorBuilder> zstdInputStreamBuilder;

        /**
         * Create a new {@link TarZstdArchiveExtractorBuilder} with the given path.
         *
         * @param path the path to the archive to extract
         * @throws IOException if an I/O error occurred
         */
        public TarZstdArchiveExtractorBuilder(Path path) throws IOException {
            this(Files.newInputStream(path), true);
        }

        /**
         * Create a new {@link TarZstdArchiveExtractorBuilder} with the given input stream.
         *
         * @param inputStream the input stream
         */
        public TarZstdArchiveExtractorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private TarZstdArchiveExtractorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.zstdInputStreamBuilder = new ZstdDecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Access the Zstandard input stream builder.
         *
         * @return the Zstandard input stream builder
         */
        public ZstdDecompressorInputStreamBuilder<TarZstdArchiveExtractorBuilder> zstdInputStream() {
            return zstdInputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public TarZstdArchiveExtractorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveInputStream buildArchiveInputStream() throws IOException {
            return super.buildTarArchiveInputStream(zstdInputStreamBuilder.buildInputStream());
        }

        /** {@inheritDoc} */
        @Override
        public TarZstdArchiveExtractor build() throws IOException {
            return new TarZstdArchiveExtractor(this);
        }
    }
}
