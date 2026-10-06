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

import java.util.Map;
import java.util.Objects;

/**
 * Pack200. Obtain the default with {@link Compression#pack200()}.
 *
 * <p>Pack200 is never selected by detection in Decompressor; select it explicitly. The packer loses the content of
 * deflated streamed JAR entries: write entries STORED with size and CRC.
 */
public final class Pack200 implements Compression {
    /** Where Pack200 buffers data. */
    public enum Strategy {
        /** Buffers in memory. */
        IN_MEMORY,
        /** Buffers in a temporary file. */
        TEMP_FILE
    }

    private final Pack200.Strategy strategy;
    private final Map<String, String> properties;

    Pack200(Pack200.Strategy strategy, Map<String, String> properties) {
        Objects.requireNonNull(strategy, "strategy");
        properties = Map.copyOf(properties);
        this.strategy = strategy;
        this.properties = properties;
    }

    /**
     * Returns where Pack200 buffers data.
     *
     * @return where Pack200 buffers data
     */
    public Pack200.Strategy strategy() {
        return strategy;
    }

    /**
     * Returns a copy with where Pack200 buffers data.
     *
     * @param value where Pack200 buffers data
     * @return the changed copy
     * @throws NullPointerException if the value is null
     */
    public Pack200 strategy(Pack200.Strategy value) {
        return new Pack200(value, properties);
    }

    /**
     * Returns the Pack200 packer and unpacker properties.
     *
     * @return the Pack200 packer and unpacker properties
     */
    public Map<String, String> properties() {
        return properties;
    }

    /**
     * Returns a copy with the Pack200 packer and unpacker properties, copied.
     *
     * @param value the Pack200 packer and unpacker properties, copied
     * @return the changed copy
     * @throws NullPointerException if the map, or any of its keys or values, is null
     */
    public Pack200 properties(Map<String, String> value) {
        return new Pack200(strategy, value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Pack200 that
                && Objects.equals(strategy, that.strategy)
                && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(strategy, properties);
    }

    @Override
    public String toString() {
        return "Pack200[strategy=" + strategy + ", properties=" + properties + "]";
    }
}
