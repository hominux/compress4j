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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hominux.compress4j.archivers.zip.ZipArchiveCreator.ZipArchiveCreatorBuilder;
import com.hominux.compress4j.utils.FileUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZipArchiveCreatorTest {

    @Mock
    private ZipArchiveOutputStream mockZipStream;

    @Mock
    private ZipArchiveCreatorBuilder mockBuilder;

    private ZipArchiveCreator creator;

    @Captor
    private ArgumentCaptor<ZipArchiveEntry> entryCaptor;

    @Captor
    private ArgumentCaptor<byte[]> bytesCaptor;

    @Captor
    private ArgumentCaptor<Integer> intCaptor;

    @BeforeEach
    void setUp() {
        creator = new ZipArchiveCreator(mockZipStream);
    }

    @Test
    @DisplayName("Constructor with builder should build stream")
    void testConstructorWithBuilder() throws IOException {
        // Given
        when(mockBuilder.buildArchiveOutputStream()).thenReturn(mockZipStream);

        // When
        var creatorFromBuilder = new ZipArchiveCreator(mockBuilder);

        // Then
        assertThat(creatorFromBuilder).isNotNull();
        //noinspection resource
        verify(mockBuilder).buildArchiveOutputStream();
    }

    @Test
    @DisplayName("close() should close the underlying stream")
    void testClose() throws IOException {
        // When
        creator.close();

        // Then
        verify(mockZipStream).close();
    }

    @SuppressWarnings("OctalInteger")
    @Nested
    @DisplayName("Entry Writing Tests")
    class EntryWritingTests {

        private final FileTime testTime = FileTime.fromMillis(123456789000L);

        @Test
        @DisplayName("writeDirectoryEntry should write a correct directory entry")
        void testWriteDirectoryEntry() throws IOException {
            // When
            creator.writeDirectory("testDir", 0, testTime);

            // Then
            var inOrder = inOrder(mockZipStream);
            inOrder.verify(mockZipStream).putArchiveEntry(entryCaptor.capture());
            inOrder.verify(mockZipStream).closeArchiveEntry();

            var entry = entryCaptor.getValue();
            assertThat(entry.getName()).isEqualTo("testDir/");
            assertThat(entry.isDirectory()).isTrue();
            assertThat(entry.getTime()).isEqualTo(testTime.toMillis());
        }

        @Test
        @DisplayName("writeFileEntry should write a correct file entry")
        void testWriteFileEntry() throws IOException {
            // Given
            byte[] data = "test data".getBytes();
            var dataStream = new ByteArrayInputStream(data);
            var size = data.length;
            var mode = 0644;

            // When
            creator.writeFile("testFile.txt", dataStream, OptionalLong.of(size), mode, testTime);

            // Then
            var inOrder = inOrder(mockZipStream);
            inOrder.verify(mockZipStream).putArchiveEntry(entryCaptor.capture());

            inOrder.verify(mockZipStream).write(bytesCaptor.capture(), intCaptor.capture(), intCaptor.capture());

            inOrder.verify(mockZipStream).closeArchiveEntry();

            var entry = entryCaptor.getValue();
            assertThat(entry.getName()).isEqualTo("testFile.txt");
            assertThat(entry.isDirectory()).isFalse();
            assertThat(entry.getSize()).isEqualTo(size);
            assertThat(entry.getTime()).isEqualTo(testTime.toMillis());
            assertThat(entry.getUnixMode()).isEqualTo(UnixStat.FILE_FLAG | mode);

            byte[] writtenBytes = bytesCaptor.getValue();
            var offset = intCaptor.getAllValues().get(0);
            var length = intCaptor.getAllValues().get(1);

            byte[] actualData = new byte[length];
            System.arraycopy(writtenBytes, offset, actualData, 0, length);

            assertThat(actualData).isEqualTo(data);
        }

        @Test
        @DisplayName("writeFileEntry should write file entry with no mode")
        void testWriteFileEntry_NoMode() throws IOException {
            // Given
            byte[] data = "test data".getBytes();
            var dataStream = new ByteArrayInputStream(data);
            var size = data.length;

            // When
            creator.writeFile("testFile.txt", dataStream, OptionalLong.of(size), FileUtils.NO_MODE, testTime);

            // Then
            verify(mockZipStream).putArchiveEntry(entryCaptor.capture());

            var entry = entryCaptor.getValue();
            assertThat(entry.getName()).isEqualTo("testFile.txt");
            assertThat(entry.getSize()).isEqualTo(size);
            assertThat(entry.getUnixMode()).isZero();
        }

        @Test
        @DisplayName("writeFileEntry should write a symlink as a file containing the target path")
        void testWriteFileEntry_Symlink() throws IOException {
            // Given
            var targetPath = Paths.get("../target.txt");
            byte[] targetBytes = targetPath.toString().getBytes();
            var size = targetBytes.length;
            var mode = 0777;
            var dataStream = new ByteArrayInputStream(new byte[0]);

            // When
            creator.writeSymlink("testLink.lnk", targetPath.toString(), mode, testTime);

            // Then
            var inOrder = inOrder(mockZipStream);
            inOrder.verify(mockZipStream).putArchiveEntry(entryCaptor.capture());
            inOrder.verify(mockZipStream).write(bytesCaptor.capture());
            inOrder.verify(mockZipStream).closeArchiveEntry();

            var entry = entryCaptor.getValue();
            assertThat(entry.getName()).isEqualTo("testLink.lnk");
            assertThat(entry.isDirectory()).isFalse();
            assertThat(entry.getSize()).isEqualTo(size);
            assertThat(entry.getTime()).isEqualTo(testTime.toMillis());
            assertThat(entry.isUnixSymlink()).isTrue();
            assertThat(entry.getUnixMode()).isEqualTo(UnixStat.LINK_FLAG | mode);
            assertThat(bytesCaptor.getValue()).isEqualTo(targetBytes);
        }

        @Test
        @DisplayName("writeFileEntry should give a symlink without a mode full permissions")
        void testWriteFileEntry_SymlinkNoMode() throws IOException {
            // When
            creator.writeSymlink("link", "target", FileUtils.NO_MODE, testTime);

            // Then
            verify(mockZipStream).putArchiveEntry(entryCaptor.capture());
            assertThat(entryCaptor.getValue().getUnixMode()).isEqualTo(UnixStat.LINK_FLAG | 0777);
        }

        @Test
        @DisplayName("writeFileEntry should drop symlink mode bits above the permission bits")
        void testWriteFileEntry_SymlinkMasksStrayBits() throws IOException {
            // When
            creator.writeSymlink("link", "target", UnixStat.FILE_FLAG | 0755, testTime);

            // Then
            verify(mockZipStream).putArchiveEntry(entryCaptor.capture());
            assertThat(entryCaptor.getValue().getUnixMode()).isEqualTo(UnixStat.LINK_FLAG | 0755);
        }
    }
}
