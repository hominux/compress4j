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
package com.hominux.compress4j.internal.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExpansionMeterTest {

    @Test
    void totalSizeIsEnforced() {
        var meter = new ExpansionMeter(ExtractionLimits.noLimits().withMaxTotalSize(10), () -> 10);
        assertThatThrownBy(() -> meter.produced(11, Optional.of("a")))
                .isInstanceOfSatisfying(LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(Limit.TOTAL_SIZE);
                    assertThat(e.entryName()).contains("a");
                });
    }

    @Test
    void ratioIsCheckedOnlyAfterTheGrace() throws IOException {
        var meter = new ExpansionMeter(ExtractionLimits.defaults(), () -> 1);
        meter.produced(ExpansionMeter.RATIO_GRACE_BYTES, Optional.empty());
        assertThatThrownBy(() -> meter.produced(1, Optional.empty()))
                .isInstanceOfSatisfying(
                        LimitExceededException.class, e -> assertThat(e.limit()).isEqualTo(Limit.RATIO));
    }

    @Test
    void ratioWithinTheLimitPasses() throws IOException {
        long compressed = ExpansionMeter.RATIO_GRACE_BYTES;
        var meter = new ExpansionMeter(ExtractionLimits.defaults(), () -> compressed);
        meter.produced(compressed * 100, Optional.empty());
        assertThat(meter.produced()).isEqualTo(compressed * 100);
    }

    @Test
    void unlimitedNeverThrows() {
        var meter = new ExpansionMeter(ExtractionLimits.noLimits(), () -> 0);
        assertThatCode(() -> meter.produced(Long.MAX_VALUE / 2, Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    void meterCountsReads() throws IOException {
        var meter = new ExpansionMeter(ExtractionLimits.noLimits().withMaxTotalSize(3), () -> 1);
        var in = meter.meter(new ByteArrayInputStream(new byte[5]), Optional.empty());
        assertThat(in.read(new byte[3])).isEqualTo(3);
        assertThatThrownBy(in::read).isInstanceOf(LimitExceededException.class);
    }

    private static void assertLimit(Limit expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                .isEqualTo(expected));
    }

    private static InputStream zeros() {
        return new InputStream() {
            @Override
            public int read() {
                return 0;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return len;
            }
        };
    }

    @Test
    void skipIsMeteredForTotalSize() {
        var meter = new ExpansionMeter(ExtractionLimits.noLimits().withMaxTotalSize(3), () -> 1);
        var in = meter.meter(new ByteArrayInputStream(new byte[10]), Optional.empty());
        assertLimit(Limit.TOTAL_SIZE, () -> in.skip(5));
    }

    @Test
    void skipIsMeteredForRatio() {
        int size = (int) ExpansionMeter.RATIO_GRACE_BYTES + 1;
        var meter = new ExpansionMeter(ExtractionLimits.defaults(), () -> 1);
        var in = meter.meter(new ByteArrayInputStream(new byte[size]), Optional.empty());
        assertLimit(Limit.RATIO, () -> in.skip(size));
    }

    @Test
    void endlessStreamFailsShortlyAfterTheGrace() {
        var meter = new ExpansionMeter(ExtractionLimits.defaults(), () -> 1);
        var in = meter.meter(zeros(), Optional.empty());
        var buffer = new byte[8192];
        assertLimit(Limit.RATIO, () -> {
            while (true) {
                in.read(buffer);
            }
        });
        assertThat(meter.produced()).isLessThan(ExpansionMeter.RATIO_GRACE_BYTES + 2 * buffer.length);
    }

    @Test
    void endOfStreamIsNotCounted() throws IOException {
        var meter = new ExpansionMeter(ExtractionLimits.noLimits(), () -> 1);
        var in = meter.meter(new ByteArrayInputStream(new byte[0]), Optional.empty());
        assertThat(in.read()).isEqualTo(-1);
        assertThat(in.read(new byte[4])).isEqualTo(-1);
        assertThat(meter.produced()).isZero();
    }

    @Test
    void totalEqualToTheMaximumPasses() throws IOException {
        var meter = new ExpansionMeter(ExtractionLimits.noLimits().withMaxTotalSize(10), () -> 1);
        meter.produced(10, Optional.empty());
        assertThat(meter.produced()).isEqualTo(10);
    }

    @Test
    void unlimitedRatioNeverThrowsWithTinyCompressedCount() throws IOException {
        var meter = new ExpansionMeter(ExtractionLimits.noLimits().withMaxTotalSize(Long.MAX_VALUE), () -> 1);
        meter.produced(ExpansionMeter.RATIO_GRACE_BYTES * 1000, Optional.empty());
        assertThat(meter.produced()).isEqualTo(ExpansionMeter.RATIO_GRACE_BYTES * 1000);
    }

    @Test
    void totalSaturatesInsteadOfWrapping() throws IOException {
        var meter = new ExpansionMeter(ExtractionLimits.noLimits(), () -> 1);
        meter.produced(Long.MAX_VALUE, Optional.empty());
        meter.produced(Long.MAX_VALUE, Optional.empty());
        assertThat(meter.produced()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void negativeCountsAreIgnored() throws IOException {
        var meter = new ExpansionMeter(ExtractionLimits.noLimits(), () -> 1);
        meter.produced(5, Optional.empty());
        meter.produced(-3, Optional.empty());
        assertThat(meter.produced()).isEqualTo(5);
    }

    @Test
    void resetRewindsTheTotal() throws IOException {
        var meter = new ExpansionMeter(ExtractionLimits.noLimits(), () -> 1);
        var in = meter.meter(new ByteArrayInputStream(new byte[10]), Optional.empty());
        in.read(new byte[2]);
        in.mark(10);
        in.read(new byte[5]);
        in.reset();
        assertThat(meter.produced()).isEqualTo(2);
    }
}
