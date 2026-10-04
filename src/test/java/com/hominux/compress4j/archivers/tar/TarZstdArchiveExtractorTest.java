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
import static org.mockito.Mockito.mock;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.exceptions.ArchiveLimitExceededException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.OptionalLong;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarZstdArchiveExtractorTest {

    @TempDir
    Path tempDir;

    @SuppressWarnings("OctalInteger")
    private static byte[] archiveOf(String name, byte[] content) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var creator = TarZstdArchiveCreator.builder(bytes).build()) {
            creator.writeFile(
                    name,
                    new ByteArrayInputStream(content),
                    OptionalLong.of(content.length),
                    0644,
                    FileTime.from(Instant.now()));
        }
        return bytes.toByteArray();
    }

    @Test
    void shouldReturnEmptyWhenNoMoreEntries() throws IOException {
        try (var extractor = new TarZstdArchiveExtractor(mock(TarArchiveInputStream.class))) {
            assertThat(extractor.nextEntry()).isEmpty();
        }
    }

    @Test
    void shouldRoundTripThroughCreator() throws IOException {
        var archive = archiveOf("dir/file.txt", "payload".getBytes(StandardCharsets.UTF_8));

        try (var extractor = TarZstdArchiveExtractor.builder(new ByteArrayInputStream(archive))
                .build()) {
            extractor.extract(tempDir);
        }

        assertThat(tempDir.resolve("dir/file.txt")).hasContent("payload");
    }

    @Test
    void shouldRejectPathTraversal() throws IOException {
        var archive = archiveOf("../escape.txt", "x".getBytes(StandardCharsets.UTF_8));
        var target = Files.createDirectory(tempDir.resolve("target"));

        try (var extractor = TarZstdArchiveExtractor.builder(new ByteArrayInputStream(archive))
                .build()) {
            assertThatThrownBy(() -> extractor.extract(target)).isInstanceOf(IOException.class);
        }
        assertThat(tempDir.resolve("escape.txt")).doesNotExist();
    }

    @Test
    void shouldEnforceMaxEntrySizeOnHighlyCompressiblePayload() throws IOException {
        var payload = new byte[1_000_000];
        Arrays.fill(payload, (byte) 'a');
        var archive = archiveOf("bomb.txt", payload);
        assertThat(archive).hasSizeLessThan(payload.length / 10);

        try (var extractor = TarZstdArchiveExtractor.builder(new ByteArrayInputStream(archive))
                .maxEntrySize(1024)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(ArchiveLimitExceededException.class);
        }
    }

    @Test
    void shouldEnforceMaxEntries() throws IOException {
        var archive = archiveOf("one.txt", "1".getBytes(StandardCharsets.UTF_8));

        try (var extractor = TarZstdArchiveExtractor.builder(new ByteArrayInputStream(archive))
                .maxEntries(0)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(ArchiveLimitExceededException.class);
        }
    }

    @Test
    void shouldRejectTruncatedArchive() throws IOException {
        var archive =
                archiveOf("file.txt", "a longer payload to truncate".repeat(100).getBytes(StandardCharsets.UTF_8));
        var truncated = Arrays.copyOf(archive, archive.length / 2);

        try (var extractor = TarZstdArchiveExtractor.builder(new ByteArrayInputStream(truncated))
                .build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldRejectNonZstdInput() throws IOException {
        var gzip = new ByteArrayOutputStream();
        try (var out = new GZIPOutputStream(gzip)) {
            out.write("not zstd".getBytes(StandardCharsets.UTF_8));
        }

        try (var extractor = TarZstdArchiveExtractor.builder(new ByteArrayInputStream(gzip.toByteArray()))
                .build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldEnforceMaxTotalSize() throws IOException {
        var archive = archiveOf("big.txt", "x".repeat(4096).getBytes(StandardCharsets.UTF_8));

        try (var extractor = TarZstdArchiveExtractor.builder(new ByteArrayInputStream(archive))
                .maxTotalSize(1024)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(tempDir)).isInstanceOf(ArchiveLimitExceededException.class);
        }
    }

    @Test
    void shouldRejectEscapingSymlinkWhenDisallowed() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(new ZstdCompressorOutputStream(bytes))) {
            var link = new TarArchiveEntry("link", TarConstants.LF_SYMLINK);
            link.setLinkName("../outside");
            tar.putArchiveEntry(link);
            tar.closeArchiveEntry();
        }
        var target = Files.createDirectory(tempDir.resolve("target"));

        try (var extractor = TarZstdArchiveExtractor.builder(new ByteArrayInputStream(bytes.toByteArray()))
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(target)).isInstanceOf(IOException.class);
        }
        assertThat(target.resolve("link")).doesNotExist();
    }
}
