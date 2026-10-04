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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import java.io.IOException;
import java.util.Optional;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZipArchiveExtractorTest {

    @Mock
    private ZipFileArchiveInputStream mockInputStream;

    private ZipArchiveExtractor extractor;

    @BeforeEach
    void setUp() throws IOException {
        extractor = new ZipArchiveExtractor.ZipArchiveExtractorBuilder(Optional.empty(), Optional.empty()) {
            @Override
            public ArchiveInputStream<ZipArchiveEntry> buildArchiveInputStream() {
                return mockInputStream;
            }
        }.build();
    }

    @SuppressWarnings("OctalInteger")
    @Nested
    @DisplayName("nextEntry() Tests")
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

            when(mockInputStream.getNextEntry()).thenReturn(mockZipEntry);

            // When
            var entry = extractor.nextEntry().orElseThrow();

            // Then
            assertThat(entry.name()).isEqualTo("file.txt");
            assertThat(entry.type()).isEqualTo(ArchiveExtractor.Entry.Type.FILE);
            assertThat(entry.mode()).isEqualTo(0644);
            assertThat(entry.linkTarget()).isEmpty();
            verify(mockInputStream, times(1)).getUnixSymlink(mockZipEntry);
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

            when(mockInputStream.getNextEntry()).thenReturn(mockZipEntry);

            // When
            var entry = extractor.nextEntry().orElseThrow();

            // Then
            assertThat(entry.name()).isEqualTo("directory");
            assertThat(entry.type()).isEqualTo(ArchiveExtractor.Entry.Type.DIR);
            assertThat(entry.mode()).isEqualTo(0755);
            verify(mockInputStream, times(1)).getUnixSymlink(mockZipEntry);
        }

        @Test
        @DisplayName("Should return SYMLINK entry correctly")
        void testNextEntry_Symlink() throws IOException {
            // Given
            var mockZipEntry = mock(ZipArchiveEntry.class);
            when(mockZipEntry.getName()).thenReturn("link");
            when(mockZipEntry.isUnixSymlink()).thenReturn(true);
            when(mockZipEntry.getUnixMode()).thenReturn(0777);
            lenient().when(mockZipEntry.getSize()).thenReturn(11L);
            when(mockInputStream.getUnixSymlink(mockZipEntry)).thenReturn("target/file");

            when(mockInputStream.getNextEntry()).thenReturn(mockZipEntry);

            // When
            var entry = extractor.nextEntry().orElseThrow();

            // Then
            assertThat(entry.name()).isEqualTo("link");
            assertThat(entry.type()).isEqualTo(ArchiveExtractor.Entry.Type.SYMLINK);
            assertThat(entry.linkTarget()).contains("target/file");
            assertThat(entry.mode()).isEqualTo(0777);
            assertThat(entry.size()).hasValue(0);
            verify(mockInputStream, times(1)).getUnixSymlink(mockZipEntry);
        }

        @Test
        @DisplayName("Should return empty when no more entries")
        void testNextEntry_Empty() throws IOException {
            // Given
            when(mockInputStream.getNextEntry()).thenReturn(null);

            // When
            var entry = extractor.nextEntry();

            // Then
            assertThat(entry).isEmpty();
        }

        @Test
        @DisplayName("Should propagate IOException from getNextEntry")
        void testNextEntry_ThrowsIOException() throws IOException {
            // Given
            when(mockInputStream.getNextEntry()).thenThrow(new IOException("Test read error"));

            // When & Then
            assertThatThrownBy(() -> extractor.nextEntry())
                    .isInstanceOf(IOException.class)
                    .hasMessage("Test read error");
        }

        @Test
        @DisplayName("Should propagate IOException from getUnixSymlink")
        void testNextEntry_SymlinkThrowsIOException() throws IOException {
            // Given
            var mockZipEntry = mock(ZipArchiveEntry.class);
            when(mockZipEntry.getName()).thenReturn("link");
            when(mockZipEntry.isUnixSymlink()).thenReturn(true);
            when(mockInputStream.getNextEntry()).thenReturn(mockZipEntry);
            when(mockInputStream.getUnixSymlink(mockZipEntry)).thenThrow(new IOException("Test symlink error"));

            // When & Then
            assertThatThrownBy(() -> extractor.nextEntry())
                    .isInstanceOf(IOException.class)
                    .hasMessage("Test symlink error");
        }
    }

    @Test
    @DisplayName("openEntryStream should return the wrapped archive input stream")
    void testOpenEntryStream() {
        // Given
        var mockEntry = mock(ArchiveExtractor.Entry.class);

        // When
        var resultStream = extractor.openEntryStream(mockEntry);

        // Then
        assertThat(resultStream).isSameAs(mockInputStream);
    }

    @Test
    @DisplayName("close should close the underlying input stream")
    void testClose() throws IOException {
        // When
        extractor.close();

        // Then
        verify(mockInputStream, times(1)).close();
    }
}
