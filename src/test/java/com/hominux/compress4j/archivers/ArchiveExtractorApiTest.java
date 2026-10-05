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

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ArchiveExtractorApiTest {

    @Test
    void hasNoPublicMutators() {
        assertThat(Arrays.stream(ArchiveExtractor.class.getMethods())
                        .filter(m -> m.getDeclaringClass() == ArchiveExtractor.class)
                        .map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Test
    void isNotIterable() {
        assertThat(Iterable.class.isAssignableFrom(ArchiveExtractor.class)).isFalse();
    }
}
