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

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecompressorTest {

    @TempDir
    Path dir;

    private Path gzip(byte[] content) throws IOException {
        Path target = dir.resolve("data.gz");
        try (var compressor = Compressor.builder(target, Compression.gzip()).build()) {
            compressor.write(new ByteArrayInputStream(content));
        }
        return target;
    }

    @Test
    void writeRefusesAnExistingTargetByDefault() throws IOException {
        Path source = gzip("new".getBytes(StandardCharsets.UTF_8));
        Path target = Files.writeString(dir.resolve("out.txt"), "old");
        try (var decompressor = Decompressor.builder(source).build()) {
            assertThatThrownBy(() -> decompressor.write(target)).isInstanceOf(FileAlreadyExistsException.class);
        }
        assertThat(target).hasContent("old");
    }

    @Test
    void overwriteReplacesTheTarget() throws IOException {
        Path source = gzip("new".getBytes(StandardCharsets.UTF_8));
        Path target = Files.writeString(dir.resolve("out.txt"), "old");
        try (var decompressor = Decompressor.builder(source).overwrite(true).build()) {
            assertThat(decompressor.write(target)).isEqualTo(3);
        }
        assertThat(target).hasContent("new");
    }

    @Test
    void ratioLimitStopsBombsAndCanBeRaised() throws IOException {
        Path bomb = gzip(new byte[3 << 20]);
        try (var decompressor = Decompressor.builder(bomb).build()) {
            assertThatThrownBy(() -> decompressor.inputStream().readAllBytes())
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> {
                        assertThat(e.limit()).isEqualTo(Limit.RATIO);
                        assertThat(e).hasMessageContaining("maxRatio");
                    });
        }
        try (var decompressor =
                Decompressor.builder(bomb).maxRatio(ExtractionLimits.UNLIMITED).build()) {
            assertThat(decompressor.inputStream().readAllBytes()).hasSize(3 << 20);
        }
    }

    @Test
    void failedWriteDeletesThePartialTarget() throws IOException {
        Path source = gzip(new byte[4096]);
        Path target = dir.resolve("out.bin");
        try (var decompressor = Decompressor.builder(source).maxTotalSize(1000).build()) {
            assertThatThrownBy(() -> decompressor.write(target)).isInstanceOf(LimitExceededException.class);
        }
        assertThat(target).doesNotExist();
    }

    @Test
    void failedBuildLeavesTheCallersStreamOpen() {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream corrupt = new ByteArrayInputStream(new byte[] {1, 2, 3}) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        assertThatThrownBy(
                        () -> Decompressor.builder(corrupt, Compression.gzip()).build())
                .isInstanceOf(IOException.class);
        assertThat(closed).isFalse();
    }

    @Test
    void detectsTheCodecFromAStream() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var compressor = Compressor.builder(bytes, Compression.bzip2()).build()) {
            compressor.write(new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));
        }
        try (var decompressor = Decompressor.builder(new ByteArrayInputStream(bytes.toByteArray()))
                .build()) {
            assertThat(decompressor.inputStream().readAllBytes())
                    .asString(StandardCharsets.UTF_8)
                    .isEqualTo("x");
        }
    }

    @Test
    void detectionDecompressesConcatenatedMembers() throws IOException {
        var bytes = new ByteArrayOutputStream();
        for (String part : List.of("one", "two")) {
            try (var compressor = Compressor.builder(bytes, Compression.gzip()).build()) {
                compressor.write(new ByteArrayInputStream(part.getBytes(StandardCharsets.UTF_8)));
            }
        }
        try (var decompressor = Decompressor.builder(new ByteArrayInputStream(bytes.toByteArray()))
                .build()) {
            assertThat(decompressor.inputStream().readAllBytes())
                    .asString(StandardCharsets.UTF_8)
                    .isEqualTo("onetwo");
        }
    }

    @Test
    void detectionNeverSelectsPack200() throws IOException {
        byte[] pack200 = {(byte) 0xCA, (byte) 0xFE, (byte) 0xD0, 0x0D, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        try (var decompressor =
                Decompressor.builder(new ByteArrayInputStream(pack200)).build()) {
            assertThat(decompressor.inputStream().readAllBytes()).isEqualTo(pack200);
        }
    }

    @Test
    void plainInputIsDetectedAsNone() throws IOException {
        byte[] plain = "just text, no signature".getBytes(StandardCharsets.UTF_8);
        try (var decompressor =
                Decompressor.builder(new ByteArrayInputStream(plain)).build()) {
            assertThat(decompressor.inputStream().readAllBytes()).isEqualTo(plain);
        }
    }

    @Test
    void writeTwiceRefusesTheSecondTargetOnlyWhenItExists() throws IOException {
        Path source = gzip("data".getBytes(StandardCharsets.UTF_8));
        Path first = dir.resolve("first.txt");
        Path second = dir.resolve("second.txt");
        try (var decompressor = Decompressor.builder(source).build()) {
            assertThat(decompressor.write(first)).isEqualTo(4);
            assertThat(decompressor.write(second)).isZero();
        }
        assertThat(second).hasContent("");
    }

    @Test
    void failedWriteKeepsAnExistingTargetWhenOverwriteIsOff() throws IOException {
        Path source = gzip(new byte[4096]);
        Path target = Files.writeString(dir.resolve("out.bin"), "old");
        try (var decompressor = Decompressor.builder(source).maxTotalSize(1000).build()) {
            assertThatThrownBy(() -> decompressor.write(target)).isInstanceOf(FileAlreadyExistsException.class);
        }
        assertThat(target).hasContent("old");
    }

    @Test
    void readsFromAChannelWithAnExplicitCodec() throws IOException {
        Path source = gzip("chan".getBytes(StandardCharsets.UTF_8));
        try (SeekableByteChannel channel = Files.newByteChannel(source, StandardOpenOption.READ);
                var decompressor =
                        Decompressor.builder(channel, Compression.gzip()).build()) {
            assertThat(decompressor.inputStream().readAllBytes())
                    .asString(StandardCharsets.UTF_8)
                    .isEqualTo("chan");
        }
    }
}
