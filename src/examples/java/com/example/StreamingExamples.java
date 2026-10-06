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
package com.example;

import com.hominux.compress4j.archivers.ArchiveItem;
import com.hominux.compress4j.archivers.EntrySource;
import com.hominux.compress4j.archivers.sevenz.SevenZArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.archivers.zip.ZipArchiveCreator;
import com.hominux.compress4j.archivers.zip.ZipArchiveExtractor;
import com.hominux.compress4j.compressors.Compression;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public final class StreamingExamples {

    private StreamingExamples() {}

    public static Optional<String> readOneFile(Path archive) throws IOException {
        // tag::read-one-file[]
        try (var extractor = TarArchiveExtractor.builder(archive).build()) {
            return extractor.stream()
                    .filter(item -> item.entry().name().equals("config/app.properties"))
                    .findFirst()
                    .map(item -> {
                        try {
                            return new String(item.content().readAllBytes(), StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        // end::read-one-file[]
    }

    public static void staleItem(Path archive) throws IOException {
        // tag::stale-item[]
        try (var extractor = TarArchiveExtractor.builder(archive).build()) {
            var items = extractor.stream().toList(); // advances past every entry
            items.getFirst().content(); // throws IllegalStateException: entry ... is no longer current
        }
        // end::stale-item[]
    }

    public static void writeFromWalk(Path dir, Path archive) throws IOException {
        // tag::write-from-walk[]
        try (var creator = TarArchiveCreator.builder(archive)
                        .compression(Compression.gzip())
                        .build();
                Stream<Path> paths = Files.walk(dir)) {
            creator.addAll(
                    paths.skip(1).filter(p -> !p.toString().endsWith(".tmp")).map(p -> {
                        try {
                            return EntrySource.of(dir, p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }));
        }
        // end::write-from-walk[]
    }

    public static void repack(Path tarGz, Path zip) throws IOException {
        // tag::repack[]
        try (var extractor = TarArchiveExtractor.builder(tarGz).build();
                var creator = ZipArchiveCreator.builder(zip).build()) {
            creator.addAll(extractor.stream().map(ArchiveItem::toSource));
        }
        // end::repack[]
    }

    public static void unknownSize(InputStream download, Path archive, Path tempDir) throws IOException {
        // tag::unknown-size[]
        var unsized = new EntrySource.File(
                "download.bin", 0, FileTime.from(Instant.now()), OptionalLong.empty(), () -> download);
        try (var creator = TarArchiveCreator.builder(archive)
                .compression(Compression.gzip())
                .build()) {
            creator.add(EntrySource.buffered(unsized, tempDir));
        }
        // end::unknown-size[]
    }

    public static void zipFromStream(InputStream download, Path outputDir) throws IOException {
        // tag::zip-streaming[]
        try (var extractor = ZipArchiveExtractor.streaming(download).build()) {
            extractor.extract(outputDir);
        }
        // end::zip-streaming[]
    }

    public static void sevenZFromStream(InputStream download, Path outputDir) throws IOException {
        // tag::sevenz-from-stream[]
        Files.createDirectories(outputDir);
        Path spool = Files.createTempFile(
                outputDir, "archive", ".7z"); // 7z needs random access, so spool the stream to disk
        try {
            Files.copy(download, spool, StandardCopyOption.REPLACE_EXISTING);
            try (var extractor = SevenZArchiveExtractor.builder(spool).build()) {
                extractor.extract(outputDir);
            }
        } finally {
            Files.deleteIfExists(spool);
        }
        // end::sevenz-from-stream[]
    }
}
