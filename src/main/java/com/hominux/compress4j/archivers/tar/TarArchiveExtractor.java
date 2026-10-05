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
package com.hominux.compress4j.archivers.tar;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.internal.codec.Codecs;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

/**
 * Reads tar archives, plain or compressed with any codec {@link Compression} can read except Pack200.
 *
 * <p>Compression is detected from the leading bytes: gzip, bzip2, xz, zstd, lz4-framed, Unix {@code .Z} and
 * snappy-framed. Concatenated gzip, bzip2, xz and lz4-framed members or streams, as written by parallel compressors,
 * are read to the end of the archive; an explicit {@code compression(...)} is used exactly as given. Input with no
 * detected signature is read as plain tar. LZMA has no signature and needs {@code compression(Compression.lzma())}.
 * Input that is not a tar archive fails with an {@link IOException}.
 *
 * <p>Supported entries are regular files (flags {@code '0'}, NUL and {@code '7'}, plus GNU sparse files), directories
 * and symbolic links. Hard links, character devices, block devices and FIFOs are reported through the unsupported-entry
 * handler and skipped, as is every other flag ({@code "unknown type"}). A global PAX header ({@code 'g'}) is archive
 * metadata: it is skipped without a report.
 *
 * @since 2.2
 */
public final class TarArchiveExtractor extends ArchiveExtractor<TarArchiveInputStream> {

    private static final String PACK200_MESSAGE = "Pack200 compresses JAR files, not tar streams";
    private static final int RECORD_SIZE = TarConstants.DEFAULT_RCDSIZE;

    private boolean started;

    private TarArchiveExtractor(Builder builder) throws IOException {
        super(builder);
    }

    TarArchiveExtractor(TarArchiveInputStream tarArchiveInputStream) {
        super(tarArchiveInputStream);
    }

    /**
     * Creates a builder reading the archive at the given path.
     *
     * @param path the path to the archive to extract
     * @return the builder
     * @throws IOException if the file cannot be opened
     */
    public static Builder builder(Path path) throws IOException {
        return new Builder(Files.newInputStream(path), true);
    }

    /**
     * Creates a builder reading from the channel's current position. The extractor closes the channel when it is
     * closed; a failed {@code build()} leaves it open. The channel is read through a stream that is buffered, and the
     * reader may consume up to 8 KiB past the end of the archive.
     *
     * @param channel the channel holding the archive
     * @return the builder
     * @since 5.0
     */
    public static Builder builder(SeekableByteChannel channel) {
        return new Builder(Channels.newInputStream(channel), false);
    }

    /**
     * Creates a builder reading the archive from the given stream. The extractor closes the stream when it is closed; a
     * failed {@code build()} leaves it open. A stream without mark support is buffered, and the reader may consume up
     * to 8 KiB past the end of the archive.
     *
     * @param inputStream the input stream of the archive to extract
     * @return the builder
     */
    public static Builder builder(InputStream inputStream) {
        return new Builder(inputStream, false);
    }

    /** {@inheritDoc} */
    @Override
    protected Optional<Entry> nextEntry() throws IOException {
        return getNextTarArchiveEntry().map(TarArchiveExtractor::toEntry);
    }

    private static Entry toEntry(TarArchiveEntry te) {
        Entry base = new Entry(te.getName(), type(te), te.getMode()).withLinkTarget(te.getLinkName());
        return base.withMetadata(te.getLastModifiedDate(), base.type() == Entry.Type.FILE ? te.getSize() : 0);
    }

    /** {@inheritDoc} */
    @Override
    protected InputStream openEntryStream(Entry entry) {
        return archiveInputStream;
    }

    private Optional<TarArchiveEntry> getNextTarArchiveEntry() throws IOException {
        TarArchiveEntry te;
        while ((te = archiveInputStream.getNextEntry()) != null) {
            started = true;
            requireValidChecksum(te);
            if (te.isGlobalPaxHeader()) {
                continue;
            }
            Optional<String> unsupported = unsupportedKind(te);
            if (unsupported.isEmpty()) {
                return Optional.of(te);
            }
            reportUnsupported(te.getName(), unsupported.orElseThrow());
        }
        requireNotTruncatedBeforeFirstEntry();
        started = true;
        return Optional.empty();
    }

    private static void requireValidChecksum(TarArchiveEntry te) throws IOException {
        if (!te.isCheckSumOK()) {
            throw new IOException("not a tar archive: the header checksum of entry '" + te.getName() + "' is wrong");
        }
    }

    private void requireNotTruncatedBeforeFirstEntry() throws IOException {
        long read = archiveInputStream.getBytesRead();
        if (!started && read > 0 && read < RECORD_SIZE) {
            throw new IOException("not a tar archive: " + read + " bytes, shorter than one header record");
        }
    }

    private static Optional<String> unsupportedKind(TarArchiveEntry te) {
        if (te.isLink()) {
            return Optional.of("hard link");
        } else if (te.isCharacterDevice()) {
            return Optional.of("character device");
        } else if (te.isBlockDevice()) {
            return Optional.of("block device");
        } else if (te.isFIFO()) {
            return Optional.of("fifo");
        } else if (isSupported(te)) {
            return Optional.empty();
        }
        return Optional.of("unknown type");
    }

    private static boolean isSupported(TarArchiveEntry te) {
        return te.isDirectory() || te.isSymbolicLink() || isRegularFile(te);
    }

    private static boolean isRegularFile(TarArchiveEntry te) {
        byte flag = te.getLinkFlag();
        return flag == TarConstants.LF_OLDNORM
                || flag == TarConstants.LF_NORMAL
                || flag == TarConstants.LF_CONTIG
                || te.isGNUSparse();
    }

    private static Entry.Type type(TarArchiveEntry te) {
        if (te.isSymbolicLink()) {
            return Entry.Type.SYMLINK;
        } else if (te.isDirectory()) {
            return Entry.Type.DIR;
        } else {
            return Entry.Type.FILE;
        }
    }

    /**
     * Builder for {@link TarArchiveExtractor}; the compression is detected unless set.
     *
     * @since 5.0
     */
    public static final class Builder
            extends ArchiveExtractorBuilder<TarArchiveInputStream, Builder, TarArchiveExtractor> {
        private final InputStream inputStream;
        private Optional<Compression> compression = Optional.empty();
        private Charset encoding = StandardCharsets.UTF_8;

        private Builder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStream = inputStream;
        }

        /**
         * Sets the compression instead of detecting it. LZMA has no magic number and always needs this.
         *
         * @param compression the compression of the whole tar stream
         * @return this builder
         * @throws IllegalArgumentException if the compression is Pack200, which compresses JAR files, not tar streams
         * @throws NullPointerException if the compression is null
         * @since 5.0
         */
        public Builder compression(Compression compression) {
            if (Objects.requireNonNull(compression, "compression") instanceof Compression.Pack200) {
                throw new IllegalArgumentException(PACK200_MESSAGE);
            }
            this.compression = Optional.of(Objects.requireNonNull(compression, "compression"));
            return this;
        }

        /**
         * Sets the encoding of entry names. Defaults to UTF-8.
         *
         * @param encoding the encoding
         * @return this builder
         * @throws NullPointerException if the encoding is null
         * @since 5.0
         */
        public Builder encoding(Charset encoding) {
            this.encoding = Objects.requireNonNull(encoding, "encoding");
            return this;
        }

        /** {@inheritDoc} */
        @Override
        protected Builder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveInputStream buildArchiveInputStream() throws IOException {
            InputStream buffered = inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream);
            Compression selected = compression.isPresent() ? compression.orElseThrow() : detected(buffered);
            return new TarArchiveInputStream(Codecs.decompressing(selected, buffered), encoding.name());
        }

        private static Compression detected(InputStream buffered) throws IOException {
            return switch (Codecs.detect(buffered)) {
                case Compression.Pack200 pack200 -> Compression.none();
                case Compression.Gzip gzip -> gzip.decompressConcatenated(true);
                case Compression.Bzip2 bzip2 -> bzip2.decompressConcatenated(true);
                case Compression.Xz xz -> xz.decompressConcatenated(true);
                case Compression.Lz4Framed lz4 -> lz4.decompressConcatenated(true);
                case Compression other -> other;
            };
        }

        /** {@inheritDoc} */
        @Override
        public TarArchiveExtractor build() throws IOException {
            return new TarArchiveExtractor(this);
        }
    }
}
