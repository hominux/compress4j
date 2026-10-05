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
package com.hominux.compress4j.internal.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class ArchiverDependencyCheckerTest {

    @Test
    void shouldNotThrowExceptionsForUnchecked() {
        assertDoesNotThrow(() -> ArchiverDependencyChecker.check("entry"));
    }

    @Test
    void shouldNotThrowForBrotliWhenDecIsPresent() {
        assertDoesNotThrow(ArchiverDependencyChecker::checkBrotli);
        assertDoesNotThrow(() -> ArchiverDependencyChecker.check("br"));
    }

    @Test
    void shouldNotThrowForLZMAWhenXzIsPresent() {
        assertDoesNotThrow(ArchiverDependencyChecker::checkLZMA);
    }

    @Test
    void shouldNotThrowForXZWhenXzIsPresent() {
        assertDoesNotThrow(ArchiverDependencyChecker::checkXZ);
    }

    @Test
    void shouldNotThrowForZstdWhenZstdJniIsPresent() {
        assertDoesNotThrow(ArchiverDependencyChecker::checkZstd);
    }
}
