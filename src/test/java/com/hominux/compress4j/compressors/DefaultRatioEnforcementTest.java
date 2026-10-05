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
package com.hominux.compress4j.compressors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.EntrySource;
import com.hominux.compress4j.archivers.tar.TarArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.compressors.catalog.CodecCatalog;
import com.hominux.compress4j.compressors.catalog.CodecFormat;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Default limits must stop a ratio bomb in every codec that can produce one. */
class DefaultRatioEnforcementTest {

    private static final int ZEROS = 16 << 20;

    // Snappy copies cap below the default ratio and Pack200 is unmetered, so neither can breach it.
    private static final Set<String> UNBOMBABLE = Set.of("snappy-framed", "snappy-raw", "pack200");

    @TempDir
    Path dir;

    static Stream<CodecFormat> bombable() {
        return CodecCatalog.writable().filter(format -> !UNBOMBABLE.contains(format.name()));
    }

    private static byte[] bomb(Compression compression) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var compressor = Compressor.builder(out, compression).build()) {
            compressor.write(new ByteArrayInputStream(new byte[ZEROS]));
        }
        return out.toByteArray();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bombable")
    void decompressorStopsABombWithDefaultLimits(CodecFormat format) throws IOException {
        byte[] bomb = bomb(format.compression());
        try (var decompressor = Decompressor.builder(new ByteArrayInputStream(bomb), format.compression())
                .build()) {
            assertThatThrownBy(() -> decompressor.inputStream().transferTo(OutputStream.nullOutputStream()))
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                            .isEqualTo(Limit.RATIO));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bombable")
    void tarExtractorStopsACompressedBombWithDefaultLimits(CodecFormat format) throws IOException {
        Path archive = dir.resolve("bomb.tar");
        try (var creator = TarArchiveCreator.builder(archive)
                .compression(format.compression())
                .build()) {
            creator.add(EntrySource.file("zeros", new byte[ZEROS]));
        }
        Path out = Files.createDirectory(dir.resolve("out"));
        try (var extractor = TarArchiveExtractor.builder(archive)
                .compression(format.compression())
                .build()) {
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                            .isEqualTo(Limit.RATIO));
        }
    }
}
