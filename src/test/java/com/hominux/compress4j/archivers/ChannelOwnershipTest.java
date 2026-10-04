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
package com.hominux.compress4j.archivers;

import static com.hominux.compress4j.archivers.catalog.Capability.RANDOM_ACCESS_INPUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.hominux.compress4j.archivers.catalog.ArchiveFormat;
import com.hominux.compress4j.archivers.catalog.FormatCatalog;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ChannelOwnershipTest {

    private static final byte[] PREFIX = "prefix-bytes".getBytes(StandardCharsets.UTF_8);

    static Stream<ArchiveFormat> channelReaders() {
        return FormatCatalog.all().filter(f -> f.readFromChannel().isPresent());
    }

    static Stream<ArchiveFormat> channelWriters() {
        return FormatCatalog.all().filter(f -> f.createOnChannel().isPresent());
    }

    private static byte[] archive(ArchiveFormat format) throws IOException {
        var channel = new SeekableInMemoryByteChannel();
        try (var creator =
                FormatCatalog.writerOf(format).createOnChannel().orElseThrow().apply(channel)) {
            creator.addFile("a.txt", "alpha".getBytes(StandardCharsets.UTF_8));
        }
        return Arrays.copyOf(channel.array(), (int) channel.size());
    }

    private static String firstContent(ArchiveExtractor<?> extractor) throws IOException {
        var item = extractor.stream().findFirst().orElseThrow();
        return new String(item.content().readAllBytes(), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @MethodSource("channelWriters")
    void closingTheCreatorClosesTheChannel(ArchiveFormat format) throws IOException {
        var channel = new SeekableInMemoryByteChannel();

        try (var creator = format.createOnChannel().orElseThrow().apply(channel)) {
            creator.addFile("a.txt", "alpha".getBytes(StandardCharsets.UTF_8));
        }

        assertThat(channel.isOpen()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("channelReaders")
    void closingTheExtractorClosesTheChannel(ArchiveFormat format) throws IOException {
        var channel = new SeekableInMemoryByteChannel(archive(format));

        try (var extractor = format.readFromChannel().orElseThrow().apply(channel)) {
            extractor.stream().toList();
        }

        assertThat(channel.isOpen()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("channelReaders")
    void garbageFailsAndLeavesTheCallersChannelOpen(ArchiveFormat format) {
        var bytes = new byte[1024];
        Arrays.fill(bytes, (byte) 0xFF);
        var garbage = new SeekableInMemoryByteChannel(bytes);

        Throwable failure = catchThrowable(() ->
                format.readFromChannel().orElseThrow().apply(garbage).stream().toList());

        assertThat(failure).isNotNull();
        assertThat(garbage.isOpen()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("channelReaders")
    void channelPositionMatchesDeclaration(ArchiveFormat format) throws IOException {
        byte[] archive = archive(format);
        SeekableInMemoryByteChannel channel;
        if (format.has(RANDOM_ACCESS_INPUT)) {
            channel = new SeekableInMemoryByteChannel(archive);
        } else {
            var bytes = new ByteArrayOutputStream();
            bytes.write(PREFIX);
            bytes.write(archive);
            channel = new SeekableInMemoryByteChannel(bytes.toByteArray());
        }
        channel.position(PREFIX.length);

        try (var extractor = format.readFromChannel().orElseThrow().apply(channel)) {
            assertThat(firstContent(extractor)).isEqualTo("alpha");
        }
    }
}
