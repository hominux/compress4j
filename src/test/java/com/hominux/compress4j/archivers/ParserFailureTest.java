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
package com.hominux.compress4j.archivers;

import static com.hominux.compress4j.archivers.ErrorHandlerChoice.ABORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.memory.InMemoryArchiveEntry;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import com.hominux.compress4j.exceptions.UnsafeEntryException;
import com.hominux.compress4j.internal.archive.EntryReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParserFailureTest {

    @TempDir
    Path dir;

    private static InMemoryArchiveEntry entry(String name, String content) {
        return InMemoryArchiveEntry.builder().name(name).content(content).build();
    }

    private static class DelegatingReader implements EntryReader {
        private final EntryReader delegate;

        DelegatingReader(EntryReader delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Entry> next() throws IOException {
            return delegate.next();
        }

        @Override
        public InputStream open(Entry entry) throws IOException {
            return delegate.open(entry);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static EntryReader failingNext(EntryReader delegate, Exception failure) {
        return new DelegatingReader(delegate) {
            @Override
            public Optional<Entry> next() throws IOException {
                throw sneaky(failure);
            }
        };
    }

    private static EntryReader failingOpen(EntryReader delegate, Exception failure) {
        return new DelegatingReader(delegate) {
            @Override
            public InputStream open(Entry entry) throws IOException {
                throw sneaky(failure);
            }
        };
    }

    private static EntryReader failingContent(EntryReader delegate, Exception failure) {
        return new DelegatingReader(delegate) {
            @Override
            public InputStream open(Entry entry) {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw sneaky(failure);
                    }
                };
            }
        };
    }

    private static IOException sneaky(Exception failure) throws IOException {
        if (failure instanceof IOException io) {
            throw io;
        }
        throw (RuntimeException) failure;
    }

    private InMemoryArchiveExtractor.InMemoryArchiveExtractorBuilder one() throws IOException {
        return InMemoryArchiveExtractor.builder(List.of(entry("a", "xyz")));
    }

    @Test
    void parserRuntimeFailuresBecomeIOExceptions() throws IOException {
        var failure = new ArithmeticException("bad header");
        try (var extractor = one().readerDecorator(r -> failingNext(r, failure)).build()) {
            assertThatThrownBy(() -> extractor.extract(dir))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Corrupt archive")
                    .hasCause(failure);
        }
    }

    @Test
    void streamingSurfacesParserFailuresAsTheCauseOfAnUncheckedIOException() throws IOException {
        var failure = new ArithmeticException("bad header");
        try (var extractor = one().readerDecorator(r -> failingNext(r, failure)).build()) {
            assertThatThrownBy(() -> extractor.stream().count())
                    .isInstanceOfSatisfying(UncheckedIOException.class, e -> assertThat(e.getCause())
                            .hasMessage("Corrupt archive")
                            .hasCause(failure));
        }
    }

    @Test
    void openFailuresBecomeIOExceptions() throws IOException {
        var failure = new IllegalStateException("bad entry");
        try (var extractor = one().readerDecorator(r -> failingOpen(r, failure)).build()) {
            assertThatThrownBy(() -> extractor.extract(dir))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Corrupt archive")
                    .hasCause(failure);
        }
    }

    @Test
    void contentReadFailuresBecomeIOExceptionsWhenExtracting() throws IOException {
        var failure = new IndexOutOfBoundsException("bad block");
        try (var extractor =
                one().readerDecorator(r -> failingContent(r, failure)).build()) {
            assertThatThrownBy(() -> extractor.extract(dir))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Corrupt archive entry")
                    .hasCause(failure);
        }
    }

    @Test
    void contentReadFailuresBecomeIOExceptionsWhenStreaming() throws IOException {
        var failure = new IndexOutOfBoundsException("bad block");
        try (var extractor =
                one().readerDecorator(r -> failingContent(r, failure)).build()) {
            var item = extractor.stream().findFirst().orElseThrow();
            assertThatThrownBy(() -> item.content().read())
                    .isInstanceOf(IOException.class)
                    .hasMessage("Corrupt archive entry")
                    .hasCause(failure);
        }
    }

    @Test
    void errorsFromTheParserAreNotWrapped() throws IOException {
        var failure = new StackOverflowError("deep nesting");
        try (var extractor = one().readerDecorator(r -> new DelegatingReader(r) {
                    @Override
                    public Optional<Entry> next() {
                        throw failure;
                    }
                })
                .build()) {
            assertThatThrownBy(() -> extractor.extract(dir)).isSameAs(failure);
        }
    }

    @Test
    void closeFailuresOfTheParserBecomeIOExceptions() throws IOException {
        var failure = new IllegalStateException("bad trailer");
        var extractor = one().readerDecorator(r -> new DelegatingReader(r) {
                    @Override
                    public void close() {
                        throw failure;
                    }
                })
                .build();
        assertThatThrownBy(extractor::close)
                .isInstanceOf(IOException.class)
                .hasMessage("Corrupt archive")
                .hasCause(failure);
    }

    @Test
    void ioFailuresOfTheParserKeepTheirType() throws IOException {
        var failure = new IOException("disk gone");
        try (var extractor = one().readerDecorator(r -> failingNext(r, failure)).build()) {
            assertThatThrownBy(() -> extractor.extract(dir)).isSameAs(failure);
        }
    }

    @Test
    void limitBreachesInsideTheContentStreamStayLimitExceptions() throws IOException {
        var breach = new LimitExceededException(Limit.ENTRY_SIZE, 1, Optional.of("a"));
        try (var extractor =
                one().readerDecorator(r -> failingContent(r, breach)).build()) {
            assertThatThrownBy(() -> extractor.extract(dir)).isSameAs(breach);
        }
    }

    @Test
    void meterBreachesStayLimitExceptions() throws IOException {
        try (var extractor = one().maxEntrySize(1).build()) {
            assertThatThrownBy(() -> extractor.extract(dir))
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                            .isEqualTo(Limit.ENTRY_SIZE));
        }
    }

    @Test
    void unsafeEntriesStayUnsafeEntryExceptions() throws IOException {
        try (var extractor =
                InMemoryArchiveExtractor.builder(List.of(entry("../evil", "x"))).build()) {
            assertThatThrownBy(() -> extractor.extract(dir)).isInstanceOf(UnsafeEntryException.class);
        }
    }

    @Test
    void callerFilterExceptionsAreNotWrapped() throws IOException {
        var failure = new IllegalStateException("caller filter");
        try (var extractor = one().filter(e -> {
                    throw failure;
                })
                .build()) {
            assertThatThrownBy(() -> extractor.extract(dir)).isSameAs(failure);
        }
    }

    @Test
    void callerErrorHandlerExceptionsAreNotWrapped() throws IOException {
        var failure = new IllegalStateException("caller handler");
        var entryFailure = new IOException("bad entry");
        try (var extractor = one().readerDecorator(r -> failingOpen(r, entryFailure))
                .errorHandler((e, ex) -> {
                    throw failure;
                })
                .build()) {
            assertThatThrownBy(() -> extractor.extract(dir)).isSameAs(failure);
        }
    }

    @Test
    void errorHandlerStillSeesIOExceptionsFromTheParser() throws IOException {
        var entryFailure = new IOException("bad entry");
        try (var extractor = one().readerDecorator(r -> failingOpen(r, entryFailure))
                .errorHandler((e, ex) -> ABORT)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(dir)).isSameAs(entryFailure);
        }
    }

    @Test
    void callerPostProcessorExceptionsAreNotWrapped() throws IOException {
        var failure = new IllegalStateException("caller post-processor");
        try (var extractor = one().postProcessor((e, path) -> {
                    throw failure;
                })
                .build()) {
            assertThatThrownBy(() -> extractor.extract(dir)).isSameAs(failure);
        }
    }

    @Test
    void callerUnsupportedEntryHandlerExceptionsAreNotWrapped() throws IOException {
        var failure = new IllegalStateException("caller unsupported handler");
        Path tar = dir.resolve("fifo.tar");
        try (var out = new TarArchiveOutputStream(Files.newOutputStream(tar))) {
            out.putArchiveEntry(new TarArchiveEntry("pipe", TarConstants.LF_FIFO));
            out.closeArchiveEntry();
        }
        try (var extractor = TarArchiveExtractor.builder(tar)
                .unsupportedEntryHandler(e -> {
                    throw failure;
                })
                .build()) {
            Path out = dir.resolve("out");
            assertThatThrownBy(() -> extractor.extract(out)).isSameAs(failure);
        }
    }
}
