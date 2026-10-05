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
package com.hominux.compress4j.internal.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Where input comes from; nothing is opened until {@link #open()}. */
public sealed interface Source {

    /**
     * Input read from a file.
     *
     * @param path the file to read
     */
    record OfPath(Path path) implements Source {
        public OfPath {
            Objects.requireNonNull(path, "path");
        }
    }

    /**
     * Input read from a caller-owned channel.
     *
     * @param channel the channel to read
     */
    record OfChannel(SeekableByteChannel channel) implements Source {
        public OfChannel {
            Objects.requireNonNull(channel, "channel");
        }
    }

    /**
     * Input read from a caller-owned stream.
     *
     * @param stream the stream to read
     */
    record OfStream(InputStream stream) implements Source {
        public OfStream {
            Objects.requireNonNull(stream, "stream");
        }
    }

    /**
     * An opened source.
     *
     * @param in the counting stream over the source
     * @param owned whether this library opened the source, so the caller of {@code open()} must close it on success and
     *     failure alike
     */
    record Opened(CountingInputStream in, boolean owned) {
        /**
         * Closes the stream when owned, recording a close failure as suppressed on {@code failure}.
         *
         * @param failure the failure being propagated
         * @param <T> the failure type
         * @return {@code failure}
         */
        public <T extends Throwable> T closeIfOwned(T failure) {
            if (owned) {
                try {
                    in.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            return failure;
        }
    }

    /**
     * Opens the source.
     *
     * @return the opened source
     * @throws IOException if the source cannot be opened
     */
    default Opened open() throws IOException {
        return switch (this) {
            case OfPath(Path path) -> new Opened(new CountingInputStream(Files.newInputStream(path)), true);
            case OfChannel(SeekableByteChannel channel) ->
                new Opened(new CountingInputStream(Channels.newInputStream(channel)), false);
            case OfStream(InputStream stream) -> new Opened(new CountingInputStream(stream), false);
        };
    }
}
