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
package com.hominux.compress4j.compressors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hominux.compress4j.compressors.memory.InMemoryCompressor;
import com.hominux.compress4j.compressors.memory.InMemoryCompressor.InMemoryCompressorBuilder;
import com.hominux.compress4j.compressors.memory.InMemoryCompressorOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodecCompressorTest {

    @Mock
    private InMemoryCompressorOutputStream mockCompressorOutputStream;

    private InMemoryCompressor compressor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        compressor = new InMemoryCompressor(mockCompressorOutputStream);
    }

    @Test
    @DisplayName("Should construct Compressor with InMemoryCompressorOutputStream")
    void constructor_WithCompressorOutputStream_SetsField() {
        assertThat(compressor).isNotNull();
        assertThat(compressor.compressorOutputStream).isEqualTo(mockCompressorOutputStream);
    }

    @Test
    @DisplayName("Should construct Compressor with a builder")
    void constructor_WithBuilder_SetsField() throws IOException {
        // given
        InMemoryCompressorBuilder mockBuilder = mock(InMemoryCompressorBuilder.class);

        InMemoryCompressorOutputStream builderReturnedStream = mock(InMemoryCompressorOutputStream.class);
        when(mockBuilder.buildCompressorOutputStream()).thenReturn(builderReturnedStream);

        // when
        InMemoryCompressor compressorFromBuilder = new InMemoryCompressor(mockBuilder);

        // then
        assertThat(compressorFromBuilder).isNotNull();
        assertThat(compressorFromBuilder.compressorOutputStream).isEqualTo(builderReturnedStream);
        //noinspection resource
        verify(mockBuilder, times(1)).buildCompressorOutputStream();
    }

    @Test
    @DisplayName("Should write all bytes from a file to the compressor output stream")
    void write_ToFile_CopiesBytes() throws IOException {
        // given
        Path sourcePath = tempDir.resolve("source.txt");
        byte[] testBytes = "Hello Compressor".getBytes();
        Files.write(sourcePath, testBytes);

        // when
        long bytesWritten = compressor.write(sourcePath.toFile());

        // then
        assertThat(bytesWritten).isEqualTo(testBytes.length);
    }

    @Test
    @DisplayName("Should write all bytes from a path to the compressor output stream")
    void write_ToPath_CopiesBytes() throws IOException {
        // given
        Path sourcePath = tempDir.resolve("source.txt");
        byte[] testBytes = "Another string to compress".getBytes();
        Files.write(sourcePath, testBytes);

        // when
        long bytesWritten = compressor.write(sourcePath);

        // then
        assertThat(bytesWritten).isEqualTo(testBytes.length);
    }

    @Test
    @DisplayName("Should throw IOException when writing from file fails")
    void write_ToFile_ThrowsIOException_WhenCopyFails() {
        // given
        File nonExistentFile = new File("/nonexistent/source.txt");

        // when & then
        assertThatThrownBy(() -> compressor.write(nonExistentFile)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should throw IOException when writing from path fails")
    void write_ToPath_ThrowsIOException_WhenCopyFails() {
        // given
        Path nonExistentPath = tempDir.resolve("non_existent_source.txt");

        // when & then
        assertThatThrownBy(() -> compressor.write(nonExistentPath)).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("Should close the compressor output stream")
    void close_ClosesCompressorOutputStream() throws Exception {
        // when
        compressor.close();

        // then
        verify(mockCompressorOutputStream, times(1)).close();
    }

    @Test
    @DisplayName("Should throw Exception when closing compressor output stream fails")
    void close_ThrowsException_WhenCompressorOutputStreamCloseFails() throws Exception {
        // given
        doThrow(new IOException("Failed to close stream"))
                .when(mockCompressorOutputStream)
                .close();

        // when & then
        assertThatThrownBy(() -> compressor.close())
                .isInstanceOf(IOException.class)
                .hasMessage("Failed to close stream");
        verify(mockCompressorOutputStream, times(1)).close();
    }
}
