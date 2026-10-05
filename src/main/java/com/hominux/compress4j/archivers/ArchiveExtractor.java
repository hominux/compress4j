/*
 * Copyright 2024-2026 The Compress4J Project
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
import com.hominux.compress4j.archivers.ExtractionErrorPolicy.EntryOutcome;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.UnsafeEntryException;
import com.hominux.compress4j.exceptions.UnsafeInputException;
import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.archive.ReaderContext;
import com.hominux.compress4j.internal.limits.ExpansionMeter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base of the archive extractors. Subclasses add the builder for one format; the expansion ratio limit is measured
 * against the compressed bytes the format reader reports.
 *
 * <p>An extractor is not thread-safe.
 *
 * @since 2.2
 */
public abstract class ArchiveExtractor implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveExtractor.class);

    private static final Predicate<Entry> ACCEPT_ALL = entry -> true;

    private static final DirectoryModeApplier DEFAULT_MODE_APPLIER =
            (path, mode) -> HostFileSystem.of(path).applyMode(path, mode);

    private final EntryReader reader;
    private final EscapingSymlinkPolicy escapingSymlinkPolicy;
    private final Predicate<Entry> entryFilter;
    private final BiFunction<Entry, ? super IOException, ErrorHandlerChoice> errorHandler;
    private final BiConsumer<Entry, ? super Path> postProcessor;
    private final DirectoryModeApplier directoryModeApplier;
    private final int stripComponents;
    private final boolean overwrite;

    private final EntryPipeline pipeline;

    /**
     * Creates an extractor over a format reader.
     *
     * @param builder the builder holding the extraction options
     * @param reader the format reader, closed by {@link #close()}
     * @param compressedBytes supplies the compressed bytes consumed so far, for the expansion ratio limit
     */
    protected ArchiveExtractor(Builder<?, ?> builder, EntryReader reader, LongSupplier compressedBytes) {
        this.reader = reader;
        this.entryFilter = builder.entryFilter;
        this.errorHandler = builder.errorHandlerFunction;
        this.postProcessor = builder.postProcessor;
        this.stripComponents = builder.stripComponents;
        this.overwrite = builder.overwrite;
        this.escapingSymlinkPolicy = builder.escapingSymlinkPolicy;
        this.directoryModeApplier = builder.directoryModeApplier;
        this.pipeline = new EntryPipeline(
                reader, stripComponents, entryFilter, builder.limits, builder.claimMeter(compressedBytes));
    }

    /**
     * Extracts the archive to the specified directory.
     *
     * @param outputDir the directory to extract the archive to
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if this extractor was already streamed or extracted
     * @throws LimitExceededException if the archive breaches one of the configured extraction limits
     * @throws UnsafeEntryException if an entry would be written, or a symlink would point, outside outputDir
     */
    public final void extract(Path outputDir) throws IOException {
        pipeline.start();
        SymlinkGuard guard = new SymlinkGuard(outputDir);
        List<DirectoryMode> directoryModes = new ArrayList<>();
        try {
            boolean ignoreErrors = drain(outputDir, guard, directoryModes);
            guard.verify();
            applyDirectoryModes(directoryModes, ignoreErrors);
        } catch (IOException | RuntimeException failure) {
            Optional<UnsafeEntryException> escape = escapeOf(guard);
            if (escape.isPresent() && escape.orElseThrow() != failure) {
                UnsafeEntryException unsafe = escape.orElseThrow();
                if (failure instanceof UnsafeInputException) {
                    failure.addSuppressed(unsafe);
                } else {
                    unsafe.addSuppressed(failure);
                    throw unsafe;
                }
            }
            throw failure;
        }
    }

    private void applyDirectoryModes(List<DirectoryMode> directoryModes, boolean ignoreErrors) throws IOException {
        directoryModes.sort(Comparator.comparingInt(
                        (DirectoryMode d) -> d.directory().normalize().getNameCount())
                .reversed());
        boolean ignoring = ignoreErrors;
        for (DirectoryMode d : directoryModes) {
            if (!Files.isDirectory(d.directory(), LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try {
                directoryModeApplier.apply(d.directory(), d.mode());
            } catch (IOException failure) {
                ignoring = new ExtractionErrorPolicy(errorHandler).handle(failure, ignoring, d.entry())
                        instanceof EntryOutcome.IgnoreFurtherErrors;
            }
        }
    }

    private static Optional<UnsafeEntryException> escapeOf(SymlinkGuard guard) {
        try {
            guard.verify();
            return Optional.empty();
        } catch (UnsafeEntryException escape) {
            return Optional.of(escape);
        }
    }

    private boolean drain(Path outputDir, SymlinkGuard guard, List<DirectoryMode> directoryModes) throws IOException {
        boolean ignoreErrors = false;
        Optional<ArchiveItem> next;
        while ((next = pipeline.advance()).isPresent()) {
            if (extractItem(outputDir, next.orElseThrow(), ignoreErrors, guard, directoryModes)
                    instanceof EntryOutcome.IgnoreFurtherErrors) {
                ignoreErrors = true;
            }
        }
        return ignoreErrors;
    }

    private EntryOutcome extractItem(
            Path outputDir,
            ArchiveItem item,
            boolean ignoreErrors,
            SymlinkGuard guard,
            List<DirectoryMode> directoryModes)
            throws IOException {
        try {
            processItem(outputDir, item, guard, directoryModes);
            return new EntryOutcome.Continue();
        } catch (UnsafeInputException unsuppressible) {
            throw unsuppressible;
        } catch (IOException failure) {
            return new ExtractionErrorPolicy(errorHandler).handle(failure, ignoreErrors, item.entry());
        }
    }

    /**
     * Closes the reader and its underlying input.
     *
     * @throws IOException if closing fails
     */
    @Override
    public final void close() throws IOException {
        reader.close();
    }

    /**
     * Streams the archive's entries in archive order, after strip-components, the entry filter and the entry limit have
     * been applied. Each item's content is readable only while it is current (see {@link ArchiveItem}).
     *
     * <p>The stream is sequential; a parallel stream fails with {@link IllegalStateException}. An extractor can be read
     * once: a second call, or a call after {@link #extract(Path)}, throws {@link IllegalStateException}. Failures while
     * advancing surface as {@link java.io.UncheckedIOException} wrapping the {@link IOException}. Closing the stream
     * does not close this extractor.
     *
     * <p>Entry names and link targets are passed through unchecked; only {@link #extract(Path)} resolves paths safely
     * against the output directory.
     *
     * @return the entries of the archive
     * @since 5.0
     */
    public final Stream<ArchiveItem> stream() {
        return pipeline.stream();
    }

    @FunctionalInterface
    interface DirectoryModeApplier {
        void apply(Path directory, int mode) throws IOException;
    }

    private record DirectoryMode(Path directory, int mode, Entry entry) {}

    private void writeFile(ArchiveItem item, Path outputFile) throws IOException {
        Entry entry = item.entry();
        if (overwrite || !Files.exists(outputFile)) {
            InputStream content = contentOf(item);
            EntryPaths.makeDirectory(outputFile.getParent());
            try (OutputStream outputStream = Files.newOutputStream(
                    outputFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                content.transferTo(outputStream);
            }
            HostFileSystem.of(outputFile).applyMode(outputFile, entry.mode());
        } else {
            LOGGER.debug("Skipping file entry: {} (already exists)", entry.name());
        }
    }

    private static int interimDirectoryMode(int archiveMode) {
        return (archiveMode & 0777) | 0700;
    }

    private static InputStream contentOf(ArchiveItem item) throws IOException {
        try {
            return item.content();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private void processItem(Path outputDir, ArchiveItem item, SymlinkGuard guard, List<DirectoryMode> directoryModes)
            throws IOException {
        Entry entry = item.entry();
        Path outputFile = EntryPaths.entryFile(outputDir, entry.name());
        switch (entry.type()) {
            case DIR -> {
                boolean existed = Files.exists(outputFile, LinkOption.NOFOLLOW_LINKS);
                EntryPaths.makeDirectory(outputFile);
                if (entry.mode() != 0) {
                    if (!existed && Files.isDirectory(outputFile, LinkOption.NOFOLLOW_LINKS)) {
                        HostFileSystem.of(outputFile).applyMode(outputFile, interimDirectoryMode(entry.mode()));
                    }
                    directoryModes.add(new DirectoryMode(outputFile, entry.mode(), entry));
                }
            }
            case FILE -> writeFile(item, outputFile);
            case SYMLINK ->
                new SymlinkExtractor(escapingSymlinkPolicy, overwrite).extract(outputDir, entry, outputFile, guard);
        }
        postProcessor.accept(entry, outputFile);
    }

    /**
     * Builder for creating an {@link ArchiveExtractor}.
     *
     * @param <B> The type of this builder
     * @param <E> The type of {@link ArchiveExtractor} it builds
     */
    public abstract static class Builder<B extends Builder<B, E>, E extends ArchiveExtractor> {
        EscapingSymlinkPolicy escapingSymlinkPolicy = EscapingSymlinkPolicy.DISALLOW;

        Predicate<Entry> entryFilter = ACCEPT_ALL;

        BiFunction<Entry, ? super IOException, ErrorHandlerChoice> errorHandlerFunction =
                (x, y) -> ErrorHandlerChoice.ABORT;
        BiConsumer<Entry, ? super Path> postProcessor = (entry, path) -> {};
        Consumer<UnsupportedEntry> unsupportedEntryHandler = entry -> {};
        int stripComponents = 0;
        boolean overwrite = false;
        ExtractionLimits limits = ExtractionLimits.defaults();
        private Optional<PendingBuild> pending = Optional.empty();
        DirectoryModeApplier directoryModeApplier = DEFAULT_MODE_APPLIER;

        /** Creates a builder with the default options. */
        protected Builder() {}

        /**
         * Sets predicate to be used when entries are being extracted. The predicate applies to both {@link #stream()}
         * and {@link #extract(Path)} and sees each entry after strip-components: with {@code stripComponents(1)}, match
         * {@code a.txt}, not {@code root/a.txt}.
         *
         * @param entryPredicate the entries to extract
         * @return this builder
         * @throws NullPointerException if {@code entryPredicate} is {@code null}
         */
        public final B filter(Predicate<Entry> entryPredicate) {
            this.entryFilter = Objects.requireNonNull(entryPredicate, "filter");
            return getThis();
        }

        /**
         * Sets the error handler, consulted for each non-security {@link IOException} while extracting an entry, until
         * it answers {@link ErrorHandlerChoice#SKIP_ALL}. Defaults to a handler that answers
         * {@link ErrorHandlerChoice#ABORT}. An {@link UnsafeInputException} always propagates without consulting the
         * handler. A handler that returns {@code null} makes the extraction fail with a {@link NullPointerException}.
         *
         * @param errorHandlerFunction the handler; it must not return {@code null}
         * @return this builder
         * @throws NullPointerException if {@code errorHandlerFunction} is {@code null}
         */
        public final B errorHandler(BiFunction<Entry, ? super IOException, ErrorHandlerChoice> errorHandlerFunction) {
            this.errorHandlerFunction = Objects.requireNonNull(errorHandlerFunction, "errorHandler");
            return getThis();
        }

        /**
         * Sets the escaping symlink policy for the extractor. Defaults to {@link EscapingSymlinkPolicy#DISALLOW}.
         *
         * @param policy the escaping symlink policy to set
         * @return this builder
         */
        public final B escapingSymlinkPolicy(EscapingSymlinkPolicy policy) {
            this.escapingSymlinkPolicy = policy;
            return getThis();
        }

        /**
         * Sets the handler told about every entry the reader skips because its type cannot be extracted (for example
         * hard link, character device, fifo, socket, whiteout, unknown type). Called from both {@link #stream()} and
         * {@link #extract(Path)}; an exception it throws propagates to the caller. Defaults to doing nothing.
         *
         * @param handler the handler
         * @return this builder
         * @throws NullPointerException if {@code handler} is {@code null}
         * @since 5.0
         */
        public final B unsupportedEntryHandler(Consumer<UnsupportedEntry> handler) {
            this.unsupportedEntryHandler = Objects.requireNonNull(handler, "unsupportedEntryHandler");
            return getThis();
        }

        /**
         * Sets the post processor for the extractor. For directory entries it runs before the archive mode is applied,
         * which happens after all entries; a directory the extractor created then holds an interim owner-accessible
         * mode, and permissions set here may be overwritten by the archive mode.
         *
         * @param entryBiConsumer the post processor to set
         * @return this builder
         * @throws NullPointerException if {@code entryBiConsumer} is {@code null}
         */
        public final B postProcessor(BiConsumer<Entry, ? super Path> entryBiConsumer) {
            this.postProcessor = Objects.requireNonNull(entryBiConsumer, "postProcessor");
            return getThis();
        }

        /**
         * Sets the number of leading path components to strip from the entries. Applies to both {@link #stream()} and
         * {@link #extract(Path)}, before the filter runs.
         *
         * @param level the number of leading path components to strip
         * @return this builder
         */
        public final B stripComponents(int level) {
            this.stripComponents = level;
            return getThis();
        }

        /**
         * Sets whether to overwrite existing files.
         *
         * @param overwrite whether to overwrite existing files
         * @return this builder
         */
        public final B overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return getThis();
        }

        /**
         * Replaces every extraction limit; see {@link ExtractionLimits}. Builders start from
         * {@link ExtractionLimits#defaults()}, and this call discards every component set before it.
         *
         * @param limits the limits
         * @return this builder
         * @throws NullPointerException if {@code limits} is {@code null}
         * @since 5.0
         */
        public final B limits(ExtractionLimits limits) {
            this.limits = Objects.requireNonNull(limits, "limits");
            return getThis();
        }

        /**
         * Sets the maximum number of entries the extractor will process before aborting. Counts entries that pass
         * strip-components and the filter; entries skipped as unsupported or filtered out are not counted, and the
         * unsupported-entry handler is called once per skipped entry without a bound. In {@link #stream()} a breach
         * surfaces as {@link java.io.UncheckedIOException} wrapping {@link LimitExceededException}.
         *
         * <p>Defaults to 1,000,000.
         *
         * @param maxEntries the maximum number of entries, or {@link ExtractionLimits#UNLIMITED} to disable the limit
         * @return this builder
         * @throws IllegalArgumentException if {@code maxEntries} is neither {@link ExtractionLimits#UNLIMITED} nor at
         *     least 0
         * @since 3.1
         */
        public final B maxEntries(long maxEntries) {
            this.limits = limits.withMaxEntries(maxEntries);
            return getThis();
        }

        /**
         * Sets the maximum number of bytes a single entry may expand to before the extractor aborts. Counts bytes
         * actually read from the entry's {@code content()}. Defaults to unlimited.
         *
         * @param maxEntrySize the maximum size of a single entry in bytes, or {@link ExtractionLimits#UNLIMITED} to
         *     disable the limit
         * @return this builder
         * @throws IllegalArgumentException if {@code maxEntrySize} is neither {@link ExtractionLimits#UNLIMITED} nor at
         *     least 0
         * @since 3.1
         */
        public final B maxEntrySize(long maxEntrySize) {
            this.limits = limits.withMaxEntrySize(maxEntrySize);
            return getThis();
        }

        /**
         * Sets the maximum number of bytes the whole archive may expand to before the extractor aborts. Counts bytes
         * actually read from entries' {@code content()}. Defaults to unlimited.
         *
         * @param maxTotalSize the maximum total extracted size in bytes, or {@link ExtractionLimits#UNLIMITED} to
         *     disable the limit
         * @return this builder
         * @throws IllegalArgumentException if {@code maxTotalSize} is neither {@link ExtractionLimits#UNLIMITED} nor at
         *     least 0
         * @since 3.1
         */
        public final B maxTotalSize(long maxTotalSize) {
            this.limits = limits.withMaxTotalSize(maxTotalSize);
            return getThis();
        }

        /**
         * Sets the maximum expansion ratio (uncompressed bytes per compressed byte read), enforced once an extraction
         * has produced more than 1 MiB. Defaults to 100.
         *
         * @param maxRatio the maximum, at least 1, or {@link ExtractionLimits#UNLIMITED}
         * @return this builder
         * @throws IllegalArgumentException if {@code maxRatio} is neither {@link ExtractionLimits#UNLIMITED} nor at
         *     least 1
         * @since 5.0
         */
        public final B maxRatio(long maxRatio) {
            this.limits = limits.withMaxRatio(maxRatio);
            return getThis();
        }

        B directoryModeApplier(DirectoryModeApplier applier) {
            this.directoryModeApplier = applier;
            return getThis();
        }

        /**
         * Returns this builder with its concrete type.
         *
         * @return this builder
         */
        protected abstract B getThis();

        /**
         * Returns the context a format reader needs to enforce limits and report unsupported entries.
         *
         * @return a new reader context for the options set so far, owned by the extractor of this build
         */
        protected final ReaderContext readerContext() {
            var source = new AtomicReference<LongSupplier>(() -> 0);
            var meter = new ExpansionMeter(limits, () -> source.get().getAsLong());
            var context = new ReaderContext(limits, unsupportedEntryHandler, meter);
            pending = Optional.of(new PendingBuild(context, source));
            return context;
        }

        private ExpansionMeter claimMeter(LongSupplier compressedBytes) {
            var claimed = pending.orElseGet(() -> {
                readerContext();
                return pending.orElseThrow();
            });
            pending = Optional.empty();
            claimed.source().set(compressedBytes);
            return claimed.context().meter();
        }

        private record PendingBuild(ReaderContext context, AtomicReference<LongSupplier> source) {}

        /**
         * Builds the extractor.
         *
         * @return the extractor
         * @throws IOException if the archive cannot be opened
         */
        public abstract E build() throws IOException;
    }
}
