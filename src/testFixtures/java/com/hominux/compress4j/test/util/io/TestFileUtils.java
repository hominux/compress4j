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
package com.hominux.compress4j.test.util.io;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class TestFileUtils {
    private TestFileUtils() {}

    public static void deleteRecursively(Path pathToBeDeleted) throws IOException {
        final List<Path> paths;
        try (Stream<Path> walker = Files.walk(pathToBeDeleted)) {
            paths = walker.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.delete(path);
        }
    }

    /**
     * Create a file with the given content.
     *
     * @param root root directory to create the file in.
     * @param filename name of the file to create.
     * @param content content of the file.
     * @return the path to the created file.
     * @throws IOException the file could not be created.
     */
    public static Path createFile(Path root, String filename, String content) throws IOException {
        return createFile(root, filename, content, PosixFilePermissions.fromString("rw-r--r--"));
    }

    /**
     * Create a file with the given content and POSIX permissions.
     *
     * @param root root directory to create the file in.
     * @param filename name of the file to create.
     * @param content content of the file.
     * @param permissions POSIX permissions to set on the file.
     * @return the path to the created file.
     * @throws IOException the file could not be created.
     */
    public static Path createFile(Path root, String filename, String content, Set<PosixFilePermission> permissions)
            throws IOException {
        Path path = root.resolve(filename);
        write(path, content);
        setPosixPermissions(path, permissions);
        return path;
    }

    /**
     * Sets a file's POSIX permissions.
     *
     * @param path - The path to the file
     * @param permissions - The new set of permissions
     * @throws UnsupportedOperationException if the associated file system does not support the
     *     {@code PosixFileAttributeView}
     * @throws ClassCastException if the sets contains elements that are not of type {@code PosixFilePermission}
     * @throws IOException if an I/O error occurs
     */
    public static void setPosixPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }

    /**
     * Write a string as a UTF-8 file.
     *
     * @param p path to write the string to. If the parent directory does not exist, the missing parent directories are
     *     automatically created.
     * @param content content to write to the file.
     * @throws IOException the file could not be written.
     */
    public static void write(Path p, String content) throws IOException {
        write(p.toFile(), content);
    }

    /**
     * Write a string as a UTF-8 file.
     *
     * @param f file to write the string to. If the parent directory does not exist, the missing parent directories are
     *     automatically created.
     * @param content content to write to the file.
     * @throws IOException the file could not be written.
     */
    public static void write(File f, String content) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), UTF_8)) {
            w.write(content);
        }
    }
}
