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
import com.hominux.compress4j.compressors.Pack200;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.codec.Codecs;
import com.hominux.compress4j.internal.io.CountingInputStream;
import com.hominux.compress4j.internal.io.ParserFailures;
import com.hominux.compress4j.internal.io.Source;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

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
public final class TarArchiveExtractor extends ArchiveExtractor {

    private static final String PACK200_MESSAGE = "Pack200 compresses JAR files, not tar streams";

    private TarArchiveExtractor(Builder builder, EntryReader reader, LongSupplier compressedBytes) {
        super(builder, reader, compressedBytes);
    }

    /**
     * Creates a builder reading the archive at the given path.
     *
     * @param path the path to the archive to extract
     * @return the builder
     */
    public static Builder builder(Path path) {
        return new Builder(new Source.OfPath(path));
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
        return new Builder(new Source.OfChannel(channel));
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
        return new Builder(new Source.OfStream(inputStream));
    }

    /**
     * Builder for {@link TarArchiveExtractor}; the compression is detected unless set.
     *
     * @since 5.0
     */
    public static final class Builder extends ArchiveExtractor.Builder<Builder, TarArchiveExtractor> {
        private final Source source;
        private Optional<Compression> compression = Optional.empty();
        private Charset encoding = StandardCharsets.UTF_8;

        private Builder(Source source) {
            this.source = source;
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
            Objects.requireNonNull(compression, "compression");
            if (compression instanceof Pack200) {
                throw new IllegalArgumentException(PACK200_MESSAGE);
            }
            this.compression = Optional.of(compression);
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
        public TarArchiveExtractor build() throws IOException {
            Source.Opened opened = source.open();
            try {
                TarArchiveInputStream tar = tarStream(opened.in());
                return new TarArchiveExtractor(this, new TarEntryReader(tar, readerContext()), opened.in()::count);
            } catch (IOException e) {
                throw opened.closeIfOwned(e);
            } catch (RuntimeException e) {
                throw opened.closeIfOwned(e);
            }
        }

        private TarArchiveInputStream tarStream(CountingInputStream in) throws IOException {
            InputStream buffered = in.markSupported() ? in : new BufferedInputStream(in);
            Compression selected = compression.isPresent()
                    ? compression.orElseThrow()
                    : ParserFailures.call(() -> Codecs.detectForReading(buffered), ParserFailures.ARCHIVE);
            InputStream decoded =
                    ParserFailures.call(() -> Codecs.decompressing(selected, buffered), ParserFailures.ARCHIVE);
            return new TarArchiveInputStream(decoded, encoding.name());
        }
    }
}
