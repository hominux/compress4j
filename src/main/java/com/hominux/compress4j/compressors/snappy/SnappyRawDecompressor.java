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
package com.hominux.compress4j.compressors.snappy;

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.snappy.SnappyCompressorInputStream;

/**
 * Provides raw Snappy decompression that reads from a {@link SnappyCompressorInputStream}.
 *
 * @since 3.2
 */
public class SnappyRawDecompressor extends CodecDecompressor<SnappyCompressorInputStream> {

    /**
     * Constructor that takes a SnappyCompressorInputStream.
     *
     * @param inputStream the SnappyCompressorInputStream to read from.
     */
    public SnappyRawDecompressor(SnappyCompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes a SnappyRawDecompressorBuilder.
     *
     * @param builder the SnappyRawDecompressorBuilder to build from.
     * @throws IOException if an I/O error occurs
     */
    public SnappyRawDecompressor(SnappyRawDecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link SnappyRawDecompressorBuilder} reading from the given stream.
     *
     * @param inputStream the InputStream to read from
     * @return a new {@link SnappyRawDecompressorBuilder}
     */
    public static SnappyRawDecompressorBuilder builder(InputStream inputStream) {
        return new SnappyRawDecompressorBuilder(inputStream);
    }

    /**
     * Creates a {@link SnappyRawDecompressorBuilder} reading from the given path.
     *
     * @param path the Path to read from
     * @return a new {@link SnappyRawDecompressorBuilder}
     * @throws IOException if an I/O error occurs while opening the path
     */
    public static SnappyRawDecompressorBuilder builder(Path path) throws IOException {
        return new SnappyRawDecompressorBuilder(path);
    }

    /**
     * Builder for creating a {@link SnappyCompressorInputStream}.
     *
     * @param <P> the parent builder type
     */
    public static class SnappyRawDecompressorInputStreamBuilder<P> {
        private final P parent;
        private final InputStream inputStream;

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public SnappyRawDecompressorInputStreamBuilder(P parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Builds the {@link SnappyCompressorInputStream}.
         *
         * @return a new SnappyCompressorInputStream.
         * @throws IOException if an I/O error occurs.
         */
        public SnappyCompressorInputStream buildInputStream() throws IOException {
            return new SnappyCompressorInputStream(inputStream);
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

    /** Builder for creating instances of {@link SnappyRawDecompressor}. */
    public static class SnappyRawDecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    SnappyCompressorInputStream, SnappyRawDecompressor, SnappyRawDecompressorBuilder> {

        private final SnappyRawDecompressorInputStreamBuilder<SnappyRawDecompressorBuilder> inputStreamBuilder;

        /**
         * Constructor that takes an InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public SnappyRawDecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private SnappyRawDecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new SnappyRawDecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while opening the path
         */
        public SnappyRawDecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while opening the file
         */
        public SnappyRawDecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the {@link SnappyRawDecompressorInputStreamBuilder}
         */
        public SnappyRawDecompressorInputStreamBuilder<SnappyRawDecompressorBuilder> compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public SnappyCompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /** {@inheritDoc} */
        @Override
        protected SnappyRawDecompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public SnappyRawDecompressor build() throws IOException {
            return new SnappyRawDecompressor(this);
        }
    }
}
