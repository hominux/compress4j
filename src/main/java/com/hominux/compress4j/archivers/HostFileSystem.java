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

import com.hominux.compress4j.internal.util.PosixFilePermissionsMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Set;

/**
 * Reads and applies Unix modes on the file system that holds a path, chosen from the attribute views that file system
 * supports. Archive metadata never depends on the host; only these two operations do.
 *
 * <p>The DOS adapter never marks a directory read-only: on NTFS the attribute blocks deleting the directory without
 * preventing writes into it.
 */
enum HostFileSystem {
    POSIX {
        @Override
        int modeOf(Path path) throws IOException {
            PosixFileAttributeView view =
                    Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            return view == null
                    ? 0
                    : PosixFilePermissionsMapper.toUnixMode(
                            view.readAttributes().permissions());
        }

        @Override
        void applyMode(Path path, int unixMode) throws IOException {
            PosixFileAttributeView view =
                    Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (unixMode != 0 && view != null) {
                view.setPermissions(PosixFilePermissionsMapper.fromUnixMode(unixMode));
            }
        }
    },
    DOS {
        @Override
        int modeOf(Path path) {
            return 0;
        }

        @Override
        void applyMode(Path path, int unixMode) throws IOException {
            DosFileAttributeView view =
                    Files.getFileAttributeView(path, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (unixMode != 0
                    && (unixMode & OWNER_WRITE) == 0
                    && view != null
                    && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                view.setReadOnly(true);
            }
        }
    },
    NONE {
        @Override
        int modeOf(Path path) {
            return 0;
        }

        @Override
        void applyMode(Path path, int unixMode) {
            // This file system stores no permissions.
        }
    };

    private static final int OWNER_WRITE = 0200;

    /**
     * Returns the Unix permission bits of {@code path} (a symbolic link's own bits), or 0 when this file system does
     * not report them.
     */
    abstract int modeOf(Path path) throws IOException;

    /**
     * Applies the permission bits of {@code unixMode} to {@code path}; type and special bits are ignored and mode 0
     * changes nothing. The DOS adapter only sets the read-only attribute, on non-directories whose mode lacks owner
     * write.
     */
    abstract void applyMode(Path path, int unixMode) throws IOException;

    static HostFileSystem of(Path path) {
        Set<String> views = path.getFileSystem().supportedFileAttributeViews();
        if (views.contains("posix")) {
            return POSIX;
        }
        return views.contains("dos") ? DOS : NONE;
    }
}
