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
package com.hominux.compress4j.compressors.z;

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;

/**
 * Read-only: Compress4J provides no creator for this format.
 *
 * <p>Provides Unix compress (.Z) decompression that reads from a {@link ZCompressorInputStream}.
 *
 * @since 3.2
 */
public class ZDecompressor extends CodecDecompressor<ZCompressorInputStream> {

    /**
     * Constructor that takes a ZCompressorInputStream.
     *
     * @param inputStream the ZCompressorInputStream to read from.
     */
    public ZDecompressor(ZCompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes a ZDecompressorBuilder.
     *
     * @param builder the ZDecompressorBuilder to build from.
     * @throws IOException if an I/O error occurs
     */
    public ZDecompressor(ZDecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link ZDecompressorBuilder} reading from the given stream.
     *
     * @param inputStream the InputStream to read from
     * @return a new {@link ZDecompressorBuilder}
     */
    public static ZDecompressorBuilder builder(InputStream inputStream) {
        return new ZDecompressorBuilder(inputStream);
    }

    /**
     * Creates a {@link ZDecompressorBuilder} reading from the given path.
     *
     * @param path the Path to read from
     * @return a new {@link ZDecompressorBuilder}
     * @throws IOException if an I/O error occurs while opening the path
     */
    public static ZDecompressorBuilder builder(Path path) throws IOException {
        return new ZDecompressorBuilder(path);
    }

    /**
     * Builder for creating a {@link ZCompressorInputStream}.
     *
     * @param <P> the parent builder type
     */
    public static class ZDecompressorInputStreamBuilder<P> {
        private final P parent;
        private final InputStream inputStream;

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public ZDecompressorInputStreamBuilder(P parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Builds the {@link ZCompressorInputStream}.
         *
         * @return a new ZCompressorInputStream.
         * @throws IOException if an I/O error occurs.
         */
        public ZCompressorInputStream buildInputStream() throws IOException {
            return new ZCompressorInputStream(inputStream);
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

    /** Builder for creating instances of {@link ZDecompressor}. */
    public static class ZDecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<ZCompressorInputStream, ZDecompressor, ZDecompressorBuilder> {

        private final ZDecompressorInputStreamBuilder<ZDecompressorBuilder> inputStreamBuilder;

        /**
         * Constructor that takes an InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public ZDecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private ZDecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new ZDecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while opening the path
         */
        public ZDecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while opening the file
         */
        public ZDecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the {@link ZDecompressorInputStreamBuilder}
         */
        public ZDecompressorInputStreamBuilder<ZDecompressorBuilder> compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public ZCompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /** {@inheritDoc} */
        @Override
        protected ZDecompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public ZDecompressor build() throws IOException {
            return new ZDecompressor(this);
        }
    }
}
