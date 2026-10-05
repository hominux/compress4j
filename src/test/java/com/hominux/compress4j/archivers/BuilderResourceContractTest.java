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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Named.named;

import com.hominux.compress4j.archivers.arj.ArjArchiveExtractor;
import com.hominux.compress4j.archivers.catalog.ArchiveFormat;
import com.hominux.compress4j.archivers.catalog.FormatCatalog;
import com.hominux.compress4j.archivers.dump.DumpArchiveExtractor;
import com.hominux.compress4j.archivers.sevenz.SevenZArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.archivers.zip.ZipArchiveExtractor;
import com.hominux.compress4j.compressors.Compression;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BuilderResourceContractTest {

    private static final Set<Class<?>> VALIDATES_AT_BUILD = Set.of(
            TarArchiveExtractor.class,
            ZipArchiveExtractor.class,
            SevenZArchiveExtractor.class,
            ArjArchiveExtractor.class,
            DumpArchiveExtractor.class);
    private static final String LAZY_REASON = "the format validates its input on first read, not in build()";

    @TempDir
    Path dir;

    static Stream<Named<Class<?>>> extractors() {
        return Stream.concat(
                        FormatCatalog.all().map(ArchiveFormat::extractor),
                        Stream.of(ArjArchiveExtractor.class, DumpArchiveExtractor.class))
                .distinct()
                .map(type -> named(type.getSimpleName(), type));
    }

    static Stream<Named<Class<?>>> creators() {
        return FormatCatalog.writable()
                .flatMap(format -> format.creator().stream())
                .distinct()
                .map(type -> named(type.getSimpleName(), type));
    }

    private static byte[] garbage() {
        byte[] bytes = new byte[2048];
        Arrays.fill(bytes, (byte) 0xFF);
        return bytes;
    }

    private static ArchiveExtractor.Builder<?, ?> extractorBuilder(Class<?> type, Class<?> parameter, Object argument)
            throws ReflectiveOperationException {
        var builder = (ArchiveExtractor.Builder<?, ?>)
                type.getMethod("builder", parameter).invoke(null, argument);
        return builder instanceof TarArchiveExtractor.Builder tar ? tar.compression(Compression.gzip()) : builder;
    }

    private static ArchiveCreator.Builder<?, ?> creatorBuilder(Class<?> type, Path target)
            throws ReflectiveOperationException {
        return (ArchiveCreator.Builder<?, ?>)
                type.getMethod("builder", Path.class).invoke(null, target);
    }

    @ParameterizedTest
    @MethodSource("extractors")
    void unbuiltExtractorBuilderOpensNothing(Class<?> type) throws ReflectiveOperationException, IOException {
        Path file = Files.write(dir.resolve("unbuilt.bin"), garbage());

        extractorBuilder(type, Path.class, file);

        assertDoesNotThrow(() -> Files.delete(file));
    }

    @ParameterizedTest
    @MethodSource("extractors")
    void corruptPathFailsTheBuildAndReleasesTheFile(Class<?> type) throws ReflectiveOperationException, IOException {
        assumeThat(VALIDATES_AT_BUILD).as(LAZY_REASON).contains(type);
        Path file = Files.write(dir.resolve("corrupt.bin"), garbage());
        var builder = extractorBuilder(type, Path.class, file);

        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);

        assertDoesNotThrow(() -> Files.delete(file));
    }

    @ParameterizedTest
    @MethodSource("extractors")
    void corruptPathIsReleasedWhenTheLazyExtractorCloses(Class<?> type)
            throws ReflectiveOperationException, IOException {
        assumeThat(VALIDATES_AT_BUILD)
                .as("the format fails in build(), covered above")
                .doesNotContain(type);
        Path file = Files.write(dir.resolve("lazy.bin"), garbage());

        extractorBuilder(type, Path.class, file).build().close();

        assertDoesNotThrow(() -> Files.delete(file));
    }

    @ParameterizedTest
    @MethodSource("extractors")
    void corruptChannelFailsTheBuildAndStaysOpen(Class<?> type) throws ReflectiveOperationException {
        assumeThat(VALIDATES_AT_BUILD).as(LAZY_REASON).contains(type);
        SeekableByteChannel channel = new SeekableInMemoryByteChannel(garbage());
        var builder = extractorBuilder(type, SeekableByteChannel.class, channel);

        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);

        assertThat(channel.isOpen()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("extractors")
    void missingPathFailsOnlyInBuild(Class<?> type) throws ReflectiveOperationException {
        var builder = extractorBuilder(type, Path.class, dir.resolve("missing.bin"));

        assertThatThrownBy(builder::build).isInstanceOf(NoSuchFileException.class);
    }

    @ParameterizedTest
    @MethodSource("creators")
    void unbuiltCreatorBuilderCreatesNoFile(Class<?> type) throws ReflectiveOperationException {
        Path target = dir.resolve("never." + type.getSimpleName());

        creatorBuilder(type, target);

        assertThat(target).doesNotExist();
    }

    @ParameterizedTest
    @MethodSource("creators")
    void creatorBuildInAMissingDirectoryFailsAndCreatesNothing(Class<?> type) throws ReflectiveOperationException {
        Path target = dir.resolve("missing").resolve("archive");
        var builder = creatorBuilder(type, target);

        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);

        assertThat(dir.resolve("missing")).doesNotExist();
    }

    @Test
    void failedCreatorBuildLeavesTheCallersStreamOpen() {
        var stream = new FailingStream();

        assertThatThrownBy(() -> TarArchiveCreator.builder(stream)
                        .compression(Compression.xz())
                        .build())
                .isInstanceOf(IOException.class);

        assertThat(stream.closed).isFalse();
    }

    private static final class FailingStream extends OutputStream {
        private boolean closed;

        @Override
        public void write(int b) throws IOException {
            throw new IOException("write failed");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
