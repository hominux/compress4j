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

import com.hominux.compress4j.archivers.UnsupportedEntry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.jupiter.api.Test;

class TarEntryFlagTest {

    private static final byte GNU_VOLUME_HEADER = (byte) 'V';

    private static void addFile(TarArchiveOutputStream out, TarArchiveEntry entry) throws IOException {
        entry.setSize(1);
        out.putArchiveEntry(entry);
        out.write('x');
        out.closeArchiveEntry();
    }

    private static byte[] tar(TarWriter writer) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new TarArchiveOutputStream(bytes)) {
            writer.write(out);
        }
        return bytes.toByteArray();
    }

    private static List<String> names(byte[] tar, List<UnsupportedEntry> reported) throws IOException {
        try (var extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(tar))
                .unsupportedEntryHandler(reported::add)
                .build()) {
            return extractor.stream().map(item -> item.entry().name()).toList();
        }
    }

    @Test
    void globalPaxHeaderIsSkippedSilently() throws IOException {
        byte[] tar = tar(out -> {
            var global = new TarArchiveEntry("pax_global_header", TarConstants.LF_PAX_GLOBAL_EXTENDED_HEADER);
            global.addPaxHeader("comment", "0123456789012345678901234567890123456789");
            out.putArchiveEntry(global);
            addFile(out, new TarArchiveEntry("a.txt"));
        });
        List<UnsupportedEntry> reported = new ArrayList<>();
        assertThat(names(tar, reported)).containsExactly("a.txt");
        assertThat(reported).isEmpty();
    }

    @Test
    void gnuVolumeHeaderIsReportedAsUnknownType() throws IOException {
        byte[] tar = tar(out -> {
            var volume = new TarArchiveEntry("backup-label", GNU_VOLUME_HEADER);
            out.putArchiveEntry(volume);
            out.closeArchiveEntry();
            addFile(out, new TarArchiveEntry("a.txt"));
        });
        List<UnsupportedEntry> reported = new ArrayList<>();
        assertThat(names(tar, reported)).containsExactly("a.txt");
        assertThat(reported).containsExactly(new UnsupportedEntry("backup-label", "unknown type"));
    }

    @Test
    void contiguousAndOldNormalFilesAreListed() throws IOException {
        byte[] tar = tar(out -> {
            addFile(out, new TarArchiveEntry("contig", TarConstants.LF_CONTIG));
            addFile(out, new TarArchiveEntry("old", TarConstants.LF_OLDNORM));
        });
        List<UnsupportedEntry> reported = new ArrayList<>();
        assertThat(names(tar, reported)).containsExactly("contig", "old");
        assertThat(reported).isEmpty();
    }

    @FunctionalInterface
    private interface TarWriter {
        void write(TarArchiveOutputStream out) throws IOException;
    }
}
