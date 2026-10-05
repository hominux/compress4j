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
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/** Counts the bytes returned by {@link #read(ByteBuffer)}. */
public final class CountingSeekableByteChannel implements SeekableByteChannel {
    private final SeekableByteChannel delegate;
    private long count;

    /**
     * Wraps a channel.
     *
     * @param delegate the channel to count reads of
     */
    public CountingSeekableByteChannel(SeekableByteChannel delegate) {
        this.delegate = delegate;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int n = delegate.read(dst);
        if (n > 0) {
            count += n;
        }
        return n;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return delegate.write(src);
    }

    @Override
    public long position() throws IOException {
        return delegate.position();
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        delegate.position(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        return delegate.size();
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        delegate.truncate(size);
        return this;
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * Returns the total bytes read so far.
     *
     * @return the count of bytes read
     */
    public long count() {
        return count;
    }
}
