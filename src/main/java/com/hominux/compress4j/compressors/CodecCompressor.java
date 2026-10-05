/*
 * Copyright 2025-2026 The Compress4J Project
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.commons.compress.compressors.CompressorOutputStream;

/**
 * This abstract class is the superclass of all classes providing data compression functionality. It provides core
 * methods for compressing data to various output streams.
 *
 * @param <O> The type of {@link CompressorOutputStream} to write compressed data to.
 * @since 2.2
 */
public abstract class CodecCompressor<O extends CompressorOutputStream<? extends OutputStream>> implements Closeable {
    /** Compressor output stream to be used for compression. */
    protected final O compressorOutputStream;

    /**
     * Create an instance of {@link CodecCompressor}
     *
     * @param compressorOutputStream the {@link CompressorOutputStream} to write to.
     */
    protected CodecCompressor(O compressorOutputStream) {
        this.compressorOutputStream = compressorOutputStream;
    }

    /**
     * Create a new Compressor with the given output stream and options.
     *
     * @param builder the compressor output stream builder
     * @param <B> The type of {@link CodecCompressor.CompressorBuilder} to build from.
     * @param <C> The type of the {@link CodecCompressor} to instantiate.
     * @throws IOException if an I/O error occurred
     */
    protected <B extends CodecCompressor.CompressorBuilder<O, B, C>, C extends CodecCompressor<O>> CodecCompressor(
            B builder) throws IOException {
        this(BuildFailureCleanup.build(builder.ownedStream, builder::buildCompressorOutputStream));
    }

    /**
     * Writes all bytes from a file this output stream.
     *
     * @param file the path to the source file.
     * @return the number of bytes read or written.
     * @throws IOException if an I/O error occurs when reading or writing.
     */
    public long write(final File file) throws IOException {
        return write(file.toPath());
    }

    /**
     * Writes all bytes from a file to this output stream.
     *
     * @param path the path to the source file.
     * @return the number of bytes read or written.
     * @throws IOException if an I/O error occurs when reading or writing.
     */
    public long write(final Path path) throws IOException {
        return Files.copy(path, compressorOutputStream);
    }

    @Override
    public void close() throws IOException {
        compressorOutputStream.close();
    }

    /**
     * Build and instance of {@link CodecCompressor}
     *
     * @param <O> The type of {@link CompressorOutputStream} to write entries to.
     * @param <B> The type of {@link CodecCompressor.CompressorBuilder}
     * @param <C> The type of {@link CodecCompressor}
     */
    public abstract static class CompressorBuilder<
            O extends CompressorOutputStream<? extends OutputStream>,
            B extends CodecCompressor.CompressorBuilder<O, B, C>,
            C extends CodecCompressor<O>> {
        /** Output stream to which the compressor will write. */
        protected final OutputStream outputStream;

        final Optional<Closeable> ownedStream;

        /**
         * Create a new {@link CodecCompressor.CompressorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        protected CompressorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        /**
         * Create a new {@link CodecCompressor.CompressorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         * @param owned whether the builder opened {@code outputStream} itself, so a failed build closes it
         */
        protected CompressorBuilder(OutputStream outputStream, boolean owned) {
            this.outputStream = outputStream;
            this.ownedStream = owned ? Optional.of(outputStream) : Optional.empty();
        }

        /**
         * get the current instance of the object
         *
         * @return current instance
         */
        protected abstract B getThis();

        /**
         * Start a new compressor. Entries can be included in the compressor using the putEntry method, and then the
         * compressor should be closed using its close method. In addition, options can be applied to the underlying
         * stream. E.g. archiving level.
         *
         * <ol>
         *   <li>Use {@link #outputStream} as underlying output stream to which to write the compressor.
         * </ol>
         *
         * @return new compressor object for use in putEntry
         * @throws IOException thrown by the underlying output stream for I/O errors
         */
        public abstract O buildCompressorOutputStream() throws IOException;

        /**
         * Use this method to build an instance of the {@link CodecCompressor}, use
         * {@link CodecCompressor#CodecCompressor(CodecCompressor.CompressorBuilder)} to pass in instance of this
         * builder
         *
         * @return an instance of the {@link CodecCompressor}
         * @throws IOException thrown by the underlying output stream for I/O errors
         */
        public abstract C build() throws IOException;
    }
}
