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
package com.hominux.compress4j.archivers;

import java.util.Objects;

/**
 * An archive entry the reader skipped because Compress4J cannot extract its type, such as a hard link, a device or a
 * FIFO. The name is the raw archive name, before strip-components and the filter.
 *
 * @param name the entry name as stored in the archive
 * @param kind a readable description of the entry type, for example {@code "hard link"}
 * @since 5.0
 */
public record UnsupportedEntry(String name, String kind) {
    /**
     * Rejects null components.
     *
     * @throws NullPointerException if a component is null
     */
    public UnsupportedEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
    }
}
