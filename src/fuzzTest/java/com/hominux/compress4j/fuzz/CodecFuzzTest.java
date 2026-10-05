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
package com.hominux.compress4j.fuzz;

import static com.hominux.compress4j.fuzz.FuzzSupport.LIMITS;
import static com.hominux.compress4j.fuzz.FuzzSupport.consume;
import static com.hominux.compress4j.fuzz.FuzzSupport.onlyIOException;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.compressors.Decompressor;
import java.io.ByteArrayInputStream;
import java.util.Optional;

class CodecFuzzTest {

    private static void decompress(byte[] data, Optional<Compression> compression) {
        onlyIOException(() -> {
            ByteArrayInputStream source = new ByteArrayInputStream(data);
            Decompressor.Builder builder = compression.isPresent()
                    ? Decompressor.builder(source, compression.orElseThrow())
                    : Decompressor.builder(source);
            try (Decompressor decompressor = builder.limits(LIMITS).build()) {
                consume(decompressor.inputStream());
            }
        });
    }

    private static void decompress(byte[] data, Compression compression) {
        decompress(data, Optional.of(compression));
    }

    @FuzzTest(maxDuration = "60s")
    void detected(byte[] data) {
        decompress(data, Optional.empty());
    }

    @FuzzTest(maxDuration = "60s")
    void gzip(byte[] data) {
        decompress(data, Compression.gzip());
    }

    @FuzzTest(maxDuration = "60s")
    void bzip2(byte[] data) {
        decompress(data, Compression.bzip2());
    }

    @FuzzTest(maxDuration = "60s")
    void xz(byte[] data) {
        decompress(data, Compression.xz());
    }

    @FuzzTest(maxDuration = "60s")
    void lzma(byte[] data) {
        decompress(data, Compression.lzma());
    }

    @FuzzTest(maxDuration = "60s")
    void lz4Block(byte[] data) {
        decompress(data, Compression.lz4Block());
    }

    @FuzzTest(maxDuration = "60s")
    void lz4Framed(byte[] data) {
        decompress(data, Compression.lz4Framed());
    }

    @FuzzTest(maxDuration = "60s")
    void zstd(byte[] data) {
        decompress(data, Compression.zstd());
    }

    @FuzzTest(maxDuration = "60s")
    void deflate(byte[] data) {
        decompress(data, Compression.deflate());
    }

    @FuzzTest(maxDuration = "60s")
    void deflate64(byte[] data) {
        decompress(data, Compression.deflate64());
    }

    @FuzzTest(maxDuration = "60s")
    void snappyRaw(byte[] data) {
        decompress(data, Compression.snappyRaw());
    }

    @FuzzTest(maxDuration = "60s")
    void snappyFramed(byte[] data) {
        decompress(data, Compression.snappyFramed());
    }

    @FuzzTest(maxDuration = "60s")
    void brotli(byte[] data) {
        decompress(data, Compression.brotli());
    }

    @FuzzTest(maxDuration = "60s")
    void unixZ(byte[] data) {
        decompress(data, Compression.unixZ());
    }
}
