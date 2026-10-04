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
package com.hominux.compress4j.archivers.zip;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.archivers.ArchiveExtractor.Entry;
import com.hominux.compress4j.archivers.ArchiveItem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipStreamingTest {

    @TempDir
    Path out;

    private static byte[] zipWithFileAndSymlink() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var z = new ZipArchiveOutputStream(bytes)) {
            var file = new ZipArchiveEntry("run.sh");
            file.setUnixMode(0100750);
            z.putArchiveEntry(file);
            z.write("x".getBytes(StandardCharsets.UTF_8));
            z.closeArchiveEntry();
            var link = new ZipArchiveEntry("link");
            link.setUnixMode(UnixStat.LINK_FLAG | 0777);
            z.putArchiveEntry(link);
            z.write("run.sh".getBytes(StandardCharsets.UTF_8));
            z.closeArchiveEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void channelBuilderReadsModesAndSymlinks() throws IOException {
        try (var x = ZipArchiveExtractor.builder(new SeekableInMemoryByteChannel(zipWithFileAndSymlink()))
                .build()) {
            var entries = x.stream().map(ArchiveItem::entry).toList();
            assertThat(entries).extracting(Entry::type).containsExactly(Entry.Type.FILE, Entry.Type.SYMLINK);
            assertThat(entries.get(0).mode() & 0777).isEqualTo(0750);
        }
    }

    @Test
    void streamingSurfacesSymlinkAsPlainFileAndNeverCreatesALink() throws IOException {
        try (var x = ZipArchiveExtractor.streaming(new ByteArrayInputStream(zipWithFileAndSymlink()))
                .build()) {
            x.extract(out);
        }
        assertThat(out.resolve("link")).isRegularFile().hasContent("run.sh");
        assertThat(Files.isSymbolicLink(out.resolve("link"))).isFalse();
    }

    @Test
    void streamingReadsStoredEntryWithDataDescriptor() throws IOException {
        try (var x = ZipArchiveExtractor.streaming(new ByteArrayInputStream(storedWithDataDescriptor()))
                .build()) {
            var item = x.stream().findFirst().orElseThrow();
            assertThat(new String(item.content().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("stored-with-dd");
        }
    }

    /** Hand-built zip: one STORED entry, general-purpose flag bit 3, sizes in a trailing data descriptor. */
    private static byte[] storedWithDataDescriptor() {
        byte[] data = "stored-with-dd".getBytes(StandardCharsets.UTF_8);
        byte[] name = "s.txt".getBytes(StandardCharsets.UTF_8);
        var crc = new CRC32();
        crc.update(data);
        int c = (int) crc.getValue();
        var b = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x04034b50)
                .putShort((short) 20)
                .putShort((short) 8)
                .putShort((short) 0)
                .putInt(0)
                .putInt(0)
                .putInt(0)
                .putInt(0)
                .putShort((short) name.length)
                .putShort((short) 0)
                .put(name)
                .put(data);
        b.putInt(0x08074b50).putInt(c).putInt(data.length).putInt(data.length);
        int cd = b.position();
        b.putInt(0x02014b50)
                .putShort((short) 20)
                .putShort((short) 20)
                .putShort((short) 8)
                .putShort((short) 0)
                .putInt(0)
                .putInt(c)
                .putInt(data.length)
                .putInt(data.length)
                .putShort((short) name.length)
                .putShort((short) 0)
                .putShort((short) 0)
                .putShort((short) 0)
                .putShort((short) 0)
                .putInt(0)
                .putInt(0)
                .put(name);
        int cdLen = b.position() - cd;
        b.putInt(0x06054b50)
                .putShort((short) 0)
                .putShort((short) 0)
                .putShort((short) 1)
                .putShort((short) 1)
                .putInt(cdLen)
                .putInt(cd)
                .putShort((short) 0);
        return Arrays.copyOf(b.array(), b.position());
    }
}
