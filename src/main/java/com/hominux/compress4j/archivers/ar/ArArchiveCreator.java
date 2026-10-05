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
package com.hominux.compress4j.archivers.ar;

import com.hominux.compress4j.archivers.ArchiveCreator;
import com.hominux.compress4j.internal.io.Sink;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;

/**
 * Writes AR archives. AR has no directory entries, so directories are skipped. Classic AR carries no file-type bits, so
 * a symbolic link is stored as an entry whose mode has the {@code S_IFLNK} bits set and whose content is the target
 * path; third-party archives do not set those bits, so reading them is unaffected.
 *
 * @since 2.2
 */
public final class ArArchiveCreator extends ArchiveCreator {

    private ArArchiveCreator(Builder builder, ArEntryWriter writer) {
        super(builder, writer);
    }

    /**
     * Creates a builder writing the archive to the given path.
     *
     * @param path the path to write the archive to
     * @return the builder
     */
    public static Builder builder(Path path) {
        return new Builder(new Sink.OfPath(path));
    }

    /**
     * Creates a builder writing at the channel's current position. The creator closes the channel when it is closed; a
     * failed {@code build()} leaves it open.
     *
     * @param channel the channel to write the archive to
     * @return the builder
     * @since 5.0
     */
    public static Builder builder(SeekableByteChannel channel) {
        return new Builder(new Sink.OfChannel(channel));
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
     * Builder for {@link ArArchiveCreator}.
     *
     * @since 5.0
     */
    public static final class Builder extends ArchiveCreator.Builder<Builder, ArArchiveCreator> {
        private final Sink sink;

        private Builder(Sink sink) {
            this.sink = sink;
        }

        /** {@inheritDoc} */
        @Override
        protected Builder getThis() {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public ArArchiveCreator build() throws IOException {
            Sink.Opened opened = sink.open();
            try {
                return new ArArchiveCreator(this, new ArEntryWriter(new ArArchiveOutputStream(opened.out())));
            } catch (RuntimeException e) {
                throw opened.closeIfOwned(e);
            }
        }
    }
}
