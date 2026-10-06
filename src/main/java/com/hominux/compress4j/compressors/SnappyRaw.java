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
import java.util.OptionalLong;

/** Raw Snappy. Writing needs the uncompressed size. Obtain the default with {@link Compression#snappyRaw()}. */
public final class SnappyRaw implements Compression {
    private final OptionalLong uncompressedSize;

    SnappyRaw(OptionalLong uncompressedSize) {
        Objects.requireNonNull(uncompressedSize, "uncompressedSize");
        if (uncompressedSize.isPresent() && uncompressedSize.getAsLong() < 0) {
            throw new IllegalArgumentException(
                    "snappy uncompressedSize must be at least 0: " + uncompressedSize.getAsLong());
        }
        this.uncompressedSize = uncompressedSize;
    }

    /**
     * Returns the uncompressed size in bytes, required for writing.
     *
     * @return the uncompressed size in bytes, required for writing
     */
    public OptionalLong uncompressedSize() {
        return uncompressedSize;
    }

    /**
     * Returns a copy with the uncompressed size in bytes, empty for unknown.
     *
     * @param value the uncompressed size in bytes, empty for unknown
     * @return the changed copy
     * @throws IllegalArgumentException if a present value is below 0
     * @throws NullPointerException if the value is null
     */
    public SnappyRaw uncompressedSize(OptionalLong value) {
        return new SnappyRaw(value);
    }

    /**
     * Returns a copy with the uncompressed size in bytes.
     *
     * @param value the uncompressed size in bytes
     * @return the changed copy
     * @throws IllegalArgumentException if the value is below 0
     */
    public SnappyRaw uncompressedSize(long value) {
        return uncompressedSize(OptionalLong.of(value));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SnappyRaw that && Objects.equals(uncompressedSize, that.uncompressedSize);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uncompressedSize);
    }

    @Override
    public String toString() {
        return "SnappyRaw[uncompressedSize=" + uncompressedSize + "]";
    }
}
