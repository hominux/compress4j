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

/** XZ. Obtain the default with {@link Compression#xz()}. */
public final class Xz implements Compression {
    private final int preset;
    private final OptionalInt memoryLimitKiB;
    private final boolean decompressConcatenated;

    Xz(int preset, OptionalInt memoryLimitKiB, boolean decompressConcatenated) {
        Checks.range("xz preset", preset, 0, 9);
        Objects.requireNonNull(memoryLimitKiB, "memoryLimitKiB");
        Checks.limit("xz memoryLimitKiB", memoryLimitKiB);
        this.preset = preset;
        this.memoryLimitKiB = memoryLimitKiB;
        this.decompressConcatenated = decompressConcatenated;
    }

    /**
     * Returns the compression preset, from 0 to 9.
     *
     * @return the compression preset, from 0 to 9
     */
    public int preset() {
        return preset;
    }

    /**
     * Returns a copy with the compression preset, from 0 (fastest) to 9 (smallest).
     *
     * @param value the compression preset, from 0 (fastest) to 9 (smallest)
     * @return the changed copy
     * @throws IllegalArgumentException if the value is below 0 or above 9
     */
    public Xz preset(int value) {
        return new Xz(value, memoryLimitKiB, decompressConcatenated);
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
    public Xz memoryLimitKiB(OptionalInt value) {
        return new Xz(preset, value, decompressConcatenated);
    }

    /**
     * Returns a copy with the memory limit for reading in KiB.
     *
     * @param value the memory limit for reading in KiB
     * @return the changed copy
     * @throws IllegalArgumentException if the value is not above 0
     */
    public Xz memoryLimitKiB(int value) {
        return memoryLimitKiB(OptionalInt.of(value));
    }

    /**
     * Returns whether reading continues across concatenated XZ streams.
     *
     * @return whether reading continues across concatenated XZ streams
     */
    public boolean decompressConcatenated() {
        return decompressConcatenated;
    }

    /**
     * Returns a copy with whether reading continues across concatenated XZ streams.
     *
     * @param value whether reading continues across concatenated XZ streams
     * @return the changed copy
     */
    public Xz decompressConcatenated(boolean value) {
        return new Xz(preset, memoryLimitKiB, value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Xz that
                && preset == that.preset
                && Objects.equals(memoryLimitKiB, that.memoryLimitKiB)
                && decompressConcatenated == that.decompressConcatenated;
    }

    @Override
    public int hashCode() {
        return Objects.hash(preset, memoryLimitKiB, decompressConcatenated);
    }

    @Override
    public String toString() {
        return "Xz[preset=" + preset + ", memoryLimitKiB=" + memoryLimitKiB + Checks.DECOMPRESS_CONCATENATED
                + decompressConcatenated + "]";
    }
}
