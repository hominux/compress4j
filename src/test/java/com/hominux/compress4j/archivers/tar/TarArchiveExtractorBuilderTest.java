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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hominux.compress4j.compressors.Compression;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarArchiveExtractorBuilderTest {

    @Test
    void shouldBuildAnExtractorOverAnEmptyStream() throws IOException {
        var builder = TarArchiveExtractor.builder(new ByteArrayInputStream(new byte[0]));

        try (var extractor = builder.build()) {
            assertThat(extractor.stream()).isEmpty();
        }
    }

    @Test
    void shouldRejectPack200() {
        var builder = TarArchiveExtractor.builder(InputStream.nullInputStream());
        var pack200 = Compression.pack200();

        assertThatThrownBy(() -> builder.compression(pack200)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldLeaveACallerStreamOpenWhenBuildFails() {
        var closed = new AtomicBoolean();
        var stream = new ByteArrayInputStream("not gzip".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        var builder = TarArchiveExtractor.builder(stream).compression(Compression.gzip());

        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);

        assertThat(closed).isFalse();
    }

    @Test
    void shouldNotOpenAMissingPathBeforeBuild(@TempDir Path dir) {
        Path missing = dir.resolve("missing.tar");

        var builder = assertDoesNotThrow(() -> TarArchiveExtractor.builder(missing));

        assertThatThrownBy(builder::build).isInstanceOf(NoSuchFileException.class);
    }
}
