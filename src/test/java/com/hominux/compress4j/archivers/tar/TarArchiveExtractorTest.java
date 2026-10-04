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
package com.hominux.compress4j.archivers.tar;

import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import com.hominux.compress4j.assertion.Compress4JAssertions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class TarArchiveExtractorTest {

    @Test
    void shouldReturnNextFileEntry() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());
        var tarArchiveInputStream =
                spy(TarArchiveExtractor.builder(mockInputStream).buildArchiveInputStream());
        TarArchiveEntry mockTarEntry = mock(TarArchiveEntry.class);
        given(mockTarEntry.getName()).willReturn("file.txt");
        //noinspection OctalInteger
        given(mockTarEntry.getMode()).willReturn(0400);
        given(mockTarEntry.isFile()).willReturn(true);
        given(mockTarEntry.getSize()).willReturn(10L);
        given(tarArchiveInputStream.getNextEntry()).willReturn(mockTarEntry, (TarArchiveEntry) null);

        try (@SuppressWarnings("rawtypes")
                        MockedStatic<ArchiveExtractor> mockedCompressor =
                                mockStatic(ArchiveExtractor.class, CALLS_REAL_METHODS);
                TarArchiveExtractor tarDecompressor = new TarArchiveExtractor(tarArchiveInputStream)) {
            mockedCompressor.when(ArchiveExtractor::isIsOsWindows).thenReturn(false);

            // when
            var result = tarDecompressor.nextEntry();

            // then
            Compress4JAssertions.assertThat(result.orElseThrow())
                    .hasName("file.txt")
                    .hasType(ArchiveExtractor.Entry.Type.FILE)
                    .hasMode(Set.of(OWNER_READ));
        }
    }

    @Test
    void shouldReturnNextSymlinkEntry() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());
        var tarArchiveInputStream =
                spy(TarArchiveExtractor.builder(mockInputStream).buildArchiveInputStream());
        TarArchiveEntry mockTarEntry = mock(TarArchiveEntry.class);
        given(mockTarEntry.getName()).willReturn("file.txt");
        given(mockTarEntry.getLinkName()).willReturn("target.txt");
        @SuppressWarnings("OctalInteger")
        int value = 0644;
        given(mockTarEntry.getMode()).willReturn(value);
        given(mockTarEntry.isSymbolicLink()).willReturn(true);
        given(mockTarEntry.getSize()).willReturn(10L);
        given(tarArchiveInputStream.getNextEntry()).willReturn(mockTarEntry, (TarArchiveEntry) null);

        try (@SuppressWarnings("rawtypes")
                        MockedStatic<ArchiveExtractor> mockedCompressor =
                                mockStatic(ArchiveExtractor.class, CALLS_REAL_METHODS);
                TarArchiveExtractor tarDecompressor = new TarArchiveExtractor(tarArchiveInputStream)) {
            mockedCompressor.when(ArchiveExtractor::isIsOsWindows).thenReturn(false);

            // when
            var result = tarDecompressor.nextEntry();

            // then
            Compress4JAssertions.assertThat(result.orElseThrow())
                    .hasName("file.txt")
                    .hasMode(Set.of(OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ))
                    .hasLinkName("target.txt")
                    .hasType(ArchiveExtractor.Entry.Type.SYMLINK);
        }
    }

    @Test
    void shouldReturnNextDirectoryEntry() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());
        var tarArchiveInputStream =
                spy(TarArchiveExtractor.builder(mockInputStream).buildArchiveInputStream());
        TarArchiveEntry mockTarEntry = mock(TarArchiveEntry.class);
        given(mockTarEntry.getName()).willReturn("file.txt");
        given(mockTarEntry.getLinkName()).willReturn("target.txt");
        @SuppressWarnings("OctalInteger")
        int value = 0400;
        given(mockTarEntry.getMode()).willReturn(value);
        given(mockTarEntry.isDirectory()).willReturn(true);
        given(mockTarEntry.getSize()).willReturn(10L);
        given(tarArchiveInputStream.getNextEntry()).willReturn(mockTarEntry, (TarArchiveEntry) null);

        try (@SuppressWarnings("rawtypes")
                        MockedStatic<ArchiveExtractor> mockedCompressor =
                                mockStatic(ArchiveExtractor.class, CALLS_REAL_METHODS);
                TarArchiveExtractor tarDecompressor = new TarArchiveExtractor(tarArchiveInputStream)) {
            mockedCompressor.when(ArchiveExtractor::isIsOsWindows).thenReturn(false);

            // when
            var result = tarDecompressor.nextEntry();

            // then
            Compress4JAssertions.assertThat(result.orElseThrow())
                    .hasName("file.txt")
                    .hasMode(Set.of(OWNER_READ))
                    .hasType(ArchiveExtractor.Entry.Type.DIR);
        }
    }

    @Test
    void shouldReturnNextFileEntryOnWindows() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());
        var tarArchiveInputStream =
                spy(TarArchiveExtractor.builder(mockInputStream).buildArchiveInputStream());
        TarArchiveEntry mockTarEntry = mock(TarArchiveEntry.class);
        given(mockTarEntry.getName()).willReturn("file.txt");
        given(mockTarEntry.isFile()).willReturn(true);
        given(mockTarEntry.getSize()).willReturn(10L);
        given(tarArchiveInputStream.getNextEntry()).willReturn(mockTarEntry, (TarArchiveEntry) null);

        try (@SuppressWarnings("rawtypes")
                        MockedStatic<ArchiveExtractor> mockedCompressor =
                                mockStatic(ArchiveExtractor.class, CALLS_REAL_METHODS);
                TarArchiveExtractor tarDecompressor = new TarArchiveExtractor(tarArchiveInputStream)) {
            mockedCompressor.when(ArchiveExtractor::isIsOsWindows).thenReturn(true);

            // when
            var result = tarDecompressor.nextEntry();

            // then
            Compress4JAssertions.assertThat(result.orElseThrow())
                    .hasName("file.txt")
                    .hasType(ArchiveExtractor.Entry.Type.FILE)
                    .hasMode(0);
        }
    }

    @Test
    void shouldReturnNextSymlinkEntryOnWindows() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());
        var tarArchiveInputStream =
                spy(TarArchiveExtractor.builder(mockInputStream).buildArchiveInputStream());
        TarArchiveEntry mockTarEntry = mock(TarArchiveEntry.class);
        given(mockTarEntry.getName()).willReturn("file.txt");
        given(mockTarEntry.getLinkName()).willReturn("target.txt");
        given(mockTarEntry.isSymbolicLink()).willReturn(true);
        given(mockTarEntry.getSize()).willReturn(10L);
        given(tarArchiveInputStream.getNextEntry()).willReturn(mockTarEntry, (TarArchiveEntry) null);

        try (@SuppressWarnings("rawtypes")
                        MockedStatic<ArchiveExtractor> mockedCompressor =
                                mockStatic(ArchiveExtractor.class, CALLS_REAL_METHODS);
                TarArchiveExtractor tarDecompressor = new TarArchiveExtractor(tarArchiveInputStream)) {
            mockedCompressor.when(ArchiveExtractor::isIsOsWindows).thenReturn(true);

            // when
            var result = tarDecompressor.nextEntry();

            // then
            Compress4JAssertions.assertThat(result.orElseThrow())
                    .hasName("file.txt")
                    .hasMode(Collections.emptySet())
                    .hasLinkName("target.txt")
                    .hasType(ArchiveExtractor.Entry.Type.SYMLINK);
        }
    }

    @Test
    void shouldReturnNextDirectoryEntryOnWindows() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());
        var tarArchiveInputStream =
                spy(TarArchiveExtractor.builder(mockInputStream).buildArchiveInputStream());
        TarArchiveEntry mockTarEntry = mock(TarArchiveEntry.class);
        given(mockTarEntry.getName()).willReturn("some-path");
        given(mockTarEntry.getLinkName()).willReturn("target.txt");
        given(mockTarEntry.isDirectory()).willReturn(true);
        given(mockTarEntry.getSize()).willReturn(10L);
        given(tarArchiveInputStream.getNextEntry()).willReturn(mockTarEntry, (TarArchiveEntry) null);

        try (@SuppressWarnings("rawtypes")
                        MockedStatic<ArchiveExtractor> mockedCompressor =
                                mockStatic(ArchiveExtractor.class, CALLS_REAL_METHODS);
                TarArchiveExtractor tarDecompressor = new TarArchiveExtractor(tarArchiveInputStream)) {
            mockedCompressor.when(ArchiveExtractor::isIsOsWindows).thenReturn(true);

            // when
            var result = tarDecompressor.nextEntry();

            // then
            Compress4JAssertions.assertThat(result.orElseThrow())
                    .hasName("some-path")
                    .hasMode(Collections.emptySet())
                    .hasType(ArchiveExtractor.Entry.Type.DIR);
        }
    }

    @Test
    void shouldReturnEmptyWhenNoMoreEntries() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());

        try (TarArchiveExtractor tarDecompressor =
                TarArchiveExtractor.builder(mockInputStream).build()) {
            // when
            var result = tarDecompressor.nextEntry();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Test
    void shouldSkipEntryWhenNextEntryIsHardlink() throws IOException {
        // given
        var mockInputStream = new ByteArrayInputStream("test".getBytes());
        var tarArchiveInputStream =
                spy(TarArchiveExtractor.builder(mockInputStream).buildArchiveInputStream());
        TarArchiveEntry mockTarEntry = mock(TarArchiveEntry.class);
        given(mockTarEntry.isLink()).willReturn(true);
        given(mockTarEntry.getName()).willReturn("link");
        given(tarArchiveInputStream.getNextEntry()).willReturn(mockTarEntry, (TarArchiveEntry) null);

        try (TarArchiveExtractor tarDecompressor = new TarArchiveExtractor(tarArchiveInputStream)) {
            // when
            var result = tarDecompressor.nextEntry();

            // then
            //noinspection resource
            then(tarArchiveInputStream).should(times(3)).getNextEntry();
            then(mockTarEntry).should().isLink();
            assertThat(result).isEmpty();
        }
    }
}
