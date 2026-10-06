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

import java.util.Objects;
import java.util.OptionalInt;

/**
 * LZMA, whose streams have no magic number: readers never detect it, so name it explicitly. Obtain the default with
 * {@link Compression#lzma()}.
 */
public final class Lzma implements Compression {
    private final OptionalInt memoryLimitKiB;

    Lzma(OptionalInt memoryLimitKiB) {
        Objects.requireNonNull(memoryLimitKiB, "memoryLimitKiB");
        Checks.limit("lzma memoryLimitKiB", memoryLimitKiB);
        this.memoryLimitKiB = memoryLimitKiB;
    }

    /**
     * Returns the memory limit for reading in KiB; empty means 256 MiB; {@link Integer#MAX_VALUE} KiB (about 2 TiB)
     * lifts it.
     *
     * @return the memory limit for reading in KiB, or empty for the default
     */
    public OptionalInt memoryLimitKiB() {
        return memoryLimitKiB;
    }

    /**
     * Returns a copy with the memory limit for reading in KiB; empty means 256 MiB; {@link Integer#MAX_VALUE} KiB
     * (about 2 TiB) lifts it.
     *
     * @param value the memory limit for reading in KiB, or empty for the default
     * @return the changed copy
     * @throws IllegalArgumentException if a present value is not above 0
     * @throws NullPointerException if the value is null
     */
    public Lzma memoryLimitKiB(OptionalInt value) {
        return new Lzma(value);
    }

    /**
     * Returns a copy with the memory limit for reading in KiB.
     *
     * @param value the memory limit for reading in KiB
     * @return the changed copy
     * @throws IllegalArgumentException if the value is not above 0
     */
    public Lzma memoryLimitKiB(int value) {
        return memoryLimitKiB(OptionalInt.of(value));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Lzma that && Objects.equals(memoryLimitKiB, that.memoryLimitKiB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memoryLimitKiB);
    }

    @Override
    public String toString() {
        return "Lzma[memoryLimitKiB=" + memoryLimitKiB + "]";
    }
}
