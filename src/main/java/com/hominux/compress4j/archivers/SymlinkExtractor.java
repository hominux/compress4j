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

import static com.hominux.compress4j.internal.util.FileUtils.checkValidPath;

import com.hominux.compress4j.exceptions.UnsafeEntryException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the symbolic link an archive entry describes, applying the {@link EscapingSymlinkPolicy}.
 *
 * @param policy how to treat links pointing outside the output directory
 * @param overwrite whether an existing file at the link's location is replaced
 */
record SymlinkExtractor(EscapingSymlinkPolicy policy, boolean overwrite) {

    private static final Logger LOGGER = LoggerFactory.getLogger(SymlinkExtractor.class);

    /**
     * Extracts the symlink to the output file.
     *
     * @param outputDir the directory to extract the archive to
     * @param entry the entry to extract
     * @param outputFile the file to extract the entry to
     * @param guard records created links so they can be re-checked after extraction
     * @throws IOException if an I/O error occurs
     */
    void extract(Path outputDir, Entry entry, Path outputFile, SymlinkGuard guard) throws IOException {
        String target = entry.linkTarget()
                .orElseThrow(() -> new IOException("Invalid symlink entry: " + entry.name() + " (empty target)"));

        switch (policy) {
            case DISALLOW -> {
                rejectAbsolute(entry.name(), target);
                verifySymlinkTarget(entry.name(), target, outputDir, outputFile);
            }
            case RELATIVIZE_ABSOLUTE -> {
                target = relativizeIfAbsolute(target, outputDir);
                verifySymlinkTarget(entry.name(), target, outputDir, outputFile);
            }
            case ALLOW -> LOGGER.debug("Extracting symlink entry as is: {} -> {}", entry.name(), target);
        }

        if (overwrite || !Files.exists(outputFile, LinkOption.NOFOLLOW_LINKS)) {
            Path outputTarget = Paths.get(target);
            EntryPaths.makeDirectory(outputFile.getParent());
            Path realLocation = outputFile.getParent().toRealPath().resolve(outputFile.getFileName());
            Files.deleteIfExists(outputFile);
            Files.createSymbolicLink(outputFile, outputTarget);
            if (policy != EscapingSymlinkPolicy.ALLOW) {
                guard.remember(realLocation);
            }
        } else {
            LOGGER.debug("Skipping symlink entry: {} -> {} (already exists)", entry.name(), target);
        }
    }

    private static void rejectAbsolute(String entryName, String linkTarget) throws UnsafeEntryException {
        if (Paths.get(linkTarget).isAbsolute()) {
            throw new UnsafeEntryException("Invalid symlink (absolute path): " + entryName + " -> " + linkTarget);
        }
    }

    private static String relativizeIfAbsolute(String target, Path outputDir) {
        return Paths.get(target).isAbsolute()
                ? Paths.get(outputDir.toString(), target.substring(1)).toString()
                : target;
    }

    /**
     * Verifies that the symlink target is valid.
     *
     * @param entryName the name of the entry
     * @param linkTarget the target of the symlink
     * @param outputDir the directory to extract the archive to
     * @param outputFile the file to extract the entry to
     * @throws UnsafeEntryException if the symlink target is invalid
     */
    private static void verifySymlinkTarget(String entryName, String linkTarget, Path outputDir, Path outputFile)
            throws UnsafeEntryException {
        Path linkTargetPath = outputFile.getParent().resolve(Paths.get(linkTarget));
        if (pointsAtOutputDir(linkTargetPath, outputDir)) {
            return;
        }
        try {
            checkValidPath(linkTargetPath, outputDir);
        } catch (UnsafeEntryException e) {
            throw new UnsafeEntryException(
                    "Invalid symlink (points outside of output directory): " + entryName + " -> " + linkTarget, e);
        }
    }

    private static boolean pointsAtOutputDir(Path linkTargetPath, Path outputDir) throws UnsafeEntryException {
        try {
            return linkTargetPath
                    .toFile()
                    .getCanonicalPath()
                    .equals(outputDir.toFile().getCanonicalPath());
        } catch (IOException e) {
            throw new UnsafeEntryException("Cannot resolve symlink target: " + linkTargetPath, e);
        }
    }
}
