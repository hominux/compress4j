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

import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.MemoryLimitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The source stream here is caller-supplied, so a RuntimeException it throws is indistinguishable from one thrown by
 * the codec and is reported as corrupt data. The real-codec cases use bytes that make the codec itself fail.
 */
class DecompressorParserFailureTest {

    @TempDir
    Path dir;

    private static byte[] gzip(byte[] content) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var compressor = Compressor.builder(bytes, Compression.gzip()).build()) {
            compressor.write(new ByteArrayInputStream(content));
        }
        return bytes.toByteArray();
    }

    private static InputStream failingAfterTheHeader(byte[] valid, RuntimeException failure) {
        return new ByteArrayInputStream(valid) {
            @Override
            public int read(byte[] buffer, int offset, int length) {
                if (pos > 10) {
                    throw failure;
                }
                return super.read(buffer, offset, 1);
            }

            @Override
            public int read() {
                throw failure;
            }
        };
    }

    @Test
    void runtimeFailuresOfTheSourceStreamAreReportedAsCorruptDataWhenReading() throws IOException {
        var failure = new IllegalStateException("codec broke");
        byte[] valid = gzip("data ".repeat(100).getBytes(StandardCharsets.UTF_8));
        try (var decompressor = Decompressor.builder(failingAfterTheHeader(valid, failure), Compression.gzip())
                .build()) {
            var in = decompressor.inputStream();
            assertThatThrownBy(in::readAllBytes)
                    .isInstanceOf(IOException.class)
                    .hasMessage("Corrupt compressed data")
                    .hasCause(failure);
        }
    }

    @Test
    void runtimeFailuresOfTheSourceStreamAreReportedAsCorruptDataWhenWriting() throws IOException {
        var failure = new IllegalStateException("codec broke");
        byte[] valid = gzip("data ".repeat(100).getBytes(StandardCharsets.UTF_8));
        Path target = dir.resolve("out");
        try (var decompressor = Decompressor.builder(failingAfterTheHeader(valid, failure), Compression.gzip())
                .build()) {
            assertThatThrownBy(() -> decompressor.write(target))
                    .isInstanceOf(IOException.class)
                    .hasCause(failure);
        }
        assertThat(target).doesNotExist();
    }

    @Test
    void runtimeFailuresOfTheSourceStreamWhileDetectingTheCodecAreReportedAsCorruptData() {
        var failure = new IllegalStateException("codec broke");
        var in = new InputStream() {
            @Override
            public int read() {
                throw failure;
            }
        };
        assertThatThrownBy(() -> Decompressor.builder(in).build())
                .isInstanceOf(IOException.class)
                .hasMessage("Corrupt compressed data")
                .hasCause(failure);
    }

    @Test
    void unixZHeaderWithAnOutOfBoundsCodeSizeFailsAsIOException() {
        var in = new ByteArrayInputStream(new byte[] {0x1F, (byte) 0x9D, 0x08, 0, 0, 0});
        var builder = Decompressor.builder(in);
        assertThatThrownBy(builder::build)
                .isInstanceOf(IOException.class)
                .hasMessage("Corrupt compressed data")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unixZHeaderDemandingGigabytesOfTablesIsRefused() {
        var in = new ByteArrayInputStream(new byte[] {0x1F, (byte) 0x9D, 0x1E, 0, 0, 0});
        var builder = Decompressor.builder(in, Compression.unixZ());
        assertThatThrownBy(builder::build).isInstanceOf(MemoryLimitException.class);
    }

    @Test
    void limitBreachesStayLimitExceptions() throws IOException {
        byte[] valid = gzip(new byte[3 << 20]);
        try (var decompressor =
                Decompressor.builder(new ByteArrayInputStream(valid)).build()) {
            var in = decompressor.inputStream();
            assertThatThrownBy(in::readAllBytes)
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                            .isEqualTo(Limit.RATIO));
        }
    }

    @Test
    void validDataStillDecompressesAfterWrapping() throws IOException {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        Path target = dir.resolve("hello.txt");
        try (var decompressor =
                Decompressor.builder(new ByteArrayInputStream(gzip(content))).build()) {
            decompressor.write(target);
        }
        assertThat(Files.readAllBytes(target)).isEqualTo(content);
    }
}
