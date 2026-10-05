/*
 * Copyright 2025-2026 The Compress4J Project
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.EntrySource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.archivers.zip.ZipShort;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipArchiveCreatorBuilderTest {

    private static final byte[] CONTENT =
            "Content to be compressed. ".repeat(50).getBytes(StandardCharsets.UTF_8);

    private static final ZipShort ZIP64_HEADER_ID = new ZipShort(1);
    private static final ZipShort UNICODE_PATH_HEADER_ID = new ZipShort(0x7075);

    @TempDir
    Path dir;

    private ZipArchiveEntry entryOf(ZipArchiveCreator.Builder builder) throws IOException {
        Path zip = dir.resolve("out.zip");
        try (var creator = builder.build()) {
            creator.add(EntrySource.file("a.txt", CONTENT));
        }
        try (var file = ZipFile.builder().setPath(zip).get()) {
            return file.getEntry("a.txt");
        }
    }

    @Test
    void unbuiltPathBuilderCreatesNoFile() {
        Path zip = dir.resolve("never.zip");

        ZipArchiveCreator.builder(zip).compressionLevel(9).comment("unused");

        assertThat(zip).doesNotExist();
    }

    @Test
    void missingParentDirectoryFailsInBuild() {
        var builder = ZipArchiveCreator.builder(dir.resolve("missing/out.zip"));

        assertThatThrownBy(builder::build).isInstanceOf(IOException.class);
    }

    @Test
    void invalidCompressionLevelIsRejected() {
        var builder = ZipArchiveCreator.builder(new ByteArrayOutputStream());

        assertThatThrownBy(() -> builder.compressionLevel(-2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Compression level must be between -1 and 9");
        assertThatThrownBy(() -> builder.compressionLevel(10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullOptionsAreRejected() {
        var builder = ZipArchiveCreator.builder(new ByteArrayOutputStream());

        assertThatThrownBy(() -> builder.compressionMethod(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> builder.comment(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> builder.zip64(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> builder.createUnicodeExtraFields(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> builder.encoding(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void compressionMethodSelectsStoredOrDeflated() throws IOException {
        var stored = entryOf(
                ZipArchiveCreator.builder(dir.resolve("out.zip")).compressionMethod(ZipCompressionMethod.STORED));
        assertThat(stored.getMethod()).isEqualTo(ZipEntry.STORED);

        var deflated = entryOf(
                ZipArchiveCreator.builder(dir.resolve("out.zip")).compressionMethod(ZipCompressionMethod.DEFLATED));
        assertThat(deflated.getMethod()).isEqualTo(ZipEntry.DEFLATED);
        assertThat(deflated.getCompressedSize()).isLessThan(CONTENT.length);
    }

    @Test
    void commentIsWrittenToTheArchive() throws IOException {
        var channel = new SeekableInMemoryByteChannel();
        try (var creator = ZipArchiveCreator.builder(channel).comment("note").build()) {
            creator.add(EntrySource.file("a.txt", CONTENT));
        }

        assertThat(new String(channel.array(), 0, (int) channel.size(), StandardCharsets.ISO_8859_1))
                .endsWith("note");
    }

    @Test
    void zip64AlwaysWritesZip64ExtraFields() throws IOException {
        var entry = entryOf(ZipArchiveCreator.builder(dir.resolve("out.zip")).zip64(ZipZip64Mode.ALWAYS));

        assertThat(entry.getExtraField(ZIP64_HEADER_ID)).isNotNull();
    }

    @Test
    void zip64NeverOmitsZip64ExtraFields() throws IOException {
        var entry = entryOf(ZipArchiveCreator.builder(dir.resolve("out.zip")).zip64(ZipZip64Mode.NEVER));

        assertThat(entry.getExtraField(ZIP64_HEADER_ID)).isNull();
    }

    private ZipArchiveEntry unicodeEntry(ZipUnicodeExtraFields policy, Charset encoding, String name)
            throws IOException {
        Path zip = dir.resolve("unicode.zip");
        try (var creator = ZipArchiveCreator.builder(zip)
                .encoding(encoding)
                .createUnicodeExtraFields(policy)
                .build()) {
            creator.add(EntrySource.file(name, CONTENT));
        }
        try (var file = ZipFile.builder().setPath(zip).setCharset(encoding).get()) {
            return file.getEntries().nextElement();
        }
    }

    @Test
    void unicodeExtraFieldsAlwaysAddsThemToEveryEntry() throws IOException {
        var entry = unicodeEntry(ZipUnicodeExtraFields.ALWAYS, StandardCharsets.UTF_8, "a.txt");

        assertThat(entry.getExtraField(UNICODE_PATH_HEADER_ID)).isNotNull();
    }

    @Test
    void unicodeExtraFieldsNeverAddsNone() throws IOException {
        var entry = unicodeEntry(ZipUnicodeExtraFields.NEVER, StandardCharsets.UTF_8, "caf\u00e9.txt");

        assertThat(entry.getExtraField(UNICODE_PATH_HEADER_ID)).isNull();
    }

    @Test
    void unicodeExtraFieldsNotEncodeableAddsThemOnlyWhenTheEncodingFails() throws IOException {
        var encodeable = unicodeEntry(ZipUnicodeExtraFields.NOT_ENCODEABLE, StandardCharsets.US_ASCII, "a.txt");
        var unencodeable =
                unicodeEntry(ZipUnicodeExtraFields.NOT_ENCODEABLE, StandardCharsets.US_ASCII, "caf\u00e9.txt");

        assertThat(encodeable.getExtraField(UNICODE_PATH_HEADER_ID)).isNull();
        assertThat(unencodeable.getExtraField(UNICODE_PATH_HEADER_ID)).isNotNull();
    }

    @Test
    void zip64AlwaysWithCompatibilityWritesZip64ExtraFields() throws IOException {
        var entry = entryOf(
                ZipArchiveCreator.builder(dir.resolve("out.zip")).zip64(ZipZip64Mode.ALWAYS_WITH_COMPATIBILITY));

        assertThat(entry.getExtraField(ZIP64_HEADER_ID)).isNotNull();
    }

    @Test
    void zip64ModesMapToTheirCommonsModes() {
        assertThat(ZipZip64Mode.NEVER.value).isEqualTo(Zip64Mode.Never);
        assertThat(ZipZip64Mode.ALWAYS.value).isEqualTo(Zip64Mode.Always);
        assertThat(ZipZip64Mode.ALWAYS_WITH_COMPATIBILITY.value).isEqualTo(Zip64Mode.AlwaysWithCompatibility);
        assertThat(ZipZip64Mode.AS_NEEDED.value).isEqualTo(Zip64Mode.AsNeeded);
    }

    @Test
    void encodingControlsEntryNames() throws IOException {
        Path zip = dir.resolve("latin.zip");
        try (var creator = ZipArchiveCreator.builder(zip)
                .encoding(StandardCharsets.ISO_8859_1)
                .useLanguageEncodingFlag(false)
                .fallbackToUtf8(false)
                .build()) {
            creator.add(EntrySource.file("café.txt", CONTENT));
        }

        try (var file = ZipFile.builder()
                .setPath(zip)
                .setCharset(StandardCharsets.ISO_8859_1)
                .get()) {
            assertThat(file.getEntry("café.txt")).isNotNull();
        }
    }

    @Test
    void writesToAStreamBuilder() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var creator = ZipArchiveCreator.builder(out).build()) {
            creator.add(EntrySource.file("a.txt", CONTENT));
        }

        assertThat(out.size()).isPositive();
    }

    @Test
    void ownedPathIsFilledByTheCreator() throws IOException {
        Path zip = dir.resolve("filled.zip");
        try (var creator = ZipArchiveCreator.builder(zip).build()) {
            creator.add(EntrySource.file("a.txt", CONTENT));
        }

        assertThat(Files.size(zip)).isPositive();
    }
}
