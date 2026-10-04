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
import com.hominux.compress4j.archivers.ArchiveCreator.ArchiveCreatorBuilder;
import com.hominux.compress4j.archivers.ArchiveExtractor;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.function.IOFunction;

/**
 * One archive format's builders and declared capabilities, as exercised by the contract suite.
 *
 * <p>{@code extractor} and {@code creator} are the production classes whose static builders the row calls;
 * {@code streamFactory} names the extractor's static {@code InputStream} factory. A read-only row names the writable
 * row that produces its test archives in {@code writer}.
 */
public record ArchiveFormat(
        String name,
        Set<Capability> capabilities,
        Class<?> extractor,
        Optional<Class<?>> creator,
        String streamFactory,
        Optional<String> writer,
        Optional<IOFunction<Path, ArchiveCreatorBuilder<?, ?, ?>>> builderAt,
        Optional<IOFunction<SeekableByteChannel, ArchiveCreator<?>>> createOnChannel,
        Optional<IOFunction<OutputStream, ArchiveCreator<?>>> createOnStream,
        IOFunction<Path, ArchiveExtractor<?>> readAt,
        Optional<IOFunction<SeekableByteChannel, ArchiveExtractor<?>>> readFromChannel,
        Optional<IOFunction<InputStream, ArchiveExtractor<?>>> readFromStream) {

    public boolean has(Capability capability) {
        return capabilities.contains(capability);
    }

    public boolean writable() {
        return builderAt.isPresent();
    }

    public Optional<IOFunction<Path, ArchiveCreator<?>>> createAt() {
        return builderAt.map(builder -> path -> builder.apply(path).build());
    }

    @Override
    public String toString() {
        return name;
    }
}
