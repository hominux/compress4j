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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.archivers.ArchiveItem;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import com.hominux.compress4j.internal.limits.ExpansionMeter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DumpRatioTest {

    private static final int RECORD = 1024;
    private static final int RECORDS_PER_BLOCK = 10;
    private static final int NFS_MAGIC = 60012;
    private static final int CHECKSUM_TOTAL = 84446;
    private static final int TAPE = 1;
    private static final int INODE = 2;
    private static final int BITS = 3;
    private static final int ADDR = 4;
    private static final int END = 5;
    private static final int CLRI = 6;
    private static final int RECORDS_PER_HEADER = 512;
    private static final int ZEROS = 16 << 20;
    private static final long EARLY_STOP_BOUND = 2 * ExpansionMeter.RATIO_GRACE_BYTES;

    @TempDir
    Path dir;

    private static byte[] header(int type, int ino, int mode, long size, int count) {
        ByteBuffer b = ByteBuffer.allocate(RECORD).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, type).putInt(20, ino).putInt(24, NFS_MAGIC).putShort(32, (short) mode);
        b.putLong(40, size).putInt(160, count);
        for (int i = 0; i < Math.min(count, RECORDS_PER_HEADER); i++) {
            b.put(164 + i, (byte) 1);
        }
        return sealed(b);
    }

    private static byte[] tape() {
        ByteBuffer b = ByteBuffer.allocate(RECORD).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, TAPE).putInt(24, NFS_MAGIC).putInt(888, 0x80).putInt(896, RECORDS_PER_BLOCK);
        return sealed(b);
    }

    private static byte[] sealed(ByteBuffer b) {
        int sum = 0;
        for (int i = 0; i < RECORD; i += 4) {
            sum += b.getInt(i);
        }
        b.putInt(28, CHECKSUM_TOTAL - sum);
        return b.array();
    }

    private static byte[] rootDirectoryBlock() {
        byte[] name = "zeros".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer b = ByteBuffer.allocate(RECORD).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0, 3).putShort(4, (short) RECORD).put(6, (byte) 8).put(7, (byte) name.length);
        b.put(8, name);
        return b.array();
    }

    private static List<byte[]> records() {
        List<byte[]> records = new ArrayList<>();
        records.add(tape());
        records.add(header(CLRI, 0, 0, 0, 0));
        records.add(header(BITS, 0, 0, 0, 0));
        records.add(header(INODE, 2, 040755, RECORD, 1));
        records.add(rootDirectoryBlock());
        for (int segment = 0; segment < ZEROS / RECORD / RECORDS_PER_HEADER; segment++) {
            records.add(header(segment == 0 ? INODE : ADDR, 3, 0100644, ZEROS, RECORDS_PER_HEADER));
            for (int i = 0; i < RECORDS_PER_HEADER; i++) {
                records.add(new byte[RECORD]);
            }
        }
        records.add(header(END, 0, 0, 0, 0));
        while (records.size() % RECORDS_PER_BLOCK != 0) {
            records.add(new byte[RECORD]);
        }
        return records;
    }

    private static byte[] zlibBlock(byte[] block) {
        Deflater deflater = new Deflater();
        deflater.setInput(block);
        deflater.finish();
        byte[] buffer = new byte[block.length];
        int length = deflater.deflate(buffer);
        deflater.end();
        ByteBuffer out = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
        return out.putInt(length << 4 | 1).put(buffer, 0, length).array();
    }

    private static byte[] compressedDump() throws IOException {
        List<byte[]> records = records();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int first = 0; first < records.size(); first += RECORDS_PER_BLOCK) {
            ByteArrayOutputStream block = new ByteArrayOutputStream();
            for (byte[] entry : records.subList(first, first + RECORDS_PER_BLOCK)) {
                block.write(entry);
            }
            out.write(first == 0 ? block.toByteArray() : zlibBlock(block.toByteArray()));
        }
        return out.toByteArray();
    }

    private static void drain(DumpArchiveExtractor extractor, AtomicLong delivered) throws IOException {
        try {
            extractor.stream().forEach(item -> read(item, delivered));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void read(ArchiveItem item, AtomicLong delivered) {
        byte[] buffer = new byte[8192];
        try (var content = item.content()) {
            for (int n = content.read(buffer); n >= 0; n = content.read(buffer)) {
                delivered.addAndGet(n);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void defaultRatioStopsACompressedDumpBombEarlyWhenStreaming() throws IOException {
        var delivered = new AtomicLong();
        try (var extractor = DumpArchiveExtractor.builder(new ByteArrayInputStream(compressedDump()))
                .build()) {
            assertThatThrownBy(() -> drain(extractor, delivered))
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                            .isEqualTo(Limit.RATIO));
        }
        assertThat(delivered.get()).isLessThanOrEqualTo(EARLY_STOP_BOUND);
    }

    @Test
    void defaultRatioStopsACompressedDumpBombEarlyWhenExtracting() throws IOException {
        Path archive = Files.write(dir.resolve("bomb.dump"), compressedDump());
        Path out = Files.createDirectory(dir.resolve("out"));
        try (var extractor = DumpArchiveExtractor.builder(archive).build()) {
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOfSatisfying(LimitExceededException.class, e -> assertThat(e.limit())
                            .isEqualTo(Limit.RATIO));
        }
        assertThat(Files.size(out.resolve("zeros"))).isLessThanOrEqualTo(EARLY_STOP_BOUND);
    }

    @Test
    void unlimitedRatioAcceptsTheSameDumpBomb() throws IOException {
        var delivered = new AtomicLong();
        try (var extractor = DumpArchiveExtractor.builder(new ByteArrayInputStream(compressedDump()))
                .maxRatio(ExtractionLimits.UNLIMITED)
                .build()) {
            assertDoesNotThrow(() -> drain(extractor, delivered));
        }
        assertThat(delivered.get()).isEqualTo(ZEROS);
    }
}
