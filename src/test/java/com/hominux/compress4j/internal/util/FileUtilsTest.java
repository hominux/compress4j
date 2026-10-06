/*
 * Copyright 2025-2026 The Compress4J Project
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hominux.compress4j.exceptions.UnsafeEntryException;
import com.hominux.compress4j.test.util.io.TestFileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("ResultOfMethodCallIgnored")
class FileUtilsTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void tearDown() throws IOException {
        TestFileUtils.deleteRecursively(tempDir);
    }

    @Test
    void testReadStringNormalized_normalizesLineEndings() throws IOException {
        Path file = tempDir.resolve("test.txt");
        String content = "line1\r\nline2\rline3\nline4";
        Files.writeString(file, content);
        String result = FileUtils.readStringNormalized(file);
        assertEquals("line1\nline2\nline3\nline4", result);
    }

    @Test
    void testIsInsideSafeDir_Path_true() throws IOException {
        Path safe = tempDir;
        Path inside = tempDir.resolve("subdir/file.txt");
        Files.createDirectories(inside.getParent());
        Files.createFile(inside);
        assertTrue(FileUtils.isInsideSafeDir(inside, safe));
    }

    @Test
    void testIsInsideSafeDir_Path_false() throws IOException {
        Path safe = tempDir;
        Path outside = Files.createTempFile("outside", ".txt");
        try {
            assertFalse(FileUtils.isInsideSafeDir(outside, safe));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void testIsInsideSafeDir_File_true() throws IOException {
        File safe = tempDir.toFile();
        File inside = tempDir.resolve("subdir/file.txt").toFile();
        inside.getParentFile().mkdirs();
        inside.createNewFile();
        assertTrue(FileUtils.isInsideSafeDir(inside, safe));
    }

    @Test
    void testIsInsideSafeDir_File_false() throws IOException {
        File safe = tempDir.toFile();
        File outside = File.createTempFile("outside", ".txt");
        try {
            assertFalse(FileUtils.isInsideSafeDir(outside, safe));
        } finally {
            outside.delete();
        }
    }

    @Test
    void testCheckValidPath_Path_valid() throws IOException {
        Path safe = tempDir;
        Path inside = tempDir.resolve("file.txt");
        Files.createFile(inside);
        assertDoesNotThrow(() -> FileUtils.checkValidPath(inside, safe));
    }

    @Test
    void testCheckValidPath_Path_invalid() throws IOException {
        Path safe = tempDir;
        Path outside = Files.createTempFile("outside", ".txt");
        try {
            IOException ex = assertThrows(IOException.class, () -> FileUtils.checkValidPath(outside, safe));
            assertTrue(ex.getMessage().contains("Path traversal vulnerability"));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void testCheckValidPath_File_valid() throws IOException {
        File safe = tempDir.toFile();
        File inside = tempDir.resolve("file.txt").toFile();
        inside.createNewFile();
        assertDoesNotThrow(() -> FileUtils.checkValidPath(inside, safe));
    }

    @Test
    void testCheckValidPath_File_invalid() throws IOException {
        File safe = tempDir.toFile();
        File outside = File.createTempFile("outside", ".txt");
        try {
            IOException ex = assertThrows(IOException.class, () -> FileUtils.checkValidPath(outside, safe));
            assertTrue(ex.getMessage().contains("Path traversal vulnerability"));
        } finally {
            outside.delete();
        }
    }

    @Test
    void checkValidPathRejectsTraversalWithUnsafeEntryException(@TempDir Path safe) {
        // Given
        Path outside = safe.resolve("../escape.txt");

        // Then
        assertThatThrownBy(() -> FileUtils.checkValidPath(outside, safe))
                .isInstanceOf(UnsafeEntryException.class)
                .hasMessageContaining("outside of target directory");
    }
}
