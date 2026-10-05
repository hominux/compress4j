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
package com.hominux.compress4j.compressors.gzip;

import com.hominux.compress4j.compressors.CodecCompressor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;

/**
 * Provides a Gzip compressor that writes to a {@link GzipCompressorOutputStream}. Use the builder pattern to configure
 * and create instances.
 *
 * @since 2.2
 */
public class GzipCompressor extends CodecCompressor<GzipCompressorOutputStream> {

    /**
     * Constructs a GzipCompressor with the given {@link GzipCompressorOutputStream}.
     *
     * @param compressorOutputStream the output stream to write compressed data to
     */
    public GzipCompressor(GzipCompressorOutputStream compressorOutputStream) {
        super(compressorOutputStream);
    }

    /**
     * Constructs a GzipCompressor using the provided {@link GzipCompressorBuilder}.
     *
     * @param builder the builder to configure the compressor
     * @throws IOException if an I/O error occurs during stream creation
     */
    public GzipCompressor(GzipCompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a new {@link GzipCompressorBuilder} for the given file {@link Path}.
     *
     * @param path the file path to write compressed data to
     * @return a new builder instance
     * @throws IOException if an I/O error occurs opening the file
     */
    public static GzipCompressorBuilder builder(Path path) throws IOException {
        return new GzipCompressorBuilder(path);
    }

    /**
     * Creates a new {@link GzipCompressorBuilder} for the given {@link OutputStream}.
     *
     * @param outputStream the output stream to write compressed data to
     * @return a new builder instance
     */
    public static GzipCompressorBuilder builder(OutputStream outputStream) {
        return new GzipCompressorBuilder(outputStream);
    }

    /**
     * Builder for configuring and creating a {@link GzipCompressorOutputStream}.
     *
     * @param <P> the type of the parent builder
     * @since 2.2
     */
    public static class GzipCompressorOutputStreamBuilder<P> {
        private static final int UNKNOWN_OPERATING_SYSTEM = 255;

        /** The output stream to write the compressed data to. */
        protected final OutputStream outputStream;

        private final P parent;
        private int bufferSize = 512;
        private String comment;
        private int compressionLevel = Deflater.DEFAULT_COMPRESSION;
        private int deflateStrategy = Deflater.DEFAULT_STRATEGY;
        private String fileName;
        private long modificationTime;
        private int operatingSystem = UNKNOWN_OPERATING_SYSTEM;

        /**
         * Constructs a builder for a Gzip output stream.
         *
         * @param parent the parent builder
         * @param outputStream the output stream to write compressed data to
         */
        public GzipCompressorOutputStreamBuilder(P parent, OutputStream outputStream) {
            this.parent = parent;
            this.outputStream = outputStream;
        }

        /**
         * Sets the buffer size used to retrieve compressed data from {@link Deflater} and write to the underlying
         * {@link OutputStream}.
         *
         * @param bufferSize the buffer size to set. Must be a positive value.
         * @return this builder instance
         * @throws IllegalArgumentException if the buffer size is not positive
         */
        public GzipCompressorOutputStreamBuilder<P> bufferSize(final int bufferSize) {
            if (bufferSize <= 0) {
                throw new IllegalArgumentException("invalid buffer size: " + bufferSize);
            }
            this.bufferSize = bufferSize;
            return this;
        }

        /**
         * Adds a comment to be included in the gzip file.
         *
         * @param comment the comment to be added
         * @return this builder instance
         */
        public GzipCompressorOutputStreamBuilder<P> comment(final String comment) {
            this.comment = comment;
            return this;
        }

        /**
         * Sets the compression level.
         *
         * @param compressionLevel the compression level (between 0 and 9)
         * @see Deflater#NO_COMPRESSION
         * @see Deflater#BEST_SPEED
         * @see Deflater#DEFAULT_COMPRESSION
         * @see Deflater#BEST_COMPRESSION
         * @return this builder instance
         * @throws IllegalArgumentException if the compression level is invalid
         */
        public GzipCompressorOutputStreamBuilder<P> compressionLevel(final int compressionLevel) {
            if (compressionLevel < -1 || compressionLevel > 9) {
                throw new IllegalArgumentException("Invalid gzip compression level: " + compressionLevel);
            }
            this.compressionLevel = compressionLevel;
            return this;
        }

        /**
         * Sets the deflater strategy.
         *
         * @param deflateStrategy the new compression strategy
         * @see Deflater#setStrategy(int)
         * @return this builder instance
         */
        public GzipCompressorOutputStreamBuilder<P> deflateStrategy(final int deflateStrategy) {
            this.deflateStrategy = deflateStrategy;
            return this;
        }

        /**
         * Sets the name of the compressed file.
         *
         * @param fileName the name of the file without the directory path
         * @return this builder instance
         */
        public GzipCompressorOutputStreamBuilder<P> fileName(final String fileName) {
            this.fileName = fileName;
            return this;
        }

        /**
         * Sets the modification time of the compressed file.
         *
         * @param modificationTime the modification time, in milliseconds
         * @return this builder instance
         */
        public GzipCompressorOutputStreamBuilder<P> modificationTime(final long modificationTime) {
            this.modificationTime = modificationTime;
            return this;
        }

        /**
         * Sets the operating system on which the compression took place.
         *
         * <ul>
         *   <li>0: FAT file system (MS-DOS, OS/2, NT/Win32)
         *   <li>1: Amiga
         *   <li>2: VMS (or OpenVMS)
         *   <li>3: Unix
         *   <li>4: VM/CMS
         *   <li>5: Atari TOS
         *   <li>6: HPFS file system (OS/2, NT)
         *   <li>7: Macintosh
         *   <li>8: Z-System
         *   <li>9: CP/M
         *   <li>10: TOPS-20
         *   <li>11: NTFS file system (NT)
         *   <li>12: QDOS
         *   <li>13: Acorn RISCOS
         *   <li>255: Unknown
         * </ul>
         *
         * @param operatingSystem the code of the operating system
         * @return this builder instance
         */
        public GzipCompressorOutputStreamBuilder<P> operatingSystem(final int operatingSystem) {
            this.operatingSystem = operatingSystem;
            return this;
        }

        /**
         * Builds and returns a {@link GzipCompressorOutputStream} with the current configuration.
         *
         * @return a configured GzipCompressorOutputStream
         * @throws IOException if an I/O error occurs during stream creation
         */
        public GzipCompressorOutputStream build() throws IOException {
            GzipParameters parameters = new GzipParameters();
            parameters.setBufferSize(bufferSize);
            parameters.setComment(comment);
            parameters.setCompressionLevel(compressionLevel);
            parameters.setDeflateStrategy(deflateStrategy);
            parameters.setFileName(fileName);
            parameters.setModificationTime(modificationTime);
            parameters.setOperatingSystem(operatingSystem);
            return new GzipCompressorOutputStream(outputStream, parameters);
        }

        /**
         * Returns the parent builder for further configuration.
         *
         * @return the parent builder
         */
        public P parentBuilder() {
            return parent;
        }
    }

    /**
     * Builder for configuring and creating a {@link GzipCompressor}.
     *
     * @since 2.2
     */
    public static class GzipCompressorBuilder
            extends CompressorBuilder<GzipCompressorOutputStream, GzipCompressorBuilder, GzipCompressor> {

        private final GzipCompressorOutputStreamBuilder<GzipCompressorBuilder> compressorOutputStreamBuilder;

        /**
         * Constructs a builder for a GzipCompressor using a file {@link Path}.
         *
         * @param path the file path to write compressed data to
         * @throws IOException if an I/O error occurs opening the file
         */
        public GzipCompressorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Constructs a builder for a GzipCompressor using an {@link OutputStream}.
         *
         * @param outputStream the output stream to write compressed data to
         */
        public GzipCompressorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        private GzipCompressorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new GzipCompressorOutputStreamBuilder<>(this, this.outputStream);
        }

        /**
         * Returns the {@link GzipCompressorOutputStreamBuilder}.
         *
         * @return {@link GzipCompressorOutputStreamBuilder}
         */
        public GzipCompressorOutputStreamBuilder<GzipCompressorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /**
         * Returns this builder instance.
         *
         * @return this builder
         */
        @Override
        protected GzipCompressorBuilder getThis() {
            return this;
        }

        /**
         * Builds and returns a configured {@link GzipCompressorOutputStream}.
         *
         * @return a configured GzipCompressorOutputStream
         * @throws IOException if an I/O error occurs during stream creation
         */
        @Override
        public GzipCompressorOutputStream buildCompressorOutputStream() throws IOException {
            return compressorOutputStreamBuilder.build();
        }

        /**
         * Builds and returns a configured {@link GzipCompressor}.
         *
         * @return a configured GzipCompressor
         * @throws IOException if an I/O error occurs during stream creation
         */
        @Override
        public GzipCompressor build() throws IOException {
            return new GzipCompressor(this);
        }
    }
}
