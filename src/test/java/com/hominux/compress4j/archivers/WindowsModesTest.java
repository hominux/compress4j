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

import com.hominux.compress4j.archivers.zip.ZipArchiveExtractor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributes;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
class WindowsModesTest {

    @TempDir
    Path dir;

    @Test
    void executableZipEntryStaysWritableOnWindows() throws IOException {
        Path zip = dir.resolve("x.zip");
        try (var out = new ZipArchiveOutputStream(zip)) {
            var entry = new ZipArchiveEntry("run.sh");
            entry.setUnixMode(0100755);
            out.putArchiveEntry(entry);
            out.write("echo".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }
        Path outDir = dir.resolve("out");
        try (var extractor = ZipArchiveExtractor.builder(zip).build()) {
            extractor.extract(outDir);
        }
        DosFileAttributes attrs = Files.readAttributes(outDir.resolve("run.sh"), DosFileAttributes.class);
        assertThat(attrs.isReadOnly()).isFalse();
        assertThat(attrs.isHidden()).isFalse();
    }
}
