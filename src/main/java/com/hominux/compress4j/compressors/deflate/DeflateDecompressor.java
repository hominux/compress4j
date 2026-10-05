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
package com.hominux.compress4j.compressors.deflate;

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
import org.apache.commons.compress.compressors.deflate.DeflateParameters;

/**
 * Provides Deflate decompression functionality that reads from a {@link DeflateCompressorInputStream}. This class
 * extends the {@link CodecDecompressor} base class and supports decompressing Deflate-compressed data with configurable
 * options for Zlib header handling.
 *
 * <p>Use the builder pattern to configure decompression options such as whether to expect a Zlib header in the input
 * stream before creating instances.
 *
 * @since 2.2
 */
public class DeflateDecompressor extends CodecDecompressor<DeflateCompressorInputStream> {

    /**
     * Constructor that takes a DeflateCompressorInputStream.
     *
     * @param compressorInputStream the DeflateCompressorInputStream to read from.
     */
    public DeflateDecompressor(DeflateCompressorInputStream compressorInputStream) {
        super(compressorInputStream);
    }

    /**
     * Constructor that takes a DeflateDecompressorBuilder.
     *
     * @param builder the DeflateDecompressorBuilder to build from.
     * @throws IOException thrown by the underlying output stream for I/O errors
     */
    public DeflateDecompressor(DeflateDecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a DeflateDecompressorBuilder using the provided InputStream.
     *
     * @param inputStream the InputStream to read from
     * @return a new DeflateDecompressorBuilder
     */
    public static DeflateDecompressorBuilder builder(InputStream inputStream) {
        return new DeflateDecompressorBuilder(inputStream);
    }

    /**
     * Creates a DeflateDecompressorBuilder using the provided Path.
     *
     * @param path the Path to read from
     * @return a new DeflateDecompressorBuilder
     * @throws IOException if an I/O error occurs while creating the input stream
     */
    public static DeflateDecompressorBuilder builder(Path path) throws IOException {
        return new DeflateDecompressorBuilder(path);
    }

    /** DeflateDecompressor Builder */
    public static class DeflateDecompressorInputStreamBuilder {
        private final DeflateDecompressorBuilder parent;
        private final InputStream inputStream;
        private boolean withZlibHeader = true;

        /**
         * Constructor for creating a DeflateDecompressorInputStreamBuilder with the given parent builder and input
         * stream.
         *
         * @param parent the parent builder
         * @param inputStream the input stream to read from
         */
        public DeflateDecompressorInputStreamBuilder(DeflateDecompressorBuilder parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Sets whether the decompressor should expect a Zlib header in the input stream. The default is true, meaning
         * it expects a Zlib header.
         *
         * @param withZlibHeader true if the input stream has a Zlib header, false otherwise
         * @return this builder instance for method chaining
         */
        @SuppressWarnings("UnusedReturnValue")
        public DeflateDecompressorInputStreamBuilder setWithZlibHeader(boolean withZlibHeader) {
            this.withZlibHeader = withZlibHeader;
            return this;
        }

        /**
         * Builds a DeflateCompressorInputStream with the current configuration.
         *
         * @return a new DeflateCompressorInputStream instance
         */
        public DeflateCompressorInputStream buildInputStream() {
            DeflateParameters parameters = new DeflateParameters();
            parameters.setWithZlibHeader(withZlibHeader);

            return new DeflateCompressorInputStream(inputStream, parameters);
        }

        /**
         * Builds a DeflateDecompressor using the current configuration.
         *
         * @return a new DeflateDecompressor instance
         */
        public DeflateDecompressorBuilder parentBuilder() {
            return parent;
        }
    }

    /**
     * Builder for creating a {@link DeflateDecompressor}.
     *
     * @since 2.2
     */
    public static class DeflateDecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    DeflateCompressorInputStream, DeflateDecompressor, DeflateDecompressorBuilder> {

        private final DeflateDecompressorInputStreamBuilder inputStreamBuilder;

        /**
         * Create a new {@link DeflateDecompressorBuilder} with the given input stream.
         *
         * @param inputStream the input stream to read from
         */
        public DeflateDecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        private DeflateDecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new DeflateDecompressorInputStreamBuilder(this, inputStream);
        }

        /**
         * Create a new {@link DeflateDecompressorBuilder} with the given path.
         *
         * @param path the path to read from
         * @throws IOException if an I/O error occurs while creating the input stream
         */
        public DeflateDecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Create a new {@link DeflateDecompressorBuilder} with the given file.
         *
         * @param file the file to read from
         * @throws IOException if an I/O error occurs while creating the input stream
         */
        public DeflateDecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the input stream builder
         */
        public DeflateDecompressorInputStreamBuilder inputStreamBuilder() {
            return inputStreamBuilder;
        }

        /**
         * Builds a {@link DeflateCompressorInputStream} using the current configuration.
         *
         * @return a new DeflateCompressorInputStream instance
         */
        @Override
        public DeflateCompressorInputStream buildCompressorInputStream() {
            return inputStreamBuilder.buildInputStream();
        }

        /**
         * Returns the current builder instance for method chaining.
         *
         * @return this builder instance
         */
        @Override
        protected DeflateDecompressorBuilder getThis() {
            return this;
        }

        /**
         * Builds a {@link DeflateDecompressor} using the current configuration.
         *
         * @return a new DeflateDecompressor instance
         * @throws IOException if an I/O error occurs while building the decompressor
         */
        @Override
        public DeflateDecompressor build() throws IOException {
            return new DeflateDecompressor(this);
        }
    }
}
