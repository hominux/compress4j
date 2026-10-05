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
package com.hominux.compress4j.compressors.pack200;

import com.hominux.compress4j.compressors.CodecCompressor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorOutputStream;
import org.apache.commons.compress.compressors.pack200.Pack200Strategy;

/**
 * Provides Pack200 compression functionality that writes to a {@link Pack200CompressorOutputStream}. This class extends
 * the {@link CodecCompressor} base class and supports Pack200 compression for JAR files.
 *
 * <p>Pack200 is a compression format specifically designed for Java Archive (JAR) files. It can achieve better
 * compression ratios than general-purpose compression algorithms when compressing JAR files.
 *
 * <p>Use the builder pattern to configure compression options such as caching strategy before creating instances.
 *
 * @since 2.2
 */
public class Pack200Compressor extends CodecCompressor<Pack200CompressorOutputStream> {
    /**
     * Constructor that takes a Pack200CompressorOutputStream.
     *
     * @param compressorOutputStream the Pack200CompressorOutputStream to write to.
     */
    public Pack200Compressor(Pack200CompressorOutputStream compressorOutputStream) {
        super(compressorOutputStream);
    }

    /**
     * Constructor that takes a Pack200CompressorBuilder.
     *
     * @param builder the Pack200CompressorBuilder to build from.
     * @throws IOException if an I/O error occurred
     */
    public Pack200Compressor(Pack200CompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Helper static method to create an instance of the {@link Pack200CompressorBuilder}
     *
     * @param path the path to write the compressor to
     * @return An instance of the {@link Pack200CompressorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static Pack200CompressorBuilder builder(Path path) throws IOException {
        return new Pack200CompressorBuilder(path);
    }

    /**
     * Helper static method to create an instance of the {@link Pack200CompressorBuilder}
     *
     * @param outputStream the output stream
     * @return An instance of the {@link Pack200CompressorBuilder}
     */
    public static Pack200CompressorBuilder builder(OutputStream outputStream) {
        return new Pack200CompressorBuilder(outputStream);
    }

    /**
     * Builder class for creating a {@link Pack200CompressorOutputStream}.
     *
     * @param <P> The type of the parent builder.
     */
    public static class Pack200CompressorOutputStreamBuilder<P> {
        private final P parent;
        private Pack200Strategy mode = Pack200Strategy.IN_MEMORY;
        private Map<String, String> properties = Collections.emptyMap();

        /** The output stream to write to. */
        protected final OutputStream outputStream;

        /**
         * Create a new {@link Pack200CompressorOutputStreamBuilder} with the given parent and output stream.
         *
         * @param parent the parent builder
         * @param outputStream the output stream to write to
         */
        public Pack200CompressorOutputStreamBuilder(P parent, OutputStream outputStream) {
            this.parent = parent;
            this.outputStream = outputStream;
        }

        /**
         * Set the caching strategy for Pack200 compression.
         *
         * @param mode the Pack200Strategy to use (IN_MEMORY or TEMP_FILE)
         * @return the instance of the {@link Pack200CompressorOutputStreamBuilder}
         */
        public Pack200CompressorOutputStreamBuilder<P> mode(Pack200Strategy mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Set the Pack200 properties.
         *
         * @param properties the Pack200 properties map
         * @return the instance of the {@link Pack200CompressorOutputStreamBuilder}
         */
        public Pack200CompressorOutputStreamBuilder<P> properties(Map<String, String> properties) {
            this.properties = properties != null ? properties : Collections.emptyMap();
            return this;
        }

        /**
         * Builds the {@link Pack200CompressorOutputStream} with the configured parameters.
         *
         * @return the {@link Pack200CompressorOutputStream} instance
         * @throws IOException if an I/O error occurred
         */
        public Pack200CompressorOutputStream build() throws IOException {
            return new Pack200CompressorOutputStream(outputStream, mode, properties);
        }

        /**
         * Returns the parent builder.
         *
         * @return the parent builder
         */
        public P parentBuilder() {
            return parent;
        }
    }

    /** Builder class for creating a {@link Pack200Compressor}. */
    public static class Pack200CompressorBuilder
            extends CompressorBuilder<Pack200CompressorOutputStream, Pack200CompressorBuilder, Pack200Compressor> {

        private final Pack200CompressorOutputStreamBuilder<Pack200CompressorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link Pack200CompressorBuilder} with the given path.
         *
         * @param path the path to write the compressor to
         * @throws IOException if an I/O error occurred
         */
        public Pack200CompressorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link Pack200CompressorBuilder} with the given output stream.
         *
         * @param outputStream the output stream to write the compressor to
         */
        public Pack200CompressorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        private Pack200CompressorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new Pack200CompressorOutputStreamBuilder<>(this, outputStream);
        }

        /**
         * Returns the compressor output stream builder to set options.
         *
         * @return the compressor output stream builder
         */
        public Pack200CompressorOutputStreamBuilder<Pack200CompressorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /**
         * Returns this builder instance for method chaining.
         *
         * @return this builder instance
         */
        @Override
        public Pack200CompressorBuilder getThis() {
            return this;
        }

        /**
         * Builds the Pack200CompressorOutputStream with the configured parameters.
         *
         * @return the {@link Pack200CompressorOutputStream} instance
         * @throws IOException if an I/O error occurred
         */
        @Override
        public Pack200CompressorOutputStream buildCompressorOutputStream() throws IOException {
            return compressorOutputStreamBuilder.build();
        }

        /**
         * Builds the Pack200Compressor instance.
         *
         * @return a new Pack200Compressor instance
         * @throws IOException if an I/O error occurred
         */
        @Override
        public Pack200Compressor build() throws IOException {
            return new Pack200Compressor(this);
        }
    }
}
