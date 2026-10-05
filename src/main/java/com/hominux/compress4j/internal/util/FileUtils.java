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

import com.hominux.compress4j.exceptions.UnsafeEntryException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility class for file operations. */
public class FileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

    /** Private constructor to prevent instantiation. */
    private FileUtils() {
        /* no-op */
    }

    /**
     * Reads the full content of a file into a single string, normalizing all line endings to a single newline character
     * ('\n').
     *
     * <p>This method ensures that file content comparisons in tests will work consistently across different operating
     * systems (like Windows and Linux) that use different line ending characters.
     *
     * @param path the path to the file to read.
     * @return a string containing the file's content with normalized line endings.
     * @throws IOException if an I/O error occurs reading from the file.
     */
    public static String readStringNormalized(Path path) throws IOException {
        return String.join("\n", Files.readAllLines(path, StandardCharsets.UTF_8));
    }

    /**
     * Validate whether the unsafe path resides inside the known safe path. The function assumes the directory provided
     * as safePath is an internally validated safe directory. No user input should ever reside there.
     *
     * @param unsafePath The unsafe path that needs to be checked canonically
     * @param safePath The safe directory that the unsafe path needs to reside in
     * @return {@code true} if the unsafePath resides in the safePath
     */
    public static boolean isInsideSafeDir(Path unsafePath, Path safePath) {
        return isInsideSafeDir(unsafePath.toFile(), safePath.toFile());
    }

    /**
     * Validate whether the unsafe file resides inside the known safe directory. The function assumes the directory
     * provided as safeDirectory is an internally validated safe directory. No user input should ever reside there.
     *
     * @param unsafeFile The unsafe file that needs to be checked canonically
     * @param safeDirectory The safe directory that the unsafe file needs to reside in
     * @return {@code true} if the unsafeFile resides in the safeDirectory
     */
    public static boolean isInsideSafeDir(File unsafeFile, File safeDirectory) {
        try {
            String unsafeCanonical = unsafeFile.getCanonicalPath().replace("\\", "/");
            String safeCanonical = safeDirectory.getCanonicalPath().replace("\\", "/");

            if (!unsafeCanonical.startsWith(safeCanonical + "/")) {
                LOGGER.error(
                        "Potential security issue! Accessing a resource '{}' outside of the safe directory '{}'!",
                        unsafeCanonical,
                        safeCanonical);
                return false;
            }

            return true;
        } catch (IOException e) {
            LOGGER.error("IO Exception occurred during safe dir check.", e);
            return false;
        }
    }

    /**
     * Check whether the unsafe path resides inside the known safe path. The function assumes the directory provided as
     * safePath is an internally validated safe directory. No user input should ever reside there.
     *
     * @param unsafePath The unsafe path that needs to be checked canonically
     * @param safePath The safe directory that the unsafe path needs to reside in
     * @throws UnsafeEntryException when the unsafePath is violating access restriction.
     */
    public static void checkValidPath(Path unsafePath, Path safePath) throws UnsafeEntryException {
        if (!isInsideSafeDir(unsafePath, safePath)) {
            throw new UnsafeEntryException("Path traversal vulnerability detected! Entry: " + unsafePath
                    + " is outside of target directory: " + safePath);
        }
    }

    /**
     * Check whether the unsafe file resides inside the known safe directory. The function assumes the directory
     * provided as safeDirectory is an internally validated safe directory. No user input should ever reside there.
     *
     * @param unsafeFile The unsafe file that needs to be checked canonically
     * @param safeDirectory The safe directory that the unsafe file needs to reside in
     * @throws UnsafeEntryException when the unsafeFile is violating access restriction.
     */
    public static void checkValidPath(File unsafeFile, File safeDirectory) throws UnsafeEntryException {
        if (!isInsideSafeDir(unsafeFile, safeDirectory)) {
            throw new UnsafeEntryException("Path traversal vulnerability detected! Entry: " + unsafeFile
                    + " is outside of target directory: " + safeDirectory);
        }
    }
}
