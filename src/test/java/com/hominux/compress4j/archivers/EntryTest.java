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

import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class EntryTest {

    @Test
    void basicEntryHasNoOptionalMetadata() {
        // When
        var entry = new Entry("a.txt", Entry.Type.FILE, 0644);

        // Then
        assertThat(entry.linkTarget()).isEmpty();
        assertThat(entry.lastModified()).isEmpty();
        assertThat(entry.size()).isEmpty();
    }

    @Test
    void withLastModifiedRecordsTheTime() {
        var time = FileTime.fromMillis(1_700_000_000_000L);

        assertThat(new Entry("a", Entry.Type.FILE, 0).withLastModified(time).lastModified())
                .contains(time);
    }

    @Test
    void withSizeRecordsTheSize() {
        assertThat(new Entry("a", Entry.Type.FILE, 0).withSize(12).size()).hasValue(12);
    }

    @Test
    void negativeSizeIsEmpty() {
        assertThat(new Entry("a", Entry.Type.FILE, 0).withSize(-1).size()).isEmpty();
    }

    @Test
    void blankLinkTargetIsEmpty() {
        assertThat(new Entry("l", Entry.Type.SYMLINK, 0).withLinkTarget(" ").linkTarget())
                .isEmpty();
    }

    @Test
    void linkTargetIsRecorded() {
        assertThat(new Entry("l", Entry.Type.SYMLINK, 0).withLinkTarget("t").linkTarget())
                .contains("t");
    }

    @Test
    void nameIsStillNormalised() {
        assertThat(new Entry("/a\\b/", Entry.Type.DIR, 0).name()).isEqualTo("a/b");
    }

    @Test
    void canonicalConstructorAppliesTheSameRulesAsTheWithers() {
        var entry = new Entry("l", Entry.Type.SYMLINK, 0, Optional.of(" "), Optional.empty(), OptionalLong.of(-1));

        assertThat(entry.linkTarget()).isEmpty();
        assertThat(entry.size()).isEmpty();
    }

    @Test
    void canonicalConstructorRejectsNullComponents() {
        Optional<String> noTarget = Optional.empty();
        Optional<FileTime> noTime = Optional.empty();
        OptionalLong noSize = OptionalLong.empty();

        assertThatThrownBy(() -> new Entry("a", null, 0, noTarget, noTime, noSize))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Entry("a", Entry.Type.FILE, 0, null, noTime, noSize))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Entry("a", Entry.Type.FILE, 0, noTarget, null, noSize))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Entry("a", Entry.Type.FILE, 0, noTarget, noTime, null))
                .isInstanceOf(NullPointerException.class);
    }
}
