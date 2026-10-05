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
package com.hominux.compress4j.archivers;

import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * An archive entry as callers see it: name after normalization, type, Unix mode (0 when the format has none), symlink
 * target, last-modified time and uncompressed size when the format records them.
 *
 * <p>{@link #name} is normalized but not checked for path traversal.
 *
 * @param name the normalized entry name
 * @param type the entry type
 * @param mode the Unix mode, or 0 when unknown
 * @param linkTarget the symlink target, set for {@link Type#SYMLINK} entries read from an archive
 * @param lastModified the last-modified time, when the archive records one
 * @param size the uncompressed size, when known before reading the content
 */
public record Entry(
        String name,
        Type type,
        int mode,
        Optional<String> linkTarget,
        Optional<FileTime> lastModified,
        OptionalLong size) {

    /**
     * Normalizes the name (trimmed, forward slashes, no leading or trailing slash); a blank link target and a negative
     * size become empty.
     *
     * @throws NullPointerException if any component is null
     */
    public Entry {
        Objects.requireNonNull(type, "type");
        linkTarget = Objects.requireNonNull(linkTarget, "linkTarget").filter(t -> !t.isBlank());
        Objects.requireNonNull(lastModified, "lastModified");
        size = Objects.requireNonNull(size, "size").isPresent() && size.getAsLong() < 0 ? OptionalLong.empty() : size;
        name = Objects.requireNonNull(name, "name").trim().replace('\\', '/');
        int s = 0;
        int e = name.length() - 1;
        while (s < e && name.charAt(s) == '/') s++;
        while (e >= s && name.charAt(e) == '/') e--;
        name = name.substring(s, e + 1);
    }

    /**
     * Creates an entry without link target, last-modified time or size.
     *
     * @param name the name of the entry
     * @param type the type of the entry
     * @param mode the mode of the entry
     */
    public Entry(String name, Type type, int mode) {
        this(name, type, mode, Optional.empty(), Optional.empty(), OptionalLong.empty());
    }

    /**
     * Creates a FILE or DIR entry with mode 0.
     *
     * @param name the name of the entry
     * @param isDirectory whether the entry is a directory
     */
    public Entry(String name, boolean isDirectory) {
        this(name, isDirectory ? Type.DIR : Type.FILE, 0);
    }

    /**
     * Returns a copy with the given link target; a blank target yields an empty one.
     *
     * @param target the symlink target
     * @return the copy
     * @throws NullPointerException if {@code target} is {@code null}
     */
    public Entry withLinkTarget(String target) {
        return new Entry(name, type, mode, Optional.of(target), lastModified, size);
    }

    /**
     * Returns a copy with the given last-modified time.
     *
     * @param time the last-modified time
     * @return the copy
     * @throws NullPointerException if {@code time} is {@code null}
     */
    public Entry withLastModified(FileTime time) {
        return new Entry(name, type, mode, linkTarget, Optional.of(time), size);
    }

    /**
     * Returns a copy with the given uncompressed size; a negative size yields an empty one.
     *
     * @param bytes the uncompressed size
     * @return the copy
     */
    public Entry withSize(long bytes) {
        return new Entry(name, type, mode, linkTarget, lastModified, OptionalLong.of(bytes));
    }

    /**
     * Returns a copy with the given name, normalized.
     *
     * @param newName the new name
     * @return the copy
     */
    public Entry withName(String newName) {
        return new Entry(newName, type, mode, linkTarget, lastModified, size);
    }

    /** Type of the entry. */
    public enum Type {
        FILE,
        DIR,
        SYMLINK
    }
}
