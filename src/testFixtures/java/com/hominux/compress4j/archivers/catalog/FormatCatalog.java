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

import com.hominux.compress4j.archivers.LegacyArchiveCreator;
import com.hominux.compress4j.archivers.LegacyArchiveCreator.ArchiveCreatorBuilder;
import com.hominux.compress4j.archivers.LegacyArchiveExtractor;
import com.hominux.compress4j.archivers.ar.ArArchiveCreator;
import com.hominux.compress4j.archivers.ar.ArArchiveExtractor;
import com.hominux.compress4j.archivers.catalog.ArchiveFormat.Reader;
import com.hominux.compress4j.archivers.cpio.CpioArchiveCreator;
import com.hominux.compress4j.archivers.cpio.CpioArchiveExtractor;
import com.hominux.compress4j.archivers.sevenz.SevenZArchiveCreator;
import com.hominux.compress4j.archivers.sevenz.SevenZArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarArchiveCreator;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.archivers.zip.ZipArchiveCreator;
import com.hominux.compress4j.archivers.zip.ZipArchiveExtractor;
import com.hominux.compress4j.compressors.Compression;
import java.io.IOException;
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

/** The archive formats the contract suite exercises, with their capabilities. */
public final class FormatCatalog {

    private static final Set<Capability> TAR =
            EnumSet.of(DIRECTORIES, MODES, SYMLINKS, LAST_MODIFIED, REQUIRES_SIZE, STREAM_INPUT, STREAM_OUTPUT);

    private FormatCatalog() {}

    public static Stream<ArchiveFormat> all() {
        return Stream.of(
                tar("tar", Compression.none(), false),
                tar("tar.gz", Compression.gzip(), false),
                tar("tar.bz2", Compression.bzip2(), false),
                tar("tar.xz", Compression.xz(), false),
                tar("tar.lzma", Compression.lzma(), true),
                tar("tar.lz4", Compression.lz4Framed(), false),
                tar("tar.zst", Compression.zstd(), false),
                streamNative(
                        "ar",
                        EnumSet.of(MODES, SYMLINKS, LAST_MODIFIED, REQUIRES_SIZE, STREAM_INPUT, STREAM_OUTPUT),
                        ArArchiveExtractor.class,
                        ArArchiveCreator.class,
                        ArArchiveCreator::builder,
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
                        CpioArchiveCreator::builder,
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
                        Optional.of(lb(ZipArchiveCreator::builder)),
                        Optional.of(lw((SeekableByteChannel ch) ->
                                ZipArchiveCreator.builder(ch).build())),
                        Optional.of(lw(
                                (OutputStream o) -> ZipArchiveCreator.builder(o).build())),
                        lr((Path p) -> ZipArchiveExtractor.builder(p).build()),
                        Optional.of(lr((SeekableByteChannel ch) ->
                                ZipArchiveExtractor.builder(ch).build())),
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
                        lr((Path p) -> ZipArchiveExtractor.streaming(Files.newInputStream(p))
                                .build()),
                        Optional.empty(),
                        Optional.of(lr((InputStream i) ->
                                ZipArchiveExtractor.streaming(i).build()))),
                new ArchiveFormat(
                        "7z",
                        EnumSet.of(DIRECTORIES, MODES, SYMLINKS, LAST_MODIFIED, RANDOM_ACCESS_INPUT),
                        SevenZArchiveExtractor.class,
                        Optional.of(SevenZArchiveCreator.class),
                        "builder",
                        Optional.empty(),
                        Optional.of(lb(SevenZArchiveCreator::builder)),
                        Optional.of(lw((SeekableByteChannel ch) ->
                                SevenZArchiveCreator.builder(ch).build())),
                        Optional.empty(),
                        lr((Path p) -> SevenZArchiveExtractor.builder(p).build()),
                        Optional.of(lr((SeekableByteChannel ch) ->
                                SevenZArchiveExtractor.builder(ch).build())),
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

    private static ArchiveFormat tar(String name, Compression compression, boolean explicitOnRead) {
        return new ArchiveFormat(
                name,
                TAR,
                TarArchiveExtractor.class,
                Optional.of(TarArchiveCreator.class),
                "builder",
                Optional.empty(),
                Optional.of((path, filter) -> ArchiveFormat.writer(TarArchiveCreator.builder(path)
                        .compression(compression)
                        .filter(filter)
                        .build())),
                Optional.of(ch -> ArchiveFormat.writer(
                        TarArchiveCreator.builder(ch).compression(compression).build())),
                Optional.of(o -> ArchiveFormat.writer(
                        TarArchiveCreator.builder(o).compression(compression).build())),
                p -> tarReader(TarArchiveExtractor.builder(p), compression, explicitOnRead),
                Optional.of(ch -> tarReader(TarArchiveExtractor.builder(ch), compression, explicitOnRead)),
                Optional.of(i -> tarReader(TarArchiveExtractor.builder(i), compression, explicitOnRead)));
    }

    private static Reader tarReader(
            TarArchiveExtractor.Builder builder, Compression compression, boolean explicitOnRead) throws IOException {
        return ArchiveFormat.reader((explicitOnRead ? builder.compression(compression) : builder).build());
    }

    private static ArchiveFormat streamNative(
            String name,
            Set<Capability> capabilities,
            Class<?> extractor,
            Class<?> creator,
            IOFunction<Path, ArchiveCreatorBuilder<?, ?, ?>> builderAt,
            IOFunction<SeekableByteChannel, LegacyArchiveCreator<?>> createOnChannel,
            IOFunction<OutputStream, LegacyArchiveCreator<?>> createOnStream,
            IOFunction<Path, LegacyArchiveExtractor<?>> readAt,
            IOFunction<SeekableByteChannel, LegacyArchiveExtractor<?>> readFromChannel,
            IOFunction<InputStream, LegacyArchiveExtractor<?>> readFromStream) {
        return new ArchiveFormat(
                name,
                capabilities,
                extractor,
                Optional.of(creator),
                "builder",
                Optional.empty(),
                Optional.of(lb(builderAt)),
                Optional.of(lw(createOnChannel)),
                Optional.of(lw(createOnStream)),
                lr(readAt),
                Optional.of(lr(readFromChannel)),
                Optional.of(lr(readFromStream)));
    }

    private static ArchiveFormat.FilteredWriter lb(IOFunction<Path, ArchiveCreatorBuilder<?, ?, ?>> builderAt) {
        return (path, filter) ->
                ArchiveFormat.writer(builderAt.apply(path).filter(filter).build());
    }

    private static <T> IOFunction<T, ArchiveFormat.Writer> lw(IOFunction<T, ? extends LegacyArchiveCreator<?>> f) {
        return t -> ArchiveFormat.writer(f.apply(t));
    }

    private static <T> IOFunction<T, ArchiveFormat.Reader> lr(IOFunction<T, ? extends LegacyArchiveExtractor<?>> f) {
        return t -> ArchiveFormat.reader(f.apply(t));
    }
}
