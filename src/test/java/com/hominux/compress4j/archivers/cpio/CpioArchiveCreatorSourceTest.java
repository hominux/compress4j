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
package com.hominux.compress4j.archivers.cpio;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.archivers.EntrySource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CpioArchiveCreatorSourceTest {

    private static final FileTime T = FileTime.from(Instant.EPOCH);

    @ParameterizedTest
    @ValueSource(shorts = {CpioConstants.FORMAT_NEW, CpioConstants.FORMAT_OLD_ASCII, CpioConstants.FORMAT_OLD_BINARY})
    void directoriesAndSymlinksKeepTheirModesOrTakeTheDefaults(short format) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var creator = CpioArchiveCreator.builder(out)
                .cpioOutputStream()
                .format(format)
                .and()
                .build()) {
            creator.add(new EntrySource.Directory("plain", 0, T));
            creator.add(new EntrySource.Directory("shared", 0750, T));
            creator.add(new EntrySource.Symlink("link-default", "plain", 0, T));
            creator.add(new EntrySource.Symlink("link-mode", "plain", 0640, T));
        }

        try (var in = new CpioArchiveInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(modeOf(in.getNextEntry())).isEqualTo(CpioConstants.C_ISDIR | 0755);
            assertThat(modeOf(in.getNextEntry())).isEqualTo(CpioConstants.C_ISDIR | 0750);
            assertThat(modeOf(in.getNextEntry())).isEqualTo(CpioConstants.C_ISLNK | 0777);
            assertThat(modeOf(in.getNextEntry())).isEqualTo(CpioConstants.C_ISLNK | 0640);
        }
    }

    private static long modeOf(CpioArchiveEntry entry) {
        return entry.getMode();
    }
}
