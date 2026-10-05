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
package com.hominux.compress4j.compressors.zstd;

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import com.hominux.compress4j.utils.ArchiverDependencyChecker;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

/**
 * Provides Zstandard decompression that reads from a {@link ZstdCompressorInputStream}. Requires
 * {@code com.github.luben:zstd-jni} at runtime.
 *
 * @since 3.2
 */
public class ZstdDecompressor extends CodecDecompressor<ZstdCompressorInputStream> {

    /**
     * Constructor that takes a ZstdCompressorInputStream.
     *
     * @param inputStream the ZstdCompressorInputStream to read from.
     */
    public ZstdDecompressor(ZstdCompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes a ZstdDecompressorBuilder.
     *
     * @param builder the ZstdDecompressorBuilder to build from.
     * @throws IOException if an I/O error occurs
     */
    public ZstdDecompressor(ZstdDecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link ZstdDecompressorBuilder} reading from the given stream.
     *
     * @param inputStream the InputStream to read from
     * @return a new {@link ZstdDecompressorBuilder}
     */
    public static ZstdDecompressorBuilder builder(InputStream inputStream) {
        return new ZstdDecompressorBuilder(inputStream);
    }

    /**
     * Creates a {@link ZstdDecompressorBuilder} reading from the given path.
     *
     * @param path the Path to read from
     * @return a new {@link ZstdDecompressorBuilder}
     * @throws IOException if an I/O error occurs while opening the path
     */
    public static ZstdDecompressorBuilder builder(Path path) throws IOException {
        return new ZstdDecompressorBuilder(path);
    }

    /**
     * Builder for creating a {@link ZstdCompressorInputStream}.
     *
     * @param <P> the parent builder type
     */
    public static class ZstdDecompressorInputStreamBuilder<P> {
        private final P parent;
        private final InputStream inputStream;

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public ZstdDecompressorInputStreamBuilder(P parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Builds the {@link ZstdCompressorInputStream}.
         *
         * @return a new ZstdCompressorInputStream.
         * @throws IOException if an I/O error occurs.
         * @throws com.hominux.compress4j.exceptions.MissingArchiveDependencyException if zstd-jni is not available
         */
        public ZstdCompressorInputStream buildInputStream() throws IOException {
            ArchiverDependencyChecker.checkZstd();
            return new ZstdCompressorInputStream(inputStream);
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

    /** Builder for creating instances of {@link ZstdDecompressor}. */
    public static class ZstdDecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    ZstdCompressorInputStream, ZstdDecompressor, ZstdDecompressorBuilder> {

        private final ZstdDecompressorInputStreamBuilder<ZstdDecompressorBuilder> inputStreamBuilder;

        /**
         * Constructor that takes an InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public ZstdDecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private ZstdDecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new ZstdDecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while opening the path
         */
        public ZstdDecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while opening the file
         */
        public ZstdDecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the {@link ZstdDecompressorInputStreamBuilder}
         */
        public ZstdDecompressorInputStreamBuilder<ZstdDecompressorBuilder> compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public ZstdCompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /** {@inheritDoc} */
        @Override
        protected ZstdDecompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public ZstdDecompressor build() throws IOException {
            return new ZstdDecompressor(this);
        }
    }
}
