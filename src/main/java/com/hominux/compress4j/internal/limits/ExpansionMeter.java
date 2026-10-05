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
package com.hominux.compress4j.internal.limits;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Meters uncompressed output against the total-size and expansion-ratio limits. Not thread-safe. */
public final class ExpansionMeter {

    /** Uncompressed bytes allowed before the ratio is enforced. */
    public static final long RATIO_GRACE_BYTES = 1L << 20;

    private final ExtractionLimits limits;
    private final LongSupplier compressedBytes;
    private long produced;

    /**
     * Creates a meter.
     *
     * @param limits the limits to enforce
     * @param compressedBytes supplies the compressed bytes consumed so far
     */
    public ExpansionMeter(ExtractionLimits limits, LongSupplier compressedBytes) {
        this.limits = limits;
        this.compressedBytes = compressedBytes;
    }

    /**
     * Adds produced bytes and enforces the limits. Non-positive counts are ignored. The total saturates at
     * {@link Long#MAX_VALUE}. The ratio is {@code floor(produced / max(1, compressed))} and is enforced only once the
     * total exceeds {@link #RATIO_GRACE_BYTES}.
     *
     * @param bytes the bytes produced
     * @param entryName the entry being produced, reported on a breach
     * @throws LimitExceededException if the total exceeds the maximum total size or the ratio exceeds the maximum
     */
    public void produced(long bytes, Optional<String> entryName) throws LimitExceededException {
        if (bytes <= 0) {
            return;
        }
        produced = saturatingAdd(produced, bytes);
        if (limits.maxTotalSize() != ExtractionLimits.UNLIMITED && produced > limits.maxTotalSize()) {
            throw new LimitExceededException(Limit.TOTAL_SIZE, limits.maxTotalSize(), entryName);
        }
        if (ratioExceeded()) {
            throw new LimitExceededException(Limit.RATIO, limits.maxRatio(), entryName);
        }
    }

    private boolean ratioExceeded() {
        return limits.maxRatio() != ExtractionLimits.UNLIMITED
                && produced > RATIO_GRACE_BYTES
                && produced / Math.max(1, compressedBytes.getAsLong()) > limits.maxRatio();
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }

    /**
     * Returns the bytes produced so far.
     *
     * @return the running total
     */
    public long produced() {
        return produced;
    }

    /**
     * Wraps a stream so every read and skip is metered. Reset rewinds the total to its value at the mark.
     *
     * @param decompressed the decompressed stream
     * @param entryName the entry being read, reported on a breach
     * @return the metered stream
     */
    public InputStream meter(InputStream decompressed, Optional<String> entryName) {
        return new MeteredInputStream(decompressed, entryName);
    }

    private final class MeteredInputStream extends FilterInputStream {
        private final Optional<String> entryName;
        private long markedProduced;

        MeteredInputStream(InputStream in, Optional<String> entryName) {
            super(in);
            this.entryName = entryName;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                produced(1, entryName);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            produced(n, entryName);
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            produced(skipped, entryName);
            return skipped;
        }

        @Override
        public synchronized void mark(int readLimit) {
            super.mark(readLimit);
            markedProduced = produced;
        }

        @Override
        public synchronized void reset() throws IOException {
            super.reset();
            produced = markedProduced;
        }
    }
}
