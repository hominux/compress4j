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
package com.hominux.compress4j.archivers.tar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.archivers.ArchiveItem;
import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.archivers.EntrySource;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.internal.codec.LibraryHidingLoader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

class TarWithoutOptionalLibrariesTest {

    private static final List<String> HIDDEN = List.of("org.tukaani.", "com.github.luben.", "org.brotli.");

    /** Runs inside a class loader that cannot see xz, zstd or brotli. */
    public static final class Scenario implements Callable<String> {
        @Override
        public String call() throws Exception {
            return roundTrip(Compression.none()) + "," + roundTrip(Compression.gzip());
        }

        private static String roundTrip(Compression compression) throws Exception {
            var bytes = new ByteArrayOutputStream();
            try (var creator =
                    TarArchiveCreator.builder(bytes).compression(compression).build()) {
                creator.add(EntrySource.file("a.txt", "alpha".getBytes(StandardCharsets.UTF_8)));
            }
            try (var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(bytes.toByteArray()))
                    .build()) {
                return extractor.stream()
                        .map(ArchiveItem::entry)
                        .map(Entry::name)
                        .reduce("", String::concat);
            }
        }
    }

    @Test
    void plainAndGzipTarsWorkWithoutXzZstdOrBrotli() throws Exception {
        try (var loader = new LibraryHidingLoader(HIDDEN.toArray(String[]::new))) {
            @SuppressWarnings("unchecked")
            var scenario = (Callable<String>) loader.loadClass(Scenario.class.getName())
                    .getDeclaredConstructor()
                    .newInstance();

            assertThat(scenario.call()).isEqualTo("a.txt,a.txt");
        }
    }
}
