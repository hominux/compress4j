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

import com.hominux.compress4j.archivers.arj.ArjArchiveExtractor;
import com.hominux.compress4j.archivers.catalog.ArchiveFormat;
import com.hominux.compress4j.archivers.catalog.FormatCatalog;
import com.hominux.compress4j.archivers.dump.DumpArchiveExtractor;
import com.hominux.compress4j.test.util.Corruptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.apache.commons.io.function.IOFunction;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CorruptArchiveTest {

    private static final int FLIPS = 60;
    private static final int RECORD = 1024;
    private static final FileTime MODIFIED = FileTime.from(Instant.parse("2024-05-01T10:00:00Z"));

    @TempDir
    Path dir;

    record Subject(String name, IOFunction<Path, ArchiveFormat.Reader> read, Optional<ArchiveFormat> writer) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Subject> subjects() {
        var arj = new Subject(
                "arj", p -> ArchiveFormat.reader(ArjArchiveExtractor.builder(p).build()), Optional.empty());
        var dump = new Subject(
                "dump",
                p -> ArchiveFormat.reader(DumpArchiveExtractor.builder(p).build()),
                Optional.empty());
        var cataloged = FormatCatalog.readable()
                .map(f -> new Subject(f.name(), f.readAt(), Optional.of(FormatCatalog.writerOf(f))));
        return Stream.concat(cataloged, Stream.of(arj, dump));
    }

    private List<byte[]> damaged(Subject subject) throws IOException {
        if (subject.writer().isPresent()) {
            return Corruptions.variantsOf(valid(subject.writer().orElseThrow()), FLIPS);
        }
        byte[] magic = subject.name().equals("arj") ? new byte[] {0x60, (byte) 0xEA} : dumpMagic();
        return Corruptions.garbageAfter(magic, 4 * RECORD, FLIPS);
    }

    private static byte[] dumpMagic() {
        byte[] prefix = new byte[28];
        prefix[24] = 0x6C;
        prefix[25] = (byte) 0xEA;
        return prefix;
    }

    private byte[] valid(ArchiveFormat format) throws IOException {
        Path archive = dir.resolve("valid." + format.name());
        try (var creator = format.createAt().orElseThrow().apply(archive)) {
            creator.add(fixed("a/one.txt", "first entry".repeat(50)));
            creator.add(fixed("two.txt", "second"));
        }
        return Files.readAllBytes(archive);
    }

    private static EntrySource fixed(String name, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new EntrySource.File(
                name, 0644, MODIFIED, OptionalLong.of(bytes.length), () -> new ByteArrayInputStream(bytes));
    }

    private static void drain(ArchiveFormat.Reader reader) throws IOException {
        try {
            reader.stream().forEach(CorruptArchiveTest::readContent);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void readContent(ArchiveItem item) {
        try (var content = item.content()) {
            content.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest
    @MethodSource("subjects")
    void corruptInputOnlyThrowsIOException(Subject subject) throws IOException {
        var failures = new ArrayList<String>();
        int index = 0;
        for (byte[] bytes : damaged(subject)) {
            Path archive = Files.write(dir.resolve("damaged-" + index), bytes);
            Path out = dir.resolve("out-" + index);
            check(subject, "stream", index, failures, () -> {
                try (var reader = subject.read().apply(archive)) {
                    drain(reader);
                }
            });
            check(subject, "extract", index, failures, () -> {
                try (var reader = subject.read().apply(archive)) {
                    reader.extract(out);
                }
            });
            index++;
        }
        assertThat(failures).as("seed %d", Corruptions.SEED).isEmpty();
    }

    private static void check(
            Subject subject, String path, int variant, List<String> failures, Corruptions.Action action) {
        Corruptions.nonIoFailure(action)
                .ifPresent(e -> failures.add(subject.name() + " " + path + " variant " + variant + ": " + e));
    }
}
