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

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.compressors.Compression;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarLzmaUpstreamSampleTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExtractUpstreamSample() throws IOException {
        var sample = Files.createTempFile(tempDir, "sample", ".tar.lzma");
        try (var in = TarLzmaUpstreamSampleTest.class.getResourceAsStream("/archives/upstream-bla.tar.lzma")) {
            Files.copy(in, sample, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        var out = Files.createDirectory(tempDir.resolve("out"));

        try (var extractor = TarArchiveExtractor.builder(sample)
                .compression(Compression.lzma())
                .build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("test1.xml")).isNotEmptyFile();
        assertThat(out.resolve("test2.xml")).isNotEmptyFile();
    }
}
