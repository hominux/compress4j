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
package com.hominux.compress4j.compressors.deflate64;

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.deflate64.Deflate64CompressorInputStream;

/**
 * Read-only: Compress4J provides no creator for this format.
 *
 * <p>Provides Deflate64 decompression that reads from a {@link Deflate64CompressorInputStream}.
 *
 * @since 3.2
 */
public class Deflate64Decompressor extends CodecDecompressor<Deflate64CompressorInputStream> {

    /**
     * Constructor that takes a Deflate64CompressorInputStream.
     *
     * @param inputStream the Deflate64CompressorInputStream to read from.
     */
    public Deflate64Decompressor(Deflate64CompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes a Deflate64DecompressorBuilder.
     *
     * @param builder the Deflate64DecompressorBuilder to build from.
     * @throws IOException if an I/O error occurs
     */
    public Deflate64Decompressor(Deflate64DecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link Deflate64DecompressorBuilder} reading from the given stream.
     *
     * @param inputStream the InputStream to read from
     * @return a new {@link Deflate64DecompressorBuilder}
     */
    public static Deflate64DecompressorBuilder builder(InputStream inputStream) {
        return new Deflate64DecompressorBuilder(inputStream);
    }

    /**
     * Creates a {@link Deflate64DecompressorBuilder} reading from the given path.
     *
     * @param path the Path to read from
     * @return a new {@link Deflate64DecompressorBuilder}
     * @throws IOException if an I/O error occurs while opening the path
     */
    public static Deflate64DecompressorBuilder builder(Path path) throws IOException {
        return new Deflate64DecompressorBuilder(path);
    }

    /**
     * Builder for creating a {@link Deflate64CompressorInputStream}.
     *
     * @param <P> the parent builder type
     */
    public static class Deflate64DecompressorInputStreamBuilder<P> {
        private final P parent;
        private final InputStream inputStream;

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public Deflate64DecompressorInputStreamBuilder(P parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Builds the {@link Deflate64CompressorInputStream}.
         *
         * @return a new Deflate64CompressorInputStream.
         * @throws IOException if an I/O error occurs.
         */
        public Deflate64CompressorInputStream buildInputStream() throws IOException {
            return new Deflate64CompressorInputStream(inputStream);
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

    /** Builder for creating instances of {@link Deflate64Decompressor}. */
    public static class Deflate64DecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    Deflate64CompressorInputStream, Deflate64Decompressor, Deflate64DecompressorBuilder> {

        private final Deflate64DecompressorInputStreamBuilder<Deflate64DecompressorBuilder> inputStreamBuilder;

        /**
         * Constructor that takes an InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public Deflate64DecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private Deflate64DecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new Deflate64DecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while opening the path
         */
        public Deflate64DecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while opening the file
         */
        public Deflate64DecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the {@link Deflate64DecompressorInputStreamBuilder}
         */
        public Deflate64DecompressorInputStreamBuilder<Deflate64DecompressorBuilder> compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public Deflate64CompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /** {@inheritDoc} */
        @Override
        protected Deflate64DecompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public Deflate64Decompressor build() throws IOException {
            return new Deflate64Decompressor(this);
        }
    }
}
