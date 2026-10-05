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
package com.hominux.compress4j.internal.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.archivers.UnsupportedEntry;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReaderContextTest {

    private static ReaderContext contextWith(ExtractionLimits limits) {
        return new ReaderContext(limits, entry -> {});
    }

    private static byte[] readDeclared(ExtractionLimits limits, long declaredSize) throws IOException {
        return contextWith(limits)
                .readDeclared("a", new ByteArrayInputStream(new byte[(int) declaredSize]), declaredSize);
    }

    @Test
    void zeroIsAStrictLimit() {
        var limits = ExtractionLimits.noLimits().withMaxEntrySize(0);
        assertThatCode(() -> readDeclared(limits, 0)).doesNotThrowAnyException();
        assertThatThrownBy(() -> readDeclared(limits, 1))
                .isInstanceOfSatisfying(
                        LimitExceededException.class, e -> assertThat(e.limit()).isEqualTo(Limit.ENTRY_SIZE));
    }

    @Test
    void readDeclared_allowsSizeAtTheLimit() throws IOException {
        var limits = ExtractionLimits.noLimits().withMaxEntrySize(10);
        assertThat(readDeclared(limits, 10)).hasSize(10);
    }

    @Test
    void readDeclared_rejectsSizeAboveTheLimitBeforeReading() {
        var limits = ExtractionLimits.noLimits().withMaxEntrySize(10);
        assertThatThrownBy(() -> contextWith(limits).readDeclared("a", InputStream.nullInputStream(), 11))
                .isInstanceOfSatisfying(LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(Limit.ENTRY_SIZE);
                    assertThat(e.maximum()).isEqualTo(10);
                    assertThat(e.entryName()).contains("a");
                });
    }

    @Test
    void readDeclared_neverRejectsWhenUnlimited() throws IOException {
        assertThat(readDeclared(ExtractionLimits.noLimits(), 5)).hasSize(5);
    }

    @Test
    void reportUnsupported_tellsTheHandler() {
        List<UnsupportedEntry> seen = new ArrayList<>();
        new ReaderContext(ExtractionLimits.noLimits(), seen::add).reportUnsupported("n", "fifo");
        assertThat(seen).containsExactly(new UnsupportedEntry("n", "fifo"));
    }

    @Test
    void readDeclared_failsOnAHugeDeclaredSizeWithAShortStream() {
        var context = contextWith(ExtractionLimits.noLimits());
        var in = new ByteArrayInputStream(new byte[3]);

        assertThatThrownBy(() -> context.readDeclared("a", in, Integer.MAX_VALUE))
                .isInstanceOf(EOFException.class)
                .hasMessageContaining("3 of " + Integer.MAX_VALUE);
    }

    @Test
    void readDeclared_failsWhenTheStreamEndsEarly() {
        var context = contextWith(ExtractionLimits.noLimits());
        var in = new ByteArrayInputStream(new byte[2]);

        assertThatThrownBy(() -> context.readDeclared("a", in, 5)).isInstanceOf(EOFException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE, Integer.MAX_VALUE + 1L, Long.MAX_VALUE})
    void readDeclared_rejectsSizesOutsideTheIntRangeAsIOException(long size) {
        var context = contextWith(ExtractionLimits.noLimits());
        var in = InputStream.nullInputStream();

        assertThatThrownBy(() -> context.readDeclared("a", in, size))
                .isExactlyInstanceOf(IOException.class)
                .hasMessageContaining("'a'");
    }
}
