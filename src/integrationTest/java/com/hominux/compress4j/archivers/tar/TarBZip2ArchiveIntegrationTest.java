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
 * End-to-end tests for TAR.BZ2 archive functionality.
 *
 * @since 2.2
 */
class TarBZip2ArchiveIntegrationTest extends AbstractArchiverIntegrationTest {

    @Override
    protected Writer writerAt(Path archivePath) throws IOException {
        return ArchiveFormat.writer(TarArchiveCreator.builder(Files.newOutputStream(archivePath))
                .compression(Compression.bzip2())
                .build());
    }

    @Override
    protected Reader readerAt(Path archivePath) throws IOException {
        return ArchiveFormat.reader(
                TarArchiveExtractor.builder(Files.newInputStream(archivePath)).build());
    }

    @Override
    protected String getExtension() {
        return ".tar.bz2";
    }

    @Test
    void testBZip2CompressionFeatures() throws Exception {
        var textFile = tempDir.resolve("text.txt");
        var binaryFile = tempDir.resolve("binary.dat");

        Files.write(textFile, "Text content with patterns patterns patterns".getBytes());

        byte[] binaryData = new byte[2048];
        for (int i = 0; i < binaryData.length; i++) {
            binaryData[i] = (byte) (i % 256);
        }
        Files.write(binaryFile, binaryData);

        var archivePath = tempDir.resolve("bzip2-test.tar.bz2");
        var extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);

        try (var creator = TarArchiveCreator.builder(Files.newOutputStream(archivePath))
                .compression(Compression.bzip2())
                .build()) {
            creator.add(EntrySource.file("text.txt", textFile));
            creator.add(EntrySource.file("binary.dat", binaryFile));
        }

        try (var extractor =
                TarArchiveExtractor.builder(Files.newInputStream(archivePath)).build()) {
            extractor.extract(extractDir);
        }

        assertThat(extractDir.resolve("text.txt")).exists().hasContent("Text content with patterns patterns patterns");
        assertThat(extractDir.resolve("binary.dat")).exists().hasBinaryContent(binaryData);
    }
}
