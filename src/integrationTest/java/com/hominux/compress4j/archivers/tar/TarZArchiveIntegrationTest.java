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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.UpstreamSamples;
import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.exceptions.LimitExceededException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarZArchiveIntegrationTest {

    @TempDir
    Path tempDir;

    private Path sample() throws IOException {
        return UpstreamSamples.copy("/archives/upstream-bla.tar.Z", tempDir);
    }

    private void extractAll(byte[] bytes) throws IOException {
        try (var extractor =
                TarZArchiveExtractor.builder(new ByteArrayInputStream(bytes)).build()) {
            extractor.extract(tempDir.resolve("bytes-out"));
        }
    }

    @Test
    void shouldExtractUpstreamSample() throws IOException {
        var out = Files.createDirectory(tempDir.resolve("out"));

        try (var extractor = TarZArchiveExtractor.builder(sample()).build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("test1.xml")).isNotEmptyFile();
        assertThat(out.resolve("test2.xml")).isNotEmptyFile();
    }

    @Test
    void shouldExtractUpstreamSampleFromStream() throws IOException {
        var out = Files.createDirectory(tempDir.resolve("stream-out"));

        try (var in = Files.newInputStream(sample());
                var extractor = TarZArchiveExtractor.builder(in).build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("test1.xml")).isNotEmptyFile();
    }

    @Test
    void shouldExposeInputStreamBuilderParent() {
        var builder = TarZArchiveExtractor.builder(new ByteArrayInputStream(new byte[0]));

        assertThat(builder.zInputStream().parentBuilder()).isSameAs(builder);
    }

    @Test
    void shouldEnforceMaxEntries() throws IOException {
        try (var extractor =
                TarZArchiveExtractor.builder(sample()).maxEntries(1).build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir.resolve("limit1")))
                    .isInstanceOf(LimitExceededException.class);
        }
    }

    @Test
    void shouldEnforceMaxEntrySize() throws IOException {
        try (var extractor =
                TarZArchiveExtractor.builder(sample()).maxEntrySize(1).build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir.resolve("limit2")))
                    .isInstanceOf(LimitExceededException.class);
        }
    }

    @Test
    void shouldEnforceMaxTotalSize() throws IOException {
        try (var extractor =
                TarZArchiveExtractor.builder(sample()).maxTotalSize(1).build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir.resolve("limit3")))
                    .isInstanceOf(LimitExceededException.class);
        }
    }

    @Test
    void shouldRejectNonFormatInput() throws IOException {
        var gzip = new ByteArrayOutputStream();
        try (var out = new GZIPOutputStream(gzip)) {
            out.write("not compress".getBytes(StandardCharsets.UTF_8));
        }
        var bytes = gzip.toByteArray();

        assertThatThrownBy(() -> extractAll(bytes)).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectTruncatedArchive() throws IOException {
        var bytes = Files.readAllBytes(sample());
        var truncated = Arrays.copyOf(bytes, bytes.length / 2);

        assertThatThrownBy(() -> extractAll(truncated)).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectPathTraversalAndWriteNothingOutside() throws IOException {
        var archive = UpstreamSamples.copy("/archives/traversal.tar.Z", tempDir);
        var target = Files.createDirectory(tempDir.resolve("target"));

        try (var extractor = TarZArchiveExtractor.builder(archive).build()) {
            assertThatThrownBy(() -> extractor.extract(target)).isInstanceOf(IOException.class);
        }

        assertThat(tempDir.resolve("escape.txt")).doesNotExist();
    }

    @Test
    void shouldRejectEscapingSymlinkWhenDisallowed() throws IOException {
        var archive = UpstreamSamples.copy("/archives/escaping-symlink.tar.Z", tempDir);
        var target = Files.createDirectory(tempDir.resolve("link-target"));

        try (var extractor = TarZArchiveExtractor.builder(archive)
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(target)).isInstanceOf(IOException.class);
        }

        assertThat(target.resolve("link")).doesNotExist();
    }
}
