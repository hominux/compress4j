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

import com.hominux.compress4j.compressors.catalog.CodecCatalog;
import com.hominux.compress4j.compressors.catalog.CodecFormat;
import com.hominux.compress4j.internal.codec.Codecs;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CodecContractTest {

    private static final byte[] TEXT = "contract ".repeat(200).getBytes(StandardCharsets.UTF_8);

    private static final Set<String> LAZY_HEADER =
            Set.of("lz4-block", "zstd", "deflate", "snappy-raw", "deflate64", "brotli", "pack200");

    static Stream<CodecFormat> writable() {
        return CodecCatalog.writable().filter(format -> !format.name().equals("pack200"));
    }

    static Stream<CodecFormat> detectableWritable() {
        return writable().filter(CodecFormat::detectable);
    }

    private static Compression forWriting(CodecFormat format) {
        return format.compression() instanceof Compression.SnappyRaw raw
                ? raw.uncompressedSize(TEXT.length)
                : format.compression();
    }

    private static byte[] compress(CodecFormat format) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var compressor = Compressor.builder(bytes, forWriting(format)).build()) {
            compressor.write(new ByteArrayInputStream(TEXT));
        }
        return bytes.toByteArray();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writable")
    void roundTrips(CodecFormat format) throws IOException {
        try (var decompressor = Decompressor.builder(new ByteArrayInputStream(compress(format)), format.compression())
                .build()) {
            assertThat(decompressor.inputStream().readAllBytes()).isEqualTo(TEXT);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("detectableWritable")
    void isDetected(CodecFormat format) throws IOException {
        try (var decompressor =
                Decompressor.builder(new ByteArrayInputStream(compress(format))).build()) {
            assertThat(decompressor.inputStream().readAllBytes()).isEqualTo(TEXT);
        }
    }

    static Stream<CodecFormat> all() {
        return CodecCatalog.all();
    }

    static Stream<CodecFormat> undetectableWritable() {
        return writable().filter(format -> !format.detectable());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("undetectableWritable")
    void undetectedStreamPassesThroughUndecoded(CodecFormat format) throws IOException {
        try (var decompressor =
                Decompressor.builder(new ByteArrayInputStream(compress(format))).build()) {
            assertThat(decompressor.inputStream().readAllBytes()).isNotEqualTo(TEXT);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("all")
    void failedBuildNeverClosesTheCallersStream(CodecFormat format) throws IOException {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream caller = new ByteArrayInputStream(new byte[] {0}) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        if (LAZY_HEADER.contains(format.name())) {
            Decompressor.builder(caller, format.compression()).build().close();
        } else {
            var builder = Decompressor.builder(caller, format.compression());
            assertThatThrownBy(builder::build).isInstanceOfAny(IOException.class, RuntimeException.class);
            assertThat(closed).isFalse();
        }
    }

    static Stream<CodecFormat> readOnly() {
        return CodecCatalog.all().filter(format -> !format.compression().canWrite());
    }

    private static byte[] handBuiltHeader(CodecFormat format) {
        return switch (format.name()) {
            case "z" -> new byte[] {0x1f, (byte) 0x9d, (byte) 0x90, 0, 0, 0};
            case "deflate64" -> new byte[] {0x01, 0x03, 0x00, (byte) 0xfc, (byte) 0xff, 'a', 'b', 'c'};
            case "brotli" -> new byte[] {0x06};
            default -> throw new IllegalArgumentException(format.name());
        };
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("readOnly")
    void readOnlyCodecDetectionMatchesTheCatalog(CodecFormat format) throws IOException {
        var in = new BufferedInputStream(new ByteArrayInputStream(handBuiltHeader(format)));
        Compression detected = Codecs.detect(in);
        assertThat(detected.equals(format.compression()))
                .as("detected %s", detected)
                .isEqualTo(format.detectable());
    }
}
