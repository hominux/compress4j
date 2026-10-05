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

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200Strategy;

/**
 * Provides Pack200 decompression functionality that reads from a {@link Pack200CompressorInputStream}. This class
 * extends the {@link CodecDecompressor} base class and supports decompressing Pack200-compressed JAR files.
 *
 * <p>Pack200 is a compression format specifically designed for Java Archive (JAR) files. This decompressor can restore
 * JAR files that have been compressed with Pack200.
 *
 * <p>Use the builder pattern to configure decompression options such as caching strategy before creating instances.
 *
 * @since 2.2
 */
public class Pack200Decompressor extends CodecDecompressor<Pack200CompressorInputStream> {

    /**
     * Constructor that takes a Pack200CompressorInputStream.
     *
     * @param inputStream the Pack200CompressorInputStream to read from.
     */
    public Pack200Decompressor(Pack200CompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes a Pack200DecompressorBuilder.
     *
     * @param builder the Pack200DecompressorBuilder to build from.
     * @throws IOException thrown by the underlying output stream for I/O errors
     */
    public Pack200Decompressor(Pack200DecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a Pack200DecompressorBuilder using the provided InputStream.
     *
     * @param inputStream the InputStream to read from
     * @return a new Pack200DecompressorBuilder
     */
    public static Pack200DecompressorBuilder builder(InputStream inputStream) {
        return new Pack200DecompressorBuilder(inputStream);
    }

    /**
     * Creates a Pack200DecompressorBuilder using the provided Path.
     *
     * @param path the Path to read from
     * @return a new Pack200DecompressorBuilder
     * @throws IOException if an I/O error occurs while creating the input stream
     */
    public static Pack200DecompressorBuilder builder(Path path) throws IOException {
        return new Pack200DecompressorBuilder(path);
    }

    /**
     * Pack200Decompressor Builder
     *
     * @since 2.2
     */
    public static class Pack200DecompressorInputStreamBuilder {
        private final Pack200DecompressorBuilder parent;
        private final InputStream inputStream;
        private Pack200Strategy mode = Pack200Strategy.IN_MEMORY;
        private Map<String, String> properties = Collections.emptyMap();

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public Pack200DecompressorInputStreamBuilder(Pack200DecompressorBuilder parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Sets the caching strategy for Pack200 decompression.
         *
         * @param mode the Pack200Strategy to use (IN_MEMORY or TEMP_FILE)
         * @return this builder instance for method chaining.
         */
        @SuppressWarnings("UnusedReturnValue")
        public Pack200DecompressorInputStreamBuilder mode(Pack200Strategy mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Set the Pack200 properties.
         *
         * @param properties the Pack200 properties map
         * @return the instance of the {@link Pack200DecompressorInputStreamBuilder}
         */
        public Pack200DecompressorInputStreamBuilder properties(Map<String, String> properties) {
            this.properties = properties != null ? properties : Collections.emptyMap();
            return this;
        }

        /**
         * Builds the Pack200CompressorInputStream using the provided InputStream and options.
         *
         * @return a new Pack200CompressorInputStream.
         * @throws IOException if an I/O error occurs while creating the input stream.
         */
        public Pack200CompressorInputStream buildInputStream() throws IOException {
            return new Pack200CompressorInputStream(inputStream, mode, properties);
        }

        /**
         * Returns the parent builder.
         *
         * @return the parent builder.
         */
        public Pack200DecompressorBuilder parentBuilder() {
            return parent;
        }
    }

    /**
     * Builder for creating instances of {@link Pack200Decompressor}.
     *
     * @since 2.2
     */
    public static class Pack200DecompressorBuilder
            extends DecompressorBuilder<Pack200CompressorInputStream, Pack200Decompressor, Pack200DecompressorBuilder> {

        private final Pack200DecompressorInputStreamBuilder inputStreamBuilder;

        /**
         * Constructor that takes a InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public Pack200DecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        private Pack200DecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new Pack200DecompressorInputStreamBuilder(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while opening the path
         */
        public Pack200DecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the Pack200DecompressorInputStreamBuilder instance
         */
        public Pack200DecompressorInputStreamBuilder compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /**
         * Builds a Pack200CompressorInputStream using the current configuration.
         *
         * @return a new Pack200CompressorInputStream instance
         * @throws IOException if an I/O error occurs while creating the input stream
         */
        @Override
        public Pack200CompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /**
         * Returns the current builder instance for method chaining.
         *
         * @return this builder instance
         */
        @Override
        protected Pack200DecompressorBuilder getThis() {
            return this;
        }

        /**
         * Builds a Pack200Decompressor using the current configuration.
         *
         * @return a new Pack200Decompressor instance
         * @throws IOException if an I/O error occurs while creating the decompressor
         */
        @Override
        public Pack200Decompressor build() throws IOException {
            return new Pack200Decompressor(this);
        }
    }
}
