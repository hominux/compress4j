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
package com.example.archivers.tar;

import static com.hominux.compress4j.archivers.ArchiveExtractor.ErrorHandlerChoice.RETRY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.zip.Deflater.BEST_COMPRESSION;
import static java.util.zip.Deflater.HUFFMAN_ONLY;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.BIGNUMBER_ERROR;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.BIGNUMBER_POSIX;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_GNU;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_POSIX;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarBZip2ArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarBZip2ArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarGzArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarGzArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarLz4ArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarLz4ArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarLzmaArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarLzmaArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarXzArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarXzArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarZArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarZstdArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarZstdArchiveExtractor;
import java.io.IOException;
import java.nio.file.Path;
import org.tukaani.xz.LZMA2Options;

@SuppressWarnings({"java:S1192", "unused"})
public class TarExamples {

    private TarExamples() {
        /* no-op */
    }

    public static void tarCreator() throws IOException {
        // tag::tar-creator[]
        try (TarArchiveCreator tarCreator = TarArchiveCreator.builder(Path.of("example.tar"))
                .blockSize(1024)
                .encoding(UTF_8.name())
                .addPaxHeadersForNonAsciiNames(true)
                .bigNumberMode(BIGNUMBER_ERROR)
                .longFileMode(LONGFILE_GNU)
                .filter(s -> !s.name().endsWith("some_file.txt"))
                .build()) {
            tarCreator.addDirectoryRecursively(Path.of("exampleDir"));
            tarCreator.addFile(Path.of("path/to/file.txt"));
        }
        // end::tar-creator[]
    }

    public static void tarExtractor() throws IOException {
        // tag::tar-extractor[]
        try (TarArchiveExtractor tarExtractor = TarArchiveExtractor.builder(Path.of("example.tar"))
                .filter(entry -> !entry.name().startsWith("bad"))
                .errorHandler((entry, exception) -> RETRY)
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .postProcessor((entry, exception) -> {})
                .stripComponents(1)
                .overwrite(true)
                .build()) {
            tarExtractor.extract(Path.of("outputDir"));
        }
        // end::tar-extractor[]
    }

    public static void tarGzCreator() throws IOException {
        // tag::tar-gz-creator[]
        try (TarGzArchiveCreator tarGzCreator = TarGzArchiveCreator.builder(Path.of("example.tar.gz"))
                .compressorOutputStreamBuilder()
                .bufferSize(1024)
                .compressionLevel(BEST_COMPRESSION)
                .comment("comment")
                .deflateStrategy(HUFFMAN_ONLY)
                .operatingSystem(0)
                .parentBuilder()
                .longFileMode(LONGFILE_POSIX)
                .bigNumberMode(BIGNUMBER_POSIX)
                .blockSize(1024)
                .encoding(UTF_8.name())
                .addPaxHeadersForNonAsciiNames(true)
                .filter(s -> !s.name().endsWith("some_file.txt"))
                .build()) {
            tarGzCreator.addDirectoryRecursively(Path.of("exampleDir"));
            tarGzCreator.addFile(Path.of("path/to/file.txt"));
        }
        // end::tar-gz-creator[]
    }

    public static void tarGzExtractor() throws IOException {
        // tag::tar-gz-extractor[]
        try (TarGzArchiveExtractor tarGzExtractor = TarGzArchiveExtractor.builder(Path.of("example.tar.gz"))
                .filter(entry -> !entry.name().startsWith("bad"))
                .errorHandler((entry, exception) -> RETRY)
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .postProcessor((entry, exception) -> {})
                .stripComponents(1)
                .overwrite(true)
                .build()) {
            tarGzExtractor.extract(Path.of("outputDir"));
        }
        // end::tar-gz-extractor[]
    }

    public static void tarBzip2Creator() throws IOException {
        // tag::tar-bzip2-creator[]
        try (TarBZip2ArchiveCreator tarBzip2Creator = TarBZip2ArchiveCreator.builder(Path.of("example.tar.bz2"))
                .compressorOutputStreamBuilder()
                .blockSize(1024)
                .parentBuilder()
                .blockSize(1024)
                .encoding(UTF_8.name())
                .addPaxHeadersForNonAsciiNames(true)
                .bigNumberMode(BIGNUMBER_ERROR)
                .longFileMode(LONGFILE_GNU)
                .filter(s -> !s.name().endsWith("some_file.txt"))
                .build()) {
            tarBzip2Creator.addDirectoryRecursively(Path.of("exampleDir"));
            tarBzip2Creator.addFile(Path.of("path/to/file.txt"));
        }
        // end::tar-bzip2-creator[]
    }

    public static void tarBzip2Extractor() throws IOException {
        // tag::tar-bzip2-extractor[]
        try (TarBZip2ArchiveExtractor tarBzip2Extractor = TarBZip2ArchiveExtractor.builder(Path.of("example.tar.bz2"))
                .filter(entry -> !entry.name().startsWith("bad"))
                .errorHandler((entry, exception) -> RETRY)
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .postProcessor((entry, exception) -> {})
                .stripComponents(1)
                .overwrite(true)
                .build()) {
            tarBzip2Extractor.extract(Path.of("outputDir"));
        }
        // end::tar-bzip2-extractor[]
    }

    public static void tarXzCreator() throws IOException {
        // tag::tar-xz-creator[]
        try (TarXzArchiveCreator tarXzCreator = TarXzArchiveCreator.builder(Path.of("example.tar.xz"))
                .compressorOutputStreamBuilder()
                .lzma2Options(new LZMA2Options())
                .parentBuilder()
                .blockSize(1024)
                .encoding(UTF_8.name())
                .addPaxHeadersForNonAsciiNames(true)
                .bigNumberMode(BIGNUMBER_ERROR)
                .longFileMode(LONGFILE_GNU)
                .filter(s -> !s.name().endsWith("some_file.txt"))
                .build()) {
            tarXzCreator.addDirectoryRecursively(Path.of("exampleDir"));
            tarXzCreator.addFile(Path.of("path/to/file.txt"));
        }
        // end::tar-xz-creator[]
    }

    public static void tarXzExtractor() throws IOException {
        // tag::tar-xz-extractor[]
        try (TarXzArchiveExtractor tarXzExtractor = TarXzArchiveExtractor.builder(Path.of("example.tar.xz"))
                .filter(entry -> !entry.name().startsWith("bad"))
                .errorHandler((entry, exception) -> RETRY)
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .postProcessor((entry, exception) -> {})
                .stripComponents(1)
                .overwrite(true)
                .build()) {
            tarXzExtractor.extract(Path.of("outputDir"));
        }
        // end::tar-xz-extractor[]
    }

    public static void tarZstdCreator() throws IOException {
        // tag::tar-zstd-creator[]
        try (TarZstdArchiveCreator tarZstdCreator = TarZstdArchiveCreator.builder(Path.of("example.tar.zst"))
                .compressorOutputStreamBuilder()
                .level(6)
                .parentBuilder()
                .blockSize(1024)
                .encoding(UTF_8.name())
                .addPaxHeadersForNonAsciiNames(true)
                .bigNumberMode(BIGNUMBER_ERROR)
                .longFileMode(LONGFILE_GNU)
                .filter(s -> !s.name().endsWith("some_file.txt"))
                .build()) {
            tarZstdCreator.addDirectoryRecursively(Path.of("exampleDir"));
            tarZstdCreator.addFile(Path.of("path/to/file.txt"));
        }
        // end::tar-zstd-creator[]
    }

    public static void tarZstdExtractor() throws IOException {
        // tag::tar-zstd-extractor[]
        try (TarZstdArchiveExtractor tarZstdExtractor = TarZstdArchiveExtractor.builder(Path.of("example.tar.zst"))
                .filter(entry -> !entry.name().startsWith("bad"))
                .errorHandler((entry, exception) -> RETRY)
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .postProcessor((entry, exception) -> {})
                .stripComponents(1)
                .overwrite(true)
                .build()) {
            tarZstdExtractor.extract(Path.of("outputDir"));
        }
        // end::tar-zstd-extractor[]
    }

    public static void tarLzmaCreator() throws IOException {
        // tag::tar-lzma-creator[]
        try (TarLzmaArchiveCreator tarLzmaCreator = TarLzmaArchiveCreator.builder(Path.of("example.tar.lzma"))
                .blockSize(1024)
                .encoding(UTF_8.name())
                .addPaxHeadersForNonAsciiNames(true)
                .bigNumberMode(BIGNUMBER_ERROR)
                .longFileMode(LONGFILE_GNU)
                .filter(s -> !s.name().endsWith("some_file.txt"))
                .build()) {
            tarLzmaCreator.addDirectoryRecursively(Path.of("exampleDir"));
            tarLzmaCreator.addFile(Path.of("path/to/file.txt"));
        }
        // end::tar-lzma-creator[]
    }

    public static void tarLzmaExtractor() throws IOException {
        // tag::tar-lzma-extractor[]
        try (TarLzmaArchiveExtractor tarLzmaExtractor = TarLzmaArchiveExtractor.builder(Path.of("example.tar.lzma"))
                .filter(entry -> !entry.name().startsWith("bad"))
                .errorHandler((entry, exception) -> RETRY)
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .postProcessor((entry, exception) -> {})
                .stripComponents(1)
                .overwrite(true)
                .build()) {
            tarLzmaExtractor.extract(Path.of("outputDir"));
        }
        // end::tar-lzma-extractor[]
    }

    public static void tarLz4Creator() throws IOException {
        // tag::tar-lz4-creator[]
        try (TarLz4ArchiveCreator tarLz4Creator = TarLz4ArchiveCreator.builder(Path.of("example.tar.lz4"))
                .blockSize(1024)
                .encoding(UTF_8.name())
                .addPaxHeadersForNonAsciiNames(true)
                .bigNumberMode(BIGNUMBER_ERROR)
                .longFileMode(LONGFILE_GNU)
                .filter(s -> !s.name().endsWith("some_file.txt"))
                .build()) {
            tarLz4Creator.addDirectoryRecursively(Path.of("exampleDir"));
            tarLz4Creator.addFile(Path.of("path/to/file.txt"));
        }
        // end::tar-lz4-creator[]
    }

    public static void tarLz4Extractor() throws IOException {
        // tag::tar-lz4-extractor[]
        try (TarLz4ArchiveExtractor tarLz4Extractor = TarLz4ArchiveExtractor.builder(Path.of("example.tar.lz4"))
                .filter(entry -> !entry.name().startsWith("bad"))
                .errorHandler((entry, exception) -> RETRY)
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .postProcessor((entry, exception) -> {})
                .stripComponents(1)
                .overwrite(true)
                .build()) {
            tarLz4Extractor.extract(Path.of("outputDir"));
        }
        // end::tar-lz4-extractor[]
    }

    public static void tarZExtractor() throws IOException {
        // tag::tar-z-extractor[]
        try (TarZArchiveExtractor tarZExtractor = TarZArchiveExtractor.builder(Path.of("example.tar.Z"))
                .filter(entry -> !entry.name().startsWith("bad"))
                .errorHandler((entry, exception) -> RETRY)
                .escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy.DISALLOW)
                .postProcessor((entry, exception) -> {})
                .stripComponents(1)
                .overwrite(true)
                .build()) {
            tarZExtractor.extract(Path.of("outputDir"));
        }
        // end::tar-z-extractor[]
    }
}
