/*
 * Copyright 2024-2026 The Compress4J Project
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

import static com.hominux.compress4j.ExtractionLimits.UNLIMITED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ExtractionBudgetTest {

    @Test
    void countEntry_throwsOnceMaxEntriesIsExceeded() throws IOException {
        ExtractionBudget budget =
                new ExtractionBudget(ExtractionLimits.noLimits().withMaxEntries(2));
        budget.countEntry();
        budget.countEntry();
        assertThatThrownBy(budget::countEntry).isInstanceOfSatisfying(LimitExceededException.class, e -> {
            assertThat(e.limit()).isEqualTo(Limit.ENTRIES);
            assertThat(e.maximum()).isEqualTo(2);
            assertThat(e.entryName()).isEmpty();
        });
    }

    @Test
    void countEntry_neverThrowsWhenUnlimited() {
        ExtractionBudget budget = new ExtractionBudget(ExtractionLimits.noLimits());
        assertThatCode(() -> {
                    for (int i = 0; i < 1000; i++) budget.countEntry();
                })
                .doesNotThrowAnyException();
    }

    @Test
    void meterThrowsOnTheReadThatCrossesTheEntryLimit() throws IOException {
        // Given
        var budget = new ExtractionBudget(new ExtractionLimits(UNLIMITED, 4, UNLIMITED, UNLIMITED));
        var in = budget.meter("e", new ByteArrayInputStream(new byte[10]));

        // When
        int first = in.read(new byte[4]);

        // Then
        assertThat(first).isEqualTo(4);
        assertThatThrownBy(in::read).isInstanceOfSatisfying(LimitExceededException.class, e -> {
            assertThat(e.limit()).isEqualTo(Limit.ENTRY_SIZE);
            assertThat(e.maximum()).isEqualTo(4);
            assertThat(e.entryName()).contains("e");
        });
    }

    @Test
    void meterCountsTotalAcrossEntries() throws IOException {
        // Given
        var budget = new ExtractionBudget(new ExtractionLimits(UNLIMITED, UNLIMITED, 6, UNLIMITED));

        // When
        budget.meter("a", new ByteArrayInputStream(new byte[4])).readAllBytes();
        var second = budget.meter("b", new ByteArrayInputStream(new byte[4]));

        // Then
        assertThatThrownBy(second::readAllBytes).isInstanceOfSatisfying(LimitExceededException.class, e -> {
            assertThat(e.limit()).isEqualTo(Limit.TOTAL_SIZE);
            assertThat(e.maximum()).isEqualTo(6);
        });
    }

    @Test
    void meterCountsSkippedBytes() {
        // Given
        var budget = new ExtractionBudget(new ExtractionLimits(UNLIMITED, 4, UNLIMITED, UNLIMITED));
        var in = budget.meter("s", new ByteArrayInputStream(new byte[10]));

        // Then
        assertThatThrownBy(() -> in.skip(10)).isInstanceOfSatisfying(LimitExceededException.class, e -> {
            assertThat(e.limit()).isEqualTo(Limit.ENTRY_SIZE);
            assertThat(e.maximum()).isEqualTo(4);
        });
    }

    @Test
    void unlimitedBudgetReturnsTheSameStream() {
        var raw = new ByteArrayInputStream(new byte[1]);
        assertThat(new ExtractionBudget(ExtractionLimits.noLimits()).meter("x", raw))
                .isSameAs(raw);
    }

    @Test
    void entryOfExactlyMaxEntrySizeReadsFullyThenReturnsEof() throws IOException {
        // Given
        var budget = new ExtractionBudget(new ExtractionLimits(UNLIMITED, 4, UNLIMITED, UNLIMITED));
        var in = budget.meter("e", new ByteArrayInputStream(new byte[4]));

        // Then
        assertThat(in.readNBytes(4)).hasSize(4);
        assertThat(in.read()).isEqualTo(-1);
    }

    @Test
    void totalOfExactlyMaxTotalSizeAcrossTwoEntriesReadsWithoutThrowing() {
        // Given
        var budget = new ExtractionBudget(new ExtractionLimits(UNLIMITED, UNLIMITED, 6, UNLIMITED));

        // Then
        assertThatCode(() -> {
                    budget.meter("a", new ByteArrayInputStream(new byte[3])).readAllBytes();
                    budget.meter("b", new ByteArrayInputStream(new byte[3])).readAllBytes();
                })
                .doesNotThrowAnyException();
    }

    @Test
    void zeroIsAStrictLimit() {
        var limits = ExtractionLimits.noLimits().withMaxEntrySize(0);
        assertThatCode(() -> ExtractionBudget.checkDeclaredSize(limits, "a", 0)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ExtractionBudget.checkDeclaredSize(limits, "a", 1))
                .isInstanceOfSatisfying(
                        LimitExceededException.class, e -> assertThat(e.limit()).isEqualTo(Limit.ENTRY_SIZE));
    }

    @Test
    void checkDeclaredSize_allowsSizeAtTheLimit() {
        var limits = ExtractionLimits.noLimits().withMaxEntrySize(10);
        assertThatCode(() -> ExtractionBudget.checkDeclaredSize(limits, "a", 10))
                .doesNotThrowAnyException();
    }

    @Test
    void checkDeclaredSize_rejectsSizeAboveTheLimit() {
        var limits = ExtractionLimits.noLimits().withMaxEntrySize(10);
        assertThatThrownBy(() -> ExtractionBudget.checkDeclaredSize(limits, "a", 11))
                .isInstanceOfSatisfying(LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(Limit.ENTRY_SIZE);
                    assertThat(e.maximum()).isEqualTo(10);
                    assertThat(e.entryName()).contains("a");
                });
    }

    @Test
    void checkDeclaredSize_neverRejectsWhenUnlimited() {
        assertThatCode(() -> ExtractionBudget.checkDeclaredSize(ExtractionLimits.noLimits(), "a", Long.MAX_VALUE))
                .doesNotThrowAnyException();
    }

    @Test
    void defaultLimitsStopTheEntryAfterOneMillion() throws IOException {
        var produced = new java.util.concurrent.atomic.AtomicLong();
        EntryReader reader = new EntryReader() {
            @Override
            public java.util.Optional<ArchiveExtractor.Entry> next() {
                return java.util.Optional.of(new ArchiveExtractor.Entry(
                        "e" + produced.incrementAndGet(), ArchiveExtractor.Entry.Type.DIR, 0));
            }

            @Override
            public java.io.InputStream open(ArchiveExtractor.Entry entry) {
                return java.io.InputStream.nullInputStream();
            }

            @Override
            public void release(java.io.InputStream content) throws java.io.IOException {
                content.close();
            }
        };
        var pipeline = new EntryPipeline(reader, 0, entry -> true, ExtractionLimits.defaults());
        for (int i = 0; i < 1_000_000; i++) {
            assertThat(pipeline.advance()).isPresent();
        }
        assertThatThrownBy(pipeline::advance).isInstanceOfSatisfying(LimitExceededException.class, e -> {
            assertThat(e.limit()).isEqualTo(Limit.ENTRIES);
            assertThat(e.maximum()).isEqualTo(1_000_000);
        });
    }
}
