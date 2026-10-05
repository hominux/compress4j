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
import org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorInputStream;

/**
 * Provides block LZ4 decompression that reads from a {@link BlockLZ4CompressorInputStream}.
 *
 * @since 3.2
 */
public class Lz4BlockDecompressor extends CodecDecompressor<BlockLZ4CompressorInputStream> {

    /**
     * Constructor that takes a BlockLZ4CompressorInputStream.
     *
     * @param inputStream the BlockLZ4CompressorInputStream to read from.
     */
    public Lz4BlockDecompressor(BlockLZ4CompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes a Lz4BlockDecompressorBuilder.
     *
     * @param builder the Lz4BlockDecompressorBuilder to build from.
     * @throws IOException if an I/O error occurs
     */
    public Lz4BlockDecompressor(Lz4BlockDecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link Lz4BlockDecompressorBuilder} reading from the given stream.
     *
     * @param inputStream the InputStream to read from
     * @return a new {@link Lz4BlockDecompressorBuilder}
     */
    public static Lz4BlockDecompressorBuilder builder(InputStream inputStream) {
        return new Lz4BlockDecompressorBuilder(inputStream);
    }

    /**
     * Creates a {@link Lz4BlockDecompressorBuilder} reading from the given path.
     *
     * @param path the Path to read from
     * @return a new {@link Lz4BlockDecompressorBuilder}
     * @throws IOException if an I/O error occurs while opening the path
     */
    public static Lz4BlockDecompressorBuilder builder(Path path) throws IOException {
        return new Lz4BlockDecompressorBuilder(path);
    }

    /**
     * Builder for creating a {@link BlockLZ4CompressorInputStream}.
     *
     * @param <P> the parent builder type
     */
    public static class Lz4BlockDecompressorInputStreamBuilder<P> {
        private final P parent;
        private final InputStream inputStream;

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public Lz4BlockDecompressorInputStreamBuilder(P parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Builds the {@link BlockLZ4CompressorInputStream}.
         *
         * @return a new BlockLZ4CompressorInputStream.
         * @throws IOException if an I/O error occurs.
         */
        public BlockLZ4CompressorInputStream buildInputStream() throws IOException {
            return new BlockLZ4CompressorInputStream(inputStream);
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

    /** Builder for creating instances of {@link Lz4BlockDecompressor}. */
    public static class Lz4BlockDecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    BlockLZ4CompressorInputStream, Lz4BlockDecompressor, Lz4BlockDecompressorBuilder> {

        private final Lz4BlockDecompressorInputStreamBuilder<Lz4BlockDecompressorBuilder> inputStreamBuilder;

        /**
         * Constructor that takes an InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public Lz4BlockDecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private Lz4BlockDecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new Lz4BlockDecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while opening the path
         */
        public Lz4BlockDecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while opening the file
         */
        public Lz4BlockDecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the {@link Lz4BlockDecompressorInputStreamBuilder}
         */
        public Lz4BlockDecompressorInputStreamBuilder<Lz4BlockDecompressorBuilder> compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public BlockLZ4CompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /** {@inheritDoc} */
        @Override
        protected Lz4BlockDecompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public Lz4BlockDecompressor build() throws IOException {
            return new Lz4BlockDecompressor(this);
        }
    }
}
