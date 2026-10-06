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

import com.hominux.compress4j.archivers.UnsupportedEntry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CpioHardLinkTest {

    @TempDir
    Path out;

    @Test
    void namesWithoutDataAreReportedAndTheLastLinkKeepsTheContent() throws IOException {
        List<UnsupportedEntry> unsupported = new ArrayList<>();

        try (CpioArchiveExtractor extractor = CpioArchiveExtractor.builder(new ByteArrayInputStream(newc()))
                .unsupportedEntryHandler(unsupported::add)
                .build()) {
            extractor.extract(out);
        }

        assertThat(unsupported).extracting(UnsupportedEntry::name).containsExactly("first.txt");
        assertThat(out.resolve("first.txt")).doesNotExist();
        assertThat(out.resolve("second.txt")).hasContent("hello");
    }

    @Test
    void streamSkipsTheNamesWithoutData() throws IOException {
        try (CpioArchiveExtractor extractor =
                CpioArchiveExtractor.builder(new ByteArrayInputStream(newc())).build()) {
            assertThat(extractor.stream().map(item -> item.entry().name()).toList())
                    .containsExactly("second.txt");
        }
    }

    @Test
    void emptyFileWithOneLinkStillExtracts() throws IOException {
        List<UnsupportedEntry> unsupported = new ArrayList<>();

        try (CpioArchiveExtractor extractor = CpioArchiveExtractor.builder(
                        new ByteArrayInputStream(newc(entry("empty.txt", 1, new byte[0]))))
                .unsupportedEntryHandler(unsupported::add)
                .build()) {
            extractor.extract(out);
        }

        assertThat(unsupported).isEmpty();
        assertThat(out.resolve("empty.txt")).isEmptyFile();
    }

    @Test
    void hardLinkedEmptyFileIsReportedOnEveryName() throws IOException {
        List<UnsupportedEntry> unsupported = new ArrayList<>();

        try (CpioArchiveExtractor extractor = CpioArchiveExtractor.builder(
                        new ByteArrayInputStream(newc(entry("a", 2, new byte[0]), entry("b", 2, new byte[0]))))
                .unsupportedEntryHandler(unsupported::add)
                .build()) {
            extractor.extract(out);
        }

        assertThat(unsupported).extracting(UnsupportedEntry::name).containsExactly("a", "b");
    }

    private record Item(String name, int links, byte[] data) {}

    private static Item entry(String name, int links, byte[] data) {
        return new Item(name, links, data);
    }

    private static byte[] newc() throws IOException {
        return newc(
                entry("first.txt", 2, new byte[0]),
                entry("second.txt", 2, "hello".getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] newc(Item... items) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CpioArchiveOutputStream cpio = new CpioArchiveOutputStream(bytes, CpioConstants.FORMAT_NEW)) {
            for (Item item : items) {
                put(cpio, item);
            }
        }
        return bytes.toByteArray();
    }

    private static void put(CpioArchiveOutputStream cpio, Item item) throws IOException {
        CpioArchiveEntry entry = new CpioArchiveEntry(CpioConstants.FORMAT_NEW, item.name(), item.data().length);
        entry.setMode(CpioConstants.C_ISREG | 0644);
        entry.setInode(7);
        entry.setNumberOfLinks(item.links());
        cpio.putArchiveEntry(entry);
        cpio.write(item.data());
        cpio.closeArchiveEntry();
    }
}
