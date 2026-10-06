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

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.io.ParserFailures;
import com.hominux.compress4j.internal.limits.ExpansionMeter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Applies strip-components, the entry filter, extraction limits and the content-lifetime guard to raw entries. */
final class EntryPipeline {

    private final EntryReader reader;
    private final int stripComponents;
    private final Predicate<Entry> filter;
    private final ExtractionBudget budget;

    private boolean started;
    private boolean exhausted;
    private long position;
    private Optional<InputStream> guardedContent = Optional.empty();

    EntryPipeline(
            EntryReader reader,
            int stripComponents,
            Predicate<Entry> filter,
            ExtractionLimits limits,
            LongSupplier compressedBytes) {
        this(reader, stripComponents, filter, limits, new ExpansionMeter(limits, compressedBytes));
    }

    EntryPipeline(
            EntryReader reader,
            int stripComponents,
            Predicate<Entry> filter,
            ExtractionLimits limits,
            ExpansionMeter meter) {
        this.reader = reader;
        this.stripComponents = stripComponents;
        this.filter = filter;
        this.budget = new ExtractionBudget(limits, meter);
    }

    Stream<ArchiveItem> stream() {
        start();
        return StreamSupport.stream(new ItemSpliterator(), false);
    }

    void start() {
        if (started) {
            throw new IllegalStateException("Archive entries can be read only once per extractor");
        }
        started = true;
    }

    Optional<ArchiveItem> advance() throws IOException {
        guardedContent = Optional.empty();
        if (exhausted) {
            return Optional.empty();
        }
        position++;
        Optional<Entry> raw;
        while ((raw = ParserFailures.call(reader::next, ParserFailures.ARCHIVE)).isPresent()) {
            Optional<Entry> visible = strip(raw.orElseThrow()).filter(filter);
            if (visible.isPresent()) {
                budget.countEntry();
                return Optional.of(new ArchiveItem(visible.orElseThrow(), this, position));
            }
        }
        exhausted = true;
        return Optional.empty();
    }

    InputStream content(ArchiveItem item) {
        if (!isCurrent(item.position())) {
            throw new IllegalStateException(staleMessage(item.entry()));
        }
        if (item.entry().type() != Entry.Type.FILE) {
            return InputStream.nullInputStream();
        }
        if (guardedContent.isEmpty()) {
            try {
                InputStream raw = ParserFailures.call(() -> reader.open(item.entry()), ParserFailures.ARCHIVE);
                InputStream parsed = ParserFailures.wrap(raw, ParserFailures.ARCHIVE_ENTRY);
                guardedContent = Optional.of(
                        new GuardedContent(item, budget.meter(item.entry().name(), parsed)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return guardedContent.orElseThrow();
    }

    private boolean isCurrent(long itemPosition) {
        return itemPosition == position;
    }

    private Optional<Entry> strip(Entry entry) {
        if (stripComponents == 0) {
            return Optional.of(entry);
        }
        return EntryPaths.stripComponents(entry.name(), stripComponents).map(entry::withName);
    }

    private static String staleMessage(Entry entry) {
        return "entry " + entry.name() + " is no longer current";
    }

    private final class GuardedContent extends FilterInputStream {
        private final ArchiveItem item;

        private GuardedContent(ArchiveItem item, InputStream in) {
            super(in);
            this.item = item;
        }

        @Override
        public int read() throws IOException {
            checkCurrent();
            return super.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            checkCurrent();
            return super.read(buffer, offset, length);
        }

        @Override
        public long skip(long n) throws IOException {
            checkCurrent();
            return super.skip(n);
        }

        @Override
        public void close() {
            // The archive owns the underlying stream; it outlives this entry.
        }

        private void checkCurrent() throws IOException {
            if (!isCurrent(item.position())) {
                throw new IOException(staleMessage(item.entry()));
            }
        }
    }

    private final class ItemSpliterator extends Spliterators.AbstractSpliterator<ArchiveItem> {

        private ItemSpliterator() {
            super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
        }

        @Override
        public boolean tryAdvance(Consumer<? super ArchiveItem> action) {
            try {
                Optional<ArchiveItem> next = advance();
                next.ifPresent(action);
                return next.isPresent();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Spliterator<ArchiveItem> trySplit() {
            throw new IllegalStateException(
                    "Archive entries are read sequentially; parallel streams are not supported");
        }
    }
}
