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
package com.hominux.compress4j.archivers.tar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.archivers.ArchiveItem;
import com.hominux.compress4j.archivers.Entry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarMetadataTest {

    @TempDir
    Path dir;

    @Test
    void tarReportsArchiveModesOnEveryHost() throws IOException {
        Path tar = dir.resolve("m.tar");
        try (var out = new TarArchiveOutputStream(Files.newOutputStream(tar))) {
            var run = new TarArchiveEntry("run.sh");
            run.setMode(0100755);
            run.setSize(1);
            out.putArchiveEntry(run);
            out.write('x');
            out.closeArchiveEntry();
            var d = new TarArchiveEntry("d/");
            d.setMode(040750);
            out.putArchiveEntry(d);
            out.closeArchiveEntry();
        }
        Map<String, Entry> entries;
        try (var extractor = TarArchiveExtractor.builder(tar).build()) {
            entries = extractor.stream().map(ArchiveItem::entry).collect(Collectors.toMap(Entry::name, e -> e));
        }
        assertThat(entries.get("run.sh").mode() & 07777).isEqualTo(0755);
        assertThat(entries.get("run.sh").type()).isEqualTo(Entry.Type.FILE);
        assertThat(entries.get("d").mode() & 07777).isEqualTo(0750);
        assertThat(entries.get("d").type()).isEqualTo(Entry.Type.DIR);
        assertThat(entries.get("run.sh").linkTarget()).isEmpty();
        assertThat(entries.get("d").linkTarget()).isEmpty();
    }

    @Test
    void tarSymlinkKeepsTypeModeAndTarget() throws IOException {
        Path tar = dir.resolve("s.tar");
        try (var out = new TarArchiveOutputStream(Files.newOutputStream(tar))) {
            var link = new TarArchiveEntry("link", TarConstants.LF_SYMLINK);
            link.setLinkName("target.txt");
            link.setMode(0120777);
            out.putArchiveEntry(link);
            out.closeArchiveEntry();
        }
        Entry entry;
        try (var extractor = TarArchiveExtractor.builder(tar).build()) {
            entry = extractor.stream().map(ArchiveItem::entry).findFirst().orElseThrow();
        }
        assertThat(entry.type()).isEqualTo(Entry.Type.SYMLINK);
        assertThat(entry.mode() & 07777).isEqualTo(0777);
        assertThat(entry.linkTarget()).contains("target.txt");
    }
}
