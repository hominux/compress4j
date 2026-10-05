/*
 * Copyright 2024-2026 The Compress4J Project
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
package com.hominux.compress4j.compressors;

import com.hominux.compress4j.utils.BuildFailureCleanup;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.compress.compressors.CompressorInputStream;

/**
 * This abstract class is the superclass of all classes providing data decompression functionality. It provides core
 * methods for decompressing data from various input streams.
 *
 * @param <I> The type of {@link CompressorInputStream} to read compressed data from.
 * @since 2.2
 */
public abstract class CodecDecompressor<I extends CompressorInputStream> implements Closeable {
    /** Compressor input stream to be used for decompression. */
    protected final I compressorInputStream;

    /**
     * Create an instance of {@link CodecDecompressor}
     *
     * @param compressorInputStream the {@link CompressorInputStream} to read from.
     */
    protected CodecDecompressor(I compressorInputStream) {
        this.compressorInputStream = compressorInputStream;
    }

    /**
     * Create a new Decompressor with the given input stream and options.
     *
     * @param builder the compressor input stream builder
     * @param <B> The type of {@link CodecDecompressor.DecompressorBuilder} to build from.
     * @param <D> The type of the {@link CodecDecompressor} to instantiate.
     * @throws IOException thrown by the underlying output stream for I/O errors
     */
    protected <B extends CodecDecompressor.DecompressorBuilder<I, D, B>, D extends CodecDecompressor<I>>
            CodecDecompressor(B builder) throws IOException {
        this(BuildFailureCleanup.build(builder.ownedStream, builder::buildCompressorInputStream));
    }

    /**
     * Writes all bytes from a file to this output stream.
     *
     * @param file the path to the source file.
     * @return the number of bytes written
     * @throws IOException if an I/O error occurred
     */
    public long write(final File file) throws IOException {
        return write(file.toPath());
    }

    /**
     * Writes all bytes from a path to this output stream.
     *
     * @param path the path to the source file.
     * @return the number of bytes written
     * @throws IOException if an I/O error occurred
     */
    public long write(final Path path) throws IOException {
        return Files.copy(compressorInputStream, path);
    }

    /**
     * Closes the decompressor input stream.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        compressorInputStream.close();
    }

    /**
     * Build and instance of {@link CodecDecompressor}
     *
     * @param <I> The type of {@link CompressorInputStream} to read entries from.
     * @param <D> The type of {@link CodecDecompressor}
     * @param <B> The type of {@link CodecDecompressor.DecompressorBuilder}
     */
    public abstract static class DecompressorBuilder<
            I extends CompressorInputStream,
            D extends CodecDecompressor<I>,
            B extends CodecDecompressor.DecompressorBuilder<I, D, B>> {

        /** Input stream to read from for decompression. */
        protected final InputStream inputStream;

        final Optional<Closeable> ownedStream;

        /**
         * Constructor that takes an input stream to read from.
         *
         * @param inputStream the input stream to read from
         */
        protected DecompressorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        /**
         * Constructor that takes an input stream to read from.
         *
         * @param inputStream the input stream to read from
         * @param owned whether the builder opened {@code inputStream} itself, so a failed build closes it
         */
        protected DecompressorBuilder(InputStream inputStream, boolean owned) {
            this.inputStream = inputStream;
            this.ownedStream = owned ? Optional.of(inputStream) : Optional.empty();
        }

        /**
         * Returns the input stream to be used for decompression.
         *
         * @return the input stream
         * @throws IOException if an I/O error occurs while building the compressor input stream
         */
        public abstract I buildCompressorInputStream() throws IOException;

        /**
         * Returns the input stream to be used for decompression.
         *
         * @return the input stream
         */
        protected abstract B getThis();

        /**
         * Builds an instance of {@link CodecDecompressor}.
         *
         * @return a new instance of {@link CodecDecompressor}
         * @throws IOException if an I/O error occurs
         */
        public abstract D build() throws IOException;
    }
}
