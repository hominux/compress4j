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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarArchiveCreatorBuilderTest {

    @Test
    void shouldPadTheArchiveToTheConfiguredBlockSize() throws IOException {
        var bytes = new ByteArrayOutputStream();

        try (var creator = TarArchiveCreator.builder(bytes)
                .blockSize(4096)
                .longFileMode(TarLongFileMode.POSIX)
                .bigNumberMode(TarBigNumberMode.POSIX)
                .encoding(StandardCharsets.UTF_8)
                .build()) {
            assertDoesNotThrow(creator::close);
        }

        assertThat(bytes.size()).isEqualTo(4096);
    }

    @Test
    void shouldTerminateAnEmptyArchiveWithTheDefaultBlock() throws IOException {
        var bytes = new ByteArrayOutputStream();

        TarArchiveCreator.builder(bytes).build().close();

        assertThat(bytes.size()).isEqualTo(1024);
    }

    @Test
    void shouldRejectInvalidBlockSizesBeforeWritingAnything() {
        var sink = new ByteArrayOutputStream();
        var builder = TarArchiveCreator.builder(sink);

        for (int invalid : new int[] {0, -1, 100, 513, -512}) {
            assertThatThrownBy(() -> builder.blockSize(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(sink.size()).isZero();
        assertThat(builder.blockSize(-511)).isSameAs(builder);
        assertThat(builder.blockSize(512)).isSameAs(builder);
    }

    @Test
    void shouldNotCreateTheFileBeforeBuild(@TempDir Path dir) {
        Path target = dir.resolve("never.tar");

        TarArchiveCreator.builder(target).compression(Compression.gzip());

        assertThat(target).doesNotExist();
    }

    @Test
    void shouldLeaveACallerStreamOpenWhenBuildFails() {
        var closed = new AtomicBoolean();
        var failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("write refused");
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        var builder = TarArchiveCreator.builder(failing).compression(Compression.gzip());

        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);

        assertThat(closed).isFalse();
    }
}
