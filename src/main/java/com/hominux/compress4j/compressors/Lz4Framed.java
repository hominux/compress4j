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

/** LZ4 frame format. Obtain the default with {@link Compression#lz4Framed()}. */
public final class Lz4Framed implements Compression {
    private final boolean decompressConcatenated;

    Lz4Framed(boolean decompressConcatenated) {
        this.decompressConcatenated = decompressConcatenated;
    }

    /**
     * Returns whether reading continues across concatenated LZ4 frames.
     *
     * @return whether reading continues across concatenated LZ4 frames
     */
    public boolean decompressConcatenated() {
        return decompressConcatenated;
    }

    /**
     * Returns a copy with whether reading continues across concatenated LZ4 frames.
     *
     * @param value whether reading continues across concatenated LZ4 frames
     * @return the changed copy
     */
    public Lz4Framed decompressConcatenated(boolean value) {
        return new Lz4Framed(value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Lz4Framed that && decompressConcatenated == that.decompressConcatenated;
    }

    @Override
    public int hashCode() {
        return Objects.hash(decompressConcatenated);
    }

    @Override
    public String toString() {
        return "Lz4Framed[decompressConcatenated=" + decompressConcatenated + "]";
    }
}
