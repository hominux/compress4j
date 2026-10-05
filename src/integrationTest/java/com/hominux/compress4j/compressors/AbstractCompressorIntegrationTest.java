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
package com.hominux.compress4j.compressors;

import static com.hominux.compress4j.assertion.Compress4JAssertions.assertThat;
import static com.hominux.compress4j.test.util.io.TestFileUtils.createFile;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public abstract class AbstractCompressorIntegrationTest {

    @TempDir
    protected Path tempDir;

    protected abstract CodecCompressor<?> compressorBuilder(Path compressPath) throws IOException;

    protected abstract CodecDecompressor<?> decompressorBuilder(Path compressPath) throws IOException;

    protected abstract String compressionExtension();

    protected Path osCompressedPath() {
        try {
            String archivePathStr = "/compression/compress.txt" + compressionExtension();
            URL resource = getClass().getResource(archivePathStr);
            Assertions.assertThat(resource)
                    .as("Compression file not found: " + archivePathStr + " in resources")
                    .isNotNull();
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            fail("Failed to load test resource", e);
            return null;
        }
    }

    @Test
    void compressDecompressSameFile() throws Exception {
        var sourcePath = createFile(tempDir, "sourceFile", "compressMe");
        var compressPath = tempDir.resolve("compressTest.txt" + compressionExtension());
        var decompressPath = tempDir.resolve("decompressedTest.txt");

        try (CodecCompressor<?> compressor = compressorBuilder(compressPath)) {
            compressor.write(sourcePath);
        }

        assertThat(compressPath).exists();

        try (CodecDecompressor<?> decompressor = decompressorBuilder(compressPath)) {
            decompressor.write(decompressPath);
        }

        assertThat(decompressPath).exists();
        Assertions.assertThat(FileUtils.readFileToString(sourcePath.toFile(), "UTF-8"))
                .isEqualTo(FileUtils.readFileToString(decompressPath.toFile(), "UTF-8"));
    }

    @Test
    void shouldDecompressOsCompressedFile() throws Exception {
        var compressedPath = osCompressedPath();
        var preCompressedPath = createFile(tempDir, "normalFile.txt", "this is the decompressed textfile\n");
        var decompressPath = tempDir.resolve("decompressedTest.txt");

        try (CodecDecompressor<?> decompressor = decompressorBuilder(compressedPath)) {
            decompressor.write(decompressPath);
        }

        assertThat(decompressPath).exists();
        Assertions.assertThat(FileUtils.readFileToString(preCompressedPath.toFile(), "UTF-8"))
                .isEqualTo(FileUtils.readFileToString(decompressPath.toFile(), "UTF-8"));
    }
}
