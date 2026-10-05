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
package com.hominux.compress4j.archivers;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import com.hominux.compress4j.internal.limits.ExpansionMeter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Tracks what one {@link ArchiveExtractor#stream()} or {@link ArchiveExtractor#extract} run has consumed against its
 * {@link ExtractionLimits}.
 */
final class ExtractionBudget {

    private final ExtractionLimits limits;
    private final ExpansionMeter meter;
    private long entries = 0;

    ExtractionBudget(ExtractionLimits limits, LongSupplier compressedBytes) {
        this(limits, new ExpansionMeter(limits, compressedBytes));
    }

    ExtractionBudget(ExtractionLimits limits, ExpansionMeter meter) {
        this.limits = limits;
        this.meter = meter;
    }

    void countEntry() throws LimitExceededException {
        if (limits.maxEntries() != ExtractionLimits.UNLIMITED && ++entries > limits.maxEntries()) {
            throw new LimitExceededException(Limit.ENTRIES, limits.maxEntries(), Optional.empty());
        }
    }

    InputStream meter(String entryName, InputStream in) {
        if (limits.maxEntrySize() == ExtractionLimits.UNLIMITED
                && limits.maxTotalSize() == ExtractionLimits.UNLIMITED
                && limits.maxRatio() == ExtractionLimits.UNLIMITED) {
            return in;
        }
        return new MeteredInputStream(entryName, in);
    }

    private final class MeteredInputStream extends FilterInputStream {
        private final String entryName;
        private long entryBytes;

        private MeteredInputStream(String entryName, InputStream in) {
            super(in);
            this.entryName = entryName;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            count(skipped);
            return skipped;
        }

        private void count(long n) throws LimitExceededException {
            entryBytes += n;
            if (limits.maxEntrySize() != ExtractionLimits.UNLIMITED && entryBytes > limits.maxEntrySize()) {
                throw new LimitExceededException(Limit.ENTRY_SIZE, limits.maxEntrySize(), Optional.of(entryName));
            }
            meter.produced(n, Optional.of(entryName));
        }
    }
}
