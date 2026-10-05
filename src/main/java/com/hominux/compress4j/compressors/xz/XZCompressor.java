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
package com.hominux.compress4j.compressors.xz;

import com.hominux.compress4j.compressors.CodecCompressor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.tukaani.xz.LZMA2Options;

/**
 * Provides XZ compression functionality that writes to an {@link XZCompressorOutputStream}. This class extends the
 * {@link CodecCompressor} and supports configurable LZMA2 options.
 *
 * <p>Use the builder pattern to configure compression options, such as {@link LZMA2Options}, before creating instances.
 *
 * @since 2.2
 */
public class XZCompressor extends CodecCompressor<XZCompressorOutputStream> {

    /**
     * Constructor that takes an XZCompressorOutputStream.
     *
     * @param compressorOutputStream the XZCompressorOutputStream to write to.
     */
    public XZCompressor(XZCompressorOutputStream compressorOutputStream) {
        super(compressorOutputStream);
    }

    /**
     * Constructor that takes an XZCompressorBuilder.
     *
     * @param builder the XZCompressorBuilder to build from.
     * @throws IOException if an I/O error occurred
     */
    public XZCompressor(XZCompressorBuilder builder) throws IOException {
        super(builder);
    }

    /**
     * Helper static method to create an instance of the {@link XZCompressorBuilder}
     *
     * @param path the path to write the compressor to
     * @return An instance of the {@link XZCompressorBuilder}
     * @throws IOException if an I/O error occurred
     */
    public static XZCompressorBuilder builder(Path path) throws IOException {
        return new XZCompressorBuilder(path);
    }

    /**
     * Helper static method to create an instance of the {@link XZCompressorBuilder}
     *
     * @param outputStream the output stream
     * @return An instance of the {@link XZCompressorBuilder}
     */
    public static XZCompressorBuilder builder(OutputStream outputStream) {
        return new XZCompressorBuilder(outputStream);
    }

    /**
     * Builder for creating an {@link XZCompressorOutputStream} using Apache Commons Compress builder.
     *
     * @param <P> The type of the parent builder.
     */
    public static class XZCompressorOutputStreamBuilder<P> {
        private final P parent;
        private LZMA2Options lzma2Options = new LZMA2Options();

        /** The output stream to write to. */
        protected final OutputStream outputStream;

        /**
         * Create a new {@link XZCompressorOutputStreamBuilder} with the given parent and output stream.
         *
         * @param parent the parent builder
         * @param outputStream the output stream to write to
         */
        public XZCompressorOutputStreamBuilder(P parent, OutputStream outputStream) {
            this.parent = parent;
            this.outputStream = outputStream;
        }

        /**
         * Sets the LZMA2 options for compression.
         *
         * <p>Passing {@code null} resets to the default value {@link LZMA2Options#LZMA2Options()}.
         *
         * @param lzma2Options LZMA options.
         * @return this builder instance.
         */
        public XZCompressorOutputStreamBuilder<P> lzma2Options(final LZMA2Options lzma2Options) {
            this.lzma2Options = lzma2Options != null ? lzma2Options : new LZMA2Options();
            return this;
        }

        /**
         * Sets the LZMA2 preset level. This is a convenience method that configures LZMA2Options based on a preset.
         *
         * <p>The preset level must be in the range [0, 9]. The default is 6.
         *
         * @param preset the LZMA2 preset level
         * @return this builder instance
         * @throws IllegalArgumentException if the preset is not supported
         * @throws IOException if an I/O error occurred
         * @see LZMA2Options#PRESET_MIN
         * @see LZMA2Options#PRESET_MAX
         * @see LZMA2Options#PRESET_DEFAULT
         */
        public XZCompressorOutputStreamBuilder<P> preset(int preset) throws IOException {
            if (preset < LZMA2Options.PRESET_MIN || preset > LZMA2Options.PRESET_MAX) {
                throw new IllegalArgumentException("XZ preset must be in the range ["
                        + LZMA2Options.PRESET_MIN + ", " + LZMA2Options.PRESET_MAX + "], but was: "
                        + preset);
            }
            this.lzma2Options = new LZMA2Options(preset);
            return this;
        }

        /**
         * Builds the {@link XZCompressorOutputStream} with the configured parameters using the Apache Commons Compress
         * builder.
         *
         * @return the {@link XZCompressorOutputStream} instance
         * @throws IOException if an I/O error occurred
         */
        public XZCompressorOutputStream build() throws IOException {
            return XZCompressorOutputStream.builder()
                    .setOutputStream(outputStream)
                    .setLzma2Options(lzma2Options)
                    .get();
        }

        /**
         * Returns the parent builder.
         *
         * @return the parent builder
         */
        public P parentBuilder() {
            return parent;
        }
    }

    /** Builder for creating an {@link XZCompressor}. */
    public static class XZCompressorBuilder
            extends CompressorBuilder<XZCompressorOutputStream, XZCompressorBuilder, XZCompressor> {

        private final XZCompressorOutputStreamBuilder<XZCompressorBuilder> compressorOutputStreamBuilder;

        /**
         * Create a new {@link XZCompressorBuilder} with the given path.
         *
         * @param path the path to write the compressor to
         * @throws IOException if an I/O error occurred
         */
        public XZCompressorBuilder(Path path) throws IOException {
            this(Files.newOutputStream(path), true);
        }

        /**
         * Create a new {@link CompressorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        public XZCompressorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        private XZCompressorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            this.compressorOutputStreamBuilder = new XZCompressorOutputStreamBuilder<>(this, outputStream);
        }

        /**
         * Returns the XZCompressorOutputStreamBuilder for this compressor.
         *
         * @return the XZCompressorOutputStreamBuilder
         */
        public XZCompressorOutputStreamBuilder<XZCompressorBuilder> compressorOutputStreamBuilder() {
            return compressorOutputStreamBuilder;
        }

        /**
         * Returns this builder instance.
         *
         * @return this builder instance
         */
        @Override
        public XZCompressorBuilder getThis() {
            return this;
        }

        /**
         * Builds and returns a configured {@link XZCompressorOutputStream}.
         *
         * @return a configured XZCompressorOutputStream
         * @throws IOException if an I/O error occurs during stream creation
         */
        @Override
        public XZCompressorOutputStream buildCompressorOutputStream() throws IOException {
            return compressorOutputStreamBuilder.build();
        }

        /**
         * Builds the XZCompressor instance.
         *
         * @return a new XZCompressor instance
         * @throws IOException if an I/O error occurred
         */
        @Override
        public XZCompressor build() throws IOException {
            return new XZCompressor(this);
        }
    }
}
