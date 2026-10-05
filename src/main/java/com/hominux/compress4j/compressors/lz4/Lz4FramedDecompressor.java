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
package com.hominux.compress4j.compressors.lz4;

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;

/**
 * Provides framed LZ4 decompression that reads from a {@link FramedLZ4CompressorInputStream}.
 *
 * @since 3.2
 */
public class Lz4FramedDecompressor extends CodecDecompressor<FramedLZ4CompressorInputStream> {

    /**
     * Constructor that takes a FramedLZ4CompressorInputStream.
     *
     * @param inputStream the FramedLZ4CompressorInputStream to read from.
     */
    public Lz4FramedDecompressor(FramedLZ4CompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes a Lz4FramedDecompressorBuilder.
     *
     * @param builder the Lz4FramedDecompressorBuilder to build from.
     * @throws IOException if an I/O error occurs
     */
    public Lz4FramedDecompressor(Lz4FramedDecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link Lz4FramedDecompressorBuilder} reading from the given stream.
     *
     * @param inputStream the InputStream to read from
     * @return a new {@link Lz4FramedDecompressorBuilder}
     */
    public static Lz4FramedDecompressorBuilder builder(InputStream inputStream) {
        return new Lz4FramedDecompressorBuilder(inputStream);
    }

    /**
     * Creates a {@link Lz4FramedDecompressorBuilder} reading from the given path.
     *
     * @param path the Path to read from
     * @return a new {@link Lz4FramedDecompressorBuilder}
     * @throws IOException if an I/O error occurs while opening the path
     */
    public static Lz4FramedDecompressorBuilder builder(Path path) throws IOException {
        return new Lz4FramedDecompressorBuilder(path);
    }

    /**
     * Builder for creating a {@link FramedLZ4CompressorInputStream}.
     *
     * @param <P> the parent builder type
     */
    public static class Lz4FramedDecompressorInputStreamBuilder<P> {
        private final P parent;
        private final InputStream inputStream;
        private boolean decompressConcatenated = false;

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public Lz4FramedDecompressorInputStreamBuilder(P parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Sets whether to decompress concatenated LZ4 frames.
         *
         * @param decompressConcatenated true if concatenated frames should be decompressed, false otherwise.
         * @return this builder instance for method chaining.
         */
        public Lz4FramedDecompressorInputStreamBuilder<P> setDecompressConcatenated(boolean decompressConcatenated) {
            this.decompressConcatenated = decompressConcatenated;
            return this;
        }

        /**
         * Builds the {@link FramedLZ4CompressorInputStream}.
         *
         * @return a new FramedLZ4CompressorInputStream.
         * @throws IOException if an I/O error occurs.
         */
        public FramedLZ4CompressorInputStream buildInputStream() throws IOException {
            return new FramedLZ4CompressorInputStream(inputStream, decompressConcatenated);
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

    /** Builder for creating instances of {@link Lz4FramedDecompressor}. */
    public static class Lz4FramedDecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    FramedLZ4CompressorInputStream, Lz4FramedDecompressor, Lz4FramedDecompressorBuilder> {

        private final Lz4FramedDecompressorInputStreamBuilder<Lz4FramedDecompressorBuilder> inputStreamBuilder;

        /**
         * Constructor that takes an InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public Lz4FramedDecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private Lz4FramedDecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new Lz4FramedDecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while opening the path
         */
        public Lz4FramedDecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while opening the file
         */
        public Lz4FramedDecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the {@link Lz4FramedDecompressorInputStreamBuilder}
         */
        public Lz4FramedDecompressorInputStreamBuilder<Lz4FramedDecompressorBuilder> compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public FramedLZ4CompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /** {@inheritDoc} */
        @Override
        protected Lz4FramedDecompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public Lz4FramedDecompressor build() throws IOException {
            return new Lz4FramedDecompressor(this);
        }
    }
}
