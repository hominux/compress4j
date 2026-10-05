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
package com.hominux.compress4j.exceptions;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Thrown when input exceeds one of the configured extraction limits. Error handlers cannot suppress it, so a handler
 * that skips errors cannot keep feeding a decompression bomb.
 *
 * @since 5.0
 */
public final class LimitExceededException extends UnsafeInputException {

    /** The limit that was exceeded. */
    public enum Limit {
        /** Number of entries in an archive. */
        ENTRIES("entry count", "maxEntries"),
        /** Uncompressed size of one entry. */
        ENTRY_SIZE("entry size", "maxEntrySize"),
        /** Uncompressed size of the whole input. */
        TOTAL_SIZE("total size", "maxTotalSize"),
        /** Uncompressed bytes produced per compressed byte read. */
        RATIO("expansion ratio", "maxRatio");

        private final String description;
        private final String builderMethod;

        Limit(String description, String builderMethod) {
            this.description = description;
            this.builderMethod = builderMethod;
        }
    }

    private final Limit limit;
    private final long maximum;
    private final @Nullable String entryName;

    /**
     * Creates the exception.
     *
     * @param limit the limit that was exceeded
     * @param maximum the configured maximum
     * @param entryName the entry being read, when the limit applies to one entry
     * @throws NullPointerException if {@code limit} or {@code entryName} is {@code null}
     */
    public LimitExceededException(Limit limit, long maximum, Optional<String> entryName) {
        super(message(Objects.requireNonNull(limit, "limit"), maximum, Objects.requireNonNull(entryName, "entryName")));
        this.limit = limit;
        this.maximum = maximum;
        this.entryName = entryName.orElse(null);
    }

    private static String message(Limit limit, long maximum, Optional<String> entryName) {
        String subject = entryName.map(name -> "Entry '" + name + "'").orElse("Input");
        return subject + " exceeded the " + limit.description + " limit of " + maximum + " (raise it with "
                + limit.builderMethod + ")";
    }

    /**
     * Returns the limit that was exceeded.
     *
     * @return the limit
     */
    public Limit limit() {
        return limit;
    }

    /**
     * Returns the configured maximum.
     *
     * @return the maximum
     */
    public long maximum() {
        return maximum;
    }

    /**
     * Returns the entry being read when the limit applies to one entry.
     *
     * @return the entry name, or empty
     */
    public Optional<String> entryName() {
        return Optional.ofNullable(entryName);
    }
}
