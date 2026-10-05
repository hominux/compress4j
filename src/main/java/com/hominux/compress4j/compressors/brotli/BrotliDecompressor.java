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
package com.hominux.compress4j.compressors.brotli;

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import com.hominux.compress4j.utils.ArchiverDependencyChecker;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream;

/**
 * Read-only: Compress4J provides no creator for this format.
 *
 * <p>Provides Brotli decompression that reads from a {@link BrotliCompressorInputStream}. Requires
 * {@code org.brotli:dec} at runtime.
 *
 * @since 3.2
 */
public class BrotliDecompressor extends CodecDecompressor<BrotliCompressorInputStream> {

    /**
     * Constructor that takes a BrotliCompressorInputStream.
     *
     * @param inputStream the BrotliCompressorInputStream to read from.
     */
    public BrotliDecompressor(BrotliCompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes a BrotliDecompressorBuilder.
     *
     * @param builder the BrotliDecompressorBuilder to build from.
     * @throws IOException if an I/O error occurs
     */
    public BrotliDecompressor(BrotliDecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link BrotliDecompressorBuilder} reading from the given stream.
     *
     * @param inputStream the InputStream to read from
     * @return a new {@link BrotliDecompressorBuilder}
     */
    public static BrotliDecompressorBuilder builder(InputStream inputStream) {
        return new BrotliDecompressorBuilder(inputStream);
    }

    /**
     * Creates a {@link BrotliDecompressorBuilder} reading from the given path.
     *
     * @param path the Path to read from
     * @return a new {@link BrotliDecompressorBuilder}
     * @throws IOException if an I/O error occurs while opening the path
     */
    public static BrotliDecompressorBuilder builder(Path path) throws IOException {
        return new BrotliDecompressorBuilder(path);
    }

    /**
     * Builder for creating a {@link BrotliCompressorInputStream}.
     *
     * @param <P> the parent builder type
     */
    public static class BrotliDecompressorInputStreamBuilder<P> {
        private final P parent;
        private final InputStream inputStream;

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public BrotliDecompressorInputStreamBuilder(P parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Builds the {@link BrotliCompressorInputStream}.
         *
         * @return a new BrotliCompressorInputStream.
         * @throws IOException if an I/O error occurs.
         * @throws com.hominux.compress4j.exceptions.MissingArchiveDependencyException if org.brotli:dec is not
         *     available
         */
        public BrotliCompressorInputStream buildInputStream() throws IOException {
            ArchiverDependencyChecker.checkBrotli();
            return new BrotliCompressorInputStream(inputStream);
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

    /** Builder for creating instances of {@link BrotliDecompressor}. */
    public static class BrotliDecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    BrotliCompressorInputStream, BrotliDecompressor, BrotliDecompressorBuilder> {

        private final BrotliDecompressorInputStreamBuilder<BrotliDecompressorBuilder> inputStreamBuilder;

        /**
         * Constructor that takes an InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public BrotliDecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private BrotliDecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new BrotliDecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while opening the path
         */
        public BrotliDecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while opening the file
         */
        public BrotliDecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the {@link BrotliDecompressorInputStreamBuilder}
         */
        public BrotliDecompressorInputStreamBuilder<BrotliDecompressorBuilder> compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public BrotliCompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /** {@inheritDoc} */
        @Override
        protected BrotliDecompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public BrotliDecompressor build() throws IOException {
            return new BrotliDecompressor(this);
        }
    }
}
