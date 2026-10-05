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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hominux.compress4j.compressors.memory.InMemoryDecompressor;
import com.hominux.compress4j.compressors.memory.InMemoryDecompressor.InMemoryDecompressorBuilder;
import com.hominux.compress4j.compressors.memory.InMemoryDecompressorInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodecDecompressorTest {

    @Mock
    private InMemoryDecompressorInputStream mockCompressorInputStream;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should construct Decompressor with a builder")
    void constructor_WithBuilder_SetsField() throws IOException {
        // given
        InMemoryDecompressorBuilder builder = new InMemoryDecompressorBuilder(mockCompressorInputStream);

        // when
        InMemoryDecompressor inMemoryDecompressor = new InMemoryDecompressor(builder);
        inMemoryDecompressor.close();

        // then
        assertThat(inMemoryDecompressor).isNotNull();
    }

    @Test
    @DisplayName("Should write all bytes from input stream to a file")
    void write_ToFile_CopiesBytes() throws IOException {
        // given
        Path outputPath = tempDir.resolve("output.txt");
        byte[] testBytes = "Hello Decompressor".getBytes();

        try (InMemoryDecompressor decompressor = InMemoryDecompressor.builder(
                        new InMemoryDecompressorInputStream(new ByteArrayInputStream(testBytes)))
                .build()) {

            // when
            long bytesWritten = decompressor.write(outputPath.toFile());

            // then
            assertThat(outputPath).exists().hasContent("Hello Decompressor");
            assertThat(bytesWritten).isEqualTo(testBytes.length);
        }
    }

    @Test
    @DisplayName("Should throw IOException when writing to file fails")
    void write_ToFile_ThrowsIOException_WhenCopyFails() throws IOException {
        // given
        File nonWritableFile = new File("/nonexistent/path/cannot_write.txt");

        try ( // when & then
        InMemoryDecompressor decompressor = new InMemoryDecompressor(mockCompressorInputStream)) {
            assertThatThrownBy(() -> decompressor.write(nonWritableFile))
                    .isInstanceOf(IOException.class)
                    .hasMessage(nonWritableFile.getPath());
        }
    }

    @Test
    @DisplayName("Should throw IOException when writing to path fails")
    void write_ToPath_ThrowsIOException_WhenCopyFails() throws IOException {
        // given
        Path nonWritablePath = tempDir.resolve("non_existent_dir/output.txt");

        try ( // when & then
        InMemoryDecompressor decompressor = new InMemoryDecompressor(mockCompressorInputStream)) {
            assertThatThrownBy(() -> decompressor.write(nonWritablePath))
                    .isInstanceOf(IOException.class)
                    .hasMessage(nonWritablePath.toString());
        }
    }

    @Test
    @DisplayName("Should close the compressor input stream")
    void close_ClosesCompressorInputStream() throws IOException {
        // given
        InMemoryDecompressor decompressor = new InMemoryDecompressor(mockCompressorInputStream);

        // when
        decompressor.close();

        // then
        verify(mockCompressorInputStream, times(1)).close();
    }

    @Test
    @DisplayName("Should throw IOException when closing compressor input stream fails")
    void close_ThrowsIOException_WhenCompressorInputStreamCloseFails() throws IOException {
        // given
        doThrow(new IOException("Failed to close stream"))
                .when(mockCompressorInputStream)
                .close();
        InMemoryDecompressor decompressor = new InMemoryDecompressor(mockCompressorInputStream);

        // when & then
        assertThatThrownBy(decompressor::close).isInstanceOf(IOException.class).hasMessage("Failed to close stream");
        verify(mockCompressorInputStream, times(1)).close();
    }
}
