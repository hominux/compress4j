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
package com.hominux.compress4j.archivers.tar;

import com.hominux.compress4j.archivers.AbstractArchiverIntegrationTest;
import com.hominux.compress4j.compressors.Compression;
import java.io.IOException;
import java.nio.file.Path;

class TarLz4ArchiveIntegrationTest extends AbstractArchiverIntegrationTest {

    @Override
    protected TarArchiveCreator archiveCreatorBuilder(Path archivePath) throws IOException {
        return TarArchiveCreator.builder(archivePath)
                .compression(Compression.lz4Framed())
                .build();
    }

    @Override
    protected TarArchiveExtractor archiveExtractorBuilder(Path archivePath) throws IOException {
        return TarArchiveExtractor.builder(archivePath).build();
    }

    @Override
    protected String getExtension() {
        return ".tar.lz4";
    }
}
