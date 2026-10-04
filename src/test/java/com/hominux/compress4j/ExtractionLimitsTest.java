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
        assertThat(ExtractionLimits.defaults()).isEqualTo(new ExtractionLimits(1_000_000, UNLIMITED, UNLIMITED, 100));
    }

    @Test
    void unlimitedDisablesEverything() {
        assertThat(ExtractionLimits.unlimited())
                .isEqualTo(new ExtractionLimits(UNLIMITED, UNLIMITED, UNLIMITED, UNLIMITED));
    }

    @Test
    void withersChangeOneComponent() {
        var limits = ExtractionLimits.defaults().withMaxTotalSize(10).withMaxRatio(5);
        assertThat(limits).isEqualTo(new ExtractionLimits(1_000_000, UNLIMITED, 10, 5));
    }

    @Test
    void negativeLimitsOtherThanUnlimitedAreRejected() {
        assertThatThrownBy(() -> ExtractionLimits.defaults().withMaxEntries(-2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxEntries");
        assertThatThrownBy(() -> ExtractionLimits.defaults().withMaxTotalSize(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTotalSize");
    }

    @Test
    void ratioMustBeAtLeastOne() {
        assertThatThrownBy(() -> ExtractionLimits.defaults().withMaxRatio(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRatio");
        assertThat(ExtractionLimits.defaults().withMaxRatio(1).maxRatio()).isEqualTo(1);
    }
}
