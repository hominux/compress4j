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

/**
 * Zstandard. Negative levels trade ratio for speed; 0 selects the default level; the accepted range is -131072 to 22.
 * Obtain the default with {@link Compression#zstd()}.
 */
public final class Zstd implements Compression {
    private final int level;

    Zstd(int level) {
        Checks.range("zstd level", level, -131072, 22);
        this.level = level;
    }

    /**
     * Returns the compression level.
     *
     * @return the compression level
     */
    public int level() {
        return level;
    }

    /**
     * Returns a copy with the compression level.
     *
     * @param value the compression level, -131072 to 22
     * @return the changed copy
     * @throws IllegalArgumentException if the value lies outside -131072 to 22
     */
    public Zstd level(int value) {
        return new Zstd(value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Zstd that && level == that.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(level);
    }

    @Override
    public String toString() {
        return "Zstd[level=" + level + "]";
    }
}
