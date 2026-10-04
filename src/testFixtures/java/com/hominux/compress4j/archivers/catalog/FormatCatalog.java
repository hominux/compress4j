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
package com.hominux.compress4j.archivers.catalog;

import static com.hominux.compress4j.archivers.catalog.Capability.DIRECTORIES;
import static com.hominux.compress4j.archivers.catalog.Capability.LAST_MODIFIED;
import static com.hominux.compress4j.archivers.catalog.Capability.MODES;
import static com.hominux.compress4j.archivers.catalog.Capability.RANDOM_ACCESS_INPUT;
import static com.hominux.compress4j.archivers.catalog.Capability.REQUIRES_SIZE;
import static com.hominux.compress4j.archivers.catalog.Capability.STREAM_INPUT;
import static com.hominux.compress4j.archivers.catalog.Capability.STREAM_OUTPUT;
import static com.hominux.compress4j.archivers.catalog.Capability.SYMLINKS;

import com.hominux.compress4j.archivers.ArchiveCreator;
import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.archivers.ar.ArArchiveCreator;
import com.hominux.compress4j.archivers.ar.ArArchiveExtractor;
import com.hominux.compress4j.archivers.cpio.CpioArchiveCreator;
import com.hominux.compress4j.archivers.cpio.CpioArchiveExtractor;
import com.hominux.compress4j.archivers.sevenz.SevenZArchiveCreator;
import com.hominux.compress4j.archivers.sevenz.SevenZArchiveExtractor;
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
import com.hominux.compress4j.archivers.tar.TarZstdArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarZstdArchiveExtractor;
import com.hominux.compress4j.archivers.zip.ZipArchiveCreator;
import com.hominux.compress4j.archivers.zip.ZipArchiveExtractor;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.io.function.IOFunction;

/** Every archive format Compress4J supports, with the capabilities the contract suite verifies. */
public final class FormatCatalog {

    private static final Set<Capability> TAR =
            EnumSet.of(DIRECTORIES, MODES, SYMLINKS, LAST_MODIFIED, REQUIRES_SIZE, STREAM_INPUT, STREAM_OUTPUT);

    private FormatCatalog() {}

    public static Stream<ArchiveFormat> all() {
        return Stream.of(
                streamNative(
                        "tar",
                        TAR,
                        TarArchiveExtractor.class,
                        TarArchiveCreator.class,
                        p -> TarArchiveCreator.builder(p).build(),
                        o -> TarArchiveCreator.builder(o).build(),
                        ch -> TarArchiveCreator.builder(ch).build(),
                        p -> TarArchiveExtractor.builder(p).build(),
                        ch -> TarArchiveExtractor.builder(ch).build(),
                        i -> TarArchiveExtractor.builder(i).build()),
                streamNative(
                        "tar.gz",
                        TAR,
                        TarGzArchiveExtractor.class,
                        TarGzArchiveCreator.class,
                        p -> TarGzArchiveCreator.builder(p).build(),
                        o -> TarGzArchiveCreator.builder(o).build(),
                        ch -> TarGzArchiveCreator.builder(ch).build(),
                        p -> TarGzArchiveExtractor.builder(p).build(),
                        ch -> TarGzArchiveExtractor.builder(ch).build(),
                        i -> TarGzArchiveExtractor.builder(i).build()),
                streamNative(
                        "tar.bz2",
                        TAR,
                        TarBZip2ArchiveExtractor.class,
                        TarBZip2ArchiveCreator.class,
                        p -> TarBZip2ArchiveCreator.builder(p).build(),
                        o -> TarBZip2ArchiveCreator.builder(o).build(),
                        ch -> TarBZip2ArchiveCreator.builder(ch).build(),
                        p -> TarBZip2ArchiveExtractor.builder(p).build(),
                        ch -> TarBZip2ArchiveExtractor.builder(ch).build(),
                        i -> TarBZip2ArchiveExtractor.builder(i).build()),
                streamNative(
                        "tar.xz",
                        TAR,
                        TarXzArchiveExtractor.class,
                        TarXzArchiveCreator.class,
                        p -> TarXzArchiveCreator.builder(p).build(),
                        o -> TarXzArchiveCreator.builder(o).build(),
                        ch -> TarXzArchiveCreator.builder(ch).build(),
                        p -> TarXzArchiveExtractor.builder(p).build(),
                        ch -> TarXzArchiveExtractor.builder(ch).build(),
                        i -> TarXzArchiveExtractor.builder(i).build()),
                streamNative(
                        "tar.lzma",
                        TAR,
                        TarLzmaArchiveExtractor.class,
                        TarLzmaArchiveCreator.class,
                        p -> TarLzmaArchiveCreator.builder(p).build(),
                        o -> TarLzmaArchiveCreator.builder(o).build(),
                        ch -> TarLzmaArchiveCreator.builder(ch).build(),
                        p -> TarLzmaArchiveExtractor.builder(p).build(),
                        ch -> TarLzmaArchiveExtractor.builder(ch).build(),
                        i -> TarLzmaArchiveExtractor.builder(i).build()),
                streamNative(
                        "tar.lz4",
                        TAR,
                        TarLz4ArchiveExtractor.class,
                        TarLz4ArchiveCreator.class,
                        p -> TarLz4ArchiveCreator.builder(p).build(),
                        o -> TarLz4ArchiveCreator.builder(o).build(),
                        ch -> TarLz4ArchiveCreator.builder(ch).build(),
                        p -> TarLz4ArchiveExtractor.builder(p).build(),
                        ch -> TarLz4ArchiveExtractor.builder(ch).build(),
                        i -> TarLz4ArchiveExtractor.builder(i).build()),
                streamNative(
                        "tar.zst",
                        TAR,
                        TarZstdArchiveExtractor.class,
                        TarZstdArchiveCreator.class,
                        p -> TarZstdArchiveCreator.builder(p).build(),
                        o -> TarZstdArchiveCreator.builder(o).build(),
                        ch -> TarZstdArchiveCreator.builder(ch).build(),
                        p -> TarZstdArchiveExtractor.builder(p).build(),
                        ch -> TarZstdArchiveExtractor.builder(ch).build(),
                        i -> TarZstdArchiveExtractor.builder(i).build()),
                streamNative(
                        "ar",
                        EnumSet.of(MODES, SYMLINKS, LAST_MODIFIED, REQUIRES_SIZE, STREAM_INPUT, STREAM_OUTPUT),
                        ArArchiveExtractor.class,
                        ArArchiveCreator.class,
                        p -> ArArchiveCreator.builder(p).build(),
                        o -> ArArchiveCreator.builder(o).build(),
                        ch -> ArArchiveCreator.builder(ch).build(),
                        p -> ArArchiveExtractor.builder(p).build(),
                        ch -> ArArchiveExtractor.builder(ch).build(),
                        i -> ArArchiveExtractor.builder(i).build()),
                streamNative(
                        "cpio",
                        EnumSet.of(
                                DIRECTORIES,
                                MODES,
                                SYMLINKS,
                                LAST_MODIFIED,
                                REQUIRES_SIZE,
                                STREAM_INPUT,
                                STREAM_OUTPUT),
                        CpioArchiveExtractor.class,
                        CpioArchiveCreator.class,
                        p -> CpioArchiveCreator.builder(p).build(),
                        o -> CpioArchiveCreator.builder(o).build(),
                        ch -> CpioArchiveCreator.builder(ch).build(),
                        p -> CpioArchiveExtractor.builder(p).build(),
                        ch -> CpioArchiveExtractor.builder(ch).build(),
                        i -> CpioArchiveExtractor.builder(i).build()),
                new ArchiveFormat(
                        "zip",
                        EnumSet.of(DIRECTORIES, MODES, SYMLINKS, LAST_MODIFIED, STREAM_OUTPUT, RANDOM_ACCESS_INPUT),
                        ZipArchiveExtractor.class,
                        Optional.of(ZipArchiveCreator.class),
                        "builder",
                        Optional.empty(),
                        Optional.of(c(p -> ZipArchiveCreator.builder(p).build())),
                        Optional.of(ch -> ZipArchiveCreator.builder(ch).build()),
                        Optional.of(co(o -> ZipArchiveCreator.builder(o).build())),
                        x(p -> ZipArchiveExtractor.builder(p).build()),
                        Optional.of(ch -> ZipArchiveExtractor.builder(ch).build()),
                        Optional.empty()),
                new ArchiveFormat(
                        "zip-streaming",
                        EnumSet.of(DIRECTORIES, LAST_MODIFIED, STREAM_INPUT),
                        ZipArchiveExtractor.class,
                        Optional.empty(),
                        "streaming",
                        Optional.of("zip"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        x(p -> ZipArchiveExtractor.streaming(Files.newInputStream(p))
                                .build()),
                        Optional.empty(),
                        Optional.of(i -> ZipArchiveExtractor.streaming(i).build())),
                new ArchiveFormat(
                        "7z",
                        EnumSet.of(DIRECTORIES, MODES, SYMLINKS, LAST_MODIFIED, RANDOM_ACCESS_INPUT),
                        SevenZArchiveExtractor.class,
                        Optional.of(SevenZArchiveCreator.class),
                        "builder",
                        Optional.empty(),
                        Optional.of(c(p -> SevenZArchiveCreator.builder(p).build())),
                        Optional.of(ch -> SevenZArchiveCreator.builder(ch).build()),
                        Optional.empty(),
                        x(p -> SevenZArchiveExtractor.builder(p).build()),
                        Optional.of(ch -> SevenZArchiveExtractor.builder(ch).build()),
                        Optional.empty()));
    }

    public static Stream<ArchiveFormat> writable() {
        return all().filter(ArchiveFormat::writable);
    }

    /** Rows whose reader the round-trip contract can exercise: writable rows and rows naming a writer. */
    public static Stream<ArchiveFormat> readable() {
        return all().filter(f -> f.writable() || f.writer().isPresent());
    }

    /**
     * The writable row that produces archives for {@code format}: the row itself when writable, else its writer.
     *
     * @param format the row to read archives with
     * @return the writing row
     */
    public static ArchiveFormat writerOf(ArchiveFormat format) {
        return format.writer().map(FormatCatalog::named).orElse(format);
    }

    /**
     * The row named {@code name}.
     *
     * @param name the row name
     * @return the row
     */
    public static ArchiveFormat named(String name) {
        return all().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    private static ArchiveFormat streamNative(
            String name,
            Set<Capability> capabilities,
            Class<?> extractor,
            Class<?> creator,
            IOFunction<Path, ArchiveCreator<?>> createAt,
            IOFunction<SeekableByteChannel, ArchiveCreator<?>> createOnChannel,
            IOFunction<OutputStream, ArchiveCreator<?>> createOnStream,
            IOFunction<Path, ArchiveExtractor<?>> readAt,
            IOFunction<SeekableByteChannel, ArchiveExtractor<?>> readFromChannel,
            IOFunction<InputStream, ArchiveExtractor<?>> readFromStream) {
        return new ArchiveFormat(
                name,
                capabilities,
                extractor,
                Optional.of(creator),
                "builder",
                Optional.empty(),
                Optional.of(createAt),
                Optional.of(createOnChannel),
                Optional.of(createOnStream),
                readAt,
                Optional.of(readFromChannel),
                Optional.of(readFromStream));
    }

    private static IOFunction<Path, ArchiveCreator<?>> c(IOFunction<Path, ArchiveCreator<?>> f) {
        return f;
    }

    private static IOFunction<OutputStream, ArchiveCreator<?>> co(IOFunction<OutputStream, ArchiveCreator<?>> f) {
        return f;
    }

    private static IOFunction<Path, ArchiveExtractor<?>> x(IOFunction<Path, ArchiveExtractor<?>> f) {
        return f;
    }
}
