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
package com.hominux.compress4j.archivers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.ar.ArArchiveExtractor;
import com.hominux.compress4j.archivers.cpio.CpioArchiveExtractor;
import com.hominux.compress4j.archivers.sevenz.SevenZArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.archivers.zip.ZipArchiveExtractor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream;
import org.apache.commons.compress.archivers.cpio.CpioConstants;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnsupportedEntryTest {

    @TempDir
    Path dir;

    private Path tarWithSpecialEntries(boolean includeRegularFile) throws IOException {
        Path tar = dir.resolve("special.tar");
        try (var out = new TarArchiveOutputStream(Files.newOutputStream(tar))) {
            if (includeRegularFile) {
                var file = new TarArchiveEntry("a.txt");
                file.setSize(1);
                out.putArchiveEntry(file);
                out.write('a');
                out.closeArchiveEntry();
            }
            var link = new TarArchiveEntry("b.txt", TarConstants.LF_LINK);
            link.setLinkName("a.txt");
            out.putArchiveEntry(link);
            out.closeArchiveEntry();
            out.putArchiveEntry(new TarArchiveEntry("pipe", TarConstants.LF_FIFO));
            out.closeArchiveEntry();
            out.putArchiveEntry(new TarArchiveEntry("tty", TarConstants.LF_CHR));
            out.closeArchiveEntry();
        }
        return tar;
    }

    @Test
    void tarStreamSkipsAndReportsHardLinksAndSpecialFiles() throws IOException {
        List<UnsupportedEntry> reported = new ArrayList<>();
        try (var extractor = TarArchiveExtractor.builder(tarWithSpecialEntries(true))
                .unsupportedEntryHandler(reported::add)
                .build()) {
            assertThat(extractor.stream().map(item -> item.entry().name())).containsExactly("a.txt");
        }
        assertThat(reported)
                .containsExactly(
                        new UnsupportedEntry("b.txt", "hard link"),
                        new UnsupportedEntry("pipe", "fifo"),
                        new UnsupportedEntry("tty", "character device"));
    }

    @Test
    void tarExtractSkipsAndReportsHardLinksAndSpecialFiles() throws IOException {
        List<UnsupportedEntry> reported = new ArrayList<>();
        Path out = dir.resolve("out");
        try (var extractor = TarArchiveExtractor.builder(tarWithSpecialEntries(true))
                .unsupportedEntryHandler(reported::add)
                .build()) {
            extractor.extract(out);
        }
        assertThat(out.resolve("a.txt")).hasContent("a");
        assertThat(out.resolve("b.txt")).doesNotExist();
        assertThat(out.resolve("pipe")).doesNotExist();
        assertThat(reported).hasSize(3);
    }

    @Test
    void archiveOfOnlyUnsupportedEntriesExtractsNothing() throws IOException {
        Path out = dir.resolve("out");
        try (var extractor =
                TarArchiveExtractor.builder(tarWithSpecialEntries(false)).build()) {
            extractor.extract(out);
        }
        assertThat(out).satisfiesAnyOf(p -> assertThat(p).doesNotExist(), p -> assertThat(p)
                .isEmptyDirectory());
    }

    @Test
    void throwingUnsupportedHandlerPropagates() throws IOException {
        try (var extractor = TarArchiveExtractor.builder(tarWithSpecialEntries(true))
                .unsupportedEntryHandler(entry -> {
                    throw new IllegalStateException("rejected " + entry.name());
                })
                .build()) {
            Path out = dir.resolve("out");
            assertThatThrownBy(() -> extractor.extract(out))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("rejected b.txt");
        }
    }

    @Test
    void cpioReportsDevicesAndFifosInsteadOfExtractingThemAsFiles() throws IOException {
        Path cpio = dir.resolve("special.cpio");
        try (var out = new CpioArchiveOutputStream(Files.newOutputStream(cpio))) {
            var file = new CpioArchiveEntry("a.txt");
            file.setMode(CpioConstants.C_ISREG | 0644);
            file.setSize(1);
            out.putArchiveEntry(file);
            out.write('a');
            out.closeArchiveEntry();
            var fifo = new CpioArchiveEntry("pipe");
            fifo.setMode(CpioConstants.C_ISFIFO | 0644);
            out.putArchiveEntry(fifo);
            out.closeArchiveEntry();
            var disk = new CpioArchiveEntry("disk");
            disk.setMode(CpioConstants.C_ISBLK | 0644);
            out.putArchiveEntry(disk);
            out.closeArchiveEntry();
        }
        List<UnsupportedEntry> reported = new ArrayList<>();
        try (var extractor = CpioArchiveExtractor.builder(cpio)
                .unsupportedEntryHandler(reported::add)
                .build()) {
            assertThat(extractor.stream().map(item -> item.entry().name())).containsExactly("a.txt");
        }
        assertThat(reported)
                .containsExactly(new UnsupportedEntry("pipe", "fifo"), new UnsupportedEntry("disk", "block device"));
    }

    @Test
    void zipReportsDeviceModes() throws IOException {
        Path zip = dir.resolve("special.zip");
        try (var out = new ZipArchiveOutputStream(zip)) {
            var file = new ZipArchiveEntry("a.txt");
            file.setUnixMode(0100644);
            out.putArchiveEntry(file);
            out.write("a".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
            var tty = new ZipArchiveEntry("tty");
            tty.setUnixMode(0020644);
            out.putArchiveEntry(tty);
            out.closeArchiveEntry();
        }
        List<UnsupportedEntry> reported = new ArrayList<>();
        try (var extractor = ZipArchiveExtractor.builder(zip)
                .unsupportedEntryHandler(reported::add)
                .build()) {
            assertThat(extractor.stream().map(item -> item.entry().name())).containsExactly("a.txt");
        }
        assertThat(reported).containsExactly(new UnsupportedEntry("tty", "character device"));
    }

    @Test
    void zipStreamingCannotSeeDeviceModes() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZipArchiveOutputStream(bytes)) {
            var file = new ZipArchiveEntry("a.txt");
            file.setUnixMode(0100644);
            out.putArchiveEntry(file);
            out.write("a".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
            var tty = new ZipArchiveEntry("tty");
            tty.setUnixMode(0020644);
            out.putArchiveEntry(tty);
            out.closeArchiveEntry();
        }
        List<UnsupportedEntry> reported = new ArrayList<>();
        try (var extractor = ZipArchiveExtractor.streaming(new ByteArrayInputStream(bytes.toByteArray()))
                .unsupportedEntryHandler(reported::add)
                .build()) {
            assertThat(extractor.stream().map(item -> item.entry().name())).containsExactly("a.txt", "tty");
        }
        assertThat(reported).isEmpty();
    }

    @Test
    void throwingUnsupportedHandlerPropagatesFromStream() throws IOException {
        try (var extractor = TarArchiveExtractor.builder(tarWithSpecialEntries(true))
                .unsupportedEntryHandler(entry -> {
                    throw new IllegalStateException("rejected " + entry.name());
                })
                .build()) {
            assertThatThrownBy(() -> drain(extractor))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("rejected b.txt");
        }
    }

    @Test
    void cpioSymlinkStreamsAsSymlinkWithTarget() throws IOException {
        Path cpio = dir.resolve("link.cpio");
        try (var out = new CpioArchiveOutputStream(Files.newOutputStream(cpio))) {
            var link = new CpioArchiveEntry("link");
            link.setMode(CpioConstants.C_ISLNK | 0777);
            link.setSize(5);
            out.putArchiveEntry(link);
            out.write("a.txt".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }
        try (var extractor = CpioArchiveExtractor.builder(cpio).build()) {
            var entries = extractor.stream().map(ArchiveItem::entry).toList();
            assertThat(entries).extracting(Entry::type).containsExactly(Entry.Type.SYMLINK);
            assertThat(entries.get(0).linkTarget()).contains("a.txt");
        }
    }

    @Test
    void zipSymlinkStreamsAsSymlinkWithTarget() throws IOException {
        Path zip = dir.resolve("link.zip");
        try (var out = new ZipArchiveOutputStream(zip)) {
            var link = new ZipArchiveEntry("link");
            link.setUnixMode(UnixStat.LINK_FLAG | 0777);
            out.putArchiveEntry(link);
            out.write("a.txt".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }
        try (var extractor = ZipArchiveExtractor.builder(zip).build()) {
            var entries = extractor.stream().map(ArchiveItem::entry).toList();
            assertThat(entries).extracting(Entry::type).containsExactly(Entry.Type.SYMLINK);
            assertThat(entries.get(0).linkTarget()).contains("a.txt");
        }
    }

    @Test
    void sevenZReportsDeviceModes() throws IOException {
        Path sevenZ = dir.resolve("special.7z");
        try (var out = new SevenZOutputFile(sevenZ.toFile())) {
            var file = out.createArchiveEntry(Files.writeString(dir.resolve("a.txt"), "a"), "a.txt");
            out.putArchiveEntry(file);
            out.write("a".getBytes(StandardCharsets.UTF_8));
            out.closeArchiveEntry();
            var tty = out.createArchiveEntry(dir.resolve("a.txt"), "tty");
            tty.setHasWindowsAttributes(true);
            tty.setWindowsAttributes(0x8000 | (0020644 << 16));
            out.putArchiveEntry(tty);
            out.closeArchiveEntry();
        }
        List<UnsupportedEntry> reported = new ArrayList<>();
        try (var extractor = SevenZArchiveExtractor.builder(sevenZ)
                .unsupportedEntryHandler(reported::add)
                .build()) {
            assertThat(extractor.stream().map(item -> item.entry().name())).containsExactly("a.txt");
        }
        assertThat(reported).containsExactly(new UnsupportedEntry("tty", "character device"));
    }

    private static void putAr(ArArchiveOutputStream out, String name, int mode, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        out.putArchiveEntry(new ArArchiveEntry(name, bytes.length, 0, 0, mode, 0));
        out.write(bytes);
        out.closeArchiveEntry();
    }

    private Path arWithModes() throws IOException {
        Path ar = dir.resolve("special.ar");
        try (var out = new ArArchiveOutputStream(Files.newOutputStream(ar))) {
            putAr(out, "a.txt", 0100644, "a");
            putAr(out, "tty", 0020644, "x");
            putAr(out, "plain.txt", 0644, "p");
            putAr(out, "pipe", 0010644, "");
        }
        return ar;
    }

    @Test
    void arReportsDevicesAndFifosInsteadOfExtractingThemAsFiles() throws IOException {
        List<UnsupportedEntry> reported = new ArrayList<>();
        try (var extractor = ArArchiveExtractor.builder(arWithModes())
                .unsupportedEntryHandler(reported::add)
                .build()) {
            assertThat(extractor.stream().map(item -> item.entry().name())).containsExactly("a.txt", "plain.txt");
        }
        assertThat(reported)
                .containsExactly(new UnsupportedEntry("tty", "character device"), new UnsupportedEntry("pipe", "fifo"));
    }

    @Test
    void arDirectoryModeStreamsAsDirectory() throws IOException {
        Path ar = dir.resolve("dirmode.ar");
        try (var out = new ArArchiveOutputStream(Files.newOutputStream(ar))) {
            putAr(out, "d", 040755, "");
        }
        List<UnsupportedEntry> reported = new ArrayList<>();
        try (var extractor = ArArchiveExtractor.builder(ar)
                .unsupportedEntryHandler(reported::add)
                .build()) {
            assertThat(extractor.stream().map(item -> item.entry().type())).containsExactly(Entry.Type.DIR);
        }
        assertThat(reported).isEmpty();
    }

    @Test
    void arExtractSkipsDeviceEntries() throws IOException {
        Path out = dir.resolve("out");
        try (var extractor = ArArchiveExtractor.builder(arWithModes()).build()) {
            extractor.extract(out);
        }
        assertThat(out.resolve("a.txt")).hasContent("a");
        assertThat(out.resolve("plain.txt")).hasContent("p");
        assertThat(out.resolve("tty")).doesNotExist();
    }

    @Test
    void sevenZDirectoryModeWithoutDirectoryAttributeIsADirectory() throws IOException {
        Path sevenZ = dir.resolve("dirmode.7z");
        Path source = Files.writeString(dir.resolve("a.txt"), "a");
        try (var out = new SevenZOutputFile(sevenZ.toFile())) {
            var d = out.createArchiveEntry(source, "d");
            d.setHasWindowsAttributes(true);
            d.setWindowsAttributes(0x8000 | (040755 << 16));
            out.putArchiveEntry(d);
            out.closeArchiveEntry();
        }
        List<UnsupportedEntry> reported = new ArrayList<>();
        try (var extractor = SevenZArchiveExtractor.builder(sevenZ)
                .unsupportedEntryHandler(reported::add)
                .build()) {
            assertThat(extractor.stream().map(item -> item.entry().type())).containsExactly(Entry.Type.DIR);
        }
        assertThat(reported).isEmpty();
    }

    @Test
    void handlerMustNotBeNull() throws IOException {
        var builder = TarArchiveExtractor.builder(tarWithSpecialEntries(true));
        assertThatThrownBy(() -> builder.unsupportedEntryHandler(null)).isInstanceOf(NullPointerException.class);
    }

    private static void drain(ArchiveExtractor<?> extractor) {
        try (var items = extractor.stream()) {
            items.forEach(item -> {});
        }
    }
}
