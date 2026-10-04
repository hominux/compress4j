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
package com.hominux.compress4j;

import static com.hominux.compress4j.ExtractionLimits.UNLIMITED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExtractionLimitsTest {

    @Test
    void defaultsAreEnforcedButGenerous() {
        var limits = ExtractionLimits.defaults();
        assertThat(limits.maxEntries()).isEqualTo(1_000_000);
        assertThat(limits.maxEntrySize()).isEqualTo(UNLIMITED);
        assertThat(limits.maxTotalSize()).isEqualTo(UNLIMITED);
        assertThat(limits.maxRatio()).isEqualTo(100);
    }

    @Test
    void unlimitedDisablesEverything() {
        var limits = ExtractionLimits.noLimits();
        assertThat(limits.maxEntries()).isEqualTo(UNLIMITED);
        assertThat(limits.maxEntrySize()).isEqualTo(UNLIMITED);
        assertThat(limits.maxTotalSize()).isEqualTo(UNLIMITED);
        assertThat(limits.maxRatio()).isEqualTo(UNLIMITED);
    }

    @Test
    void withersChangeOneComponent() {
        var limits = ExtractionLimits.defaults().withMaxTotalSize(10).withMaxRatio(5);
        assertThat(limits.maxEntries()).isEqualTo(1_000_000);
        assertThat(limits.maxEntrySize()).isEqualTo(UNLIMITED);
        assertThat(limits.maxTotalSize()).isEqualTo(10);
        assertThat(limits.maxRatio()).isEqualTo(5);
        assertThat(ExtractionLimits.defaults().withMaxEntries(7).withMaxEntrySize(8))
                .satisfies(l -> assertThat(l.maxEntries()).isEqualTo(7))
                .satisfies(l -> assertThat(l.maxEntrySize()).isEqualTo(8));
    }

    @Test
    void equalsHashCodeAndToStringFollowTheComponents() {
        var limits = ExtractionLimits.defaults().withMaxTotalSize(10);
        assertThat(limits)
                .isEqualTo(ExtractionLimits.defaults().withMaxTotalSize(10))
                .hasSameHashCodeAs(ExtractionLimits.defaults().withMaxTotalSize(10))
                .isNotEqualTo(ExtractionLimits.defaults())
                .isNotEqualTo(ExtractionLimits.defaults().withMaxTotalSize(11))
                .isNotEqualTo(ExtractionLimits.defaults().withMaxEntries(2).withMaxTotalSize(10))
                .isNotEqualTo(ExtractionLimits.defaults().withMaxEntrySize(2).withMaxTotalSize(10))
                .isNotEqualTo(ExtractionLimits.defaults().withMaxRatio(2).withMaxTotalSize(10))
                .isNotEqualTo("limits");
        assertThat(limits)
                .hasToString("ExtractionLimits[maxEntries=1000000, maxEntrySize=-1, maxTotalSize=10, maxRatio=100]");
    }

    @Test
    void negativeLimitsOtherThanUnlimitedAreRejected() {
        var defaults = ExtractionLimits.defaults();
        assertThatThrownBy(() -> defaults.withMaxEntries(-2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxEntries");
        assertThatThrownBy(() -> defaults.withMaxTotalSize(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTotalSize");
    }

    @Test
    void ratioMustBeAtLeastOne() {
        var defaults = ExtractionLimits.defaults();
        assertThatThrownBy(() -> defaults.withMaxRatio(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRatio");
        assertThat(defaults.withMaxRatio(1).maxRatio()).isEqualTo(1);
    }
}
