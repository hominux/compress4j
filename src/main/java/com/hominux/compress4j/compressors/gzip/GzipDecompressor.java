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
package com.hominux.compress4j.compressors.gzip;

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.function.IOConsumer;

/**
 * Provides Gzip decompression functionality that reads from a {@link GzipCompressorInputStream}. This class extends the
 * {@link CodecDecompressor} base class and supports decompressing Gzip-compressed data with configurable options for
 * character encoding and header processing.
 *
 * <p>Use the builder pattern to configure decompression options such as character encoding before creating instances.
 *
 * @since 2.2
 */
public class GzipDecompressor extends CodecDecompressor<GzipCompressorInputStream> {

    /**
     * Constructs a GzipDecompressor using the provided {@link GzipDecompressorBuilder}.
     *
     * @param builder the builder to configure the decompressor
     * @throws IOException if an I/O error occurs during stream creation
     */
    public GzipDecompressor(GzipDecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Constructs a GzipDecompressor with the given {@link GzipCompressorInputStream}.
     *
     * @param compressorInputStream the input stream to read compressed data from
     */
    public GzipDecompressor(GzipCompressorInputStream compressorInputStream) {
        super(compressorInputStream);
    }

    /**
     * Creates a new {@link GzipDecompressorBuilder} for the given file {@link Path}.
     *
     * @param path the file path to read compressed data from
     * @return a new builder instance
     * @throws IOException if an I/O error occurs opening the file
     */
    public static GzipDecompressorBuilder builder(Path path) throws IOException {
        return new GzipDecompressorBuilder(path);
    }

    /**
     * Creates a new {@link GzipDecompressorBuilder} for the given {@link InputStream}.
     *
     * @param inputStream the input stream to read compressed data from
     * @return a new builder instance
     */
    public static GzipDecompressorBuilder builder(InputStream inputStream) {
        return new GzipDecompressorBuilder(inputStream);
    }

    /** Builder for configuring and creating a {@link GzipCompressorInputStream}. */
    public static class GzipDecompressorInputStreamBuilder {
        private final GzipDecompressorBuilder parent;
        private final InputStream inputStream;

        /** True if decompressing multi-member streams. */
        private boolean decompressConcatenated;

        private Charset fileNameCharset = StandardCharsets.ISO_8859_1;

        private IOConsumer<GzipCompressorInputStream> onMemberStart;

        private IOConsumer<GzipCompressorInputStream> onMemberEnd;

        /**
         * Constructs a builder for a Gzip input stream.
         *
         * @param parent the parent builder
         * @param inputStream the input stream to read compressed data from
         */
        public GzipDecompressorInputStreamBuilder(GzipDecompressorBuilder parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Sets whether to decompress concatenated GZIP streams.
         *
         * @param decompressConcatenated true if concatenated streams should be decompressed, false otherwise
         * @return this builder instance
         */
        public GzipDecompressorInputStreamBuilder setDecompressConcatenated(boolean decompressConcatenated) {
            this.decompressConcatenated = decompressConcatenated;
            return this;
        }

        /**
         * Sets the Charset to use for file names and comments.
         *
         * <p><em>Setting a value other than {@link StandardCharsets#ISO_8859_1} is not compliant with the <a
         * href="https://datatracker.ietf.org/doc/html/rfc1952">RFC 1952 GZIP File Format Specification</a></em>. Use at
         * your own risk of interoperability issues.
         *
         * <p>The default value is {@link StandardCharsets#ISO_8859_1}.
         *
         * @param fileNameCharset the Charset to use, null maps to {@link StandardCharsets#ISO_8859_1}
         * @return this builder instance
         */
        public GzipDecompressorInputStreamBuilder setFileNameCharset(final Charset fileNameCharset) {
            this.fileNameCharset = fileNameCharset;
            return this;
        }

        /**
         * Sets the consumer called when a member trailer is parsed.
         *
         * @param onMemberEnd the consumer to call on member end
         * @return this builder instance
         * @see GzipCompressorInputStream#getMetaData()
         */
        public GzipDecompressorInputStreamBuilder setOnMemberEnd(
                final IOConsumer<GzipCompressorInputStream> onMemberEnd) {
            this.onMemberEnd = onMemberEnd;
            return this;
        }

        /**
         * Sets the consumer called when a member header is parsed.
         *
         * @param onMemberStart the consumer to call on member start
         * @return this builder instance
         * @see GzipCompressorInputStream#getMetaData()
         */
        public GzipDecompressorInputStreamBuilder setOnMemberStart(
                final IOConsumer<GzipCompressorInputStream> onMemberStart) {
            this.onMemberStart = onMemberStart;
            return this;
        }

        /**
         * Builds and returns a {@link GzipCompressorInputStream} with the current configuration.
         *
         * @return a configured GzipCompressorInputStream
         * @throws IOException if an I/O error occurs during stream creation
         */
        public GzipCompressorInputStream buildInputStream() throws IOException {
            return GzipCompressorInputStream.builder()
                    .setInputStream(inputStream)
                    .setDecompressConcatenated(decompressConcatenated)
                    .setFileNameCharset(fileNameCharset)
                    .setOnMemberStart(onMemberStart)
                    .setOnMemberEnd(onMemberEnd)
                    .get();
        }

        /**
         * Returns the parent builder for further configuration.
         *
         * @return the parent builder
         */
        public GzipDecompressorBuilder parentBuilder() {
            return parent;
        }
    }

    /**
     * Builder for configuring and creating a {@link GzipDecompressor}.
     *
     * @since 2.2
     */
    public static class GzipDecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    GzipCompressorInputStream, GzipDecompressor, GzipDecompressorBuilder> {

        private final GzipDecompressorInputStreamBuilder inputStreamBuilder;
        /**
         * Constructor that takes a {@link GzipCompressorInputStream}.
         *
         * @param inputStream the {@link GzipCompressorInputStream} to read from.
         */
        public GzipDecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        private GzipDecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new GzipDecompressorInputStreamBuilder(this, inputStream);
        }

        /**
         * Constructor that takes a Path to read from.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while creating the input stream
         */
        public GzipDecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File to read from.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while creating the input stream
         */
        public GzipDecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for further configuration.
         *
         * @return the input stream builder
         */
        public GzipDecompressorInputStreamBuilder compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /**
         * Builds and returns a configured {@link GzipCompressorInputStream}.
         *
         * @return a configured GzipCompressorInputStream
         * @throws IOException if an I/O error occurs during stream creation
         */
        @Override
        public GzipCompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /**
         * Returns this builder instance.
         *
         * @return this builder
         */
        @Override
        protected GzipDecompressorBuilder getThis() {
            return this;
        }

        /**
         * Builds and returns a configured {@link GzipDecompressor}.
         *
         * @return a configured GzipDecompressor
         * @throws IOException if an I/O error occurs during stream creation
         */
        @Override
        public GzipDecompressor build() throws IOException {

            return new GzipDecompressor(this);
        }
    }
}
