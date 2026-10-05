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

import com.hominux.compress4j.compressors.lz4.Lz4BlockDecompressor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarLz4UpstreamSampleTest {

    @TempDir
    Path tempDir;

    private Path sample(String name) throws IOException {
        var target = tempDir.resolve(name);
        try (var in = TarLz4UpstreamSampleTest.class.getResourceAsStream("/archives/" + name)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    @Test
    void shouldExtractFramedUpstreamSample() throws IOException {
        var out = Files.createDirectory(tempDir.resolve("framed"));

        try (var extractor =
                TarArchiveExtractor.builder(sample("upstream-bla.tar.lz4")).build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("test1.xml")).isNotEmptyFile();
        assertThat(out.resolve("test2.xml")).isNotEmptyFile();
    }

    @Test
    void shouldExtractBlockUpstreamSample() throws IOException {
        var tar = tempDir.resolve("block.tar");
        try (var decompressor = Lz4BlockDecompressor.builder(sample("upstream-bla.tar.block_lz4"))
                .build()) {
            decompressor.write(tar);
        }
        var out = Files.createDirectory(tempDir.resolve("block"));

        try (var extractor = TarArchiveExtractor.builder(tar).build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("test1.xml")).isNotEmptyFile();
        assertThat(out.resolve("test2.xml")).isNotEmptyFile();
    }
}
