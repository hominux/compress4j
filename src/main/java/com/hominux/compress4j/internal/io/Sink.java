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
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Where output goes; nothing is opened until {@link #open()}. */
public sealed interface Sink {

    /**
     * Output written to a file.
     *
     * @param path the file to write
     */
    record OfPath(Path path) implements Sink {
        public OfPath {
            Objects.requireNonNull(path, "path");
        }
    }

    /**
     * Output written to a caller-owned channel.
     *
     * @param channel the channel to write
     */
    record OfChannel(SeekableByteChannel channel) implements Sink {
        public OfChannel {
            Objects.requireNonNull(channel, "channel");
        }
    }

    /**
     * Output written to a caller-owned stream.
     *
     * @param stream the stream to write
     */
    record OfStream(OutputStream stream) implements Sink {
        public OfStream {
            Objects.requireNonNull(stream, "stream");
        }
    }

    /**
     * An opened sink.
     *
     * @param out the stream over the sink
     * @param owned whether this library opened the sink, so the caller of {@code open()} must close it on success and
     *     failure alike
     */
    record Opened(OutputStream out, boolean owned) {
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
                    out.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            return failure;
        }
    }

    /**
     * Opens the sink.
     *
     * @return the opened sink
     * @throws IOException if the sink cannot be opened
     */
    default Opened open() throws IOException {
        return switch (this) {
            case OfPath(Path path) -> new Opened(Files.newOutputStream(path), true);
            case OfChannel(SeekableByteChannel channel) -> new Opened(Channels.newOutputStream(channel), false);
            case OfStream(OutputStream stream) -> new Opened(stream, false);
        };
    }
}
