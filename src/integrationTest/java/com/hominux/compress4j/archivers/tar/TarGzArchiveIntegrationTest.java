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
package com.hominux.compress4j.archivers.tar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.archivers.AbstractArchiverIntegrationTest;
import com.hominux.compress4j.archivers.EntrySource;
import com.hominux.compress4j.archivers.catalog.ArchiveFormat;
import com.hominux.compress4j.archivers.catalog.ArchiveFormat.Reader;
import com.hominux.compress4j.archivers.catalog.ArchiveFormat.Writer;
import com.hominux.compress4j.compressors.Compression;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for TAR.GZ archive functionality.
 *
 * @since 2.2
 */
class TarGzArchiveIntegrationTest extends AbstractArchiverIntegrationTest {

    @Override
    protected Writer writerAt(Path archivePath) throws IOException {
        return ArchiveFormat.writer(TarArchiveCreator.builder(Files.newOutputStream(archivePath))
                .compression(Compression.gzip())
                .build());
    }

    @Override
    protected Reader readerAt(Path archivePath) throws IOException {
        return ArchiveFormat.reader(
                TarArchiveExtractor.builder(Files.newInputStream(archivePath)).build());
    }

    @Override
    protected String getExtension() {
        return ".tar.gz";
    }

    @Test
    void testCompressionEfficiency() throws Exception {
        var sourceFile = tempDir.resolve("large.txt");
        var content = "This is a repetitive line for testing compression efficiency.\n".repeat(1000);
        Files.write(sourceFile, content.getBytes());

        var archivePath = tempDir.resolve("compressed.tar.gz");
        var extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);

        try (var creator = TarArchiveCreator.builder(Files.newOutputStream(archivePath))
                .compression(Compression.gzip())
                .build()) {
            creator.add(EntrySource.file("large.txt", sourceFile));
        }

        assertThat(Files.size(archivePath)).isLessThan(Files.size(sourceFile));

        try (var extractor =
                TarArchiveExtractor.builder(Files.newInputStream(archivePath)).build()) {
            extractor.extract(extractDir);
        }

        assertThat(extractDir.resolve("large.txt")).exists().hasContent(content);
    }
}
