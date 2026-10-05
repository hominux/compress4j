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
package com.hominux.compress4j.internal.codec;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.compressors.Compression;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

class ReadOnlyCodecsIntegrationTest {

    private static InputStream resource(String name) {
        return Objects.requireNonNull(ReadOnlyCodecsIntegrationTest.class.getResourceAsStream("/" + name), name);
    }

    @Test
    void readsDeflate64() throws IOException {
        try (InputStream in = Codecs.decompressing(Compression.deflate64(), resource("compression/lorem.deflate64"))) {
            byte[] text = in.readAllBytes();
            assertThat(text).hasSize(144_060);
            assertThat(new String(text, 1, 55, java.nio.charset.StandardCharsets.US_ASCII))
                    .isEqualTo("Lorem ipsum dolor sit amet, consectetur adipiscing elit");
        }
    }

    @Test
    void readsUnixZ() throws IOException {
        try (var tar = new TarArchiveInputStream(
                Codecs.decompressing(Compression.unixZ(), resource("archives/upstream-bla.tar.Z")))) {
            assertThat(tar.getNextEntry()).isNotNull();
        }
    }

    @Test
    void readsBrotliSampleToItsExpectedContent() throws IOException {
        try (InputStream in = Codecs.decompressing(
                Compression.brotli(), resource("compression/upstream-brotli.testdata.compressed"))) {
            assertThat(in.readAllBytes())
                    .isEqualTo(resource("compression/upstream-brotli.testdata.uncompressed")
                            .readAllBytes());
        }
    }

    @Test
    void readsBrotli() throws IOException {
        try (var tar = new TarArchiveInputStream(
                Codecs.decompressing(Compression.brotli(), resource("archives/upstream-bla.tar.br")))) {
            assertThat(tar.getNextEntry()).isNotNull();
        }
    }
}
