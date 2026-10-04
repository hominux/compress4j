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

import static com.hominux.compress4j.utils.FileUtils.NO_MODE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;
import org.apache.commons.lang3.SystemUtils;

/** Builds {@link EntrySource}s for filesystem paths. */
final class PathSources {

    private PathSources() {}

    static EntrySource of(String name, Path path, BasicFileAttributes attrs, FileTime lastModified) throws IOException {
        return of(name, path, attrs, lastModified, SystemUtils.IS_OS_WINDOWS);
    }

    /** DOS attributes are not permission bits, so on Windows the mode stays unknown and the format default applies. */
    static EntrySource of(String name, Path path, BasicFileAttributes attrs, FileTime lastModified, boolean windows)
            throws IOException {
        if (attrs.isOther()) {
            throw new IllegalArgumentException(path + " is not a regular file, directory or symlink");
        }
        int mode = windows ? NO_MODE : FileModes.of(path, false);
        if (attrs.isSymbolicLink()) {
            return new EntrySource.Symlink(name, Files.readSymbolicLink(path).toString(), mode, lastModified);
        }
        if (attrs.isDirectory()) {
            return new EntrySource.Directory(name, mode, lastModified);
        }
        return new EntrySource.File(
                name, mode, lastModified, OptionalLong.of(attrs.size()), () -> Files.newInputStream(path));
    }
}
