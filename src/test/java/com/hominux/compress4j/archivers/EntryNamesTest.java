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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EntryNamesTest {

    @ParameterizedTest
    @CsvSource({
        "/a/b/, a/b",
        "a\\b, a/b",
        "a\\\\b, a/b",
        "//a, a",
        "a, a",
        "some_dir\\file_name.txt, some_dir/file_name.txt",
        "/file.txt, file.txt",
        "/../../../file.txt, ../../../file.txt",
        "path/, path",
        "\\file.txt, file.txt",
        "\\..\\..\\..\\file.txt, ../../../file.txt",
        "path\\, path",
        "/a/b\\\\c/d/, a/b/c/d",
        "a//b, a//b",
        "a///, a",
        "' a ', ' a '"
    })
    void sanitisedNormalisesSlashes(String raw, String expected) {
        assertThat(EntryNames.sanitised(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "//", "\\", " ", "", "/ ", " /", "\\ ", " \\"})
    void sanitisedRejectsBlankNames(String raw) {
        assertThatThrownBy(() -> EntryNames.sanitised(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid entry name: " + raw);
    }
}
