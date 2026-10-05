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
package com.hominux.compress4j.archivers.dump;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.internal.io.Source;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream;

/**
 * Read-only: Compress4J provides no creator for this format.
 *
 * <p>Extracts UNIX dump archives. The unnamed root directory entry is skipped. Directory and regular file entries are
 * extracted; every other entry type is reported through the unsupported-entry handler and skipped. The expansion ratio
 * is measured against the bytes consumed from the source.
 *
 * @since 3.2
 */
public final class DumpArchiveExtractor extends ArchiveExtractor {

    private DumpArchiveExtractor(Builder builder, DumpEntryReader reader, LongSupplier compressedBytes) {
        super(builder, reader, compressedBytes);
    }

    /**
     * Creates a builder reading the archive at the given path.
     *
     * @param path the path to the archive
     * @return the builder
     */
    public static Builder builder(Path path) {
        return new Builder(new Source.OfPath(path));
    }

    /**
     * Creates a builder reading from the channel's current position. The extractor closes the channel when it is
     * closed; a failed {@code build()} leaves it open.
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
     * failed {@code build()} leaves it open.
     *
     * @param inputStream the archive stream
     * @return the builder
     */
    public static Builder builder(InputStream inputStream) {
        return new Builder(new Source.OfStream(inputStream));
    }

    /**
     * Builder for {@link DumpArchiveExtractor}.
     *
     * @since 5.0
     */
    public static final class Builder extends ArchiveExtractor.Builder<Builder, DumpArchiveExtractor> {
        private final Source source;
        private Charset encoding = StandardCharsets.UTF_8;

        private Builder(Source source) {
            this.source = source;
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
        public DumpArchiveExtractor build() throws IOException {
            Source.Opened opened = source.open();
            try {
                DumpEntryReader reader = new DumpEntryReader(archiveStream(opened), readerContext());
                return new DumpArchiveExtractor(this, reader, opened.in()::count);
            } catch (IOException e) {
                throw opened.closeIfOwned(e);
            } catch (RuntimeException e) {
                throw opened.closeIfOwned(e);
            }
        }

        private DumpArchiveInputStream archiveStream(Source.Opened opened) throws IOException {
            try {
                return new DumpArchiveInputStream(opened.in(), encoding.name());
            } catch (ArchiveException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
    }
}
