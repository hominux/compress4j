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

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.OptionalLong;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SparseTarEntryTest {

    private static final long REAL_SIZE = 1_048_581;

    @TempDir
    Path out;

    @Test
    void extractedFileHasTheExpandedLength() throws IOException {
        try (TarArchiveExtractor extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(sparseTar()))
                .maxRatio(ExtractionLimits.UNLIMITED)
                .build()) {
            extractor.extract(out);
        }

        assertThat(out.resolve("big.bin")).hasSize(REAL_SIZE);
    }

    @Test
    void streamReportsTheExpandedSize() throws IOException {
        try (TarArchiveExtractor extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(sparseTar()))
                .build()) {
            assertThat(extractor.stream().map(item -> item.entry().size()).toList())
                    .containsExactly(OptionalLong.of(REAL_SIZE));
        }
    }

    @Test
    void entrySizeLimitBelowTheExpandedSizeFails() throws IOException {
        try (TarArchiveExtractor extractor = TarArchiveExtractor.builder(new ByteArrayInputStream(sparseTar()))
                .maxRatio(ExtractionLimits.UNLIMITED)
                .maxEntrySize(1024)
                .build()) {
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                            .isEqualTo(Limit.ENTRY_SIZE));
        }
    }

    private static byte[] sparseTar() throws IOException {
        byte[] stored = "hello".getBytes(StandardCharsets.US_ASCII);
        byte[] records = (paxRecord("GNU.sparse.name", "big.bin")
                        + paxRecord("GNU.sparse.size", Long.toString(REAL_SIZE))
                        + paxRecord("GNU.sparse.numblocks", "1")
                        + paxRecord("GNU.sparse.map", (REAL_SIZE - 5) + ",5"))
                .getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(bytes)) {
            put(tar, new TarArchiveEntry("PaxHeaders/big", TarConstants.LF_PAX_EXTENDED_HEADER_LC), records);
            put(tar, new TarArchiveEntry("GNUSparseFile.0/big.bin"), stored);
        }
        return bytes.toByteArray();
    }

    private static void put(TarArchiveOutputStream tar, TarArchiveEntry entry, byte[] data) throws IOException {
        entry.setSize(data.length);
        tar.putArchiveEntry(entry);
        tar.write(data);
        tar.closeArchiveEntry();
    }

    private static String paxRecord(String key, String value) {
        String body = " " + key + "=" + value + "\n";
        int length = body.length() + 1;
        while (Integer.toString(length).length() + body.length() != length) {
            length = Integer.toString(length).length() + body.length();
        }
        return length + body;
    }
}
