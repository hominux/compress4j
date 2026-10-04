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

import static com.hominux.compress4j.archivers.ArchiveExtractor.ErrorHandlerChoice.RETRY;
import static com.hominux.compress4j.utils.FileUtils.DOS_HIDDEN;
import static com.hominux.compress4j.utils.FileUtils.DOS_READ_ONLY;
import static com.hominux.compress4j.utils.PosixFilePermissionsMapper.fromUnixMode;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;

import com.hominux.compress4j.archivers.ExtractionErrorPolicy.EntryOutcome;
import com.hominux.compress4j.exceptions.ArchiveLimitExceededException;
import com.hominux.compress4j.exceptions.ArchiveSecurityException;
import com.hominux.compress4j.exceptions.UnsafeEntryException;
import com.hominux.compress4j.utils.BuildFailureCleanup;
import jakarta.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This abstract class is the superclass of all classes providing archive extraction functionality. This class provides
 * the core functionality to extract files and directories from archives.
 *
 * <p>An extractor is not thread-safe.
 *
 * @param <A> The type of {@link ArchiveInputStream} to read entries from.
 * @since 2.2
 */
public abstract class ArchiveExtractor<A extends ArchiveInputStream<? extends ArchiveEntry>> implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveExtractor.class);

    /**
     * Value disabling an extraction limit.
     *
     * @since 3.1
     */
    public static final long UNLIMITED = -1L;

    private static final Predicate<Entry> ACCEPT_ALL = entry -> true;

    /** Archive input stream to be used for extraction. */
    protected A archiveInputStream;
    /** Escaping symlink policy for the extractor. */
    private final ArchiveExtractor.EscapingSymlinkPolicy escapingSymlinkPolicy;
    /** Filter for the extractor. */
    private final Predicate<Entry> entryFilter;
    /** Error handler for the extractor. */
    private final BiFunction<Entry, ? super IOException, ErrorHandlerChoice> errorHandler;
    /** Post processor for the extractor. */
    private final BiConsumer<Entry, ? super Path> postProcessor;

    /** Number of leading path components to strip from the extracted entries. */
    private final int stripComponents;

    /** Whether to overwrite existing files. */
    private final boolean overwrite;

    /** Extraction limits configured through the builder's max-entries, max-entry-size and max-total-size options. */
    private final ExtractionLimits limits;

    private final EntryPipeline pipeline;

    /**
     * Creates a new {@code ArchiveExtractor}.
     *
     * @param builder - the archive input stream builder
     * @param <B> - the type of the {@code ArchiveExtractorBuilder} to build from
     * @param <C> The type of the {@link ArchiveExtractor} to instantiate.
     * @throws IOException - if the {@code A} could not be created
     */
    protected <B extends ArchiveExtractorBuilder<A, B, C>, C extends ArchiveExtractor<A>> ArchiveExtractor(B builder)
            throws IOException {
        this.archiveInputStream = BuildFailureCleanup.build(builder.ownedStream, builder::buildArchiveInputStream);
        this.entryFilter = builder.entryFilter;
        this.errorHandler = builder.errorHandlerFunction;
        this.postProcessor = builder.postProcessor;
        this.stripComponents = builder.stripComponents;
        this.overwrite = builder.overwrite;
        this.escapingSymlinkPolicy = builder.escapingSymlinkPolicy;
        this.limits = new ExtractionLimits(builder.maxEntries, builder.maxEntrySize, builder.maxTotalSize);
        this.pipeline = new EntryPipeline(reader(), stripComponents, entryFilter, limits);
    }

    /**
     * Creates a new {@code ArchiveExtractor}.
     *
     * @param archiveInputStream - the {@code A} to the compressed file
     */
    protected ArchiveExtractor(A archiveInputStream) {
        this.archiveInputStream = archiveInputStream;
        this.entryFilter = ACCEPT_ALL;
        this.errorHandler = (x, y) -> ErrorHandlerChoice.BAIL_OUT;
        this.postProcessor = null;
        this.stripComponents = 0;
        this.overwrite = false;
        this.escapingSymlinkPolicy = EscapingSymlinkPolicy.DISALLOW;
        this.limits = ExtractionLimits.NONE;
        this.pipeline = new EntryPipeline(reader(), stripComponents, entryFilter, limits);
    }

    /**
     * Sets the attributes of the output file.
     *
     * @param mode the mode to set
     * @param outputFile the file to set the attributes of
     * @throws IOException if an I/O error occurs
     */
    protected static void setAttributes(int mode, Path outputFile) throws IOException {
        if (isIsOsWindows()) {
            DosFileAttributeView attrs = Files.getFileAttributeView(outputFile, DosFileAttributeView.class);
            if (attrs != null) {
                if ((mode & DOS_READ_ONLY) != 0) attrs.setReadOnly(true);
                if ((mode & DOS_HIDDEN) != 0) attrs.setHidden(true);
            } else {
                LOGGER.trace("Cannot set DOS attributes for file: {}", outputFile);
            }
        } else {
            PosixFileAttributeView attrs = Files.getFileAttributeView(outputFile, PosixFileAttributeView.class);
            if (attrs != null) {
                attrs.setPermissions(fromUnixMode(mode));
            } else {
                LOGGER.trace("Cannot set POSIX attributes for file: {}", outputFile);
            }
        }
    }

    /**
     * Check if the OS is Windows.
     *
     * @return {@code true} if the OS is Windows, {@code false} otherwise
     */
    public static boolean isIsOsWindows() {
        return IS_OS_WINDOWS;
    }

    /**
     * Extracts the archive to the specified directory.
     *
     * @param outputDir the directory to extract the archive to
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if this extractor was already streamed or extracted
     * @throws ArchiveLimitExceededException if the archive breaches one of the configured extraction limits
     * @throws UnsafeEntryException if an entry would be written, or a symlink would point, outside outputDir
     */
    public final void extract(Path outputDir) throws IOException {
        pipeline.start();
        SymlinkGuard guard = new SymlinkGuard(outputDir);
        try {
            drain(outputDir, guard);
            guard.verify();
        } catch (IOException | RuntimeException failure) {
            Optional<UnsafeEntryException> escape = escapeOf(guard);
            if (escape.isPresent() && escape.orElseThrow() != failure) {
                UnsafeEntryException unsafe = escape.orElseThrow();
                if (failure instanceof ArchiveSecurityException) {
                    failure.addSuppressed(unsafe);
                } else {
                    unsafe.addSuppressed(failure);
                    pipeline.release(unsafe);
                    throw unsafe;
                }
            }
            pipeline.release(failure);
            throw failure;
        }
        pipeline.release(null);
    }

    private static Optional<UnsafeEntryException> escapeOf(SymlinkGuard guard) {
        try {
            guard.verify();
            return Optional.empty();
        } catch (UnsafeEntryException escape) {
            return Optional.of(escape);
        }
    }

    private void drain(Path outputDir, SymlinkGuard guard) throws IOException {
        boolean ignoreErrors = false;
        Optional<ArchiveItem> next;
        while ((next = pipeline.advance()).isPresent()) {
            switch (extractItem(outputDir, next.orElseThrow(), ignoreErrors, guard)) {
                case EntryOutcome.Abort() -> {
                    return;
                }
                case EntryOutcome.IgnoreFurtherErrors() -> ignoreErrors = true;
                case EntryOutcome.Continue() -> {
                    /* no-op */
                }
            }
        }
    }

    private EntryOutcome extractItem(Path outputDir, ArchiveItem item, boolean ignoreErrors, SymlinkGuard guard)
            throws IOException {
        while (true) {
            try {
                processItem(outputDir, item, guard);
                return new EntryOutcome.Continue();
            } catch (ArchiveSecurityException unsuppressible) {
                throw unsuppressible;
            } catch (IOException ioException) {
                ErrorHandlerChoice choice =
                        new ExtractionErrorPolicy(errorHandler).handle(ioException, ignoreErrors, item.entry());
                if (choice != RETRY) {
                    return ExtractionErrorPolicy.outcomeOf(choice);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
        archiveInputStream.close();
    }

    /**
     * Close the stream for the current entry. This method is called after the entry has been processed and should close
     * stream opened by {@link #openEntryStream(Entry)}.
     *
     * @param stream the InputStream for the current entry
     * @throws IOException if an I/O error occurs
     */
    @SuppressWarnings("RedundantThrows")
    protected void closeEntryStream(@SuppressWarnings("unused") InputStream stream) throws IOException {
        /* no-op */
    }

    /**
     * Retrieve the next entry from the archive.
     *
     * @return the next entry from the archive, or empty if there are no more entries
     * @throws IOException if an I/O error occurs
     * @since 3.0
     */
    protected abstract Optional<Entry> nextEntry() throws IOException;

    /**
     * Open the stream for the current entry. This method is called before the entry is processed and should open the
     * stream for the current entry.
     *
     * @param entry the entry to open the stream for
     * @return the InputStream for the current entry
     * @throws IOException if an I/O error occurs
     * @since 3.0
     */
    protected abstract InputStream openEntryStream(Entry entry) throws IOException;

    private EntryReader reader() {
        return new EntryReader() {
            @Override
            public Optional<Entry> next() throws IOException {
                return nextEntry();
            }

            @Override
            public InputStream open(Entry entry) throws IOException {
                return openEntryStream(entry);
            }

            @Override
            public void release(InputStream content) throws IOException {
                closeEntryStream(content);
            }
        };
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
    public Stream<ArchiveItem> stream() {
        return pipeline.stream();
    }

    private void writeFile(ArchiveItem item, Path outputFile) throws IOException {
        Entry entry = item.entry();
        if (overwrite || !Files.exists(outputFile)) {
            InputStream content = contentOf(item);
            EntryPaths.makeDirectory(outputFile.getParent());
            try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
                content.transferTo(outputStream);
            }
            if (entry.mode != 0) {
                setAttributes(entry.mode, outputFile);
            }
        } else {
            LOGGER.debug("Skipping file entry: {} (already exists)", entry.name);
        }
    }

    private static InputStream contentOf(ArchiveItem item) throws IOException {
        try {
            return item.content();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Reads exactly {@code declaredSize} bytes from {@code in}, enforcing the configured maximum entry size first. Lets
     * a subclass safely read an entry's content into memory (e.g. a symlink target that has no dedicated header field)
     * without letting a crafted archive force an oversized allocation via its own declared size.
     *
     * @param entryName the name of the entry being read, used in the exception message
     * @param in the stream to read from
     * @param declaredSize the number of bytes to read, as declared by the archive entry
     * @return the bytes read
     * @throws IOException if an I/O error occurs
     * @throws ArchiveLimitExceededException if declaredSize exceeds the configured maximum entry size
     */
    protected byte[] readEntryContent(String entryName, InputStream in, long declaredSize) throws IOException {
        limits.checkDeclaredSize(entryName, declaredSize);
        return IOUtils.toByteArray(in, declaredSize);
    }

    private void processItem(Path outputDir, ArchiveItem item, SymlinkGuard guard) throws IOException {
        Entry entry = item.entry();
        Path outputFile = EntryPaths.entryFile(outputDir, entry.name);
        switch (entry.type) {
            case DIR -> {
                EntryPaths.makeDirectory(outputFile);
                if (entry.mode != 0) {
                    setAttributes(entry.mode, outputFile);
                }
            }
            case FILE -> writeFile(item, outputFile);
            case SYMLINK ->
                new SymlinkExtractor(escapingSymlinkPolicy, overwrite).extract(outputDir, entry, outputFile, guard);
        }
        if (postProcessor != null) {
            postProcessor.accept(entry, outputFile);
        }
    }

    /**
     * Policy for handling symbolic links which point to outside of archive.
     *
     * <p>This is needed to prevent directory traversal attacks when extracting archives from untrusted sources.
     *
     * <p>For example, if an archive contains a symlink {@code foo -> /opt/foo} and the archive is extracted to
     * {@code /foo/bar}, then the symlink should not point to {@code /opt/foo} but rather to {@code /foo/bar/opt/foo}.
     *
     * <p>Example: {@code foo -> /opt/foo}
     *
     * <p>or {@code foo -> ../foo}
     *
     * <p>Extractors default to {@link #DISALLOW}.
     */
    public enum EscapingSymlinkPolicy {
        /**
         * Extract as is with no modification or check. Potentially can point to a completely different object if the
         * archive is transferred from some other host.
         */
        ALLOW,

        /**
         * Rejects targets that are absolute or resolve outside the output directory, including through links created
         * later in the same archive.
         */
        DISALLOW,

        /**
         * Rewrites absolute targets under the output directory, then applies the same checks as {@link #DISALLOW}. For
         * example, when archive contains link to {@code /opt/foo} and archive is extracted to {@code /foo/bar} then the
         * resulting link will be {@code /foo/bar/opt/foo}.
         */
        RELATIVIZE_ABSOLUTE
    }

    /** Specifies the action to be taken by the error handler. */
    public enum ErrorHandlerChoice {
        /**
         * Stop the extraction and return normally. Entries extracted before the failure are left in place; the
         * extractor never deletes anything it has already written.
         */
        ABORT,

        /** Do not handle error, just rethrow the exception */
        BAIL_OUT,

        /** Retry failed entry extraction */
        RETRY,

        /** Skip this entry from extraction */
        SKIP,

        /**
         * Skip this entry and keep extracting the remaining ones, ignoring any further {@link IOException} without
         * consulting the error handler again.
         */
        SKIP_ALL
    }

    /**
     * Builder for creating an {@link ArchiveExtractor}.
     *
     * @param <A> The type of {@link ArchiveInputStream} to read entries from.
     * @param <B> The type of the {@code ArchiveExtractorBuilder} to build from.
     * @param <C> The type of the {@link ArchiveExtractor} to instantiate.
     */
    public abstract static class ArchiveExtractorBuilder<
            A extends ArchiveInputStream<? extends ArchiveEntry>,
            B extends ArchiveExtractorBuilder<A, B, C>,
            C extends ArchiveExtractor<A>> {
        /** How symbolic links whose target escapes the output directory are handled during extraction. */
        protected ArchiveExtractor.EscapingSymlinkPolicy escapingSymlinkPolicy = EscapingSymlinkPolicy.DISALLOW;

        Predicate<Entry> entryFilter = ACCEPT_ALL;

        BiFunction<Entry, ? super IOException, ErrorHandlerChoice> errorHandlerFunction =
                (x, y) -> ErrorHandlerChoice.BAIL_OUT;
        BiConsumer<Entry, ? super Path> postProcessor;
        int stripComponents = 0;
        boolean overwrite = false;
        long maxEntries = UNLIMITED;
        long maxEntrySize = UNLIMITED;
        long maxTotalSize = UNLIMITED;

        final Optional<Closeable> ownedStream;

        /**
         * Default constructor for ArchiveExtractor.
         *
         * <p><b>Warning:</b> Use of this constructor does not provide a comment or initialize required fields. It is
         * recommended to use the builder or parameterized constructors instead.
         */
        protected ArchiveExtractorBuilder() {
            this.ownedStream = Optional.empty();
        }

        /**
         * Constructor for builders that read from a stream.
         *
         * @param stream the stream the archive is read from
         * @param owned whether the builder opened {@code stream} itself, so a failed build closes it
         */
        protected ArchiveExtractorBuilder(Closeable stream, boolean owned) {
            this.ownedStream = owned ? Optional.of(stream) : Optional.empty();
        }

        /**
         * Sets predicate to be used when entries are being extracted. The predicate applies to both {@link #stream()}
         * and {@link #extract(Path)} and sees each entry after strip-components: with {@code stripComponents(1)}, match
         * {@code a.txt}, not {@code root/a.txt}.
         *
         * @param entryPredicate the Predicate to filter entries to be extract from the archive.
         * @return the instance of the {@link ArchiveExtractor.ArchiveExtractorBuilder}
         */
        public B filter(@Nullable Predicate<Entry> entryPredicate) {
            this.entryFilter = entryPredicate != null ? entryPredicate : ACCEPT_ALL;
            return getThis();
        }

        /**
         * Sets the error handler for the extractor.
         *
         * <p>An {@link ArchiveSecurityException} always propagates; the handler is not consulted for it.
         *
         * @param errorHandlerFunction the error handler to set
         * @return the instance of the {@link ArchiveExtractor.ArchiveExtractorBuilder}
         */
        public B errorHandler(BiFunction<Entry, ? super IOException, ErrorHandlerChoice> errorHandlerFunction) {
            this.errorHandlerFunction = errorHandlerFunction;
            return getThis();
        }

        /**
         * Sets the escaping symlink policy for the extractor. Defaults to {@link EscapingSymlinkPolicy#DISALLOW}.
         *
         * @param policy the escaping symlink policy to set
         * @return the instance of the {@link ArchiveExtractor.ArchiveExtractorBuilder}
         */
        public B escapingSymlinkPolicy(ArchiveExtractor.EscapingSymlinkPolicy policy) {
            this.escapingSymlinkPolicy = policy;
            return getThis();
        }

        /**
         * Sets the post processor for the extractor.
         *
         * @param entryBiConsumer the post processor to set
         * @return the instance of the {@link ArchiveExtractor.ArchiveExtractorBuilder}
         */
        public B postProcessor(BiConsumer<Entry, ? super Path> entryBiConsumer) {
            this.postProcessor = entryBiConsumer;
            return getThis();
        }

        /**
         * Sets the number of leading path components to strip from the entries. Applies to both {@link #stream()} and
         * {@link #extract(Path)}, before the filter runs.
         *
         * @param level the number of leading path components to strip
         * @return the instance of the {@link ArchiveExtractor.ArchiveExtractorBuilder}
         */
        public B stripComponents(int level) {
            this.stripComponents = level;
            return getThis();
        }

        /**
         * Sets whether to overwrite existing files.
         *
         * @param overwrite whether to overwrite existing files
         * @return the instance of the {@link ArchiveExtractor.ArchiveExtractorBuilder}
         */
        public B overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return getThis();
        }

        /**
         * Sets the maximum number of entries the extractor will process before aborting. Counts entries that pass
         * strip-components and the filter. In {@link #stream()} a breach surfaces as
         * {@link java.io.UncheckedIOException} wrapping {@link ArchiveLimitExceededException}.
         *
         * @param maxEntries the maximum number of entries, or {@link ArchiveExtractor#UNLIMITED} to disable the limit
         * @return the instance of the {@link ArchiveExtractor.ArchiveExtractorBuilder}
         * @since 3.1
         */
        public B maxEntries(long maxEntries) {
            this.maxEntries = maxEntries;
            return getThis();
        }

        /**
         * Sets the maximum number of bytes a single entry may expand to before the extractor aborts. Counts bytes
         * actually read from the entry's {@code content()}.
         *
         * @param maxEntrySize the maximum size of a single entry in bytes, or {@link ArchiveExtractor#UNLIMITED} to
         *     disable the limit
         * @return the instance of the {@link ArchiveExtractor.ArchiveExtractorBuilder}
         * @since 3.1
         */
        public B maxEntrySize(long maxEntrySize) {
            this.maxEntrySize = maxEntrySize;
            return getThis();
        }

        /**
         * Sets the maximum number of bytes the whole archive may expand to before the extractor aborts. Counts bytes
         * actually read from entries' {@code content()}.
         *
         * @param maxTotalSize the maximum total extracted size in bytes, or {@link ArchiveExtractor#UNLIMITED} to
         *     disable the limit
         * @return the instance of the {@link ArchiveExtractor.ArchiveExtractorBuilder}
         * @since 3.1
         */
        public B maxTotalSize(long maxTotalSize) {
            this.maxTotalSize = maxTotalSize;
            return getThis();
        }

        /**
         * get the current instance of the object
         *
         * @return current instance
         */
        protected abstract B getThis();

        /**
         * Build a {@code A} from the given {@code InputStream}. If you want to combine an archive format with a
         * compression format - like when reading a `tar.gz` file - you wrap the {@code ArchiveInputStream} around
         *
         * <pre>{@code
         * return new TarArchiveInputStream(new GzipCompressorInputStream(inputStream));
         * }</pre>
         *
         * @return a {@code A} from the given {@code InputStream}
         * @throws IOException - if the {@code A} could not be created
         */
        public abstract A buildArchiveInputStream() throws IOException;

        /**
         * Use this method to build an instance of the {@link ArchiveExtractor}, use
         * {@link ArchiveExtractor#ArchiveExtractor(ArchiveExtractor.ArchiveExtractorBuilder)} to pass in instance of
         * this builder
         *
         * @return an instance of the {@link ArchiveExtractor}
         * @throws IOException thrown by the underlying output stream for I/O errors
         */
        public abstract C build() throws IOException;
    }

    /**
     * An archive entry as callers see it: name after normalisation, type, Unix mode (0 when the format has none),
     * symlink target, last-modified time and uncompressed size when the format records them.
     *
     * <p>It is recommended to use {@link #name} as a key for the entry, as it is normalized and trimmed.
     *
     * @param name the normalised entry name
     * @param type the entry type
     * @param mode the Unix mode, or 0 when unknown
     * @param linkTarget the symlink target, present only for {@link Type#SYMLINK}
     * @param lastModified the last-modified time, when the archive records one
     * @param size the uncompressed size, when known before reading the content
     */
    public record Entry(
            String name,
            Type type,
            int mode,
            Optional<String> linkTarget,
            Optional<FileTime> lastModified,
            OptionalLong size) {

        /**
         * Normalizes the name by trimming whitespace, replacing backslashes with forward slashes and removing leading
         * and trailing slashes; a blank link target and a negative size become empty.
         *
         * @throws NullPointerException if any component is null
         */
        public Entry {
            Objects.requireNonNull(type, "type");
            linkTarget = Objects.requireNonNull(linkTarget, "linkTarget").filter(t -> !t.isBlank());
            Objects.requireNonNull(lastModified, "lastModified");
            size = Objects.requireNonNull(size, "size").isPresent() && size.getAsLong() < 0
                    ? OptionalLong.empty()
                    : size;
            name = Objects.requireNonNull(name, "name").trim().replace('\\', '/');
            int s = 0;
            int e = name.length() - 1;
            while (s < e && name.charAt(s) == '/') s++;
            while (e >= s && name.charAt(e) == '/') e--;
            name = name.substring(s, e + 1);
        }

        /**
         * Creates an entry without link target, last-modified time or size.
         *
         * @param name the name of the entry
         * @param type the type of the entry
         * @param mode the mode of the entry
         */
        public Entry(String name, Type type, int mode) {
            this(name, type, mode, Optional.empty(), Optional.empty(), OptionalLong.empty());
        }

        /**
         * Creates a FILE or DIR entry with mode 0.
         *
         * @param name the name of the entry
         * @param isDirectory whether the entry is a directory
         */
        public Entry(String name, boolean isDirectory) {
            this(name, isDirectory ? Type.DIR : Type.FILE, 0);
        }

        /**
         * Returns a copy with the given link target; a null or blank target yields an empty one.
         *
         * @param target the symlink target
         * @return the copy
         */
        public Entry withLinkTarget(@Nullable String target) {
            Optional<String> t = Optional.ofNullable(target).filter(s -> !s.isBlank());
            return new Entry(name, type, mode, t, lastModified, size);
        }

        /**
         * Returns a copy with the given metadata; a null date or negative size yields an empty value.
         *
         * @param modified the last-modified date
         * @param bytes the uncompressed size
         * @return the copy
         */
        public Entry withMetadata(@Nullable Date modified, long bytes) {
            return new Entry(
                    name,
                    type,
                    mode,
                    linkTarget,
                    Optional.ofNullable(modified).map(d -> FileTime.fromMillis(d.getTime())),
                    bytes < 0 ? OptionalLong.empty() : OptionalLong.of(bytes));
        }

        /**
         * Returns a copy with the given name, normalised.
         *
         * @param newName the new name
         * @return the copy
         */
        public Entry withName(String newName) {
            return new Entry(newName, type, mode, linkTarget, lastModified, size);
        }

        /** Type of the entry. */
        public enum Type {
            /** File */
            FILE,
            /** Directory */
            DIR,
            /** Symbolic link */
            SYMLINK
        }
    }
}
