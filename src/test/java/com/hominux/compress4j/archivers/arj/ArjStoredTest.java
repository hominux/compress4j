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
package com.hominux.compress4j.archivers.arj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

class ArjStoredTest {

    private static final int FIRST_HEADER_SIZE = 30;
    private static final int SIZE = 3 << 19;

    private static byte[] block(byte[] firstHeader, String name) {
        ByteArrayOutputStream basic = new ByteArrayOutputStream();
        basic.write(FIRST_HEADER_SIZE);
        basic.writeBytes(firstHeader);
        basic.writeBytes((name + "\0\0").getBytes(StandardCharsets.US_ASCII));
        byte[] basicBytes = basic.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(basicBytes);
        ByteBuffer out = ByteBuffer.allocate(2 + 2 + basicBytes.length + 4 + 2).order(ByteOrder.LITTLE_ENDIAN);
        out.put((byte) 0x60)
                .put((byte) 0xEA)
                .putShort((short) basicBytes.length)
                .put(basicBytes);
        return out.putInt((int) crc.getValue()).putShort((short) 0).array();
    }

    private static byte[] mainHeader() {
        ByteBuffer b = ByteBuffer.allocate(FIRST_HEADER_SIZE - 1).order(ByteOrder.LITTLE_ENDIAN);
        b.put(0, (byte) 11).put(1, (byte) 1);
        return block(b.array(), "a");
    }

    private static byte[] localHeader(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        ByteBuffer b = ByteBuffer.allocate(FIRST_HEADER_SIZE - 1).order(ByteOrder.LITTLE_ENDIAN);
        b.put(0, (byte) 11).put(1, (byte) 1);
        b.putInt(7, 0).putInt(11, data.length).putInt(15, data.length).putInt(19, (int) crc.getValue());
        return block(b.array(), "big");
    }

    private static byte[] storedArchive() {
        byte[] data = new byte[SIZE];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(mainHeader());
        out.writeBytes(localHeader(data));
        out.writeBytes(data);
        out.writeBytes(new byte[] {0x60, (byte) 0xEA, 0, 0});
        return out.toByteArray();
    }

    @Test
    void storedEntryAboveTheGraceSizeIsAcceptedUnderTheDefaultRatio() throws IOException {
        var delivered = new AtomicLong();
        try (var extractor = ArjArchiveExtractor.builder(new ByteArrayInputStream(storedArchive()))
                .build()) {
            assertDoesNotThrow(() -> extractor.stream().forEach(item -> {
                try (var content = item.content()) {
                    delivered.addAndGet(content.readAllBytes().length);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));
        }
        assertThat(delivered.get()).isEqualTo(SIZE);
    }
}
