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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Adds a directory tree to an archive creator, applying its entry filter. */
final class DirectoryTreeWalker extends SimpleFileVisitor<Path> {

    @FunctionalInterface
    interface EntryAdder {
        void add(EntrySource source) throws IOException;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryTreeWalker.class);

    private final Path root;
    private final String prefix;
    private final Function<BasicFileAttributes, FileTime> modTime;
    private final Predicate<EntrySource> accepts;
    private final EntryAdder adder;

    private DirectoryTreeWalker(
            Predicate<EntrySource> accepts,
            EntryAdder adder,
            Path root,
            String prefix,
            Function<BasicFileAttributes, FileTime> modTime) {
        this.root = root;
        this.prefix = prefix;
        this.modTime = modTime;
        this.accepts = accepts;
        this.adder = adder;
    }

    /**
     * Add a directory recursively to the archive using a {@code SimpleFileVisitor}.
     *
     * @param accepts the creator's entry filter
     * @param adder adds an entry to the creator
     * @param topLevelDir when a non-empty value specified, create a directory entry with this name and add all entries
     * @param directory directory to add
     * @param modTime resolves each entry's modification time from the attributes of the visited path
     * @throws IOException if an I/O error occurred
     */
    static void walk(
            Predicate<EntrySource> accepts,
            EntryAdder adder,
            String topLevelDir,
            Path directory,
            Function<BasicFileAttributes, FileTime> modTime)
            throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Path is not a directory: " + directory);
        }
        topLevelDir = topLevelDir.isEmpty() ? "" : EntryNames.sanitised(topLevelDir);
        LOGGER.atTrace().log("dir={} topLevelDir={}", directory, topLevelDir);

        Files.walkFileTree(directory, new DirectoryTreeWalker(accepts, adder, directory, topLevelDir, modTime));

        LOGGER.atTrace().log(".");
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        String name = dir.equals(root) ? prefix : entryName(dir);
        if (name.isEmpty()) {
            return FileVisitResult.CONTINUE;
        }
        EntrySource source = PathSources.of(name, dir, attrs, modTime.apply(attrs));
        if (!accepts.test(source)) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        LOGGER.atTrace().log("  {} -> {}/", dir, name);
        adder.add(source);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        String name = entryName(file);
        LOGGER.atTrace().log("  {} -> {}{}", file, name, attrs.isSymbolicLink() ? " symlink" : " size=" + attrs.size());
        adder.add(PathSources.of(name, file, attrs, modTime.apply(attrs)));
        return FileVisitResult.CONTINUE;
    }

    private String entryName(Path fileOrDir) {
        String relativeName = EntryNames.sanitised(root.relativize(fileOrDir).toString());
        return prefix.isEmpty() ? relativeName : prefix + '/' + relativeName;
    }
}
