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
package com.hominux.compress4j.archivers.sevenz;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.io.Source;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

/**
 * Reads 7z archives.
 *
 * <p>Supported entries are regular files, directories and symbolic links; every other Unix file type is reported
 * through the unsupported-entry handler and skipped.
 *
 * @since 3.2
 */
public final class SevenZArchiveExtractor extends ArchiveExtractor {

    private SevenZArchiveExtractor(Builder builder, EntryReader reader, LongSupplier compressedBytes) {
        super(builder, reader, compressedBytes);
    }

    /**
     * Creates a builder reading the archive at the given path.
     *
     * @param path the path to the archive to extract
     * @return the builder
     */
    public static Builder builder(Path path) {
        return new Builder(
                new Source.OfPath(path), Optional.of(path.toAbsolutePath().toString()));
    }

    /**
     * Creates a builder reading the whole channel, whatever its current position: {@code build()} seeks to the start,
     * so the archive must begin at offset 0. 7z keeps its header at the end of the archive, so the channel must be
     * seekable. The extractor closes the channel when it is closed; a failed {@code build()} leaves it open.
     *
     * @param channel the channel holding the archive
     * @return the builder
     * @since 5.0
     */
    public static Builder builder(SeekableByteChannel channel) {
        return new Builder(new Source.OfChannel(channel), Optional.empty());
    }

    /**
     * Builder for {@link SevenZArchiveExtractor}.
     *
     * @since 5.0
     */
    public static final class Builder extends ArchiveExtractor.Builder<Builder, SevenZArchiveExtractor> {

        private final Source.Seekable source;
        private final Optional<String> defaultName;
        private Optional<char[]> password = Optional.empty();

        private Builder(Source.Seekable source, Optional<String> defaultName) {
            this.source = source;
            this.defaultName = defaultName;
        }

        /**
         * Sets the password used to decrypt encrypted entries.
         *
         * @param password the password; the array is copied
         * @return this builder
         * @throws NullPointerException if the password is null
         * @since 5.0
         */
        public Builder password(char[] password) {
            this.password =
                    Optional.of(Objects.requireNonNull(password, "password").clone());
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
         * @throws IOException if the archive cannot be opened, for example because the password is wrong
         */
        @Override
        public SevenZArchiveExtractor build() throws IOException {
            Source.OpenedChannel opened = source.openChannel();
            try {
                opened.channel().position(0);
                SevenZFile file = open(opened);
                opened.built();
                EntryReader reader = new SevenZEntryReader(file, readerContext(), opened.channel()::count);
                return new SevenZArchiveExtractor(this, reader, opened.channel()::count);
            } catch (IOException e) {
                throw opened.closeIfOwned(e);
            } catch (RuntimeException e) {
                throw opened.closeIfOwned(e);
            }
        }

        private SevenZFile open(Source.OpenedChannel opened) throws IOException {
            SevenZFile.Builder file = SevenZFile.builder()
                    .setUseDefaultNameForUnnamedEntries(true)
                    .setSeekableByteChannel(opened.channel());
            defaultName.ifPresent(file::setDefaultName);
            password.ifPresent(file::setPassword);
            return file.get();
        }
    }
}
