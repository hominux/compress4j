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
package com.hominux.compress4j.archivers;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Yields exactly the declared number of bytes of an entry's content. Reading fails with an {@link IOException} naming
 * the entry when the content ends early or holds more bytes; detecting the excess reads at most one byte past the
 * declared size.
 */
final class DeclaredSizeInputStream extends FilterInputStream {

    private final String name;
    private final long declared;
    private long remaining;

    DeclaredSizeInputStream(InputStream in, String name, long declared) {
        super(in);
        this.name = name;
        this.declared = declared;
        this.remaining = declared;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        return read(one, 0, 1) == -1 ? -1 : one[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (remaining == 0) {
            requireNoMore();
            return -1;
        }
        int n = in.read(b, off, (int) Math.min(len, remaining));
        if (n == -1) {
            throw new IOException("Entry '" + name + "' ended after " + (declared - remaining) + " of its declared "
                    + declared + " bytes");
        }
        remaining -= n;
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }
        long skipped = in.skip(Math.min(n, remaining));
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(in.available(), remaining);
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    /**
     * Fails unless every declared byte was read and the content holds no more.
     *
     * @throws IOException if fewer than the declared bytes were read, or the content holds more
     */
    void requireExhausted() throws IOException {
        if (remaining > 0) {
            throw new IOException(
                    "Entry '" + name + "' wrote " + (declared - remaining) + " of its declared " + declared + " bytes");
        }
        requireNoMore();
    }

    private void requireNoMore() throws IOException {
        if (in.read() != -1) {
            throw new IOException("Entry '" + name + "' holds more than its declared " + declared + " bytes");
        }
    }
}
