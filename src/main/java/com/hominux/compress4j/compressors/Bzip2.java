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

/** Bzip2. Obtain the default with {@link Compression#bzip2()}. */
public final class Bzip2 implements Compression {
    private final int blockSize;
    private final boolean decompressConcatenated;

    Bzip2(int blockSize, boolean decompressConcatenated) {
        Checks.range("bzip2 blockSize", blockSize, 1, 9);
        this.blockSize = blockSize;
        this.decompressConcatenated = decompressConcatenated;
    }

    /**
     * Returns the block size in units of 100 KB, from 1 to 9.
     *
     * @return the block size in units of 100 KB, from 1 to 9
     */
    public int blockSize() {
        return blockSize;
    }

    /**
     * Returns a copy with the block size in units of 100 KB, from 1 to 9.
     *
     * @param value the block size in units of 100 KB, from 1 to 9
     * @return the changed copy
     * @throws IllegalArgumentException if the value is below 1 or above 9
     */
    public Bzip2 blockSize(int value) {
        return new Bzip2(value, decompressConcatenated);
    }

    /**
     * Returns whether reading continues across concatenated bzip2 streams.
     *
     * @return whether reading continues across concatenated bzip2 streams
     */
    public boolean decompressConcatenated() {
        return decompressConcatenated;
    }

    /**
     * Returns a copy with whether reading continues across concatenated bzip2 streams.
     *
     * @param value whether reading continues across concatenated bzip2 streams
     * @return the changed copy
     */
    public Bzip2 decompressConcatenated(boolean value) {
        return new Bzip2(blockSize, value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Bzip2 that
                && blockSize == that.blockSize
                && decompressConcatenated == that.decompressConcatenated;
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockSize, decompressConcatenated);
    }

    @Override
    public String toString() {
        return "Bzip2[blockSize=" + blockSize + Checks.DECOMPRESS_CONCATENATED + decompressConcatenated + "]";
    }
}
