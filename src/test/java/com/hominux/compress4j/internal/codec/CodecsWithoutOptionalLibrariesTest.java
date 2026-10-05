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
package com.hominux.compress4j.internal.codec;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.exceptions.MissingArchiveDependencyException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CodecsWithoutOptionalLibrariesTest {

    private static final byte[] TEXT = "hello hello hello".getBytes(StandardCharsets.UTF_8);

    private static byte[] gzipped() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (OutputStream out = Codecs.compressing(Compression.gzip(), bytes)) {
            out.write(TEXT);
        }
        return bytes.toByteArray();
    }

    private static void assertGzipStillWorks(LibraryHidingLoader loader) throws Exception {
        try (var in = loader.decompressing(loader.compression("gzip"), new ByteArrayInputStream(gzipped()))) {
            assertThat(in.readAllBytes()).isEqualTo(TEXT);
        }
    }

    private static void assertMissing(Throwable failure, String library) {
        assertThat(failure.getClass().getName()).isEqualTo(MissingArchiveDependencyException.class.getName());
        assertThat(failure.getMessage()).contains(library);
    }

    private static Throwable writeFailure(LibraryHidingLoader loader, String factory) throws Exception {
        try (var out = loader.compressing(loader.compression(factory), new ByteArrayOutputStream())) {
            out.write(1);
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private static Throwable readFailure(LibraryHidingLoader loader, String factory) throws Exception {
        try (var in = loader.decompressing(loader.compression(factory), new ByteArrayInputStream(new byte[16]))) {
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    @Test
    void withoutXzOnlyXzAndLzmaFail() throws Exception {
        try (var loader = new LibraryHidingLoader("org.tukaani.")) {
            assertGzipStillWorks(loader);
            assertMissing(writeFailure(loader, "xz"), "XZ");
            assertMissing(readFailure(loader, "xz"), "XZ");
            assertMissing(writeFailure(loader, "lzma"), "LZMA");
            assertMissing(readFailure(loader, "lzma"), "LZMA");
        }
    }

    @Test
    void withoutZstdOnlyZstdFails() throws Exception {
        try (var loader = new LibraryHidingLoader("com.github.luben.")) {
            assertGzipStillWorks(loader);
            assertMissing(writeFailure(loader, "zstd"), "Zstandard");
            assertMissing(readFailure(loader, "zstd"), "Zstandard");
        }
    }

    @Test
    void withoutBrotliOnlyBrotliFails() throws Exception {
        try (var loader = new LibraryHidingLoader("org.brotli.")) {
            assertGzipStillWorks(loader);
            assertMissing(readFailure(loader, "brotli"), "Brotli");
        }
    }

    @Test
    void withoutAsmPack200WritingNamesTheLibrary() throws Exception {
        try (var loader = new LibraryHidingLoader("org.objectweb.asm.")) {
            assertGzipStillWorks(loader);
            Throwable failure = writeFailure(loader, "pack200");
            assertMissing(failure, "Pack200");
            assertThat(failure.getMessage()).contains("ASM").contains("https://asm.ow2.io/");
        }
    }
}
