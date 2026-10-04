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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.OptionalLong;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.junit.jupiter.api.Test;

class TarLzmaArchiveCreatorTest {

    @SuppressWarnings("OctalInteger")
    @Test
    void shouldWriteArchiveReadableByPlainCommonsCompress() throws IOException {
        var bytes = new ByteArrayOutputStream();

        try (var creator = TarLzmaArchiveCreator.builder(bytes).build()) {
            creator.writeFile(
                    "file.txt",
                    new ByteArrayInputStream("hello lzma".getBytes()),
                    OptionalLong.of("hello lzma".length()),
                    0644,
                    FileTime.from(Instant.now()));
        }

        try (var tar = new TarArchiveInputStream(
                new LZMACompressorInputStream(new ByteArrayInputStream(bytes.toByteArray())))) {
            var entry = tar.getNextEntry();
            assertThat(entry.getName()).isEqualTo("file.txt");
            assertThat(new String(tar.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello lzma");
        }
    }
}
