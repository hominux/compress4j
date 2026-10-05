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
package com.hominux.compress4j.archivers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.sevenz.SevenZArchiveCreator;
import com.hominux.compress4j.archivers.sevenz.SevenZArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveBuilderReuseTest {

    private static final int ZEROS = 4 << 20;

    @TempDir
    Path dir;

    private static long drain(ArchiveExtractor extractor) throws IOException {
        var delivered = new AtomicLong();
        try {
            extractor.stream().forEach(item -> read(item, delivered));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return delivered.get();
    }

    private static void read(ArchiveItem item, AtomicLong delivered) {
        byte[] buffer = new byte[8192];
        try (var content = item.content()) {
            for (int n = content.read(buffer); n >= 0; n = content.read(buffer)) {
                delivered.addAndGet(n);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertLimit(ThrowableAssertionTarget action, Limit expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                .isEqualTo(expected));
    }

    @FunctionalInterface
    private interface ThrowableAssertionTarget {
        void run() throws IOException;
    }

    private Path tarGz() throws IOException {
        Path archive = dir.resolve("zeros.tar.gz");
        try (var creator = TarArchiveCreator.builder(archive)
                .compression(Compression.gzip())
                .build()) {
            creator.add(EntrySource.file("zeros", new byte[ZEROS]));
        }
        return archive;
    }

    private Path sevenZ(String name) throws IOException {
        Path archive = dir.resolve(name);
        try (var creator = SevenZArchiveCreator.builder(archive).build()) {
            creator.add(EntrySource.file("zeros", new byte[ZEROS]));
        }
        return archive;
    }

    @Test
    void secondTarBuildFromOneBuilderStartsFresh() throws IOException {
        var builder = TarArchiveExtractor.builder(tarGz()).maxRatio(1_000_000).maxTotalSize(ZEROS);
        try (var first = builder.build()) {
            assertThat(drain(first)).isEqualTo(ZEROS);
        }
        try (var second = builder.build()) {
            assertThat(drain(second)).isEqualTo(ZEROS);
        }
    }

    @Test
    void secondSevenZBuildFromOneBuilderStartsFresh() throws IOException {
        var builder = SevenZArchiveExtractor.builder(sevenZ("zeros.7z"))
                .maxRatio(1_000_000)
                .maxTotalSize(ZEROS);
        try (var first = builder.build()) {
            assertThat(drain(first)).isEqualTo(ZEROS);
        }
        try (var second = builder.build()) {
            assertThat(drain(second)).isEqualTo(ZEROS);
        }
    }

    @Test
    void secondTarBuildStillEnforcesTheRatioIndependently() throws IOException {
        var builder = TarArchiveExtractor.builder(tarGz()).maxRatio(2);
        try (var first = builder.build()) {
            assertLimit(() -> drain(first), Limit.RATIO);
        }
        try (var second = builder.build()) {
            assertLimit(() -> drain(second), Limit.RATIO);
        }
    }

    @Test
    void liveTarExtractorsFromOneBuilderKeepSeparateAccounting() throws IOException {
        var builder = TarArchiveExtractor.builder(tarGz()).maxRatio(1_000_000).maxTotalSize(ZEROS);
        try (var first = builder.build();
                var second = builder.build()) {
            assertThat(drain(first)).isEqualTo(ZEROS);
            assertThat(drain(second)).isEqualTo(ZEROS);
        }
    }

    @Test
    void liveSevenZExtractorsFromOneBuilderKeepSeparateAccounting() throws IOException {
        var builder = SevenZArchiveExtractor.builder(sevenZ("live.7z"))
                .maxRatio(1_000_000)
                .maxTotalSize(ZEROS);
        try (var first = builder.build();
                var second = builder.build()) {
            assertThat(drain(second)).isEqualTo(ZEROS);
            assertThat(drain(first)).isEqualTo(ZEROS);
        }
    }

    @Test
    void failedBuildDoesNotLeaveStaleLimitsForTheNextBuild() throws IOException {
        Path target = dir.resolve("late.7z");
        Files.write(target, new byte[] {1, 2, 3, 4});
        var builder = SevenZArchiveExtractor.builder(target);
        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);
        Files.copy(sevenZ("valid.7z"), target, StandardCopyOption.REPLACE_EXISTING);
        builder.maxRatio(1_000_000).maxTotalSize(ZEROS / 2);
        try (var extractor = builder.build()) {
            assertLimit(() -> drain(extractor), Limit.TOTAL_SIZE);
        }
    }
}
