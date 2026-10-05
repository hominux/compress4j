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

import com.hominux.compress4j.exceptions.UnsafeEntryException;
import com.hominux.compress4j.utils.BuildFailureCleanup;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;

/**
 * Transitional base for formats not yet moved onto {@link ArchiveCreator}.
 *
 * <p>A creator is not thread-safe.
 *
 * @param <A> The type of {@link ArchiveOutputStream} to write entries to.
 * @since 2.2
 */
public abstract class LegacyArchiveCreator<A extends ArchiveOutputStream<? extends ArchiveEntry>> implements Closeable {

    private final Predicate<? super EntrySource> filter;

    private boolean failed;

    /** Archive output stream to be used for archiving. */
    protected final A archiveOutputStream;

    /**
     * Create a new ArchiveCreator with the given output stream and options.
     *
     * @param builder the archive output stream builder
     * @param <B> The type of {@link ArchiveCreatorBuilder} to build from.
     * @param <C> The type of the {@link LegacyArchiveCreator} to instantiate.
     * @throws IOException if an I/O error occurred
     */
    protected <B extends ArchiveCreatorBuilder<A, B, C>, C extends LegacyArchiveCreator<A>> LegacyArchiveCreator(
            B builder) throws IOException {
        this(BuildFailureCleanup.build(builder.ownedStream, builder::buildArchiveOutputStream), builder.filter);
    }

    /**
     * Create a new ArchiveCreator.
     *
     * @param archiveOutputStream the archive output stream
     */
    protected LegacyArchiveCreator(A archiveOutputStream) {
        this(archiveOutputStream, source -> true);
    }

    private LegacyArchiveCreator(A archiveOutputStream, Predicate<? super EntrySource> filter) {
        this.archiveOutputStream = archiveOutputStream;
        this.filter = filter;
    }

    /**
     * Write a directory entry to the archive.
     *
     * @param name name of the entry
     * @param mode Unix permission bits, or {@code 0} when unknown
     * @param lastModified last modification time of the directory
     * @throws IOException if an I/O error occurred
     */
    protected abstract void writeDirectory(String name, int mode, FileTime lastModified) throws IOException;

    /**
     * Write a file entry to the archive.
     *
     * @param name name of the entry
     * @param content content of the file
     * @param size content length in bytes; present whenever {@link #requiresSize()} is true
     * @param mode Unix permission bits, or {@code 0} when unknown
     * @param lastModified last modification time of the file
     * @throws IOException if an I/O error occurred
     */
    protected abstract void writeFile(
            String name, InputStream content, OptionalLong size, int mode, FileTime lastModified) throws IOException;

    /**
     * Write a symbolic link entry to the archive.
     *
     * @param name name of the entry
     * @param target target of the symbolic link
     * @param mode Unix permission bits, or {@code 0} when unknown
     * @param lastModified last modification time of the link
     * @throws IOException if an I/O error occurred
     */
    protected abstract void writeSymlink(String name, String target, int mode, FileTime lastModified)
            throws IOException;

    /**
     * Whether {@link #writeFile} needs the size known up front.
     *
     * @return {@code true} if {@link #writeFile} requires a known size
     */
    protected abstract boolean requiresSize();

    /**
     * Writes one entry. The name is sanitised (backslashes become slashes, leading and trailing slashes are removed)
     * and checked for safety before the filter sees it; an entry the filter rejects is skipped. Once a write fails,
     * every later call throws {@link IllegalStateException}.
     *
     * @param source the entry to write
     * @throws UnsafeEntryException if the name starts with a drive letter, contains a NUL character or has a {@code ..}
     *     segment
     * @throws IllegalArgumentException if the sanitised name is blank, or a kept file's size is unknown and this format
     *     records sizes before content
     * @throws IllegalStateException if an earlier write failed
     * @throws IOException if writing fails or a file's content does not match its declared size; the archive is then
     *     incomplete
     */
    public final void add(EntrySource source) throws IOException {
        if (failed) {
            throw new IllegalStateException("An earlier write failed; the archive is incomplete");
        }
        EntrySource named = renamed(source, EntryNames.checked(source.name()));
        if (!filter.test(named)) {
            return;
        }
        requireSizeIfNeeded(named);
        try {
            write(named);
        } catch (IOException | RuntimeException e) {
            failed = true;
            throw e;
        }
    }

    /**
     * Writes every entry in order through {@link #add}, stopping at the first failure. The stream is consumed but not
     * closed.
     *
     * @param sources the entries to write
     * @throws UnsafeEntryException if an entry name is unsafe, as for {@link #add}
     * @throws IllegalArgumentException if an entry name is blank, or a file's size is unknown and the format records
     *     sizes first, as for {@link #add}
     * @throws IllegalStateException if an earlier write failed
     * @throws IOException if the stream fails with an {@link UncheckedIOException}, whose cause is thrown, or writing
     *     fails
     */
    public final void addAll(Stream<? extends EntrySource> sources) throws IOException {
        Iterator<? extends EntrySource> it = sources.iterator();
        while (true) {
            EntrySource next;
            try {
                if (!it.hasNext()) {
                    return;
                }
                next = it.next();
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            add(next);
        }
    }

    boolean accepts(EntrySource source) {
        return filter.test(source);
    }

    private void write(EntrySource source) throws IOException {
        switch (source) {
            case EntrySource.Directory(var name, var mode, var lastModified) ->
                writeDirectory(name, mode, lastModified);
            case EntrySource.Symlink(var name, var target, var mode, var lastModified) ->
                writeSymlink(name, target, mode, lastModified);
            case EntrySource.File f -> writeFile(f);
        }
    }

    private void writeFile(EntrySource.File f) throws IOException {
        try (InputStream in = f.content().get()) {
            if (f.size().isEmpty()) {
                writeFile(f.name(), in, f.size(), f.mode(), f.lastModified());
                return;
            }
            var sized = new DeclaredSizeInputStream(in, f.name(), f.size().getAsLong());
            writeFile(f.name(), sized, f.size(), f.mode(), f.lastModified());
            sized.requireExhausted();
        }
    }

    private void requireSizeIfNeeded(EntrySource source) {
        if (source instanceof EntrySource.File f && f.size().isEmpty() && requiresSize()) {
            throw new IllegalArgumentException("Entry '" + f.name() + "' has no size, which this format"
                    + " records before the content; wrap it with EntrySource.buffered");
        }
    }

    private static EntrySource renamed(EntrySource source, String name) {
        return switch (source) {
            case EntrySource.File f -> new EntrySource.File(name, f.mode(), f.lastModified(), f.size(), f.content());
            case EntrySource.Directory d -> new EntrySource.Directory(name, d.mode(), d.lastModified());
            case EntrySource.Symlink s -> new EntrySource.Symlink(name, s.target(), s.mode(), s.lastModified());
        };
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
        archiveOutputStream.close();
    }

    /**
     * Add a directory recursively to the archive. The last modification time of the directory will be used as the last
     * modification time of the entry.
     *
     * <p>The builder's filter applies; a rejected directory skips its whole subtree. A socket, FIFO or device in the
     * tree fails the walk with {@link IllegalArgumentException}.
     *
     * @param directory directory to add
     * @throws IOException if an I/O error occurred
     */
    public final void addDirectoryRecursively(Path directory) throws IOException {
        addDirectoryRecursively("", directory);
    }

    /**
     * Add a directory recursively to the archive. The last modification time of the directory will be used as the last
     * modification time of the entry.
     *
     * <p>The builder's filter applies; a rejected directory skips its whole subtree. A socket, FIFO or device in the
     * tree fails the walk with {@link IllegalArgumentException}.
     *
     * @param topLevelDir prefix for every entry name; empty adds no prefix, a non-empty blank or all-slash value throws
     *     {@link IllegalArgumentException}
     * @param directory directory to add
     * @throws IOException if an I/O error occurred
     */
    public final void addDirectoryRecursively(String topLevelDir, Path directory) throws IOException {
        addDirectoryRecursively(topLevelDir, directory, BasicFileAttributes::lastModifiedTime);
    }

    /**
     * Add a directory recursively to the archive.
     *
     * <p>The builder's filter applies; a rejected directory skips its whole subtree. A socket, FIFO or device in the
     * tree fails the walk with {@link IllegalArgumentException}.
     *
     * @param topLevelDir prefix for every entry name; empty adds no prefix, a non-empty blank or all-slash value throws
     *     {@link IllegalArgumentException}
     * @param directory directory to add
     * @param modTime last modification time of the directory
     * @throws IOException if an I/O error occurred
     */
    public final void addDirectoryRecursively(String topLevelDir, Path directory, FileTime modTime) throws IOException {
        addDirectoryRecursively(topLevelDir, directory, attrs -> modTime);
    }

    private void addDirectoryRecursively(
            String topLevelDir, Path directory, Function<BasicFileAttributes, FileTime> modTime) throws IOException {
        DirectoryTreeWalker.walk(this::accepts, this::add, topLevelDir, directory, modTime);
    }

    /**
     * Add {@code path}, named by its file name, through {@link #add}. The file's last modification time is used.
     *
     * @param path path to add
     * @throws IOException if an I/O error occurred
     */
    public final void addFile(Path path) throws IOException {
        BasicFileAttributes attrs = readAttributes(path);
        add(PathSources.of(path.getFileName().toString(), path, attrs, attrs.lastModifiedTime()));
    }

    private static BasicFileAttributes readAttributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Build and instance of {@link LegacyArchiveCreator}
     *
     * @param <A> The type of {@link ArchiveOutputStream} to write entries to.
     * @param <B> The type of {@link ArchiveCreatorBuilder}
     * @param <C> The type of {@link LegacyArchiveCreator}
     */
    public abstract static class ArchiveCreatorBuilder<
            A extends ArchiveOutputStream<? extends ArchiveEntry>,
            B extends ArchiveCreatorBuilder<A, B, C>,
            C extends LegacyArchiveCreator<A>> {
        /** Output stream to write the archive to. */
        protected final OutputStream outputStream;

        final Optional<Closeable> ownedStream;

        Predicate<? super EntrySource> filter = source -> true;

        /**
         * Create a new {@link ArchiveCreatorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        protected ArchiveCreatorBuilder(OutputStream outputStream) {
            this(outputStream, false);
        }

        /**
         * Create a new {@link ArchiveCreatorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         * @param owned whether the builder opened {@code outputStream} itself, so a failed build closes it
         */
        protected ArchiveCreatorBuilder(OutputStream outputStream, boolean owned) {
            this.outputStream = outputStream;
            this.ownedStream = owned ? Optional.of(outputStream) : Optional.empty();
        }

        /**
         * Sets which entries are written; the predicate sees each entry after its name is sanitised. A rejected
         * directory added by {@code addDirectoryRecursively} skips its whole subtree. The predicate may run more than
         * once for the same entry, so it should have no side effects.
         *
         * @param predicate the entries to keep
         * @return this builder
         */
        public B filter(Predicate<? super EntrySource> predicate) {
            this.filter = Objects.requireNonNull(predicate, "predicate");
            return getThis();
        }

        /**
         * Returns this builder with its concrete type.
         *
         * @return this builder
         */
        protected abstract B getThis();

        /**
         * Start a new archive. Entries can be included in the archive using the putEntry method, and then the archive
         * should be closed using its close method. In addition, options can be applied to the underlying stream. E.g.
         * archiving level.
         *
         * <ol>
         *   <li>Use {@link #outputStream} as underlying output stream to which to write the archive.
         * </ol>
         *
         * @return new archive object for use in putEntry
         * @throws IOException thrown by the underlying output stream for I/O errors
         */
        public abstract A buildArchiveOutputStream() throws IOException;

        /**
         * Use this method to build an instance of the {@link LegacyArchiveCreator}, use
         * {@link LegacyArchiveCreator#LegacyArchiveCreator(ArchiveCreatorBuilder)} to pass in instance of this builder
         *
         * @return an instance of the {@link LegacyArchiveCreator}
         * @throws IOException thrown by the underlying output stream for I/O errors
         */
        public abstract C build() throws IOException;
    }
}
