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
package com.hominux.compress4j.archivers.dump;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.UpstreamSamples;
import com.hominux.compress4j.exceptions.LimitExceededException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DumpArchiveIntegrationTest {

    @TempDir
    Path tempDir;

    private Path sample() throws IOException {
        return UpstreamSamples.copy("/archives/upstream-bla.dump", tempDir);
    }

    private void extractAll(byte[] bytes) throws IOException {
        try (var extractor =
                DumpArchiveExtractor.builder(new ByteArrayInputStream(bytes)).build()) {
            extractor.extract(tempDir.resolve("bytes-out"));
        }
    }

    @Test
    void shouldExtractUpstreamSampleFromPath() throws IOException {
        var out = Files.createDirectory(tempDir.resolve("out"));

        try (var extractor = DumpArchiveExtractor.builder(sample()).build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("test1.xml")).isNotEmptyFile();
        assertThat(out.resolve("test2.xml")).isNotEmptyFile();
        assertThat(out.resolve("lost+found")).isDirectory();
    }

    @Test
    void shouldExtractUpstreamSampleFromFileAndStream() throws IOException {
        var fromFile = Files.createDirectory(tempDir.resolve("file-out"));
        var fromStream = Files.createDirectory(tempDir.resolve("stream-out"));

        try (var extractor = DumpArchiveExtractor.builder(sample()).build()) {
            extractor.extract(fromFile);
        }
        try (var in = Files.newInputStream(sample());
                var extractor = DumpArchiveExtractor.builder(in).encoding(UTF_8).build()) {
            extractor.extract(fromStream);
        }

        assertThat(fromFile.resolve("test1.xml")).isNotEmptyFile();
        assertThat(fromStream.resolve("test2.xml")).isNotEmptyFile();
    }

    @Test
    void shouldEnforceMaxEntries() throws IOException {
        try (var extractor =
                DumpArchiveExtractor.builder(sample()).maxEntries(1).build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir.resolve("limit1")))
                    .isInstanceOf(LimitExceededException.class);
        }
    }

    @Test
    void shouldEnforceMaxEntrySize() throws IOException {
        try (var extractor =
                DumpArchiveExtractor.builder(sample()).maxEntrySize(1).build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir.resolve("limit2")))
                    .isInstanceOf(LimitExceededException.class);
        }
    }

    @Test
    void shouldEnforceMaxTotalSize() throws IOException {
        try (var extractor =
                DumpArchiveExtractor.builder(sample()).maxTotalSize(1).build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir.resolve("limit3")))
                    .isInstanceOf(LimitExceededException.class);
        }
    }

    @Test
    void shouldRejectNonDumpInput() {
        var bytes = new byte[2048];

        assertThatThrownBy(() -> extractAll(bytes)).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectTruncatedArchive() throws IOException {
        var bytes = Files.readAllBytes(sample());
        var truncated = Arrays.copyOf(bytes, 4096);

        assertThatThrownBy(() -> extractAll(truncated)).isInstanceOf(IOException.class);
    }
}
