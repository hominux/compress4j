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

import com.hominux.compress4j.compressors.catalog.CodecCatalog;
import com.hominux.compress4j.compressors.catalog.CodecFormat;
import com.hominux.compress4j.test.util.Corruptions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CorruptCompressedDataTest {

    private static final int FLIPS = 60;
    private static final byte[] TEXT = "corrupt me ".repeat(300).getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    static Stream<CodecFormat> codecs() {
        return CodecCatalog.writable().filter(format -> !format.name().equals("pack200"));
    }

    private static byte[] compress(CodecFormat format) throws IOException {
        Compression compression = format.compression() instanceof Compression.SnappyRaw raw
                ? raw.uncompressedSize(TEXT.length)
                : format.compression();
        var bytes = new ByteArrayOutputStream();
        try (var compressor = Compressor.builder(bytes, compression).build()) {
            compressor.write(new ByteArrayInputStream(TEXT));
        }
        return bytes.toByteArray();
    }

    private static void read(byte[] bytes, CodecFormat format, boolean detect) throws IOException {
        var in = new ByteArrayInputStream(bytes);
        var builder = detect ? Decompressor.builder(in) : Decompressor.builder(in, format.compression());
        try (var decompressor = builder.build()) {
            decompressor.inputStream().readAllBytes();
        }
    }

    static Stream<CodecFormat> readOnlyCodecs() {
        return CodecCatalog.all()
                .filter(format -> !format.compression().canWrite())
                .filter(format -> !format.name().equals("pack200"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecs")
    void corruptDataOnlyThrowsIOException(CodecFormat format) throws IOException {
        assertThat(failures(format, Corruptions.variantsOf(compress(format), FLIPS)))
                .as("seed %d", Corruptions.SEED)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("readOnlyCodecs")
    void garbageForReadOnlyCodecsOnlyThrowsIOException(CodecFormat format) {
        byte[] magic = format.compression() instanceof Compression.UnixZ ? new byte[] {0x1F, (byte) 0x9D} : new byte[0];
        assertThat(failures(format, Corruptions.garbageAfter(magic, 512, FLIPS)))
                .as("seed %d", Corruptions.SEED)
                .isEmpty();
    }

    private List<String> failures(CodecFormat format, List<byte[]> variants) {
        var failures = new ArrayList<String>();
        for (int index = 0; index < variants.size(); index++) {
            byte[] bytes = variants.get(index);
            Path target = dir.resolve("out-" + index);
            recordFailure(format, "read", index, failures, () -> read(bytes, format, false));
            recordFailure(format, "detect", index, failures, () -> read(bytes, format, true));
            recordFailure(format, "write", index, failures, () -> write(bytes, format, target));
        }
        return failures;
    }

    private static void write(byte[] bytes, CodecFormat format, Path target) throws IOException {
        try (var decompressor = Decompressor.builder(new ByteArrayInputStream(bytes), format.compression())
                .build()) {
            decompressor.write(target);
        }
    }

    private static void recordFailure(
            CodecFormat format, String path, int variant, List<String> failures, Corruptions.Action action) {
        Corruptions.nonIoFailure(action)
                .ifPresent(e -> failures.add(format.name() + " " + path + " variant " + variant + ": " + e));
    }
}
