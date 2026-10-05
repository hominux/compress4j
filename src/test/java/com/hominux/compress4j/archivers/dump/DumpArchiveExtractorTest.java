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
package com.hominux.compress4j.archivers.dump;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.archivers.UnsupportedEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.apache.commons.compress.archivers.dump.DumpArchiveEntry;
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DumpArchiveExtractorTest {

    private static DumpArchiveInputStream streamOf(DumpArchiveEntry entry) throws IOException {
        var in = mock(DumpArchiveInputStream.class);
        when(in.getNextEntry()).thenReturn(entry);
        return in;
    }

    private static DumpArchiveEntry entry(String name, DumpArchiveEntry.TYPE type) {
        var entry = mock(DumpArchiveEntry.class);
        when(entry.getName()).thenReturn(name);
        when(entry.getType()).thenReturn(type);
        return entry;
    }

    @Test
    void shouldReturnEmptyWhenNoMoreEntries() throws IOException {
        try (var extractor = new DumpArchiveExtractor(mock(DumpArchiveInputStream.class))) {
            assertThat(extractor.nextEntry()).isEmpty();
        }
    }

    @Test
    void shouldSkipTheRootEntryWhichHasNoName() throws IOException {
        var root = entry("", DumpArchiveEntry.TYPE.DIRECTORY);
        var file = entry("file.txt", DumpArchiveEntry.TYPE.FILE);
        var in = mock(DumpArchiveInputStream.class);
        when(in.getNextEntry()).thenReturn(root, file, null);

        try (var extractor = new DumpArchiveExtractor(in)) {
            assertThat(extractor.nextEntry())
                    .hasValueSatisfying(e -> assertThat(e.name()).isEqualTo("file.txt"));
            assertThat(extractor.nextEntry()).isEmpty();
        }
    }

    @Test
    void shouldMapDirectoryEntry() throws IOException {
        var entry = entry("dir", DumpArchiveEntry.TYPE.DIRECTORY);

        try (var extractor = new DumpArchiveExtractor(streamOf(entry))) {
            assertThat(extractor.nextEntry())
                    .hasValueSatisfying(e -> assertThat(e.type()).isEqualTo(Entry.Type.DIR));
        }
    }

    @Test
    void shouldMapFileEntryAndKeepTypeAndPermissionBits() throws IOException {
        var entry = entry("file.txt", DumpArchiveEntry.TYPE.FILE);
        when(entry.getMode()).thenReturn(0100640);

        try (var extractor = new DumpArchiveExtractor(streamOf(entry))) {
            assertThat(extractor.nextEntry()).hasValueSatisfying(e -> {
                assertThat(e.type()).isEqualTo(Entry.Type.FILE);
                assertThat(e.mode()).isEqualTo(0100640);
            });
        }
    }

    @Test
    void shouldOpenFileEntryThatReadsItsDataThenReachesEndOfStream() throws IOException {
        var in = streamOf(entry("file.txt", DumpArchiveEntry.TYPE.FILE));
        when(in.read()).thenReturn((int) 'h', (int) 'i', -1);

        try (var extractor = new DumpArchiveExtractor(in)) {
            var mapped = extractor.nextEntry().orElseThrow();

            var data = extractor.openEntryStream(mapped);

            assertThat(data.read()).isEqualTo('h');
            assertThat(data.read()).isEqualTo('i');
            assertThat(data.read()).isEqualTo(-1);
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DumpArchiveEntry.TYPE.class,
            names = {"LINK", "SOCKET", "FIFO", "BLKDEV", "CHRDEV", "WHITEOUT", "UNKNOWN"})
    void shouldSkipUnsupportedEntryTypesAndReportThem(DumpArchiveEntry.TYPE type) throws IOException {
        var in = mock(DumpArchiveInputStream.class);
        var special = entry("special", type);
        when(in.getNextEntry()).thenReturn(special).thenReturn(null);
        var reported = new ArrayList<UnsupportedEntry>();

        try (var extractor = extractorOver(in, reported::add)) {
            assertThat(extractor.nextEntry()).isEmpty();
        }

        assertThat(reported).containsExactly(new UnsupportedEntry("special", DumpArchiveExtractor.kindOf(type)));
    }

    private static DumpArchiveExtractor extractorOver(DumpArchiveInputStream in, Consumer<UnsupportedEntry> handler)
            throws IOException {
        var builder = new DumpArchiveExtractor.DumpArchiveExtractorBuilder(InputStream.nullInputStream()) {
            @Override
            public DumpArchiveInputStream buildArchiveInputStream() {
                return in;
            }
        };
        return builder.unsupportedEntryHandler(handler).build();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldNotSkipAnUnnamedEntryThatIsNotADirectory() throws IOException {
        var unnamedFile = entry("", DumpArchiveEntry.TYPE.FILE);

        try (var extractor = new DumpArchiveExtractor(streamOf(unnamedFile))) {
            assertThat(extractor.nextEntry())
                    .hasValueSatisfying(e -> assertThat(e.name()).isEmpty());
        }
    }

    @Test
    void shouldReportUnsupportedEntryToTheUnsupportedHandlerAndCreateNothing() throws IOException {
        var in = mock(DumpArchiveInputStream.class);
        var link = entry("link", DumpArchiveEntry.TYPE.LINK);
        when(in.getNextEntry()).thenReturn(link).thenReturn(null);
        var seen = new ArrayList<UnsupportedEntry>();

        try (var extractor = extractorOver(in, seen::add)) {
            extractor.extract(tempDir);
        }

        assertThat(seen).containsExactly(new UnsupportedEntry("link", "symbolic link"));
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void shouldRejectPathTraversalAndWriteNothingOutside() throws IOException {
        var in = mock(DumpArchiveInputStream.class);
        var escaping = entry("../escape.txt", DumpArchiveEntry.TYPE.FILE);
        when(in.getNextEntry()).thenReturn(escaping).thenReturn(null);
        var target = Files.createDirectory(tempDir.resolve("target"));

        try (var extractor = new DumpArchiveExtractor(in)) {
            assertThatThrownBy(() -> extractor.extract(target)).isInstanceOf(IOException.class);
        }

        assertThat(tempDir.resolve("escape.txt")).doesNotExist();
    }
}
