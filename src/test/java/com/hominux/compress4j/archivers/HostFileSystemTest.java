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
import static org.assertj.core.api.Assertions.catchThrowable;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class HostFileSystemTest {

    private final FileSystem unix = Jimfs.newFileSystem(
            Configuration.unix().toBuilder().setAttributeViews("basic", "posix").build());
    private final FileSystem windows = Jimfs.newFileSystem(Configuration.windows().toBuilder()
            .setAttributeViews("basic", "dos")
            .build());
    private final FileSystem plain = Jimfs.newFileSystem(
            Configuration.unix().toBuilder().setAttributeViews("basic").build());

    @AfterEach
    void close() throws IOException {
        unix.close();
        windows.close();
        plain.close();
    }

    @Test
    void choosesTheAdapterFromThePathsFileSystem() {
        assertThat(HostFileSystem.of(unix.getPath("/a"))).isEqualTo(HostFileSystem.POSIX);
        assertThat(HostFileSystem.of(windows.getPath("C:\\a"))).isEqualTo(HostFileSystem.DOS);
        assertThat(HostFileSystem.of(plain.getPath("/a"))).isEqualTo(HostFileSystem.NONE);
    }

    @Test
    void posixReadsAndAppliesPermissionBits() throws IOException {
        Path file = Files.createFile(unix.getPath("/f"));
        HostFileSystem.POSIX.applyMode(file, 0100750);
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rwxr-x---"));
        assertThat(HostFileSystem.POSIX.modeOf(file)).isEqualTo(0750);
    }

    @Test
    void posixIgnoresModeZero() throws IOException {
        Path file = Files.createFile(unix.getPath("/f"));
        var before = Files.getPosixFilePermissions(file);
        HostFileSystem.POSIX.applyMode(file, 0);
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(before);
    }

    @Test
    void dosKeepsOwnerWritableFilesWritable() throws IOException {
        Path file = Files.createFile(windows.getPath("C:\\f"));
        HostFileSystem.DOS.applyMode(file, 0100755);
        DosFileAttributes attrs = Files.readAttributes(file, DosFileAttributes.class);
        assertThat(attrs.isReadOnly()).isFalse();
        assertThat(attrs.isHidden()).isFalse();
    }

    @Test
    void dosMarksFilesTheOwnerCannotWriteReadOnly() throws IOException {
        Path file = Files.createFile(windows.getPath("C:\\f"));
        HostFileSystem.DOS.applyMode(file, 0100444);
        assertThat(Files.readAttributes(file, DosFileAttributes.class).isReadOnly())
                .isTrue();
    }

    @Test
    void dosNeverHidesAndIgnoresModeZero() throws IOException {
        Path file = Files.createFile(windows.getPath("C:\\f"));
        HostFileSystem.DOS.applyMode(file, 0);
        HostFileSystem.DOS.applyMode(file, 0100002);
        DosFileAttributes attrs = Files.readAttributes(file, DosFileAttributes.class);
        assertThat(attrs.isHidden()).isFalse();
        assertThat(HostFileSystem.DOS.modeOf(file)).isZero();
    }

    @Test
    void noneAdapterIgnoresModes() throws IOException {
        Path file = Files.createFile(plain.getPath("/f"));
        HostFileSystem.NONE.applyMode(file, 0100444);
        assertThat(HostFileSystem.NONE.modeOf(file)).isZero();
        assertThat(Files.isWritable(file)).isTrue();
    }

    @Test
    void posixAppliesOnlyPermissionBitsToDirectories() throws IOException {
        Path dir = Files.createDirectory(unix.getPath("/d"));
        HostFileSystem.POSIX.applyMode(dir, 040750);
        assertThat(Files.getPosixFilePermissions(dir)).isEqualTo(PosixFilePermissions.fromString("rwxr-x---"));
    }

    @Test
    void posixDropsSpecialBits() throws IOException {
        Path file = Files.createFile(unix.getPath("/f"));
        HostFileSystem.POSIX.applyMode(file, 04755);
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThat(HostFileSystem.POSIX.modeOf(file)).isEqualTo(0755);
    }

    @Test
    void dosNeverMarksDirectoriesReadOnly() throws IOException {
        Path dir = Files.createDirectory(windows.getPath("C:\\d"));
        HostFileSystem.DOS.applyMode(dir, 040555);
        assertThat(Files.readAttributes(dir, DosFileAttributes.class).isReadOnly())
                .isFalse();
    }

    @Test
    void dosKeepsWritableDirectoriesWritable() throws IOException {
        Path dir = Files.createDirectory(windows.getPath("C:\\d"));
        HostFileSystem.DOS.applyMode(dir, 040755);
        assertThat(Files.readAttributes(dir, DosFileAttributes.class).isReadOnly())
                .isFalse();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void modeOfASymlinkIsTheLinksOwnModeNotTheTargets(@TempDir Path tmp) throws IOException {
        Path target = Files.writeString(tmp.resolve("target.txt"), "content");
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r--r--"));
        Path link = Files.createSymbolicLink(tmp.resolve("link.txt"), target.getFileName());
        assertThat(HostFileSystem.POSIX.modeOf(link)).isNotEqualTo(0644);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void posixApplyModeDoesNotChangeTheTargetOfASymlink(@TempDir Path tmp) throws IOException {
        Path target = Files.writeString(tmp.resolve("target.txt"), "content");
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r--r--"));
        Path link = Files.createSymbolicLink(tmp.resolve("link.txt"), target.getFileName());
        Throwable outcome = catchThrowable(() -> HostFileSystem.POSIX.applyMode(link, 0777));
        assertThat(outcome)
                .satisfiesAnyOf(t -> assertThat(t).isNull(), t -> assertThat(t).isInstanceOf(IOException.class));
        assertThat(Files.getPosixFilePermissions(target)).isEqualTo(PosixFilePermissions.fromString("rw-r--r--"));
    }
}
