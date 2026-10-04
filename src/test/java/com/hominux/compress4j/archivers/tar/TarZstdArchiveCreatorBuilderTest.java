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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.BIGNUMBER_POSIX;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_POSIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.assertArg;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.hominux.compress4j.archivers.tar.TarZstdArchiveCreator.TarZstdArchiveCreatorBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.junit.jupiter.api.Test;

class TarZstdArchiveCreatorBuilderTest {

    @Test
    void shouldBuildArchiveOutputStream() throws IOException {
        var builder = spy(TarZstdArchiveCreator.builder(mock(OutputStream.class))
                .longFileMode(LONGFILE_POSIX)
                .bigNumberMode(BIGNUMBER_POSIX));

        try (var out = spy(builder.buildArchiveOutputStream())) {
            assertThat(out)
                    .isNotNull()
                    .extracting("longFileMode", "bigNumberMode")
                    .containsExactly(LONGFILE_POSIX, BIGNUMBER_POSIX);
            then(builder).should().buildTarArchiveOutputStream(assertArg(o -> assertThat(o)
                    .isInstanceOf(ZstdCompressorOutputStream.class)));
        }
    }

    @Test
    void shouldUseDefaultLevelWhenNoneConfigured() throws IOException {
        var withDefault = archive(builder -> builder);
        var withLevelThree = archive(
                builder -> builder.compressorOutputStreamBuilder().level(3).parentBuilder());

        assertThat(withDefault).isEqualTo(withLevelThree);
    }

    @Test
    void shouldApplyConfiguredLevel() throws IOException {
        var fastest = archive(
                builder -> builder.compressorOutputStreamBuilder().level(1).parentBuilder());
        var strongest = archive(
                builder -> builder.compressorOutputStreamBuilder().level(19).parentBuilder());

        assertThat(strongest).hasSizeLessThan(fastest.length);
    }

    @SuppressWarnings("OctalInteger")
    private static byte[] archive(UnaryOperator<TarZstdArchiveCreatorBuilder> configure) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var payload = IntStream.range(0, 2000)
                .mapToObj(i -> "line " + i + " value " + (i * 31 % 97) + "\n")
                .collect(joining())
                .getBytes(UTF_8);
        try (var creator = configure.apply(TarZstdArchiveCreator.builder(bytes)).build()) {
            creator.writeFile(
                    "data.txt",
                    new ByteArrayInputStream(payload),
                    OptionalLong.of(payload.length),
                    0644,
                    FileTime.fromMillis(0));
        }
        return bytes.toByteArray();
    }
}
