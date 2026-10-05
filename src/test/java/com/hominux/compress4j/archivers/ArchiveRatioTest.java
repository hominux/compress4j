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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.archivers.tar.TarArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import com.hominux.compress4j.internal.limits.ExpansionMeter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveRatioTest {

    private static final int ZEROS = 16 << 20;
    private static final long EARLY_STOP_BOUND = ExpansionMeter.RATIO_GRACE_BYTES + (256 << 10);

    @TempDir
    Path dir;

    private static void drain(ArchiveExtractor extractor, AtomicLong delivered) throws IOException {
        try {
            extractor.stream().forEach(item -> read(item, delivered));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
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

    private Path zeroArchive(String name, Compression compression) throws IOException {
        Path archive = dir.resolve(name);
        try (var creator =
                TarArchiveCreator.builder(archive).compression(compression).build()) {
            creator.add(EntrySource.file("zeros", new byte[ZEROS]));
        }
        return archive;
    }

    @Test
    void defaultRatioStopsACompressedTarBombEarlyWhenStreaming() throws IOException {
        Path tgz = zeroArchive("bomb.tar.gz", Compression.gzip());
        var delivered = new AtomicLong();
        try (var extractor = TarArchiveExtractor.builder(tgz).build()) {
            assertThatThrownBy(() -> drain(extractor, delivered))
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                            .isEqualTo(Limit.RATIO));
        }
        assertThat(delivered.get()).isLessThanOrEqualTo(EARLY_STOP_BOUND);
    }

    @Test
    void defaultRatioStopsACompressedTarBombEarlyWhenExtracting() throws IOException {
        Path tgz = zeroArchive("bomb.tar.gz", Compression.gzip());
        Path out = Files.createDirectory(dir.resolve("out"));
        try (var extractor = TarArchiveExtractor.builder(tgz).build()) {
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                            .isEqualTo(Limit.RATIO));
        }
        assertThat(Files.size(out.resolve("zeros"))).isLessThanOrEqualTo(EARLY_STOP_BOUND);
    }

    @Test
    void unlimitedRatioAcceptsTheSameTarBomb() throws IOException {
        Path tgz = zeroArchive("bomb.tar.gz", Compression.gzip());
        var delivered = new AtomicLong();
        try (var extractor = TarArchiveExtractor.builder(tgz)
                .maxRatio(ExtractionLimits.UNLIMITED)
                .build()) {
            assertDoesNotThrow(() -> drain(extractor, delivered));
        }
        assertThat(delivered.get()).isEqualTo(ZEROS);
    }

    @Test
    void noLimitsAcceptTheSameTarBomb() throws IOException {
        Path tgz = zeroArchive("bomb.tar.gz", Compression.gzip());
        var delivered = new AtomicLong();
        try (var extractor = TarArchiveExtractor.builder(tgz)
                .limits(ExtractionLimits.noLimits())
                .build()) {
            assertDoesNotThrow(() -> drain(extractor, delivered));
        }
        assertThat(delivered.get()).isEqualTo(ZEROS);
    }

    @Test
    void uncompressedTarNeverTripsTheRatio() throws IOException {
        Path tar = zeroArchive("plain.tar", Compression.none());
        var delivered = new AtomicLong();
        try (var extractor = TarArchiveExtractor.builder(tar).build()) {
            assertDoesNotThrow(() -> drain(extractor, delivered));
        }
        assertThat(delivered.get()).isEqualTo(ZEROS);
    }
}
