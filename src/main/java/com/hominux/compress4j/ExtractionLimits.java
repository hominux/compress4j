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
package com.hominux.compress4j;

/**
 * Limits on what reading untrusted input may produce. Each component is a maximum, or {@link #UNLIMITED}. A breach
 * throws {@link com.hominux.compress4j.exceptions.LimitExceededException}, which error handlers cannot suppress.
 *
 * @param maxEntries maximum number of archive entries, counted after strip-components and the filter
 * @param maxEntrySize maximum uncompressed bytes of one entry
 * @param maxTotalSize maximum uncompressed bytes of the whole input
 * @param maxRatio maximum uncompressed bytes produced per compressed byte read. Readers in this version validate it but
 *     do not enforce it.
 * @since 5.0
 */
public record ExtractionLimits(long maxEntries, long maxEntrySize, long maxTotalSize, long maxRatio) {

    /** Value disabling a limit. */
    public static final long UNLIMITED = -1;

    private static final ExtractionLimits DEFAULTS = new ExtractionLimits(1_000_000, UNLIMITED, UNLIMITED, 100);
    private static final ExtractionLimits NONE = new ExtractionLimits(UNLIMITED, UNLIMITED, UNLIMITED, UNLIMITED);

    /**
     * Rejects a count or size below 0 and a ratio below 1, except {@link #UNLIMITED}.
     *
     * @throws IllegalArgumentException if a size or count is below 0 other than {@link #UNLIMITED}, or the ratio is
     *     below 1 other than {@link #UNLIMITED}
     */
    public ExtractionLimits {
        requireLimit("maxEntries", maxEntries, 0);
        requireLimit("maxEntrySize", maxEntrySize, 0);
        requireLimit("maxTotalSize", maxTotalSize, 0);
        requireLimit("maxRatio", maxRatio, 1);
    }

    private static void requireLimit(String name, long value, long minimum) {
        if (value != UNLIMITED && value < minimum) {
            throw new IllegalArgumentException(
                    name + " must be " + UNLIMITED + " or at least " + minimum + ": " + value);
        }
    }

    /**
     * Returns the limits every reader starts from: 1,000,000 entries, an expansion ratio of 100, no size limits.
     *
     * @return the default limits
     */
    public static ExtractionLimits defaults() {
        return DEFAULTS;
    }

    /**
     * Returns limits that allow anything; use only for trusted input.
     *
     * @return limits with every component {@link #UNLIMITED}
     */
    public static ExtractionLimits unlimited() {
        return NONE;
    }

    /**
     * Returns a copy with the given entry limit.
     *
     * @param value the maximum number of entries, or {@link #UNLIMITED}
     * @return the copy
     * @throws IllegalArgumentException if the value is below 0 and not {@link #UNLIMITED}
     */
    public ExtractionLimits withMaxEntries(long value) {
        return new ExtractionLimits(value, maxEntrySize, maxTotalSize, maxRatio);
    }

    /**
     * Returns a copy with the given entry size limit.
     *
     * @param value the maximum bytes of one entry, or {@link #UNLIMITED}
     * @return the copy
     * @throws IllegalArgumentException if the value is below 0 and not {@link #UNLIMITED}
     */
    public ExtractionLimits withMaxEntrySize(long value) {
        return new ExtractionLimits(maxEntries, value, maxTotalSize, maxRatio);
    }

    /**
     * Returns a copy with the given total size limit.
     *
     * @param value the maximum bytes of the whole input, or {@link #UNLIMITED}
     * @return the copy
     * @throws IllegalArgumentException if the value is below 0 and not {@link #UNLIMITED}
     */
    public ExtractionLimits withMaxTotalSize(long value) {
        return new ExtractionLimits(maxEntries, maxEntrySize, value, maxRatio);
    }

    /**
     * Returns a copy with the given expansion ratio limit.
     *
     * @param value the maximum ratio, at least 1, or {@link #UNLIMITED}
     * @return the copy
     * @throws IllegalArgumentException if the value is below 1 and not {@link #UNLIMITED}
     */
    public ExtractionLimits withMaxRatio(long value) {
        return new ExtractionLimits(maxEntries, maxEntrySize, maxTotalSize, value);
    }
}
