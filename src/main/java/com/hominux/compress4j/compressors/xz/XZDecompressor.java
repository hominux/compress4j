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
package com.hominux.compress4j.compressors.xz;

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

/**
 * Provides XZ decompression functionality that reads from an {@link XZCompressorInputStream}. This class extends the
 * {@link CodecDecompressor} base class and supports decompressing XZ-compressed data with optional support for
 * concatenated streams and memory limits.
 *
 * <p>Use the builder pattern to configure and create instances with custom decompression options.
 *
 * @since 2.2
 */
public class XZDecompressor extends CodecDecompressor<XZCompressorInputStream> {

    /**
     * Constructor that takes an XZCompressorInputStream.
     *
     * @param inputStream the XZCompressorInputStream to read from.
     */
    public XZDecompressor(XZCompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes an XZDecompressorBuilder.
     *
     * @param builder the XZDecompressorBuilder to build from.
     * @throws IOException thrown by the underlying output stream for I/O errors
     */
    public XZDecompressor(XZDecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates an XZDecompressorBuilder using the provided InputStream.
     *
     * @param inputStream the InputStream to read from
     * @return a new XZDecompressorBuilder
     */
    public static XZDecompressorBuilder builder(InputStream inputStream) {
        return new XZDecompressorBuilder(inputStream);
    }

    /**
     * Creates an XZDecompressorBuilder using the provided Path.
     *
     * @param path the Path to read from
     * @return a new XZDecompressorBuilder
     * @throws IOException if an I/O error occurs while creating the input stream
     */
    public static XZDecompressorBuilder builder(Path path) throws IOException {
        return new XZDecompressorBuilder(path);
    }

    /**
     * Builder for creating an {@link XZCompressorInputStream} using Apache Commons Compress builder.
     *
     * @param <P> the parent builder type
     * @since 2.3
     */
    public static class XZDecompressorInputStreamBuilder<P> {
        private static final int NO_MEMORY_LIMIT = -1;

        private final P parent;
        private final InputStream inputStream;
        private boolean decompressConcatenated = false;
        private int memoryLimitInKb = NO_MEMORY_LIMIT;

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public XZDecompressorInputStreamBuilder(P parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Sets whether to decompress concatenated XZ streams.
         *
         * @param decompressConcatenated true if concatenated streams should be decompressed, false otherwise.
         * @return this builder instance for method chaining.
         */
        public XZDecompressorInputStreamBuilder<P> setDecompressConcatenated(boolean decompressConcatenated) {
            this.decompressConcatenated = decompressConcatenated;
            return this;
        }

        /**
         * Configures the memory limit for the decompressor.
         *
         * @param memoryLimitInKb memory limit in KiB, use -1 for no limit (default)
         * @return this builder instance for method chaining.
         * @throws IllegalArgumentException if memoryLimitInKb is zero or smaller than -1.
         */
        public XZDecompressorInputStreamBuilder<P> setMemoryLimitInKb(int memoryLimitInKb) {
            if (memoryLimitInKb <= 0 && memoryLimitInKb != -1) {
                throw new IllegalArgumentException("Memory limit must be positive or -1");
            }
            this.memoryLimitInKb = memoryLimitInKb;
            return this;
        }

        /**
         * Builds the XZCompressorInputStream using the provided InputStream and options via the Apache Commons Compress
         * builder.
         *
         * @return a new XZCompressorInputStream.
         * @throws IOException if an I/O error occurs while creating the input stream.
         */
        public XZCompressorInputStream buildInputStream() throws IOException {
            return XZCompressorInputStream.builder()
                    .setInputStream(inputStream)
                    .setDecompressConcatenated(decompressConcatenated)
                    .setMemoryLimitKiB(memoryLimitInKb)
                    .get();
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
     * Builder for creating instances of {@link XZDecompressor}.
     *
     * @since 2.3
     */
    public static class XZDecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    XZCompressorInputStream, XZDecompressor, XZDecompressorBuilder> {

        private final XZDecompressorInputStreamBuilder<XZDecompressorBuilder> inputStreamBuilder;
        /**
         * Constructor that takes an InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public XZDecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        private XZDecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new XZDecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while creating the input stream
         */
        public XZDecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while creating the input stream
         */
        public XZDecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the XZDecompressorInputStreamBuilder instance
         */
        public XZDecompressorInputStreamBuilder<XZDecompressorBuilder> compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /**
         * Builds an XZCompressorInputStream using the current configuration.
         *
         * @return a new XZCompressorInputStream instance
         * @throws IOException if an I/O error occurs while creating the input stream
         */
        @Override
        public XZCompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /**
         * Returns the current builder instance for method chaining.
         *
         * @return this builder instance
         */
        @Override
        protected XZDecompressorBuilder getThis() {
            return this;
        }

        /**
         * Builds an XZDecompressor using the current configuration.
         *
         * @return a new XZDecompressor instance
         * @throws IOException if an I/O error occurs while creating the decompressor
         */
        @Override
        public XZDecompressor build() throws IOException {
            return new XZDecompressor(this);
        }
    }
}
