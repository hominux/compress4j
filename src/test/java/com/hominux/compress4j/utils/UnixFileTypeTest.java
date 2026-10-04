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
package com.hominux.compress4j.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnixFileTypeTest {

    @ParameterizedTest
    @CsvSource({
        "0100644, FILE",
        "0000644, FILE",
        "0000000, FILE",
        "0040755, DIRECTORY",
        "0120777, SYMLINK",
        "0020644, CHARACTER_DEVICE",
        "0060644, BLOCK_DEVICE",
        "0010644, FIFO",
        "0140755, SOCKET",
        "0170000, UNKNOWN"
    })
    void classifiesByTheFileTypeBits(String octalMode, UnixFileType expected) {
        assertThat(UnixFileType.of(Integer.parseInt(octalMode, 8))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"CHARACTER_DEVICE, character device", "FIFO, fifo", "SOCKET, socket", "UNKNOWN, unknown type"})
    void kindIsReadable(UnixFileType type, String kind) {
        assertThat(type.kind()).isEqualTo(kind);
    }
}
