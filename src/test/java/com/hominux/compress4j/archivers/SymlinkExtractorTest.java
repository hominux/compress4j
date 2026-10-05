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

import static com.hominux.compress4j.archivers.EscapingSymlinkPolicy.ALLOW;
import static com.hominux.compress4j.archivers.EscapingSymlinkPolicy.DISALLOW;
import static com.hominux.compress4j.archivers.EscapingSymlinkPolicy.RELATIVIZE_ABSOLUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.exceptions.UnsafeEntryException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class SymlinkExtractorTest {

    @TempDir
    Path outputDir;

    private static Entry link(String target) {
        return new Entry("link", Entry.Type.SYMLINK, 0).withLinkTarget(target);
    }

    private void extract(SymlinkExtractor extractor, Entry entry) throws IOException {
        extractor.extract(outputDir, entry, outputDir.resolve(entry.name()), new SymlinkGuard(outputDir));
    }

    @Test
    void allow_createsTheLinkAsIs() throws IOException {
        extract(new SymlinkExtractor(ALLOW, false), link("/opt/foo"));
        assertThat(Files.readSymbolicLink(outputDir.resolve("link"))).isEqualTo(Path.of("/opt/foo"));
    }

    @Test
    void relativizeAbsolute_rebasesAbsoluteTargetsUnderTheOutputDir() throws IOException {
        extract(new SymlinkExtractor(RELATIVIZE_ABSOLUTE, false), link("/opt/foo"));
        assertThat(Files.readSymbolicLink(outputDir.resolve("link"))).isEqualTo(outputDir.resolve("opt/foo"));
    }

    @Test
    void disallow_rejectsAbsoluteTargets() {
        assertThatThrownBy(() -> extract(new SymlinkExtractor(DISALLOW, false), link("/opt/foo")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid symlink (absolute path): link -> /opt/foo");
    }

    @Test
    void disallow_rejectsTargetsEscapingTheOutputDir() {
        assertThatThrownBy(() -> extract(new SymlinkExtractor(DISALLOW, false), link("../outside")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid symlink (points outside of output directory): link -> ../outside");
    }

    @Test
    void disallow_acceptsTargetsInsideTheOutputDir() throws IOException {
        extract(new SymlinkExtractor(DISALLOW, false), link("inside/file"));
        assertThat(Files.readSymbolicLink(outputDir.resolve("link"))).isEqualTo(Path.of("inside/file"));
    }

    @Test
    void rejectsBlankTargets() {
        assertThatThrownBy(() -> extract(new SymlinkExtractor(ALLOW, false), link("  ")))
                .isInstanceOf(IOException.class)
                .hasMessage("Invalid symlink entry: link (empty target)");
    }

    @Test
    void keepsAnExistingLinkUnlessOverwriting() throws IOException {
        Files.createSymbolicLink(outputDir.resolve("link"), Path.of("old"));
        extract(new SymlinkExtractor(ALLOW, false), link("new"));
        assertThat(Files.readSymbolicLink(outputDir.resolve("link"))).isEqualTo(Path.of("old"));

        extract(new SymlinkExtractor(ALLOW, true), link("new"));
        assertThat(Files.readSymbolicLink(outputDir.resolve("link"))).isEqualTo(Path.of("new"));
    }

    @Test
    void disallowRejectsAbsoluteTargetWithUnsafeEntryException(@TempDir Path out) {
        // Given
        var entry = new Entry("link", Entry.Type.SYMLINK, 0777).withLinkTarget("/etc/passwd");
        var extractor = new SymlinkExtractor(DISALLOW, false);

        // Then
        assertThatThrownBy(() -> extractor.extract(out, entry, out.resolve("link"), new SymlinkGuard(out)))
                .isInstanceOf(UnsafeEntryException.class);
    }

    @Test
    void disallowRejectsEscapingRelativeTargetWithUnsafeEntryException(@TempDir Path out) {
        // Given
        var entry = new Entry("link", Entry.Type.SYMLINK, 0777).withLinkTarget("../../outside");
        var extractor = new SymlinkExtractor(DISALLOW, false);

        // Then
        assertThatThrownBy(() -> extractor.extract(out, entry, out.resolve("link"), new SymlinkGuard(out)))
                .isInstanceOf(UnsafeEntryException.class)
                .hasCauseInstanceOf(UnsafeEntryException.class);
    }

    @Test
    void disallowAcceptsRelativeTargetThatStaysInside(@TempDir Path out) throws IOException {
        // Given
        Files.writeString(out.resolve("b.txt"), "b");
        var entry = new Entry("a/link", Entry.Type.SYMLINK, 0777).withLinkTarget("../b.txt");
        var extractor = new SymlinkExtractor(DISALLOW, false);

        // When
        extractor.extract(out, entry, out.resolve("a/link"), new SymlinkGuard(out));

        // Then
        assertThat(Files.readSymbolicLink(out.resolve("a/link"))).isEqualTo(Path.of("../b.txt"));
    }

    @Test
    void relativizeAbsoluteRewritesAbsoluteTargetsInside(@TempDir Path out) throws IOException {
        var entry = new Entry("link", Entry.Type.SYMLINK, 0777).withLinkTarget("/etc/passwd");
        new SymlinkExtractor(RELATIVIZE_ABSOLUTE, false)
                .extract(out, entry, out.resolve("link"), new SymlinkGuard(out));
        assertThat(Files.readSymbolicLink(out.resolve("link"))).isEqualTo(out.resolve("etc/passwd"));
    }

    @Test
    void relativizeAbsoluteRejectsTargetsThatStillEscape(@TempDir Path out) {
        var absolute = new Entry("a", Entry.Type.SYMLINK, 0777).withLinkTarget("/../../etc");
        var relative = new Entry("r", Entry.Type.SYMLINK, 0777).withLinkTarget("../../x");
        var extractor = new SymlinkExtractor(RELATIVIZE_ABSOLUTE, false);

        assertThatThrownBy(() -> extractor.extract(out, absolute, out.resolve("a"), new SymlinkGuard(out)))
                .isInstanceOf(UnsafeEntryException.class);
        assertThatThrownBy(() -> extractor.extract(out, relative, out.resolve("r"), new SymlinkGuard(out)))
                .isInstanceOf(UnsafeEntryException.class);
    }

    @Test
    void disallowAcceptsTargetsThatResolveToTheOutputDirectory(@TempDir Path out) throws IOException {
        // Given
        var extractor = new SymlinkExtractor(EscapingSymlinkPolicy.DISALLOW, false);
        var self = new Entry("link", Entry.Type.SYMLINK, 0777).withLinkTarget(".");
        var up = new Entry("a/link", Entry.Type.SYMLINK, 0777).withLinkTarget("..");

        // When
        extractor.extract(out, self, out.resolve("link"), new SymlinkGuard(out));
        extractor.extract(out, up, out.resolve("a/link"), new SymlinkGuard(out));

        // Then
        assertThat(Files.readSymbolicLink(out.resolve("link"))).isEqualTo(Path.of("."));
        assertThat(Files.readSymbolicLink(out.resolve("a/link"))).isEqualTo(Path.of(".."));
    }
}
