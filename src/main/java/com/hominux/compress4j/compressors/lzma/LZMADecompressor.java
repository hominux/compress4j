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
package com.hominux.compress4j.compressors.lzma;

import static java.nio.file.Files.newInputStream;

import com.hominux.compress4j.compressors.CodecDecompressor;
import com.hominux.compress4j.utils.ArchiverDependencyChecker;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;

/**
 * Provides LZMA decompression that reads from a {@link LZMACompressorInputStream}. Requires {@code org.tukaani:xz} at
 * runtime.
 *
 * @since 3.2
 */
public class LZMADecompressor extends CodecDecompressor<LZMACompressorInputStream> {

    /**
     * Constructor that takes a LZMACompressorInputStream.
     *
     * @param inputStream the LZMACompressorInputStream to read from.
     */
    public LZMADecompressor(LZMACompressorInputStream inputStream) {
        super(inputStream);
    }

    /**
     * Constructor that takes a LZMADecompressorBuilder.
     *
     * @param builder the LZMADecompressorBuilder to build from.
     * @throws IOException if an I/O error occurs
     */
    public LZMADecompressor(LZMADecompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a {@link LZMADecompressorBuilder} reading from the given stream.
     *
     * @param inputStream the InputStream to read from
     * @return a new {@link LZMADecompressorBuilder}
     */
    public static LZMADecompressorBuilder builder(InputStream inputStream) {
        return new LZMADecompressorBuilder(inputStream);
    }

    /**
     * Creates a {@link LZMADecompressorBuilder} reading from the given path.
     *
     * @param path the Path to read from
     * @return a new {@link LZMADecompressorBuilder}
     * @throws IOException if an I/O error occurs while opening the path
     */
    public static LZMADecompressorBuilder builder(Path path) throws IOException {
        return new LZMADecompressorBuilder(path);
    }

    /**
     * Builder for creating a {@link LZMACompressorInputStream}.
     *
     * @param <P> the parent builder type
     */
    public static class LZMADecompressorInputStreamBuilder<P> {
        private final P parent;
        private final InputStream inputStream;
        private static final int NO_MEMORY_LIMIT = -1;

        private int memoryLimitInKb = NO_MEMORY_LIMIT;

        /**
         * Constructor that takes a parent builder and an InputStream.
         *
         * @param parent the parent builder to return to after building the input stream.
         * @param inputStream the InputStream to read from.
         */
        public LZMADecompressorInputStreamBuilder(P parent, InputStream inputStream) {
            this.parent = parent;
            this.inputStream = inputStream;
        }

        /**
         * Configures the memory limit for the decompressor.
         *
         * @param memoryLimitInKb memory limit in KiB, use -1 for no limit (default)
         * @return this builder instance for method chaining.
         * @throws IllegalArgumentException if memoryLimitInKb is zero or smaller than -1.
         */
        public LZMADecompressorInputStreamBuilder<P> setMemoryLimitInKb(int memoryLimitInKb) {
            if (memoryLimitInKb <= 0 && memoryLimitInKb != NO_MEMORY_LIMIT) {
                throw new IllegalArgumentException("Memory limit must be positive or -1");
            }
            this.memoryLimitInKb = memoryLimitInKb;
            return this;
        }

        /**
         * Builds the {@link LZMACompressorInputStream}.
         *
         * @return a new LZMACompressorInputStream.
         * @throws IOException if an I/O error occurs.
         * @throws com.hominux.compress4j.exceptions.MissingArchiveDependencyException if org.tukaani:xz is not
         *     available
         */
        public LZMACompressorInputStream buildInputStream() throws IOException {
            ArchiverDependencyChecker.checkLZMA();
            return LZMACompressorInputStream.builder()
                    .setInputStream(inputStream)
                    .setMemoryLimitKiB(memoryLimitInKb)
                    .get();
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

    /** Builder for creating instances of {@link LZMADecompressor}. */
    public static class LZMADecompressorBuilder
            extends CodecDecompressor.DecompressorBuilder<
                    LZMACompressorInputStream, LZMADecompressor, LZMADecompressorBuilder> {

        private final LZMADecompressorInputStreamBuilder<LZMADecompressorBuilder> inputStreamBuilder;

        /**
         * Constructor that takes an InputStream.
         *
         * @param inputStream the InputStream to read from.
         */
        public LZMADecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        @SuppressWarnings("this-escape")
        private LZMADecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStreamBuilder = new LZMADecompressorInputStreamBuilder<>(this, inputStream);
        }

        /**
         * Constructor that takes a Path.
         *
         * @param path the Path to read from.
         * @throws IOException if an I/O error occurs while opening the path
         */
        public LZMADecompressorBuilder(Path path) throws IOException {
            this(newInputStream(path), true);
        }

        /**
         * Constructor that takes a File.
         *
         * @param file the File to read from.
         * @throws IOException if an I/O error occurs while opening the file
         */
        public LZMADecompressorBuilder(File file) throws IOException {
            this(file.toPath());
        }

        /**
         * Returns the input stream builder for this decompressor.
         *
         * @return the {@link LZMADecompressorInputStreamBuilder}
         */
        public LZMADecompressorInputStreamBuilder<LZMADecompressorBuilder> compressorInputStreamBuilder() {
            return inputStreamBuilder;
        }

        /** {@inheritDoc} */
        @Override
        public LZMACompressorInputStream buildCompressorInputStream() throws IOException {
            return inputStreamBuilder.buildInputStream();
        }

        /** {@inheritDoc} */
        @Override
        protected LZMADecompressorBuilder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public LZMADecompressor build() throws IOException {
            return new LZMADecompressor(this);
        }
    }
}
