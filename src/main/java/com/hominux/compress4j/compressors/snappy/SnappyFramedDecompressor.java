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
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream;

/**
 * Provides framed Snappy decompression that reads from a {@link FramedSnappyCompressorInputStream}.
 *
 * @since 3.2
 */
public class SnappyFramedDecompressor extends CodecDecompressor<FramedSnappyCompressorInputStream> {

    /**
     * Constructor that takes a FramedSnappyCompressorInputStream.
     *
     * @param inputStream the FramedSnappyCompressorInputStream to read from.
     */
    public SnappyFramedDecompressor(FramedSnappyCompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes a SnappyFramedDecompressorBuilder.
     *
     * @param builder the SnappyFramedDecompressorBuilder to build from.
     * @throws IOException if an I/O error occurs
     */
    public SnappyFramedDecompressor(SnappyFramedDecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link SnappyFramedDecompressorBuilder} reading from the given stream.
     *
     * @param inputStream the InputStream to read from
     * @return a new {@link SnappyFramedDecompressorBuilder}
     */
    public static SnappyFramedDecompressorBuilder builder(InputStream inputStream) {
        return new SnappyFramedDecompressorBuilder(inputStream);
    }

    /**
     * Creates a {@link SnappyFramedDecompressorBuilder} reading from the given path.
     *
     * @param path the Path to read from
     * @return a new {@link SnappyFramedDecompressorBuilder}
     * @throws IOException if an I/O error occurs while opening the path
     */
    public static SnappyFramedDecompressorBuilder builder(Path path) throws IOException {
        return new SnappyFramedDecompressorBuilder(path);
    }

    /**
     * Builder for creating a {@link FramedSnappyCompressorInputStream}.
     *
     * @param <P> the parent builder type
     */
    public static class SnappyFramedDecompressorInputStreamBuilder<P> {
        private final P parent;
        private final InputStream inputStream;

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public SnappyFramedDecompressorInputStreamBuilder(P parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Builds the {@link FramedSnappyCompressorInputStream}.
         *
         * @return a new FramedSnappyCompressorInputStream.
         * @throws IOException if an I/O error occurs.
         */
        public FramedSnappyCompressorInputStream buildInputStream() throws IOException {
            return new FramedSnappyCompressorInputStream(inputStream);
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

    /** Builder for creating instances of {@link SnappyFramedDecompressor}. */
    public static class SnappyFramedDecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    FramedSnappyCompressorInputStream, SnappyFramedDecompressor, SnappyFramedDecompressorBuilder> {

        private final SnappyFramedDecompressorInputStreamBuilder<SnappyFramedDecompressorBuilder> inputStreamBuilder;

        /**
         * Constructor that takes an InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public SnappyFramedDecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private SnappyFramedDecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new SnappyFramedDecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while opening the path
         */
        public SnappyFramedDecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while opening the file
         */
        public SnappyFramedDecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the {@link SnappyFramedDecompressorInputStreamBuilder}
         */
        public SnappyFramedDecompressorInputStreamBuilder<SnappyFramedDecompressorBuilder>
                compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public FramedSnappyCompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /** {@inheritDoc} */
        @Override
        protected SnappyFramedDecompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public SnappyFramedDecompressor build() throws IOException {
            return new SnappyFramedDecompressor(this);
        }
    }
}
