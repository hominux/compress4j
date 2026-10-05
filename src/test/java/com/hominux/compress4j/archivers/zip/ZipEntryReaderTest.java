/*
 * Copyright 2025-2026 The Compress4J Project
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
package com.hominux.compress4j.archivers.zip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.archivers.Entry;
import com.hominux.compress4j.archivers.UnsupportedEntry;
import com.hominux.compress4j.internal.archive.ReaderContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZipEntryReaderTest {

    @Mock
    private ZipFile mockZipFile;

    private final List<UnsupportedEntry> unsupported = new ArrayList<>();

    private ZipEntryReader reader;

    private void entries(ZipArchiveEntry... entries) {
        when(mockZipFile.getEntriesInPhysicalOrder()).thenReturn(Collections.enumeration(List.of(entries)));
        reader = new ZipEntryReader(mockZipFile, new ReaderContext(ExtractionLimits.defaults(), unsupported::add));
    }

    @SuppressWarnings("OctalInteger")
    @Nested
    @DisplayName("next() Tests")
    class NextEntryTests {

        @Test
        @DisplayName("Should return FILE entry correctly")
        void testNextEntry_File() throws IOException {
            // Given
            var mockZipEntry = mock(ZipArchiveEntry.class);
            when(mockZipEntry.getName()).thenReturn("file.txt");
            when(mockZipEntry.isDirectory()).thenReturn(false);
            when(mockZipEntry.isUnixSymlink()).thenReturn(false);
            when(mockZipEntry.getUnixMode()).thenReturn(0644);
            entries(mockZipEntry);

            // When
            var entry = reader.next().orElseThrow();

            // Then
            assertThat(entry.name()).isEqualTo("file.txt");
            assertThat(entry.type()).isEqualTo(Entry.Type.FILE);
            assertThat(entry.mode()).isEqualTo(0644);
            assertThat(entry.linkTarget()).isEmpty();
        }

        @Test
        @DisplayName("Should return DIR entry correctly")
        void testNextEntry_Directory() throws IOException {
            // Given
            var mockZipEntry = mock(ZipArchiveEntry.class);
            when(mockZipEntry.getName()).thenReturn("directory/");
            when(mockZipEntry.isDirectory()).thenReturn(true);
            when(mockZipEntry.isUnixSymlink()).thenReturn(false);
            when(mockZipEntry.getUnixMode()).thenReturn(0755);
            entries(mockZipEntry);

            // When
            var entry = reader.next().orElseThrow();

            // Then
            assertThat(entry.name()).isEqualTo("directory");
            assertThat(entry.type()).isEqualTo(Entry.Type.DIR);
            assertThat(entry.mode()).isEqualTo(0755);
        }

        @Test
        @DisplayName("Should return SYMLINK entry correctly")
        void testNextEntry_Symlink() throws IOException {
            // Given
            var mockZipEntry = mock(ZipArchiveEntry.class);
            when(mockZipEntry.getName()).thenReturn("link");
            when(mockZipEntry.isUnixSymlink()).thenReturn(true);
            when(mockZipEntry.getUnixMode()).thenReturn(0777);
            when(mockZipEntry.getSize()).thenReturn(11L);
            when(mockZipFile.getInputStream(mockZipEntry))
                    .thenReturn(new ByteArrayInputStream("target/file".getBytes(StandardCharsets.UTF_8)));
            entries(mockZipEntry);

            // When
            var entry = reader.next().orElseThrow();

            // Then
            assertThat(entry.name()).isEqualTo("link");
            assertThat(entry.type()).isEqualTo(Entry.Type.SYMLINK);
            assertThat(entry.linkTarget()).contains("target/file");
            assertThat(entry.mode()).isEqualTo(0777);
            assertThat(entry.size()).hasValue(0);
        }

        @Test
        @DisplayName("Should return empty when no more entries")
        void testNextEntry_Empty() throws IOException {
            // Given
            entries();

            // When
            var entry = reader.next();

            // Then
            assertThat(entry).isEmpty();
        }

        @Test
        @DisplayName("Should report and skip an entry of an unsupported type")
        void testNextEntry_UnsupportedTypeIsReportedAndSkipped() throws IOException {
            var device = mock(ZipArchiveEntry.class);
            when(device.getName()).thenReturn("tty");
            when(device.getUnixMode()).thenReturn(0020600);
            var file = mock(ZipArchiveEntry.class);
            when(file.getName()).thenReturn("a.txt");
            when(file.getUnixMode()).thenReturn(0644);
            entries(device, file);
            var entry = reader.next().orElseThrow();
            assertThat(entry.name()).isEqualTo("a.txt");
            assertThat(unsupported).extracting(UnsupportedEntry::name).containsExactly("tty");
        }

        @Test
        @DisplayName("Should reject a symlink target larger than a path can be")
        void testNextEntry_OversizedSymlinkTarget() {
            var mockZipEntry = mock(ZipArchiveEntry.class);
            when(mockZipEntry.getName()).thenReturn("link");
            when(mockZipEntry.isUnixSymlink()).thenReturn(true);
            when(mockZipEntry.getSize()).thenReturn(ZipEntryReader.MAX_SYMLINK_TARGET_BYTES + 1);
            entries(mockZipEntry);

            assertThatThrownBy(() -> reader.next())
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("Should propagate IOException from reading the symlink target")
        void testNextEntry_SymlinkThrowsIOException() throws IOException {
            // Given
            var mockZipEntry = mock(ZipArchiveEntry.class);
            when(mockZipEntry.getName()).thenReturn("link");
            when(mockZipEntry.isUnixSymlink()).thenReturn(true);
            when(mockZipEntry.getSize()).thenReturn(4L);
            entries(mockZipEntry);
            when(mockZipFile.getInputStream(mockZipEntry)).thenThrow(new IOException("Test symlink error"));

            // When & Then
            assertThatThrownBy(() -> reader.next())
                    .isInstanceOf(IOException.class)
                    .hasMessage("Test symlink error");
        }
    }

    @Test
    @DisplayName("open should read the content of the current entry and next should release it")
    void testOpenReadsCurrentEntryAndNextReleasesIt() throws IOException {
        var zipEntry = mock(ZipArchiveEntry.class);
        when(zipEntry.getName()).thenReturn("a.txt");
        when(zipEntry.getUnixMode()).thenReturn(0644);
        var content = new TrackingStream("data");
        when(mockZipFile.getInputStream(zipEntry)).thenReturn(content);
        entries(zipEntry);
        var entry = reader.next().orElseThrow();
        var opened = reader.open(entry);
        byte[] bytes = opened.readAllBytes();
        opened.close();
        assertThat(bytes).isEqualTo("data".getBytes(StandardCharsets.UTF_8));
        assertThat(content.closed).isFalse();
        assertThat(reader.next()).isEmpty();
        assertThat(content.closed).isTrue();
    }

    @Test
    @DisplayName("open before next should fail")
    void testOpenWithoutCurrentEntryFails() {
        entries();
        assertThatThrownBy(() -> reader.open(new Entry("a", Entry.Type.FILE, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("close should close the zip file")
    void testClose() throws IOException {
        entries();
        reader.close();
        verify(mockZipFile, times(1)).close();
    }

    private static final class TrackingStream extends ByteArrayInputStream {
        private boolean closed;

        TrackingStream(String data) {
            super(data.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
