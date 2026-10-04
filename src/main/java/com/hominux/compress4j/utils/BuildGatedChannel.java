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
package com.hominux.compress4j.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * Hands a caller's channel to a commons-compress reader without letting a failed open close it. As of commons-compress
 * 1.28, {@code ZipFile} and {@code SevenZFile} close a caller-supplied channel when reading the archive fails.
 * {@link #close()} does nothing until {@link #built()} is called, then closes the wrapped channel.
 */
public final class BuildGatedChannel implements SeekableByteChannel {

    private final SeekableByteChannel channel;
    private boolean built;

    /**
     * Wraps the channel.
     *
     * @param channel the caller's channel
     */
    public BuildGatedChannel(SeekableByteChannel channel) {
        this.channel = channel;
    }

    /** Marks the build as succeeded, so {@link #close()} closes the wrapped channel from now on. */
    public void built() {
        built = true;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return channel.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return channel.write(src);
    }

    @Override
    public long position() throws IOException {
        return channel.position();
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        channel.position(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        channel.truncate(size);
        return this;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (built) {
            channel.close();
        }
    }
}
