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
package com.hominux.compress4j.archivers.tar;

import com.hominux.compress4j.internal.limits.ExpansionMeter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.commons.compress.archivers.tar.TarConstants;

/**
 * Meters the bytes the tar parser consumes for headers, long names, PAX records and sparse maps. Metering is paused
 * while entry content is delivered, because the extraction pipeline meters that content itself.
 */
final class GatedMeterInputStream extends FilterInputStream {

    /** Per-entry allowance, in tar records, for fixed framing that does not count as produced. */
    private static final long FRAMING_ALLOWANCE = 5L * TarConstants.DEFAULT_RCDSIZE;

    private final ExpansionMeter meter;
    private boolean metering = true;
    private long unmetered = FRAMING_ALLOWANCE;
    private long skipCredit;
    private long contentRead;

    GatedMeterInputStream(InputStream in, ExpansionMeter meter) {
        super(in);
        this.meter = meter;
    }

    void pause() {
        metering = false;
    }

    /** Returns the content bytes delivered while paused since the last call. */
    long takeContentRead() {
        long read = contentRead;
        contentRead = 0;
        return read;
    }

    /** Treats the next {@code bytes} consumed as skipped entry content, which the pipeline meters only when read. */
    void creditSkip(long bytes) {
        skipCredit = Math.max(0, bytes);
    }

    void resume() {
        metering = true;
        unmetered = FRAMING_ALLOWANCE;
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
        count(n);
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        count(skipped);
        return skipped;
    }

    private void count(long bytes) throws IOException {
        if (bytes <= 0) {
            return;
        }
        if (!metering) {
            contentRead += bytes;
            return;
        }
        long skipped = Math.min(skipCredit, bytes);
        skipCredit -= skipped;
        long forgiven = Math.min(unmetered, bytes - skipped);
        unmetered -= forgiven;
        meter.produced(bytes - skipped - forgiven, Optional.empty());
    }
}
