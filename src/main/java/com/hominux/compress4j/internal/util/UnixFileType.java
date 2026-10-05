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
package com.hominux.compress4j.internal.util;

import com.hominux.compress4j.archivers.Entry;
import java.util.Optional;

/** The file type encoded in the {@code S_IFMT} bits of a Unix mode; a mode without type bits is a regular file. */
public enum UnixFileType {
    FILE("regular file"),
    DIRECTORY("directory"),
    SYMLINK("symbolic link"),
    CHARACTER_DEVICE("character device"),
    BLOCK_DEVICE("block device"),
    FIFO("fifo"),
    SOCKET("socket"),
    UNKNOWN("unknown type");

    private static final int S_IFMT = 0170000;

    private final String kind;

    UnixFileType(String kind) {
        this.kind = kind;
    }

    /**
     * Classifies the {@code S_IFMT} bits of {@code mode}; type bits outside the seven POSIX types yield
     * {@link #UNKNOWN}.
     */
    public static UnixFileType of(int mode) {
        return switch (mode & S_IFMT) {
            case 0, 0100000 -> FILE;
            case 0040000 -> DIRECTORY;
            case 0120000 -> SYMLINK;
            case 0020000 -> CHARACTER_DEVICE;
            case 0060000 -> BLOCK_DEVICE;
            case 0010000 -> FIFO;
            case 0140000 -> SOCKET;
            default -> UNKNOWN;
        };
    }

    /** Returns the human-readable name of this type, as reported to the unsupported-entry handler. */
    public String kind() {
        return kind;
    }

    /** Returns the entry type that extraction supports for this file type, or empty for every other type. */
    public Optional<Entry.Type> entryType() {
        return switch (this) {
            case FILE -> Optional.of(Entry.Type.FILE);
            case DIRECTORY -> Optional.of(Entry.Type.DIR);
            case SYMLINK -> Optional.of(Entry.Type.SYMLINK);
            default -> Optional.empty();
        };
    }
}
