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
package com.hominux.compress4j.compressors;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

final class Checks {
    static final String DECOMPRESS_CONCATENATED = ", decompressConcatenated=";

    private static final Instant GZIP_MIN = Instant.ofEpochSecond(1);
    private static final Instant GZIP_MAX = Instant.ofEpochSecond(0xFFFFFFFFL);

    private static final int MAX_ASSIGNED_OS = 13;
    private static final int UNKNOWN_OS = 255;

    private Checks() {}

    static void range(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ": " + value);
        }
    }

    static void gzipOperatingSystem(String name, int value) {
        if (value != UNKNOWN_OS && (value < 0 || value > MAX_ASSIGNED_OS)) {
            throw new IllegalArgumentException(
                    name + " must be an RFC 1952 code, 0 to " + MAX_ASSIGNED_OS + " or " + UNKNOWN_OS + ": " + value);
        }
    }

    static void positive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be above 0: " + value);
        }
    }

    static void limit(String name, OptionalInt value) {
        if (value.isPresent() && value.getAsInt() <= 0) {
            throw new IllegalArgumentException(name + " must be above 0: " + value.getAsInt());
        }
    }

    static void gzipTime(String name, Optional<Instant> value) {
        if (value.isPresent() && (value.get().isBefore(GZIP_MIN) || value.get().isAfter(GZIP_MAX))) {
            throw new IllegalArgumentException(
                    name + " must be between " + GZIP_MIN + " and " + GZIP_MAX + ": " + value.get());
        }
    }
}
