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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompressorTest {

    @TempDir
    Path dir;

    @Test
    void compressesAFileAndAStream() throws IOException {
        Path source = Files.writeString(dir.resolve("a.txt"), "alpha");
        Path target = dir.resolve("a.txt.gz");
        try (var compressor = Compressor.builder(target, Compression.gzip()).build()) {
            assertThat(compressor.write(source)).isEqualTo(5);
            assertThat(compressor.write(new ByteArrayInputStream("beta".getBytes(StandardCharsets.UTF_8))))
                    .isEqualTo(4);
        }
        try (var decompressor = Decompressor.builder(target).build()) {
            assertThat(decompressor.inputStream().readAllBytes())
                    .asString(StandardCharsets.UTF_8)
                    .isEqualTo("alphabeta");
        }
    }

    @Test
    void readOnlyCodecIsRejectedAtTheBuilder() {
        var sink = new ByteArrayOutputStream();
        var brotli = Compression.brotli();
        assertThatThrownBy(() -> Compressor.builder(sink, brotli))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can only be read");
    }

    @Test
    void unbuiltBuilderCreatesNothing() {
        Path target = dir.resolve("never.gz");
        Compressor.builder(target, Compression.gzip());
        assertThat(target).doesNotExist();
    }

    @Test
    void failedBuildKeepsAnExistingTarget() throws IOException {
        Path target = Files.writeString(dir.resolve("keep.snappy"), "keep");
        var snappyRaw = Compression.snappyRaw();
        assertThatThrownBy(() -> Compressor.builder(target, snappyRaw)).isInstanceOf(IllegalArgumentException.class);
        assertThat(target).hasContent("keep");
    }

    @Test
    void failedBuildLeavesTheCallersStreamOpen() {
        AtomicBoolean closed = new AtomicBoolean();
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("broken");
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        assertThatThrownBy(() -> Compressor.builder(broken, Compression.gzip()).build())
                .isInstanceOf(IOException.class);
        assertThat(closed).isFalse();
    }

    @Test
    void writesToAChannel() throws IOException {
        Path target = dir.resolve("chan.gz");
        try (SeekableByteChannel channel =
                        Files.newByteChannel(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var compressor = Compressor.builder(channel, Compression.gzip()).build()) {
            compressor.write(new ByteArrayInputStream("chan".getBytes(StandardCharsets.UTF_8)));
        }
        try (var decompressor = Decompressor.builder(target).build()) {
            assertThat(decompressor.inputStream().readAllBytes())
                    .asString(StandardCharsets.UTF_8)
                    .isEqualTo("chan");
        }
    }
}
