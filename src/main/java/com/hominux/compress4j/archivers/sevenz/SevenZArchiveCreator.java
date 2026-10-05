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

import com.hominux.compress4j.archivers.ArchiveCreator;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.io.function.IOSupplier;

/**
 * Writes 7z archives. A symbolic link is stored the way p7zip stores it: a Unix-mode entry whose content is the link
 * target.
 *
 * @since 3.2
 */
public final class SevenZArchiveCreator extends ArchiveCreator {

    private SevenZArchiveCreator(Builder builder, SevenZEntryWriter writer) {
        super(builder, writer);
    }

    /**
     * Creates a builder writing the archive to the given path. 7z needs a seekable target to patch its header, so there
     * is no stream variant.
     *
     * @param path the path to write the archive to
     * @return the builder
     */
    public static Builder builder(Path path) {
        Objects.requireNonNull(path, "path");
        return new Builder(() -> new SevenZOutputFile(path.toFile()));
    }

    /**
     * Creates a builder writing to the channel from absolute offset 0, whatever its current position, overwriting any
     * bytes already there; 7z rewrites its start header on close, so the channel must be seekable. The creator closes
     * the channel when it is closed; a failed {@code build()} leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static Builder builder(SeekableByteChannel channel) {
        Objects.requireNonNull(channel, "channel");
        return new Builder(() -> new SevenZOutputFile(channel));
    }

    /**
     * Builder for {@link SevenZArchiveCreator}.
     *
     * @since 5.0
     */
    public static final class Builder extends ArchiveCreator.Builder<Builder, SevenZArchiveCreator> {

        private final IOSupplier<SevenZOutputFile> target;
        private SevenZMethod method = SevenZMethod.LZMA2;

        private Builder(IOSupplier<SevenZOutputFile> target) {
            this.target = target;
        }

        /**
         * Sets how entry content is compressed. Defaults to {@link SevenZMethod#LZMA2}.
         *
         * @param method the method
         * @return this builder
         * @throws NullPointerException if the method is null
         * @since 5.0
         */
        public Builder contentCompression(SevenZMethod method) {
            this.method = Objects.requireNonNull(method, "contentCompression");
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
        public SevenZArchiveCreator build() throws IOException {
            SevenZOutputFile file = target.get();
            file.setContentCompression(method.value);
            return new SevenZArchiveCreator(this, new SevenZEntryWriter(file));
        }
    }
}
