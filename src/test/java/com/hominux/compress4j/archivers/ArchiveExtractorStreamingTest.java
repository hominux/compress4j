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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.memory.InMemoryArchiveEntry;
import com.hominux.compress4j.archivers.memory.InMemoryArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.internal.archive.EntryReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

class ArchiveExtractorStreamingTest {

    private static List<InMemoryArchiveEntry> files(String... namesAndContents) {
        var list = new ArrayList<InMemoryArchiveEntry>();
        for (int i = 0; i < namesAndContents.length; i += 2) {
            list.add(InMemoryArchiveEntry.builder()
                    .name(namesAndContents[i])
                    .content(namesAndContents[i + 1])
                    .build());
        }
        return list;
    }

    private static String read(ArchiveItem item) {
        try {
            return new String(item.content().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void streamYieldsEntriesWithTheirContentInOrder() throws IOException {
        try (var extractor =
                InMemoryArchiveExtractor.builder(files("a", "1", "b", "2")).build()) {
            // When
            Map<String, String> seen = extractor.stream()
                    .collect(Collectors.toMap(i -> i.entry().name(), ArchiveExtractorStreamingTest::read));

            // Then
            assertThat(seen).containsExactlyInAnyOrderEntriesOf(Map.of("a", "1", "b", "2"));
        }
    }

    @Test
    void contentOfAStaleItemThrows() throws IOException {
        try (var extractor =
                InMemoryArchiveExtractor.builder(files("a", "1", "b", "2")).build()) {
            // When
            List<ArchiveItem> items = extractor.stream().toList();

            // Then
            ArchiveItem first = items.getFirst();
            assertThatThrownBy(first::content)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("a")
                    .hasMessageContaining("no longer current");
        }
    }

    @Test
    void contentStreamOpenedEarlierFailsAfterAdvance() throws IOException {
        try (var extractor =
                InMemoryArchiveExtractor.builder(files("a", "1", "b", "2")).build()) {
            // When
            List<InputStream> opened =
                    extractor.stream().map(ArchiveItem::content).toList();

            // Then
            assertThatThrownBy(() -> opened.getFirst().read())
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("no longer current");
        }
    }

    @Test
    void contentCalledTwiceReturnsTheSameStream() throws IOException {
        try (var extractor = InMemoryArchiveExtractor.builder(files("a", "12")).build()) {
            // When
            var item = extractor.stream().findFirst().orElseThrow();
            int first = item.content().read();
            int second = item.content().read();

            // Then
            assertThat((char) first).isEqualTo('1');
            assertThat((char) second).isEqualTo('2');
        }
    }

    @Test
    void findFirstItemStaysReadable() throws IOException {
        try (var extractor =
                InMemoryArchiveExtractor.builder(files("a", "1", "b", "2")).build()) {
            // When
            var b = extractor.stream()
                    .filter(i -> i.entry().name().equals("b"))
                    .findFirst()
                    .orElseThrow();

            // Then
            assertThat(read(b)).isEqualTo("2");
        }
    }

    @Test
    void directoryContentIsEmpty() throws IOException {
        var dir = InMemoryArchiveEntry.builder().name("d/").type(Entry.Type.DIR).build();
        try (var extractor = InMemoryArchiveExtractor.builder(List.of(dir)).build()) {
            // Then
            assertThat(read(extractor.stream().findFirst().orElseThrow())).isEmpty();
        }
    }

    @Test
    void stripThenFilterOnTheStrippedName() throws IOException {
        try (var extractor = InMemoryArchiveExtractor.builder(files("root/keep", "k", "root/skip", "s", "root", ""))
                .stripComponents(1)
                .filter(e -> !e.name().equals("skip"))
                .build()) {
            // Then
            assertThat(extractor.stream().map(i -> i.entry().name())).containsExactly("keep");
        }
    }

    @Test
    void maxEntriesCountsEmittedItems() throws IOException {
        try (var extractor = InMemoryArchiveExtractor.builder(files("a", "1", "skip", "2", "b", "3"))
                .filter(e -> !e.name().equals("skip"))
                .maxEntries(2)
                .build()) {
            // Then
            assertThat(extractor.stream().map(i -> i.entry().name())).containsExactly("a", "b");
        }
    }

    @Test
    void advancingPastMaxEntriesThrowsUnchecked() throws IOException {
        try (var extractor = InMemoryArchiveExtractor.builder(files("a", "1", "b", "2"))
                .maxEntries(1)
                .build()) {
            // Then
            Stream<ArchiveItem> items = extractor.stream();
            assertThatThrownBy(items::toList)
                    .isInstanceOf(UncheckedIOException.class)
                    .hasCauseInstanceOf(LimitExceededException.class);
        }
    }

    @Test
    void contentIsMetered() throws IOException {
        try (var extractor = InMemoryArchiveExtractor.builder(files("a", "12345"))
                .maxEntrySize(2)
                .build()) {
            // When
            var item = extractor.stream().findFirst().orElseThrow();

            // Then
            assertThatThrownBy(() -> item.content().readAllBytes()).isInstanceOf(LimitExceededException.class);
        }
    }

    @Test
    void parallelStreamIsRejected() throws IOException {
        try (var extractor =
                InMemoryArchiveExtractor.builder(files("a", "1", "b", "2")).build()) {
            // Then
            Stream<ArchiveItem> parallel = extractor.stream().parallel();
            assertThatThrownBy(parallel::toList)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("parallel");
        }
    }

    @Test
    void streamIsSingleUse() throws IOException {
        try (var extractor = InMemoryArchiveExtractor.builder(files("a", "1")).build()) {
            // When
            extractor.stream().toList();

            // Then
            assertThatThrownBy(extractor::stream)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("once");
        }
    }

    @Test
    void closingContentDoesNotCloseTheArchive() throws IOException {
        try (var extractor =
                TarArchiveExtractor.builder(tar("a", "1", "b", "2")).build()) {
            // When
            List<String> names = new ArrayList<>();
            extractor.stream().forEach(i -> {
                try {
                    i.content().close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                names.add(i.entry().name());
            });

            // Then
            assertThat(names).containsExactly("a", "b");
        }
    }

    @Test
    void closingContentThenReadingTheNextFileStillYieldsItsContent() throws IOException {
        try (var extractor =
                TarArchiveExtractor.builder(tar("a", "1", "b", "2")).build()) {
            // When
            List<String> contents = new ArrayList<>();
            extractor.stream().forEach(i -> {
                try (var content = i.content()) {
                    contents.add(new String(content.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            // Then
            assertThat(contents).containsExactly("1", "2");
        }
    }

    @Test
    void dirAndSymlinkItemsHaveEmptyContentWithoutOpeningTheEntryStream() throws IOException {
        var entries = List.of(
                InMemoryArchiveEntry.builder().name("d").type(Entry.Type.DIR).build(),
                InMemoryArchiveEntry.builder()
                        .name("l")
                        .type(Entry.Type.SYMLINK)
                        .linkName("t")
                        .build());
        try (var extractor = InMemoryArchiveExtractor.builder(entries)
                .readerDecorator(NoOpenReader::new)
                .build()) {
            // When
            List<byte[]> contents = extractor.stream().map(this::bytes).toList();

            // Then
            assertThat(contents).hasSize(2).allSatisfy(b -> assertThat(b).isEmpty());
        }
    }

    private byte[] bytes(ArchiveItem item) {
        try {
            return item.content().readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream tar(String... namesAndContents) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(out)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                byte[] data = namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8);
                var entry = new TarArchiveEntry(namesAndContents[i]);
                entry.setSize(data.length);
                tar.putArchiveEntry(entry);
                tar.write(data);
                tar.closeArchiveEntry();
            }
        }
        return new ClosableOnce(out.toByteArray());
    }

    private static final class ClosableOnce extends ByteArrayInputStream {
        private boolean closed;

        ClosableOnce(byte[] data) {
            super(data);
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            if (closed) {
                throw new IllegalStateException("archive stream was closed");
            }
            return super.read(b, off, len);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private record NoOpenReader(EntryReader delegate) implements EntryReader {
        @Override
        public Optional<Entry> next() throws IOException {
            return delegate.next();
        }

        @Override
        public InputStream open(Entry entry) {
            throw new AssertionError("open must not be called for " + entry.type());
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
