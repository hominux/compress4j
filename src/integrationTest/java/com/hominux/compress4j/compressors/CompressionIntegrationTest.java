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

import com.hominux.compress4j.UpstreamSamples;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.compressors.catalog.CodecCatalog;
import com.hominux.compress4j.compressors.catalog.CodecFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CompressionIntegrationTest {

    private static final String SOURCE_TEXT = "compressMe";
    private static final String SAMPLE_TEXT = "this is the decompressed textfile\n";

    @TempDir
    Path tempDir;

    static Stream<CodecFormat> sampled() {
        return CodecCatalog.all().filter(format -> format.sample().isPresent());
    }

    static Stream<CodecFormat> sampledWritable() {
        return sampled().filter(format -> format.compression().canWrite());
    }

    private Path decompressToFile(Path compressed, Compression compression, String name) throws IOException {
        Path target = tempDir.resolve(name);
        try (var decompressor = Decompressor.builder(compressed, compression).build()) {
            decompressor.write(target);
        }
        return target;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sampledWritable")
    void compressesAndDecompressesAFile(CodecFormat format) throws IOException {
        Path source = Files.writeString(tempDir.resolve("source.txt"), SOURCE_TEXT);
        Path compressed = tempDir.resolve("round." + format.name());
        try (var compressor =
                Compressor.builder(compressed, format.compression()).build()) {
            compressor.write(source);
        }

        Path restored = decompressToFile(compressed, format.compression(), "restored.txt");

        assertThat(restored).hasContent(SOURCE_TEXT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sampled")
    void decompressesTheCommittedSample(CodecFormat format) throws IOException {
        Path sample = UpstreamSamples.copy("/compression/" + format.sample().orElseThrow(), tempDir);

        Path restored = decompressToFile(sample, format.compression(), "sample.txt");

        assertThat(restored).hasContent(SAMPLE_TEXT);
    }

    @Test
    void decompressesDeflate64() throws IOException {
        Path sample = UpstreamSamples.copy("/compression/lorem.deflate64", tempDir);

        Path restored = decompressToFile(sample, Compression.deflate64(), "lorem.txt");

        assertThat(Files.size(restored)).isEqualTo(144_060);
    }

    @Test
    void decompressesBrotli() throws IOException {
        Path sample = UpstreamSamples.copy("/compression/upstream-brotli.testdata.compressed", tempDir);
        Path expected = UpstreamSamples.copy("/compression/upstream-brotli.testdata.uncompressed", tempDir);

        Path restored = decompressToFile(sample, Compression.brotli(), "brotli.txt");

        assertThat(Files.readAllBytes(restored)).isEqualTo(Files.readAllBytes(expected));
    }

    private static final byte[] TEXT = "contract ".repeat(200).getBytes(StandardCharsets.UTF_8);
    private static final Map<String, String> READ_ONLY_SAMPLES = Map.of(
            "brotli", "/compression/upstream-brotli.testdata.compressed",
            "deflate64", "/compression/lorem.deflate64",
            "z", "/archives/upstream-bla.tar.Z");

    static Stream<CodecFormat> readable() {
        return CodecCatalog.all().filter(format -> !format.name().equals("pack200"));
    }

    private static byte[] compressedBytes(CodecFormat format) throws IOException {
        if (!format.compression().canWrite()) {
            try (var in = CompressionIntegrationTest.class.getResourceAsStream(READ_ONLY_SAMPLES.get(format.name()))) {
                return in.readAllBytes();
            }
        }
        Compression compression = format.compression() instanceof Compression.SnappyRaw raw
                ? raw.uncompressedSize(TEXT.length)
                : format.compression();
        var bytes = new ByteArrayOutputStream();
        try (var compressor = Compressor.builder(bytes, compression).build()) {
            compressor.write(new ByteArrayInputStream(TEXT));
        }
        return bytes.toByteArray();
    }

    private static byte[] readAll(byte[] compressed, Compression compression) throws IOException {
        try (var decompressor = Decompressor.builder(new ByteArrayInputStream(compressed), compression)
                .build()) {
            return decompressor.inputStream().readAllBytes();
        }
    }

    private static byte[] gzipWrapped(byte[] payload) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(payload);
        }
        return bytes.toByteArray();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("readable")
    void truncatedInputFailsOrEndsEarlyAsTheCatalogStates(CodecFormat format) throws IOException {
        byte[] compressed = compressedBytes(format);
        byte[] half = Arrays.copyOf(compressed, compressed.length / 2);

        if (format.reportsTruncation()) {
            assertThatThrownBy(() -> readAll(half, format.compression())).isInstanceOf(IOException.class);
        } else {
            assertThat(readAll(half, format.compression()))
                    .hasSizeLessThan(readAll(compressed, format.compression()).length);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("readable")
    void plainTextInputFails(CodecFormat format) {
        byte[] foreign = "plain text no codec accepts, long enough".repeat(5).getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> readAll(foreign, format.compression())).isInstanceOf(IOException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("readable")
    void wrongFormatInputFails(CodecFormat format) throws IOException {
        Assumptions.assumeFalse(format.name().equals("gzip"));
        byte[] foreign = gzipWrapped(TEXT);

        assertThatThrownBy(() -> readAll(foreign, format.compression())).isInstanceOf(IOException.class);
    }

    @Test
    void xzAndLzmaReadWithDefaultLimit() throws IOException {
        for (Compression compression : List.of(Compression.xz(), Compression.lzma())) {
            var bytes = new ByteArrayOutputStream();
            try (var compressor = Compressor.builder(bytes, compression).build()) {
                compressor.write(new ByteArrayInputStream(TEXT));
            }
            assertThat(readAll(bytes.toByteArray(), compression)).isEqualTo(TEXT);
        }
    }

    @Test
    void explicitMemoryLimitIsHonoured() throws IOException {
        var packed = new ByteArrayOutputStream();
        try (var compressor = Compressor.builder(packed, Compression.xz()).build()) {
            compressor.write(new ByteArrayInputStream(TEXT));
        }

        assertThatThrownBy(() -> readAll(packed.toByteArray(), Compression.xz().memoryLimitKiB(1)))
                .isInstanceOf(IOException.class);
        assertThat(readAll(packed.toByteArray(), Compression.xz().memoryLimitKiB(Integer.MAX_VALUE)))
                .isEqualTo(TEXT);
    }

    @Test
    void detectsUnixZWithoutBeingTold() throws IOException {
        Path tar = tempDir.resolve("bla.tar");
        Path sample = UpstreamSamples.copy("/archives/upstream-bla.tar.Z", tempDir);
        try (var decompressor = Decompressor.builder(sample).build()) {
            decompressor.write(tar);
        }

        assertThat(tar).isNotEmptyFile();
        assertThat(Files.readString(tar, StandardCharsets.ISO_8859_1)).contains("test1.xml", "test2.xml");
    }

    @Test
    void extractsTheUpstreamSnappyTar() throws IOException {
        Path sample = UpstreamSamples.copy("/archives/upstream-bla.tar.sz", tempDir);
        Path tar = tempDir.resolve("bla.tar");
        try (var decompressor = Decompressor.builder(sample).build()) {
            decompressor.write(tar);
        }
        Path out = Files.createDirectory(tempDir.resolve("out"));

        try (var extractor = TarArchiveExtractor.builder(tar).build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("test1.xml")).isNotEmptyFile();
        assertThat(out.resolve("test2.xml")).isNotEmptyFile();
    }

    @Test
    void roundTripsAJarThroughPack200() throws IOException {
        Path packed = tempDir.resolve("test.pack");
        try (var compressor = Compressor.builder(packed, Compression.pack200()).build()) {
            compressor.write(new ByteArrayInputStream(jar()));
        }
        Path restored = tempDir.resolve("restored.jar");
        try (var decompressor =
                Decompressor.builder(packed, Compression.pack200()).build()) {
            decompressor.write(restored);
        }

        try (var jar = new JarInputStream(Files.newInputStream(restored))) {
            assertThat(jar.getNextJarEntry().getName()).isEqualTo("test.txt");
            assertThat(jar.readAllBytes()).isEqualTo(JAR_CONTENT);
        }
    }

    private static final byte[] JAR_CONTENT = "Test content for Pack200 compression".getBytes(StandardCharsets.UTF_8);

    private static byte[] jar() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) {
            var entry = new JarEntry("test.txt");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(JAR_CONTENT.length);
            entry.setCompressedSize(JAR_CONTENT.length);
            var crc = new CRC32();
            crc.update(JAR_CONTENT);
            entry.setCrc(crc.getValue());
            jar.putNextEntry(entry);
            jar.write(JAR_CONTENT);
            jar.closeEntry();
        }
        return bytes.toByteArray();
    }
}
