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
package com.hominux.compress4j.compressors.bzip2;

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/**
 * Provides BZip2 decompression functionality that reads from a {@link BZip2CompressorInputStream}. This class extends
 * the {@link CodecDecompressor} base class and supports decompressing BZip2-compressed data with optional support for
 * concatenated streams.
 *
 * <p>Use the builder pattern to configure and create instances with custom decompression options.
 *
 * @since 2.2
 */
public class BZip2Decompressor extends CodecDecompressor<BZip2CompressorInputStream> {

    /**
     * Constructor that takes a BZip2CompressorInputStream.
     *
     * @param inputStream the BZip2CompressorInputStream to read from.
     */
    public BZip2Decompressor(BZip2CompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes a BZip2DecompressorBuilder.
     *
     * @param builder the BZip2DecompressorBuilder to build from.
     * @throws IOException thrown by the underlying output stream for I/O errors
     */
    public BZip2Decompressor(BZip2DecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a BZip2DecompressorBuilder using the provided InputStream.
     *
     * @param inputStream the InputStream to read from
     * @return a new BZip2DecompressorBuilder
     */
    public static BZip2DecompressorBuilder builder(InputStream inputStream) {
        return new BZip2DecompressorBuilder(inputStream);
    }

    /**
     * Creates a BZip2DecompressorBuilder using the provided Path.
     *
     * @param path the Path to read from
     * @return a new BZip2DecompressorBuilder
     * @throws IOException if an I/O error occurs while creating the input stream
     */
    public static BZip2DecompressorBuilder builder(Path path) throws IOException {
        return new BZip2DecompressorBuilder(path);
    }

    /**
     * BZip2Decompressor Builder
     *
     * @param <P> the parent builder type
     * @since 2.2
     */
    public static class BZip2DecompressorInputStreamBuilder<P> {
        private final P parent;
        private final InputStream inputStream;
        private boolean decompressConcatenated = false;

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public BZip2DecompressorInputStreamBuilder(P parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Sets whether to decompress concatenated BZip2 streams.
         *
         * @param decompressConcatenated true if concatenated streams should be decompressed, false otherwise.
         * @return this builder instance for method chaining.
         */
        @SuppressWarnings("UnusedReturnValue")
        public BZip2DecompressorInputStreamBuilder<P> setDecompressConcatenated(boolean decompressConcatenated) {
            this.decompressConcatenated = decompressConcatenated;
            return this;
        }

        /**
         * Builds the BZip2CompressorInputStream using the provided InputStream and options.
         *
         * @return a new BZip2CompressorInputStream.
         * @throws IOException if an I/O error occurs while creating the input stream.
         */
        public BZip2CompressorInputStream buildInputStream() throws IOException {
            return new BZip2CompressorInputStream(inputStream, decompressConcatenated);
        }

        /**
         * Returns the parent builder.
         *
         * @return the parent builder.
         */
        public P parentBuilder() {
            return parent;
        }
    }

    /**
     * Builder for creating instances of {@link BZip2Decompressor}.
     *
     * @since 2.2
     */
    public static class BZip2DecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    BZip2CompressorInputStream, BZip2Decompressor, BZip2DecompressorBuilder> {

        private final BZip2DecompressorInputStreamBuilder<BZip2DecompressorBuilder> inputStreamBuilder;
        /**
         * Constructor that takes a InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public BZip2DecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        private BZip2DecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new BZip2DecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while creating the input stream
         */
        public BZip2DecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while creating the input stream
         */
        public BZip2DecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the BZip2DecompressorInputStreamBuilder instance
         */
        public BZip2DecompressorInputStreamBuilder<BZip2DecompressorBuilder> compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /**
         * Builds a BZip2CompressorInputStream using the current configuration.
         *
         * @return a new BZip2CompressorInputStream instance
         * @throws IOException if an I/O error occurs while creating the input stream
         */
        @Override
        public BZip2CompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /**
         * Returns the current builder instance for method chaining.
         *
         * @return this builder instance
         */
        @Override
        protected BZip2DecompressorBuilder getThis() {
            return this;
        }

        /**
         * Builds a BZip2Decompressor using the current configuration.
         *
         * @return a new BZip2Decompressor instance
         * @throws IOException if an I/O error occurs while creating the decompressor
         */
        @Override
        public BZip2Decompressor build() throws IOException {
            return new BZip2Decompressor(this);
        }
    }
}
