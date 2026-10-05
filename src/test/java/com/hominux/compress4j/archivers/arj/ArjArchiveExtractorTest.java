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
package com.hominux.compress4j.archivers.arj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.archivers.UnsupportedEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.apache.commons.compress.archivers.arj.ArjArchiveEntry;
import org.apache.commons.compress.archivers.arj.ArjArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArjArchiveExtractorTest {

    private static ArjArchiveInputStream streamOf(ArjArchiveEntry entry) throws IOException {
        var in = mock(ArjArchiveInputStream.class);
        when(in.getNextEntry()).thenReturn(entry);
        return in;
    }

    @Test
    void shouldReturnEmptyWhenNoMoreEntries() throws IOException {
        try (var extractor = new ArjArchiveExtractor(mock(ArjArchiveInputStream.class))) {
            assertThat(extractor.nextEntry()).isEmpty();
        }
    }

    @Test
    void shouldMapDirectoryEntry() throws IOException {
        var entry = mock(ArjArchiveEntry.class);
        when(entry.getName()).thenReturn("dir");
        when(entry.isDirectory()).thenReturn(true);

        try (var extractor = new ArjArchiveExtractor(streamOf(entry))) {
            assertThat(extractor.nextEntry())
                    .hasValueSatisfying(e -> assertThat(e.type()).isEqualTo(Entry.Type.DIR));
        }
    }

    @Test
    void shouldMapFileEntryAndKeepUnixTypeAndPermissionBits() throws IOException {
        var entry = mock(ArjArchiveEntry.class);
        when(entry.getName()).thenReturn("file.txt");
        when(entry.isHostOsUnix()).thenReturn(true);
        when(entry.getUnixMode()).thenReturn(0100640);

        try (var extractor = new ArjArchiveExtractor(streamOf(entry))) {
            assertThat(extractor.nextEntry()).hasValueSatisfying(e -> {
                assertThat(e.type()).isEqualTo(Entry.Type.FILE);
                assertThat(e.mode()).isEqualTo(0100640);
            });
        }
    }

    @Test
    void shouldRejectEntryItCannotRead() throws IOException {
        var entry = mock(ArjArchiveEntry.class);
        when(entry.getName()).thenReturn("secret.txt");
        var in = streamOf(entry);
        when(in.canReadEntryData(entry)).thenReturn(false);

        try (var extractor = new ArjArchiveExtractor(in)) {
            var mapped = extractor.nextEntry().orElseThrow();

            assertThatThrownBy(() -> extractor.openEntryStream(mapped))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Cannot read ARJ entry data (encrypted or unsupported method): secret.txt");
        }
    }

    @Test
    void shouldOpenReadableEntry() throws IOException {
        var entry = mock(ArjArchiveEntry.class);
        when(entry.getName()).thenReturn("ok.txt");
        var in = streamOf(entry);
        when(in.canReadEntryData(entry)).thenReturn(true);

        try (var extractor = new ArjArchiveExtractor(in)) {
            var mapped = extractor.nextEntry().orElseThrow();

            assertThat(extractor.openEntryStream(mapped)).isSameAs(in);
        }
    }

    @TempDir
    Path tempDir;

    private static ArjArchiveEntry unixEntry(String name, int mode) {
        var entry = mock(ArjArchiveEntry.class);
        when(entry.getName()).thenReturn(name);
        when(entry.isHostOsUnix()).thenReturn(true);
        when(entry.getUnixMode()).thenReturn(mode);
        return entry;
    }

    private static ArjArchiveExtractor.ArjArchiveExtractorBuilder builderOver(ArjArchiveInputStream in) {
        return new ArjArchiveExtractor.ArjArchiveExtractorBuilder(InputStream.nullInputStream()) {
            @Override
            public ArjArchiveInputStream buildArchiveInputStream() {
                return in;
            }
        };
    }

    private static ArjArchiveInputStream streamOfAll(ArjArchiveEntry... entries) throws IOException {
        var in = mock(ArjArchiveInputStream.class);
        var first = entries[0];
        var rest = new ArjArchiveEntry[entries.length - 1];
        System.arraycopy(entries, 1, rest, 0, entries.length - 1);
        when(in.getNextEntry()).thenReturn(first, rest);
        when(in.canReadEntryData(any())).thenReturn(true);
        when(in.read(any(byte[].class), anyInt(), anyInt())).thenReturn(-1);
        return in;
    }

    @Test
    void shouldReportUnixSymlinkAndDeviceToTheUnsupportedEntryHandlerAndCreateNothing() throws IOException {
        var in = streamOfAll(unixEntry("link", 0120777), unixEntry("tty", 0020644), unixEntry("pipe", 0010644), null);
        var reported = new ArrayList<UnsupportedEntry>();

        try (var extractor =
                builderOver(in).unsupportedEntryHandler(reported::add).build()) {
            extractor.extract(tempDir);
        }

        assertThat(reported)
                .containsExactly(
                        new UnsupportedEntry("link", "symbolic link"),
                        new UnsupportedEntry("tty", "character device"),
                        new UnsupportedEntry("pipe", "fifo"));
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void shouldClassifyUnixDirectoryAndModeWithoutTypeBits() throws IOException {
        var in = streamOfAll(unixEntry("d", 040755), unixEntry("f", 0644), null);

        try (var extractor = builderOver(in).build()) {
            assertThat(extractor.nextEntry())
                    .hasValueSatisfying(e -> assertThat(e.type()).isEqualTo(Entry.Type.DIR));
            assertThat(extractor.nextEntry())
                    .hasValueSatisfying(e -> assertThat(e.type()).isEqualTo(Entry.Type.FILE));
        }
    }

    @Test
    void shouldTreatUnixHostDirectoryFlagWithTypelessModeAsDirectoryWithoutReporting() throws IOException {
        var entry = unixEntry("d", 0755);
        when(entry.isDirectory()).thenReturn(true);
        var reported = new ArrayList<UnsupportedEntry>();

        try (var extractor = builderOver(streamOfAll(entry, null))
                .unsupportedEntryHandler(reported::add)
                .build()) {
            assertThat(extractor.nextEntry())
                    .hasValueSatisfying(e -> assertThat(e.type()).isEqualTo(Entry.Type.DIR));
        }
        assertThat(reported).isEmpty();
    }

    @Test
    void shouldIgnoreModeBitsOfNonUnixHosts() throws IOException {
        var entry = mock(ArjArchiveEntry.class);
        when(entry.getName()).thenReturn("dos.txt");
        when(entry.isHostOsUnix()).thenReturn(false);
        when(entry.getUnixMode()).thenReturn(0020644);

        try (var extractor = builderOver(streamOfAll(entry, null)).build()) {
            assertThat(extractor.nextEntry())
                    .hasValueSatisfying(e -> assertThat(e.type()).isEqualTo(Entry.Type.FILE));
        }
    }

    @Test
    void shouldRejectPathTraversalAndWriteNothingOutside() throws IOException {
        var entry = mock(ArjArchiveEntry.class);
        when(entry.getName()).thenReturn("../escape.txt");
        var in = mock(ArjArchiveInputStream.class);
        when(in.getNextEntry()).thenReturn(entry).thenReturn(null);
        when(in.canReadEntryData(entry)).thenReturn(true);
        var target = Files.createDirectory(tempDir.resolve("target"));

        try (var extractor = new ArjArchiveExtractor(in)) {
            assertThatThrownBy(() -> extractor.extract(target)).isInstanceOf(IOException.class);
        }

        assertThat(tempDir.resolve("escape.txt")).doesNotExist();
    }
}
