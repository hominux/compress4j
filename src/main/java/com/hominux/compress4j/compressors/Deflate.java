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

/** Deflate with an optional zlib header. Obtain the default with {@link Compression#deflate()}. */
public final class Deflate implements Compression {
    private final int level;
    private final boolean zlibHeader;

    Deflate(int level, boolean zlibHeader) {
        Checks.range("deflate level", level, -1, 9);
        this.level = level;
        this.zlibHeader = zlibHeader;
    }

    /**
     * Returns the compression level, from -1 (default) to 9.
     *
     * @return the compression level, from -1 (default) to 9
     */
    public int level() {
        return level;
    }

    /**
     * Returns a copy with the compression level, from -1 (default) to 9.
     *
     * @param value the compression level, from -1 (default) to 9
     * @return the changed copy
     * @throws IllegalArgumentException if the value is below -1 or above 9
     */
    public Deflate level(int value) {
        return new Deflate(value, zlibHeader);
    }

    /**
     * Returns whether the stream has a zlib header and trailer.
     *
     * @return whether the stream has a zlib header and trailer
     */
    public boolean zlibHeader() {
        return zlibHeader;
    }

    /**
     * Returns a copy with whether the stream has a zlib header and trailer.
     *
     * @param value whether the stream has a zlib header and trailer
     * @return the changed copy
     */
    public Deflate zlibHeader(boolean value) {
        return new Deflate(level, value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Deflate that && level == that.level && zlibHeader == that.zlibHeader;
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, zlibHeader);
    }

    @Override
    public String toString() {
        return "Deflate[level=" + level + ", zlibHeader=" + zlibHeader + "]";
    }
}
