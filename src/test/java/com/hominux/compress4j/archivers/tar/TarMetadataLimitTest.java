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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarMetadataLimitTest {

    private static final int HUGE = 16 << 20;

    @TempDir
    Path out;

    private static byte[] filled(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 'a');
        return bytes;
    }

    private static byte[] gzippedWithMetadata(byte linkFlag, byte[] metadata) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(new GZIPOutputStream(bytes))) {
            var header = new TarArchiveEntry("meta", linkFlag);
            header.setSize(metadata.length);
            tar.putArchiveEntry(header);
            tar.write(metadata);
            tar.closeArchiveEntry();
            tar.putArchiveEntry(new TarArchiveEntry("real"));
            tar.closeArchiveEntry();
        }
        return bytes.toByteArray();
    }

    private static byte[] paxPathRecord(int valueSize) {
        int digits = 1;
        while (String.valueOf(valueSize + 7 + digits).length() != digits) {
            digits++;
        }
        byte[] prefix = ((valueSize + 7 + digits) + " path=").getBytes(StandardCharsets.UTF_8);
        byte[] record = Arrays.copyOf(prefix, prefix.length + valueSize + 1);
        Arrays.fill(record, prefix.length, record.length - 1, (byte) 'a');
        record[record.length - 1] = '\n';
        return record;
    }

    private void assertRatioBreach(byte[] archive) throws IOException {
        try (var extractor =
                TarArchiveExtractor.builder(new ByteArrayInputStream(archive)).build()) {
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                            .isEqualTo(Limit.RATIO));
        }
    }

    @Test
    void gnuLongNameCountsAgainstTheRatio() throws IOException {
        assertRatioBreach(gzippedWithMetadata(TarConstants.LF_GNUTYPE_LONGNAME, filled(HUGE)));
    }

    @Test
    void gnuLongLinkNameCountsAgainstTheRatio() throws IOException {
        assertRatioBreach(gzippedWithMetadata(TarConstants.LF_GNUTYPE_LONGLINK, filled(HUGE)));
    }

    @Test
    void paxRecordCountsAgainstTheRatio() throws IOException {
        assertRatioBreach(gzippedWithMetadata(TarConstants.LF_PAX_EXTENDED_HEADER_LC, paxPathRecord(HUGE)));
    }
}
