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

import com.hominux.compress4j.internal.util.BuildGatedChannel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Where input comes from; nothing is opened until {@link #open()}. */
public sealed interface Source {

    /** A source that can be read with random access. */
    sealed interface Seekable extends Source {
        /**
         * Opens the source as a channel whose reads are counted.
         *
         * @return the opened channel
         * @throws IOException if the source cannot be opened
         */
        OpenedChannel openChannel() throws IOException;
    }

    /**
     * Input read from a file.
     *
     * @param path the file to read
     */
    record OfPath(Path path) implements Seekable {
        public OfPath {
            Objects.requireNonNull(path, "path");
        }

        @Override
        public OpenedChannel openChannel() throws IOException {
            return new OpenedChannel(new CountingSeekableByteChannel(Files.newByteChannel(path)), Optional.empty());
        }
    }

    /**
     * Input read from a caller-supplied channel.
     *
     * @param channel the channel to read
     */
    record OfChannel(SeekableByteChannel channel) implements Seekable {
        public OfChannel {
            Objects.requireNonNull(channel, "channel");
        }

        @Override
        public OpenedChannel openChannel() {
            BuildGatedChannel gate = new BuildGatedChannel(channel);
            return new OpenedChannel(new CountingSeekableByteChannel(gate), Optional.of(gate));
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
     * An opened random-access source. The caller's channel is gated: closing the result does nothing until
     * {@link #built()}, so a reader that closes the channel when it fails to open cannot close the caller's.
     *
     * @param channel the counting channel to hand to the reader
     * @param gate the gate around a caller's channel; empty when this library opened the source
     */
    record OpenedChannel(CountingSeekableByteChannel channel, Optional<BuildGatedChannel> gate) {
        /** Marks the reader as built, so closing the channel now closes a caller's channel too. */
        public void built() {
            gate.ifPresent(BuildGatedChannel::built);
        }

        /**
         * Closes the channel when this library opened it, recording a close failure as suppressed on {@code failure}.
         *
         * @param failure the failure being propagated
         * @param <T> the failure type
         * @return {@code failure}
         */
        public <T extends Throwable> T closeIfOwned(T failure) {
            if (gate.isEmpty()) {
                try {
                    channel.close();
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
