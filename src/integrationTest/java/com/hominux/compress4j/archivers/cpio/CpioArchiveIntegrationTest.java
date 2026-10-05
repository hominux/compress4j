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
package com.hominux.compress4j.archivers.cpio;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.archivers.AbstractArchiverIntegrationTest;
import com.hominux.compress4j.archivers.EntrySource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for CPIO archive functionality.
 *
 * @since 2.2
 */
class CpioArchiveIntegrationTest extends AbstractArchiverIntegrationTest {

    @Override
    protected CpioArchiveCreator archiveCreatorBuilder(Path archivePath) throws IOException {
        return CpioArchiveCreator.builder(Files.newOutputStream(archivePath)).build();
    }

    @Override
    protected CpioArchiveExtractor archiveExtractorBuilder(Path archivePath) throws IOException {
        return CpioArchiveExtractor.builder(Files.newInputStream(archivePath)).build();
    }

    @Override
    protected String getExtension() {
        return ".cpio";
    }

    @Test
    void testCpioSpecificFeatures() throws Exception {
        // given
        var sourceFile = tempDir.resolve("test.txt");
        Files.write(sourceFile, "CPIO test content".getBytes());

        var archivePath = tempDir.resolve("cpio-formats.cpio");
        var extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);

        // when
        try (var creator = CpioArchiveCreator.builder(archivePath).build()) {
            creator.add(EntrySource.file("test.txt", sourceFile));
        }

        // then
        try (var extractor = CpioArchiveExtractor.builder(archivePath).build()) {
            extractor.extract(extractDir);
        }

        assertThat(extractDir.resolve("test.txt")).isRegularFile().hasContent("CPIO test content");
    }
}
