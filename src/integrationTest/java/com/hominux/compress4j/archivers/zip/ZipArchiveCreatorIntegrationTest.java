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
package com.hominux.compress4j.archivers.zip;

import static com.hominux.compress4j.test.util.io.TestFileUtils.createFile;
import static java.util.zip.ZipEntry.DEFLATED;
import static java.util.zip.ZipEntry.STORED;
import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.archivers.AbstractArchiverIntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class ZipArchiveCreatorIntegrationTest extends AbstractArchiverIntegrationTest {

    @Override
    protected ZipArchiveCreator archiveCreatorBuilder(Path archivePath) throws IOException {
        return ZipArchiveCreator.builder(archivePath).build();
    }

    @Override
    protected ZipArchiveExtractor archiveExtractorBuilder(Path archivePath) throws IOException {
        return ZipArchiveExtractor.builder(archivePath).build();
    }

    @Override
    protected String getExtension() {
        return ".zip";
    }

    @Test
    void createArchiveWithDifferentCompressionLevels() throws Exception {
        var sourceFile = createFile(tempDir, "file.txt", "Content to be compressed. ".repeat(100));

        var archiveStoredPath = tempDir.resolve("stored.zip");
        var archiveDeflatedPath = tempDir.resolve("deflated.zip");
        var extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);

        try (var creator = ZipArchiveCreator.builder(archiveStoredPath)
                .compressionMethod(STORED)
                .build()) {
            creator.addFile(sourceFile);
        }

        try (var creator = ZipArchiveCreator.builder(archiveDeflatedPath)
                .compressionMethod(DEFLATED)
                .compressionLevel(9)
                .build()) {
            creator.addFile(sourceFile);
        }

        long storedSize = Files.size(archiveStoredPath);
        long deflatedSize = Files.size(archiveDeflatedPath);
        assertThat(storedSize).isGreaterThan(deflatedSize);

        try (var extractor = archiveExtractorBuilder(archiveDeflatedPath)) {
            extractor.extract(extractDir);
        }
        assertThat(extractDir.resolve("file.txt")).exists().hasSameTextualContentAs(sourceFile);

        Files.delete(extractDir.resolve("file.txt"));
        try (var extractor = archiveExtractorBuilder(archiveStoredPath)) {
            extractor.extract(extractDir);
        }
        assertThat(extractDir.resolve("file.txt")).exists().hasSameTextualContentAs(sourceFile);

        try (var zfStored = ZipFile.builder().setPath(archiveStoredPath).get();
                var zfDeflated = ZipFile.builder().setPath(archiveDeflatedPath).get()) {
            assertThat(zfStored.getEntry("file.txt").getMethod()).isEqualTo(STORED);
            assertThat(zfDeflated.getEntry("file.txt").getMethod()).isEqualTo(DEFLATED);
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void createArchiveWithDirectoryAndSymlink() throws Exception {
        var sourceFile = createFile(tempDir, "file.txt", "File content");
        var symlinkTarget = tempDir.resolve("link_target.txt");
        var symlink = tempDir.resolve("link.txt");
        Files.writeString(symlinkTarget, "This is the target");
        Files.createSymbolicLink(symlink, symlinkTarget.getFileName());

        var archivePath = tempDir.resolve("test_structure.zip");
        var extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);

        try (var creator = archiveCreatorBuilder(archivePath)) {
            creator.addFile(sourceFile);
            creator.addFile(symlink);
        }

        try (var extractor = archiveExtractorBuilder(archivePath)) {
            extractor.extract(extractDir);
        }

        assertThat(extractDir.resolve("file.txt")).exists().hasContent("File content");

        var extractedLink = extractDir.resolve("link.txt");
        assertThat(extractedLink).isSymbolicLink();
        assertThat(Files.readSymbolicLink(extractedLink)).isEqualTo(Path.of("link_target.txt"));
    }
}
