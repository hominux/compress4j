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
package com.hominux.compress4j.archivers.zip;

import com.hominux.compress4j.archivers.ArchiveCreator;
import com.hominux.compress4j.internal.io.Sink;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

/**
 * Writes zip archives.
 *
 * @since 2.2
 */
public final class ZipArchiveCreator extends ArchiveCreator {

    private ZipArchiveCreator(Builder builder, ZipEntryWriter writer) {
        super(builder, writer);
    }

    /**
     * Creates a builder writing the archive to the given path. A file is seekable, so zip records sizes in local
     * headers instead of data descriptors.
     *
     * @param path the path to write the archive to
     * @return the builder
     */
    public static Builder builder(Path path) {
        return new Builder(new Sink.OfPath(path));
    }

    /**
     * Creates a builder writing the archive to the given stream. The creator closes the stream when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param outputStream the output stream to write the archive to
     * @return the builder
     */
    public static Builder builder(OutputStream outputStream) {
        return new Builder(new Sink.OfStream(outputStream));
    }

    /**
     * Creates a builder writing at the channel's current position. A seekable channel lets zip record sizes in local
     * headers instead of data descriptors. The creator closes the channel when it is closed; a failed {@code build()}
     * leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static Builder builder(SeekableByteChannel channel) {
        return new Builder(new Sink.OfChannel(channel));
    }

    /**
     * Builder for {@link ZipArchiveCreator}.
     *
     * @since 5.0
     */
    public static final class Builder extends ArchiveCreator.Builder<Builder, ZipArchiveCreator> {

        private static final int DEFAULT_LEVEL = ZipArchiveOutputStream.DEFAULT_COMPRESSION;
        private static final int MAX_LEVEL = 9;

        private final Sink sink;
        private String comment = "";
        private int level = DEFAULT_LEVEL;
        private Charset encoding = StandardCharsets.UTF_8;
        private ZipCompressionMethod method = ZipCompressionMethod.DEFLATED;
        private ZipZip64Mode zip64Mode = ZipZip64Mode.AS_NEEDED;
        private ZipUnicodeExtraFields unicodeExtraFields = ZipUnicodeExtraFields.NEVER;
        private boolean fallbackToUtf8;
        private boolean useLanguageEncodingFlag = true;

        private Builder(Sink sink) {
            this.sink = sink;
        }

        /**
         * Sets the compression level. Defaults to {@code -1}, the Deflate default.
         *
         * @param compressionLevel {@code -1} for the default, {@code 0} for none up to {@code 9} for the most
         * @return this builder
         * @throws IllegalArgumentException if the level is outside {@code -1..9}
         * @since 5.0
         */
        public Builder compressionLevel(int compressionLevel) {
            if (compressionLevel < DEFAULT_LEVEL || compressionLevel > MAX_LEVEL) {
                throw new IllegalArgumentException("Compression level must be between -1 and 9");
            }
            this.level = compressionLevel;
            return this;
        }

        /**
         * Sets the compression method. Defaults to {@link ZipCompressionMethod#DEFLATED}.
         *
         * @param compressionMethod the method
         * @return this builder
         * @throws NullPointerException if the method is null
         * @since 5.0
         */
        public Builder compressionMethod(ZipCompressionMethod compressionMethod) {
            this.method = Objects.requireNonNull(compressionMethod, "compressionMethod");
            return this;
        }

        /**
         * Sets the archive comment. Defaults to empty.
         *
         * @param comment the comment
         * @return this builder
         * @throws NullPointerException if the comment is null
         * @since 5.0
         */
        public Builder comment(String comment) {
            this.comment = Objects.requireNonNull(comment, "comment");
            return this;
        }

        /**
         * Sets when Zip64 extensions are used. Defaults to {@link ZipZip64Mode#AS_NEEDED}.
         *
         * @param mode the mode
         * @return this builder
         * @throws NullPointerException if the mode is null
         * @since 5.0
         */
        public Builder zip64(ZipZip64Mode mode) {
            this.zip64Mode = Objects.requireNonNull(mode, "zip64");
            return this;
        }

        /**
         * Sets when entries get InfoZIP Unicode extra fields for their names and the comment. Defaults to
         * {@link ZipUnicodeExtraFields#NEVER}.
         *
         * @param policy when to write the extra fields
         * @return this builder
         * @throws NullPointerException if the policy is null
         * @since 5.0
         */
        public Builder createUnicodeExtraFields(ZipUnicodeExtraFields policy) {
            this.unicodeExtraFields = Objects.requireNonNull(policy, "createUnicodeExtraFields");
            return this;
        }

        /**
         * Sets whether a name the encoding cannot represent is written as UTF-8 with the language encoding flag.
         * Defaults to {@code false}.
         *
         * @param fallback whether to fall back to UTF-8
         * @return this builder
         * @since 5.0
         */
        public Builder fallbackToUtf8(boolean fallback) {
            this.fallbackToUtf8 = fallback;
            return this;
        }

        /**
         * Sets whether the language encoding flag is written when the encoding is UTF-8. Defaults to {@code true}.
         *
         * @param use whether to write the flag
         * @return this builder
         * @since 5.0
         */
        public Builder useLanguageEncodingFlag(boolean use) {
            this.useLanguageEncodingFlag = use;
            return this;
        }

        /**
         * Sets the encoding of entry names and the comment. Defaults to UTF-8.
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

        /**
         * {@inheritDoc}
         *
         * @throws IOException if the target cannot be opened
         */
        @Override
        public ZipArchiveCreator build() throws IOException {
            return switch (sink) {
                case Sink.OfPath(Path path) -> create(new ZipArchiveOutputStream(path));
                case Sink.OfChannel(SeekableByteChannel channel) -> create(new ZipArchiveOutputStream(channel));
                case Sink.OfStream(OutputStream stream) -> create(new ZipArchiveOutputStream(stream));
            };
        }

        private ZipArchiveCreator create(ZipArchiveOutputStream out) {
            configure(out);
            return new ZipArchiveCreator(this, new ZipEntryWriter(out));
        }

        private void configure(ZipArchiveOutputStream out) {
            out.setLevel(level);
            out.setMethod(method.value);
            out.setComment(comment);
            out.setUseZip64(zip64Mode.value);
            out.setCreateUnicodeExtraFields(unicodeExtraFields.value);
            out.setFallbackToUTF8(fallbackToUtf8);
            out.setUseLanguageEncodingFlag(useLanguageEncodingFlag);
            out.setEncoding(encoding.name());
        }
    }
}
