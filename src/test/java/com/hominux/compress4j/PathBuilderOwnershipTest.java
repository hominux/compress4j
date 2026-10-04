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
package com.hominux.compress4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.optional;

import com.hominux.compress4j.archivers.ar.ArArchiveCreator;
import com.hominux.compress4j.archivers.ar.ArArchiveExtractor;
import com.hominux.compress4j.archivers.cpio.CpioArchiveCreator;
import com.hominux.compress4j.archivers.cpio.CpioArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.compressors.bzip2.BZip2Compressor;
import com.hominux.compress4j.compressors.bzip2.BZip2Decompressor;
import com.hominux.compress4j.compressors.deflate.DeflateCompressor;
import com.hominux.compress4j.compressors.deflate.DeflateDecompressor;
import com.hominux.compress4j.compressors.gzip.GzipCompressor;
import com.hominux.compress4j.compressors.gzip.GzipDecompressor;
import com.hominux.compress4j.compressors.pack200.Pack200Compressor;
import com.hominux.compress4j.compressors.pack200.Pack200Decompressor;
import com.hominux.compress4j.compressors.xz.XZCompressor;
import com.hominux.compress4j.compressors.xz.XZDecompressor;
import com.hominux.compress4j.compressors.zstd.ZstdCompressor;
import com.hominux.compress4j.compressors.zstd.ZstdDecompressor;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PathBuilderOwnershipTest {

    @FunctionalInterface
    interface PathFactory {
        Object create(Path path) throws IOException;
    }

    @FunctionalInterface
    interface StreamFactory {
        Object create() throws IOException;
    }

    record Case(String name, PathFactory fromPath, StreamFactory fromStream) {
        @Override
        public String toString() {
            return name;
        }
    }

    @TempDir
    Path tempDir;

    private static OutputStream out() {
        return OutputStream.nullOutputStream();
    }

    private static InputStream in() {
        return InputStream.nullInputStream();
    }

    static Stream<Case> cases() {
        return Stream.of(
                new Case("BZip2Compressor", BZip2Compressor::builder, () -> BZip2Compressor.builder(out())),
                new Case("DeflateCompressor", DeflateCompressor::builder, () -> DeflateCompressor.builder(out())),
                new Case("GzipCompressor", GzipCompressor::builder, () -> GzipCompressor.builder(out())),
                new Case("Pack200Compressor", Pack200Compressor::builder, () -> Pack200Compressor.builder(out())),
                new Case("XZCompressor", XZCompressor::builder, () -> XZCompressor.builder(out())),
                new Case("ZstdCompressor", ZstdCompressor::builder, () -> ZstdCompressor.builder(out())),
                new Case("BZip2Decompressor", BZip2Decompressor::builder, () -> BZip2Decompressor.builder(in())),
                new Case("DeflateDecompressor", DeflateDecompressor::builder, () -> DeflateDecompressor.builder(in())),
                new Case("GzipDecompressor", GzipDecompressor::builder, () -> GzipDecompressor.builder(in())),
                new Case("Pack200Decompressor", Pack200Decompressor::builder, () -> Pack200Decompressor.builder(in())),
                new Case("XZDecompressor", XZDecompressor::builder, () -> XZDecompressor.builder(in())),
                new Case("ZstdDecompressor", ZstdDecompressor::builder, () -> ZstdDecompressor.builder(in())),
                new Case("TarArchiveCreator", TarArchiveCreator::builder, () -> TarArchiveCreator.builder(out())),
                new Case("TarArchiveExtractor", TarArchiveExtractor::builder, () -> TarArchiveExtractor.builder(in())),
                new Case("ArArchiveCreator", ArArchiveCreator::builder, () -> ArArchiveCreator.builder(out())),
                new Case("ArArchiveExtractor", ArArchiveExtractor::builder, () -> ArArchiveExtractor.builder(in())),
                new Case("CpioArchiveCreator", CpioArchiveCreator::builder, () -> CpioArchiveCreator.builder(out())),
                new Case(
                        "CpioArchiveExtractor",
                        CpioArchiveExtractor::builder,
                        () -> CpioArchiveExtractor.builder(in())));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void shouldOwnTheStreamTheBuilderHolds(Case testCase) throws IOException {
        var path = Files.createFile(tempDir.resolve("stream.bin"));

        var builder = testCase.fromPath().create(path);

        assertThat(builder)
                .extracting("ownedStream", optional(Closeable.class))
                .containsSame(Closeable.class.cast(heldStream(builder)))
                .hasValueSatisfying((ThrowingConsumer<Closeable>) Closeable::close);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void shouldNotOwnCallerSuppliedStream(Case testCase) throws IOException {
        var builder = testCase.fromStream().create();

        assertThat(builder).extracting("ownedStream", optional(Closeable.class)).isEmpty();
    }

    @Test
    void shouldCloseOpenedStreamWhenDecompressorBuildFails() throws IOException {
        var path = Files.writeString(tempDir.resolve("garbage.gz"), "not gzip");
        var builder = GzipDecompressor.builder(path);

        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);

        assertThatThrownBy(() -> InputStream.class.cast(heldStream(builder)).read())
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldCloseOpenedStreamWhenArchiveExtractorBuildFails() throws IOException {
        var path = Files.writeString(tempDir.resolve("garbage.tar.gz"), "not gzip");
        var builder = TarArchiveExtractor.builder(path).compression(Compression.gzip());

        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);

        assertThatThrownBy(() -> InputStream.class.cast(heldStream(builder)).read())
                .isInstanceOf(IOException.class);
    }

    private static Object heldStream(Object builder) {
        return fieldValue(builder, "outputStream")
                .or(() -> fieldValue(builder, "inputStream"))
                .or(() -> fieldValue(builder, "cpioInputStreamBuilder").flatMap(b -> fieldValue(b, "inputStream")))
                .orElseThrow();
    }

    private static Optional<Object> fieldValue(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return Optional.ofNullable(field.get(target));
            } catch (NoSuchFieldException e) {
                continue;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return Optional.empty();
    }
}
