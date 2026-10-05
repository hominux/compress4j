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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.exceptions.UnsafeEntryException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisabledOnOs(OS.WINDOWS)
class SymlinkGuardTest {

    @TempDir
    Path tmp;

    private static byte[] chainArchive() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(bytes)) {
            symlink(tar, "A", "s/n/n/n/k/../../../..");
            dir(tar, "s/");
            dir(tar, "s/k/");
            symlink(tar, "s/n", ".");
        }
        return bytes.toByteArray();
    }

    private static void symlink(TarArchiveOutputStream tar, String name, String target) throws IOException {
        var e = new TarArchiveEntry(name, TarConstants.LF_SYMLINK);
        e.setLinkName(target);
        tar.putArchiveEntry(e);
        tar.closeArchiveEntry();
    }

    private static void dir(TarArchiveOutputStream tar, String name) throws IOException {
        tar.putArchiveEntry(new TarArchiveEntry(name));
        tar.closeArchiveEntry();
    }

    @Test
    void chainedSymlinkEscapeIsRejectedAndRemovedByDefault() throws IOException {
        // Given
        Path out = Files.createDirectories(tmp.resolve("a/b/out"));

        try (var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(chainArchive()))
                .build()) {
            // Then
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOf(UnsafeEntryException.class)
                    .hasMessageContaining("A");
        }
        assertThat(Files.exists(out.resolve("A"), LinkOption.NOFOLLOW_LINKS)).isFalse();
    }

    @Test
    void chainIsKeptWhenAllowed() throws IOException {
        Path out = Files.createDirectories(tmp.resolve("a/b/out"));
        try (var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(chainArchive()))
                .escapingSymlinkPolicy(EscapingSymlinkPolicy.ALLOW)
                .build()) {
            extractor.extract(out);
        }
        assertThat(Files.isSymbolicLink(out.resolve("A"))).isTrue();
    }

    @Test
    void realLocationFollowsExistingLinksAndCollapsesMissingComponents() throws IOException {
        // Given
        Path base = Files.createDirectories(tmp.resolve("base"));
        Files.createDirectories(base.resolve("d"));
        Files.createSymbolicLink(base.resolve("up"), Path.of(".."));

        // Then
        assertThat(SymlinkGuard.realLocation(base.resolve("up/x")))
                .isEqualTo(tmp.toRealPath().resolve("x"));
        assertThat(SymlinkGuard.realLocation(base.resolve("missing/../d")))
                .isEqualTo(base.toRealPath().resolve("d"));
    }

    private static byte[] swappedParentArchive() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(bytes)) {
            dir(tar, "s1/");
            dir(tar, "s1/k/");
            dir(tar, "s2/");
            symlink(tar, "L", "s1");
            symlink(tar, "L/A", "n/n/n/k/../../../..");
            symlink(tar, "s1/n", ".");
            symlink(tar, "L", "s2");
            symlink(tar, "s2/A", ".");
        }
        return bytes.toByteArray();
    }

    @ParameterizedTest
    @EnumSource(
            value = EscapingSymlinkPolicy.class,
            names = {"DISALLOW", "RELATIVIZE_ABSOLUTE"})
    void linkCreatedThroughSwappedParentSymlinkIsStillChecked(EscapingSymlinkPolicy policy) throws IOException {
        // Given
        Path out = Files.createDirectories(tmp.resolve("a/b/out"));

        try (var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(swappedParentArchive()))
                .escapingSymlinkPolicy(policy)
                .overwrite(true)
                .build()) {
            // Then
            assertThatThrownBy(() -> extractor.extract(out)).isInstanceOf(UnsafeEntryException.class);
        }
        Path realOut = out.toRealPath();
        try (Stream<Path> paths = Files.walk(out)) {
            for (Path p : paths.filter(Files::isSymbolicLink).toList()) {
                assertThat(SymlinkGuard.realLocation(p)).startsWith(realOut);
            }
        }
    }

    private static byte[] chainThen(java.util.function.Consumer<TarArchiveOutputStream> extra) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(bytes)) {
            symlink(tar, "A", "s/n/n/n/k/../../../..");
            dir(tar, "s/");
            dir(tar, "s/k/");
            symlink(tar, "s/n", ".");
            extra.accept(tar);
        }
        return bytes.toByteArray();
    }

    private static void file(TarArchiveOutputStream tar, String name) {
        try {
            var e = new TarArchiveEntry(name);
            e.setSize(1);
            tar.putArchiveEntry(e);
            tar.write('x');
            tar.closeArchiveEntry();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void relativeOutputDirStillReportsUnsafeEntryAndRemovesLink() throws IOException {
        // Given
        Path out = Path.of("build/tmp/rel-guard-" + System.nanoTime() + "/out");
        Files.createDirectories(out);
        try (var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(chainArchive()))
                .build()) {
            // Then
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOf(UnsafeEntryException.class)
                    .hasMessageContaining("A");
            assertThat(Files.exists(out.resolve("A"), LinkOption.NOFOLLOW_LINKS))
                    .isFalse();
        } finally {
            Files.deleteIfExists(out.resolve("s/n"));
        }
    }

    @Test
    void messageNamesEntryRelativeToOutputDirReachedThroughAlias() throws IOException {
        // Given
        Path real = Files.createDirectories(tmp.resolve("a/b/out"));
        Path alias = Files.createSymbolicLink(tmp.resolve("alias"), real);

        try (var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(chainArchive()))
                .build()) {
            // Then
            assertThatThrownBy(() -> extractor.extract(alias))
                    .isInstanceOf(UnsafeEntryException.class)
                    .hasMessageEndingWith(": A");
        }
    }

    @Test
    void verifyRethrowsTheSameExceptionWithoutDuplicates() throws IOException {
        // Given
        Path out = Files.createDirectories(tmp.resolve("out"));
        Path link = Files.createSymbolicLink(out.resolve("l"), Path.of("../.."));
        var guard = new SymlinkGuard(out);
        guard.remember(link);

        // When
        UnsafeEntryException first = null;
        try {
            guard.verify();
        } catch (UnsafeEntryException e) {
            first = e;
        }

        // Then
        var firstFailure = first;
        assertThat(firstFailure).isNotNull();
        assertThatThrownBy(guard::verify).isSameAs(firstFailure);
        assertThat(firstFailure.getSuppressed()).isEmpty();
    }

    @Test
    void guardRejectionAfterNonSecurityFailureIsPrimary() throws IOException {
        // Given
        Path out = Files.createDirectories(tmp.resolve("a/b/out"));
        byte[] archive = chainThen(tar -> file(tar, "s"));

        try (var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(archive))
                .overwrite(true)
                .build()) {
            // Then
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOf(UnsafeEntryException.class)
                    .hasMessageContaining("A")
                    .satisfies(e ->
                            assertThat(e.getSuppressed()).hasSize(1).noneMatch(UnsafeEntryException.class::isInstance));
        }
    }

    @Test
    void securityPrimaryStaysPrimaryWithGuardSuppressedOnce() throws IOException {
        // Given
        Path out = Files.createDirectories(tmp.resolve("a/b/out"));
        byte[] archive = chainThen(tar -> file(tar, "../evil"));

        try (var extractor =
                TarArchiveExtractor.builder(new ByteArrayInputStream(archive)).build()) {
            // Then
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOf(UnsafeEntryException.class)
                    .hasMessageNotContaining("points outside")
                    .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1).allMatch(x -> x.getMessage()
                            .contains("points outside")));
        }
    }

    @Test
    void verifyNeverDeletesRecordedRegularFileOrDirectory() throws IOException {
        // Given
        Path out = Files.createDirectories(tmp.resolve("out"));
        Path file = Files.writeString(out.resolve("f"), "x");
        Path dir = Files.createDirectories(out.resolve("d"));
        var guard = new SymlinkGuard(out);
        guard.remember(file);
        guard.remember(dir);

        // When
        guard.verify();

        // Then
        assertThat(file).isRegularFile();
        assertThat(dir).isDirectory();
    }
}
