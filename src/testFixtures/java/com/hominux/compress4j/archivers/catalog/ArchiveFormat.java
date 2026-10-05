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

import com.hominux.compress4j.archivers.ArchiveCreator;
import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.archivers.ArchiveItem;
import com.hominux.compress4j.archivers.EntrySource;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.commons.io.function.IOFunction;

/**
 * One archive format's builders and declared capabilities, as exercised by the contract suite.
 *
 * <p>{@code extractor} and {@code creator} are the production classes whose static builders the row calls;
 * {@code streamFactory} names the extractor's static {@code InputStream} factory. A read-only row names the writable
 * row that produces its test archives in {@code writer}. Rows read and write through {@link Reader} and {@link Writer},
 * which both archive base types satisfy while formats move between them.
 */
public record ArchiveFormat(
        String name,
        Set<Capability> capabilities,
        Class<?> extractor,
        Optional<Class<?>> creator,
        String streamFactory,
        Optional<String> writer,
        Optional<BuilderAt> builderAt,
        Optional<IOFunction<SeekableByteChannel, Writer>> createOnChannel,
        Optional<IOFunction<OutputStream, Writer>> createOnStream,
        IOFunction<Path, Reader> readAt,
        Optional<IOFunction<SeekableByteChannel, Reader>> readFromChannel,
        Optional<IOFunction<InputStream, Reader>> readFromStream) {

    /** The reading half of an archive extractor. */
    public interface Reader extends Closeable {
        Stream<ArchiveItem> stream();

        void extract(Path outputDir) throws IOException;
    }

    /** The writing half of an archive creator. */
    public interface Writer extends Closeable {
        void add(EntrySource source) throws IOException;

        void addAll(Stream<? extends EntrySource> sources) throws IOException;

        void addDirectoryRecursively(Path directory) throws IOException;

        void addFile(Path path) throws IOException;
    }

    /** Creates the unbuilt creator builder for a path. */
    @FunctionalInterface
    public interface BuilderAt {
        ArchiveCreator.Builder<?, ?> builder(Path path);

        default Writer open(Path path, Predicate<? super EntrySource> filter) throws IOException {
            return writer(builder(path).filter(filter).build());
        }
    }

    public static Reader reader(ArchiveExtractor e) {
        return new Reader() {
            public Stream<ArchiveItem> stream() {
                return e.stream();
            }

            public void extract(Path dir) throws IOException {
                e.extract(dir);
            }

            public void close() throws IOException {
                e.close();
            }
        };
    }

    public static Writer writer(ArchiveCreator c) {
        return new Writer() {
            public void add(EntrySource source) throws IOException {
                c.add(source);
            }

            public void addAll(Stream<? extends EntrySource> sources) throws IOException {
                c.addAll(sources);
            }

            public void addDirectoryRecursively(Path directory) throws IOException {
                c.addDirectoryRecursively(directory);
            }

            public void addFile(Path path) throws IOException {
                c.addFile(path);
            }

            public void close() throws IOException {
                c.close();
            }
        };
    }

    public boolean has(Capability capability) {
        return capabilities.contains(capability);
    }

    public boolean writable() {
        return builderAt.isPresent();
    }

    public Optional<IOFunction<Path, Writer>> createAt() {
        return builderAt.map(builder -> path -> builder.open(path, source -> true));
    }

    @Override
    public String toString() {
        return name;
    }
}
