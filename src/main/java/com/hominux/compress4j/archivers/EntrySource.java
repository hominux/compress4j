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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import org.apache.commons.io.function.IOSupplier;

/**
 * Something to write into an archive: a file, a directory or a symlink, with its name, Unix permission bits ({@code 0}
 * when unknown, leaving the format's default) and last-modified time.
 *
 * @since 5.0
 */
public sealed interface EntrySource permits EntrySource.File, EntrySource.Directory, EntrySource.Symlink {

    /**
     * Returns the entry name.
     *
     * @return the relative, slash-separated name
     */
    String name();

    /**
     * Returns the permission bits.
     *
     * @return the Unix permission bits, or {@code 0} when unknown
     */
    int mode();

    /**
     * Returns the modification time.
     *
     * @return the last-modified time
     */
    FileTime lastModified();

    /**
     * A regular file. {@code content} is opened once, by the creator, which closes it after writing. {@code size} must
     * be present for formats that record sizes before content (tar, ar, cpio); see {@link #buffered}. A present
     * {@code size} must match the content exactly, or the write fails.
     *
     * @param name the entry name
     * @param mode the permission bits, masked to {@code 07777}
     * @param lastModified the last-modified time
     * @param size the content size, if known
     * @param content opens the content
     */
    record File(String name, int mode, FileTime lastModified, OptionalLong size, IOSupplier<InputStream> content)
            implements EntrySource {
        /**
         * Masks {@code mode} to permission bits and rejects null components.
         *
         * @throws IllegalArgumentException if {@code size} is negative
         */
        public File {
            Objects.requireNonNull(name, "name");
            requireModified(lastModified);
            Objects.requireNonNull(size, "size");
            if (size.isPresent() && size.getAsLong() < 0) {
                throw new IllegalArgumentException("File '" + name + "' has a negative size: " + size.getAsLong());
            }
            Objects.requireNonNull(content, "content");
            mode &= 07777;
        }
    }

    /**
     * A directory.
     *
     * @param name the entry name
     * @param mode the permission bits, masked to {@code 07777}
     * @param lastModified the last-modified time
     */
    record Directory(String name, int mode, FileTime lastModified) implements EntrySource {
        /** Rejects null components. */
        public Directory {
            Objects.requireNonNull(name, "name");
            requireModified(lastModified);
            mode &= 07777;
        }
    }

    /**
     * A symbolic link to {@code target}, stored verbatim.
     *
     * @param name the entry name
     * @param target the link target, not blank
     * @param mode the permission bits, masked to {@code 07777}
     * @param lastModified the last-modified time
     */
    record Symlink(String name, String target, int mode, FileTime lastModified) implements EntrySource {
        /**
         * Rejects null components.
         *
         * @throws IllegalArgumentException if {@code target} is blank
         */
        public Symlink {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(target, "target");
            requireModified(lastModified);
            if (target.isBlank()) {
                throw new IllegalArgumentException("Symlink '" + name + "' has a blank target");
            }
            mode &= 07777;
        }
    }

    /**
     * Describes {@code path} without following symlinks, named by its location relative to {@code base}. A
     * {@code Files.walk(base)} stream starts with {@code base} itself; skip it with {@code .skip(1)}.
     *
     * @param base the directory entry names are relative to
     * @param path a file, directory or symlink under {@code base}
     * @return the source
     * @throws IllegalArgumentException if {@code path} is {@code base}, lies outside it, or is not a regular file,
     *     directory or symlink
     * @throws IOException if the attributes cannot be read
     */
    static EntrySource of(Path base, Path path) throws IOException {
        Path normalisedBase = base.toAbsolutePath().normalize();
        Path normalisedPath = path.toAbsolutePath().normalize();
        if (normalisedPath.equals(normalisedBase) || !normalisedPath.startsWith(normalisedBase)) {
            throw new IllegalArgumentException(path + " is not strictly inside " + base);
        }
        String name = normalisedBase.relativize(normalisedPath).toString().replace('\\', '/');
        BasicFileAttributes attrs =
                Files.readAttributes(normalisedPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return PathSources.of(name, normalisedPath, attrs, attrs.lastModifiedTime());
    }

    /**
     * An in-memory file with unknown mode, modified now.
     *
     * @param name the entry name
     * @param content the file content
     * @return the source
     */
    static File file(String name, byte[] content) {
        byte[] copy = content.clone();
        return new File(
                name,
                0,
                FileTime.from(Instant.now()),
                OptionalLong.of(copy.length),
                () -> new ByteArrayInputStream(copy));
    }

    /**
     * Copies {@code source}'s content to a temp file under {@code tempDir} so its size is known. The temp file is
     * deleted when the returned source's content is closed; if the creator never opens it (for example because the
     * entry is filtered out), it remains under {@code tempDir}.
     *
     * @param source a file whose size may be unknown
     * @param tempDir the directory for the temp file
     * @return a file source with a known size
     * @throws IOException if the content cannot be copied; the temp file is removed
     */
    static File buffered(File source, Path tempDir) throws IOException {
        Path spool = Files.createTempFile(tempDir, "compress4j-", ".tmp");
        long size;
        try (InputStream in = source.content().get()) {
            Files.copy(in, spool, REPLACE_EXISTING);
            size = Files.size(spool);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(spool);
            throw e;
        }
        return new File(
                source.name(),
                source.mode(),
                source.lastModified(),
                OptionalLong.of(size),
                () -> Files.newInputStream(spool, StandardOpenOption.DELETE_ON_CLOSE));
    }

    private static void requireModified(FileTime lastModified) {
        Objects.requireNonNull(lastModified, "lastModified");
    }
}
