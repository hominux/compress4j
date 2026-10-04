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

import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.BIGNUMBER_POSIX;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_POSIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

class TarArchiveCreatorBuilderTest {

    @Test
    void shouldBuildArchiveOutputStream() throws IOException {
        // given
        var outputStream = mock(OutputStream.class);
        var builder = TarArchiveCreator.builder(outputStream)
                .longFileMode(TarLongFileMode.POSIX)
                .bigNumberMode(TarBigNumberMode.POSIX)
                .blockSize(1024)
                .encoding(StandardCharsets.UTF_8);

        // when
        try (TarArchiveOutputStream out = spy(builder.buildArchiveOutputStream())) {

            // then
            assertThat(out)
                    .isNotNull()
                    .extracting("longFileMode", "bigNumberMode", "recordsPerBlock", "charsetName")
                    .containsExactly(LONGFILE_POSIX, BIGNUMBER_POSIX, 2, "UTF-8");
        }
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
}
