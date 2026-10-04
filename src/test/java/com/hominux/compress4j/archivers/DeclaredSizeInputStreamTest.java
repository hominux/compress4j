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
package com.hominux.compress4j.archivers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class DeclaredSizeInputStreamTest {

    private static DeclaredSizeInputStream declaring(String content, long declared) {
        return new DeclaredSizeInputStream(new ByteArrayInputStream(content.getBytes()), "e.txt", declared);
    }

    @Test
    void singleByteReadsYieldTheDeclaredBytesThenEnd() throws IOException {
        try (var in = declaring("ab", 2)) {
            assertThat(in.read()).isEqualTo('a');
            assertThat(in.read()).isEqualTo('b');
            assertThat(in.read()).isEqualTo(-1);
        }
    }

    @Test
    void zeroLengthReadReturnsZeroWithoutConsuming() throws IOException {
        try (var in = declaring("ab", 2)) {
            assertThat(in.read(new byte[4], 0, 0)).isZero();
            assertThat(in.readAllBytes()).hasSize(2);
        }
    }

    @Test
    void contentEndingEarlyFailsNamingTheEntry() throws IOException {
        try (var in = declaring("ab", 5)) {
            assertThatThrownBy(in::readAllBytes)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("e.txt")
                    .hasMessageContaining("2 of its declared 5");
        }
    }

    @Test
    void contentHoldingMoreFailsOnceTheDeclaredBytesAreRead() throws IOException {
        try (var in = declaring("abc", 2)) {
            assertThat(in.read(new byte[2], 0, 2)).isEqualTo(2);
            assertThatThrownBy(() -> in.read(new byte[1], 0, 1))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("more than its declared 2");
        }
    }

    @Test
    void requireExhaustedFailsWhenDeclaredBytesWereNotAllRead() throws IOException {
        try (var in = declaring("ab", 2)) {
            assertThat(in.read()).isEqualTo('a');
            assertThatThrownBy(in::requireExhausted)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("wrote 1 of its declared 2");
        }
    }

    @Test
    void requireExhaustedPassesAfterAllDeclaredBytesWereRead() throws IOException {
        try (var in = declaring("ab", 2)) {
            in.readAllBytes();
            assertThatCode(in::requireExhausted).doesNotThrowAnyException();
        }
    }

    @Test
    void skipAndAvailableNeverExceedTheDeclaredSize() throws IOException {
        try (var in = declaring("abcdef", 3)) {
            assertThat(in.available()).isEqualTo(3);
            assertThat(in.skip(10)).isEqualTo(3);
            assertThat(in.available()).isZero();
        }
    }

    @Test
    void skippingZeroOrNegativeCountsSkipsNothing() throws IOException {
        try (var in = declaring("abc", 3)) {
            assertThat(in.skip(0)).isZero();
            assertThat(in.skip(-5)).isZero();
            assertThat(in.available()).isEqualTo(3);
        }
    }

    @Test
    void markAndResetAreNotSupported() throws IOException {
        try (var in = declaring("ab", 2)) {
            assertThat(in.markSupported()).isFalse();
            assertThatThrownBy(in::reset).isInstanceOf(IOException.class);
        }
    }
}
