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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class PathSourcesTest {

    @TempDir
    Path dir;

    private EntrySource sourceOf(Path path, boolean windows) throws IOException {
        var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return PathSources.of("f", path, attrs, attrs.lastModifiedTime(), windows);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void readsPosixPermissionsOffWindows() throws IOException {
        Path file = Files.createFile(dir.resolve("f"));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThat(sourceOf(file, false).mode()).isEqualTo(0755);
    }

    @Test
    void leavesTheModeUnknownOnWindows() throws IOException {
        Path file = Files.createFile(dir.resolve("f"));
        assertThat(sourceOf(file, true).mode()).isZero();
    }
}
