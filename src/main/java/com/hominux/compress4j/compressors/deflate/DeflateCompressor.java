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
package com.hominux.compress4j.compressors.deflate;

import com.hominux.compress4j.compressors.CodecCompressor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorOutputStream;
import org.apache.commons.compress.compressors.deflate.DeflateParameters;

/**
 * Provides a Deflate compressor that writes to a {@link DeflateCompressorOutputStream}. Use the builder pattern to
 * configure and create instances.
 *
 * @since 2.2
 */
public class DeflateCompressor extends CodecCompressor<DeflateCompressorOutputStream> {

    /**
     * Constructs a DeflateCompressor with the given {@link DeflateCompressorOutputStream}.
     *
     * @param compressorOutputStream the output stream to write compressed data to
     */
    public DeflateCompressor(DeflateCompressorOutputStream compressorOutputStream) {
        super(compressorOutputStream);
    }

    /**
     * Constructs a DeflateCompressor using the provided {@link DeflateCompressorBuilder}.
     *
     * @param builder the builder to configure the compressor
     * @throws IOException if an I/O error occurs during stream creation
     */
    public DeflateCompressor(DeflateCompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a new {@link DeflateCompressorBuilder} for the given {@link OutputStream}.
     *
     * @param compressorOutputStream the output stream to write compressed data to
     * @return a new builder instance
     */
    public static DeflateCompressorBuilder builder(OutputStream compressorOutputStream) {
        return new DeflateCompressorBuilder(compressorOutputStream);
    }

    /**
     * Creates a new {@link DeflateCompressorBuilder} for the given file {@link Path}.
     *
     * @param path the file path to write compressed data to
     * @return a new builder instance
     * @throws IOException if an I/O error occurs opening the file
     */
    public static DeflateCompressorBuilder builder(Path path) throws IOException {
        return new DeflateCompressorBuilder(path);
    }

    /**
     * Builder for configuring and creating a {@link DeflateCompressorOutputStream}.
     *
     * @param <P> the type of the parent builder
     * @since 2.2
     */
    public static class DeflateOutputStreamBuilder<P> {
        private final P parent;
        private final OutputStream outputStream;
        private boolean zlibHeader = true;
        private int compressionLevel = Deflater.DEFAULT_COMPRESSION;

        /**
         * Constructs a builder for a Deflate output stream.
         *
         * @param parent the parent builder
         * @param outputStream the output stream to write compressed data to
         */
        public DeflateOutputStreamBuilder(P parent, OutputStream outputStream) {
            this.parent = parent;
            this.outputStream = outputStream;
        }

        /**
         * Sets the compression level for the Deflate output stream.
         *
         * @param compressionLevel the desired compression level (-1 to 9, where -1 is
         *     {@link Deflater#DEFAULT_COMPRESSION})
         * @return this builder instance
         * @throws IllegalArgumentException if the compression level is invalid
         */
        public DeflateOutputStreamBuilder<P> setCompressionLevel(DeflateCompressionLevel compressionLevel) {
            if (compressionLevel.getValue() < -1 || compressionLevel.getValue() > 9) {
                throw new IllegalArgumentException("Invalid Deflate compression level: " + compressionLevel);
            }
            this.compressionLevel = compressionLevel.getValue();
            return this;
        }

        /**
         * Sets whether to include the Zlib header in the output stream.
         *
         * @param zlibHeader true to include the Zlib header, false otherwise
         * @return this builder instance
         */
        public DeflateOutputStreamBuilder<P> setZlibHeader(boolean zlibHeader) {
            this.zlibHeader = zlibHeader;
            return this;
        }

        /**
         * Builds and returns a {@link DeflateCompressorOutputStream} with the current configuration.
         *
         * @return a configured DeflateCompressorOutputStream
         */
        public DeflateCompressorOutputStream buildOutputStream() {
            DeflateParameters deflateParameters = new DeflateParameters();
            if (compressionLevel != Deflater.DEFAULT_COMPRESSION) {
                deflateParameters.setCompressionLevel(compressionLevel);
            }
            deflateParameters.setWithZlibHeader(zlibHeader);
            return new DeflateCompressorOutputStream(outputStream, deflateParameters);
        }

        /**
         * Returns the parent builder for further configuration.
         *
         * @return the parent builder
         */
        public P parentBuilder() {
            return parent;
        }
    }

    /**
     * Builder for configuring and creating a {@link DeflateCompressor}.
     *
     * @since 2.2
     */
    public static class DeflateCompressorBuilder
            extends CompressorBuilder<DeflateCompressorOutputStream, DeflateCompressorBuilder, DeflateCompressor> {
        private final DeflateOutputStreamBuilder<DeflateCompressorBuilder> compressorOutputStreamBuilder;

        /**
         * Constructs a builder for a DeflateCompressor using a file {@link Path}.
         *
         * @param path the file path to write compressed data to
         * @throws IOException if an I/O error occurs opening the file
         */
        public DeflateCompressorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Constructs a builder for a DeflateCompressor using an {@link OutputStream}.
         *
         * @param outputStream the output stream to write compressed data to
         */
        public DeflateCompressorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        private DeflateCompressorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new DeflateOutputStreamBuilder<>(this, outputStream);
        }

        /**
         * Returns the output stream builder for further configuration.
         *
         * @return the output stream builder
         */
        public DeflateOutputStreamBuilder<DeflateCompressorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /**
         * Returns this builder instance.
         *
         * @return this builder
         */
        @Override
        protected DeflateCompressorBuilder getThis() {
            return this;
        }

        /**
         * Builds and returns a configured {@link DeflateCompressorOutputStream}.
         *
         * @return a configured DeflateCompressorOutputStream
         * @throws IOException if an I/O error occurs during stream creation
         */
        @Override
        public DeflateCompressorOutputStream buildCompressorOutputStream() throws IOException {
            return compressorOutputStreamBuilder.buildOutputStream();
        }

        /**
         * Builds and returns a configured {@link DeflateCompressor}.
         *
         * @return a configured DeflateCompressor
         * @throws IOException if an I/O error occurs during stream creation
         */
        @Override
        public DeflateCompressor build() throws IOException {
            return new DeflateCompressor(this);
        }
    }
}
