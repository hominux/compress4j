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
package com.hominux.compress4j.archivers.dump;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.archivers.Entry;
import org.apache.commons.compress.archivers.dump.DumpArchiveEntry.TYPE;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DumpEntryTypeTest {

    @Test
    void filesAndDirectoriesAreSupported() {
        assertThat(DumpEntryReader.typeOf(TYPE.FILE)).contains(Entry.Type.FILE);
        assertThat(DumpEntryReader.typeOf(TYPE.DIRECTORY)).contains(Entry.Type.DIR);
    }

    @ParameterizedTest
    @CsvSource({
        "LINK, symbolic link",
        "CHRDEV, character device",
        "BLKDEV, block device",
        "FIFO, fifo",
        "SOCKET, socket",
        "WHITEOUT, whiteout",
        "UNKNOWN, unknown type"
    })
    void otherTypesAreUnsupported(TYPE type, String kind) {
        assertThat(DumpEntryReader.typeOf(type)).isEmpty();
        assertThat(DumpEntryReader.kindOf(type)).isEqualTo(kind);
    }
}
