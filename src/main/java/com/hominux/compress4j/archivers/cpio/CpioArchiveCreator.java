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
package com.hominux.compress4j.archivers.cpio;

import com.hominux.compress4j.archivers.ArchiveCreator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.apache.commons.io.IOUtils;

/**
 * The CPIO archive creator.
 *
 * @since 2.2
 */
@SuppressWarnings("OctalInteger")
public class CpioArchiveCreator extends ArchiveCreator<CpioArchiveOutputStream> {

    private final short format;

    /**
     * Create a new CpioArchiveCreator with the given output stream.
     *
     * @param cpioArchiveOutputStream the output CPIO Archive Output Stream
     */
    public CpioArchiveCreator(CpioArchiveOutputStream cpioArchiveOutputStream) {
        super(cpioArchiveOutputStream);
        this.format = CpioConstants.FORMAT_NEW;
    }

    /**
     * Create a new CpioArchiveCreator with the given output stream and options.
     *
     * @param builder the archive output stream builder
     * @throws IOException if an I/O error occurred
     */
    public CpioArchiveCreator(CpioArchiveCreatorBuilder builder) throws IOException {
        super(builder);
        this.format = builder.cpioOutputStreamBuilder.format;
    }

    @Override
    protected void writeDirectoryEntry(String name, FileTime modTime) throws IOException {
        String directoryName = name.endsWith("/") ? name : name + "/";

        CpioArchiveEntry entry;
        if (format != CpioConstants.FORMAT_NEW) {
            entry = new CpioArchiveEntry(format, directoryName, 0);
        } else {
            entry = new CpioArchiveEntry(directoryName);
            entry.setSize(0);
        }

        entry.setTime(modTime.toMillis() / 1000L);
        entry.setMode(CpioConstants.C_ISDIR | 0755);
        archiveOutputStream.putArchiveEntry(entry);
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    protected void writeFileEntry(String name, InputStream source, long length, FileTime modTime, int mode)
            throws IOException {
        long entryLength = length < 0 ? source.available() : length;
        CpioArchiveEntry entry = newEntry(name, entryLength, modTime);
        setRegularMode(entry, mode);
        archiveOutputStream.putArchiveEntry(entry);
        if (entryLength > 0) {
            IOUtils.copy(source, archiveOutputStream);
        }
        archiveOutputStream.closeArchiveEntry();
    }

    @Override
    protected void writeFileEntry(
            String name, InputStream source, long length, FileTime modTime, int mode, Path symlinkTarget)
            throws IOException {
        byte[] target = symlinkTarget.toString().getBytes(StandardCharsets.UTF_8);
        CpioArchiveEntry entry = newEntry(name, target.length, modTime);
        entry.setMode(CpioConstants.C_ISLNK | 0644);
        archiveOutputStream.putArchiveEntry(entry);
        archiveOutputStream.write(target);
        archiveOutputStream.closeArchiveEntry();
    }

    private CpioArchiveEntry newEntry(String name, long length, FileTime modTime) {
        CpioArchiveEntry entry;
        if (format != CpioConstants.FORMAT_NEW) {
            entry = new CpioArchiveEntry(format, name, length);
        } else {
            entry = new CpioArchiveEntry(name);
            entry.setSize(length);
        }
        entry.setTime(modTime.toMillis() / 1000L);
        return entry;
    }

    private static void setRegularMode(CpioArchiveEntry entry, int mode) {
        if (mode == 0) {
            entry.setMode(CpioConstants.C_ISREG | 0644);
            return;
        }
        try {
            entry.setMode(CpioConstants.C_ISREG | (mode & 0777));
        } catch (IllegalArgumentException e) {
            entry.setMode(CpioConstants.C_ISREG | 0644);
        }
    }

    /**
     * Helper static method to create an instance of the {@link CpioArchiveCreatorBuilder}
     *
     * @param path the path to write the archive to
     * @return a new instance of {@link CpioArchiveCreatorBuilder}
     * @throws IOException if an I/O error occurs opening the file
     */
    public static CpioArchiveCreatorBuilder builder(Path path) throws IOException {
        return new CpioArchiveCreatorBuilder(path);
    }

    /**
     * Creates a builder writing at the channel's current position. The creator closes the channel when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static CpioArchiveCreatorBuilder builder(SeekableByteChannel channel) {
        return builder(Channels.newOutputStream(channel));
    }

    /**
     * Helper static method to create an instance of the {@link CpioArchiveCreatorBuilder}
     *
     * @param outputStream the output stream to write the archive to
     * @return a new instance of {@link CpioArchiveCreatorBuilder}
     */
    public static CpioArchiveCreatorBuilder builder(OutputStream outputStream) {
        return new CpioArchiveCreatorBuilder(outputStream);
    }

    /**
     * Builder for configuring and creating a {@link CpioArchiveOutputStream}.
     *
     * @param <P> the type of the parent builder
     * @since 2.2
     */
    public static class CpioArchiveOutputStreamBuilder<P> {
        /** The output stream to write the archive to. */
        protected final OutputStream outputStream;

        private final P parent;
        private short format = CpioConstants.FORMAT_NEW;
        private int blockSize = CpioConstants.BLOCK_SIZE;
        private String encoding = "UTF-8";

        /**
         * Constructs a builder for a CPIO output stream.
         *
         * @param parent the parent builder
         * @param outputStream the output stream to write the archive to
         */
        public CpioArchiveOutputStreamBuilder(P parent, OutputStream outputStream) {
            this.parent = parent;
            this.outputStream = outputStream;
        }

        /**
         * Sets the CPIO format to use.
         *
         * @param format the CPIO format (e.g., CpioConstants.FORMAT_NEW, FORMAT_OLD_ASCII, etc.)
         * @return this builder instance
         */
        public CpioArchiveOutputStreamBuilder<P> format(short format) {
            this.format = format;
            return this;
        }

        /**
         * Sets the block size for the CPIO archive.
         *
         * @param blockSize the block size in bytes
         * @return this builder instance
         */
        public CpioArchiveOutputStreamBuilder<P> blockSize(int blockSize) {
            this.blockSize = blockSize;
            return this;
        }

        /**
         * Sets the character encoding for file names.
         *
         * @param encoding the character encoding
         * @return this builder instance
         */
        public CpioArchiveOutputStreamBuilder<P> encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        /**
         * Returns the parent builder.
         *
         * @return the parent builder
         */
        public P and() {
            return parent;
        }

        /**
         * Builds the {@link CpioArchiveOutputStream} with the configured options.
         *
         * @return a new CPIO archive output stream
         * @throws IOException if an I/O error occurs during stream creation
         */
        public CpioArchiveOutputStream build() throws IOException {
            return new CpioArchiveOutputStream(outputStream, format, blockSize, encoding);
        }
    }

    /**
     * Builder for configuring and creating {@link CpioArchiveCreator} instances.
     *
     * @since 2.2
     */
    public static class CpioArchiveCreatorBuilder
            extends ArchiveCreatorBuilder<CpioArchiveOutputStream, CpioArchiveCreatorBuilder, CpioArchiveCreator> {

        private final CpioArchiveOutputStreamBuilder<CpioArchiveCreatorBuilder> cpioOutputStreamBuilder;

        /**
         * Constructs a CpioArchiveCreatorBuilder with the given file path.
         *
         * @param path the file path to write the archive to
         * @throws IOException if an I/O error occurs opening the file
         */
        public CpioArchiveCreatorBuilder(Path path) throws IOException {
            super(Files.newOutputStream(path), true);
            this.cpioOutputStreamBuilder = new CpioArchiveOutputStreamBuilder<>(this, outputStream);
        }

        /**
         * Constructs a CpioArchiveCreatorBuilder with the given output stream.
         *
         * @param outputStream the output stream to write the archive to
         */
        public CpioArchiveCreatorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        private CpioArchiveCreatorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
            if (outputStream == null) {
                throw new NullPointerException("Output stream cannot be null");
            }
            this.cpioOutputStreamBuilder = new CpioArchiveOutputStreamBuilder<>(this, outputStream);
        }

        /**
         * Access the CPIO output stream builder for configuration.
         *
         * @return the CPIO output stream builder
         */
        public CpioArchiveOutputStreamBuilder<CpioArchiveCreatorBuilder> cpioOutputStream() {
            return cpioOutputStreamBuilder;
        }

        @Override
        protected CpioArchiveCreatorBuilder getThis() {
            return this;
        }

        @Override
        public CpioArchiveOutputStream buildArchiveOutputStream() throws IOException {
            return cpioOutputStreamBuilder.build();
        }

        @Override
        public CpioArchiveCreator build() throws IOException {
            return new CpioArchiveCreator(this);
        }
    }
}
