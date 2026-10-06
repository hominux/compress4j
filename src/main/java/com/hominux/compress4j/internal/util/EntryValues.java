/*
 * Copyright 2024-2026 The Compress4J Project
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
package com.hominux.compress4j.internal.util;

import com.hominux.compress4j.archivers.Entry;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import org.apache.commons.compress.archivers.ArchiveEntry;

/** Applies possibly-absent commons-compress values to an {@link Entry}; an absent value leaves the entry unchanged. */
public final class EntryValues {

    private EntryValues() {}

    /**
     * Returns the entry with the given link target, or the entry itself when the target is absent.
     *
     * @param entry the entry
     * @param target the symlink target, possibly absent
     * @return the resulting entry
     */
    public static Entry withLinkTarget(Entry entry, Optional<String> target) {
        return target.map(entry::withLinkTarget).orElse(entry);
    }

    /**
     * Returns the entry with the given size and, when the source reports one, its last-modified time.
     *
     * @param entry the entry
     * @param source the commons-compress entry whose last-modified date, when present, is applied
     * @param bytes the size; negative means unknown
     * @return the resulting entry
     */
    public static Entry withMetadata(Entry entry, ArchiveEntry source, long bytes) {
        return Optional.ofNullable(source.getLastModifiedDate())
                .map(modified -> FileTime.fromMillis(modified.getTime()))
                .map(entry::withLastModified)
                .orElse(entry)
                .withSize(bytes);
    }
}
