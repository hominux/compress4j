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
package com.example;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.archivers.ErrorHandlerChoice;
import com.hominux.compress4j.archivers.EscapingSymlinkPolicy;
import com.hominux.compress4j.archivers.UnsupportedEntry;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.archivers.zip.ZipArchiveExtractor;
import com.hominux.compress4j.compressors.Decompressor;
import com.hominux.compress4j.exceptions.LimitExceededException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public final class SecurityExamples {

    private SecurityExamples() {
        /* no-op */
    }

    public static void limits(Path archive, Path out) throws IOException {
        // tag::limits[]
        ExtractionLimits policy =
                ExtractionLimits.defaults().withMaxTotalSize(10L << 30).withMaxRatio(1000);
        try (ZipArchiveExtractor extractor =
                ZipArchiveExtractor.builder(archive).limits(policy).build()) {
            extractor.extract(out);
        }
        // end::limits[]
    }

    public static void unlimited(Path trustedArchive, Path out) throws IOException {
        // tag::unlimited[]
        try (TarArchiveExtractor extractor = TarArchiveExtractor.builder(trustedArchive)
                .limits(ExtractionLimits.noLimits())
                .build()) {
            extractor.extract(out);
        }
        // end::unlimited[]
    }

    public static void limitExceeded(Path archive, Path out) throws IOException {
        // tag::limit-exceeded[]
        try (TarArchiveExtractor extractor =
                TarArchiveExtractor.builder(archive).build()) {
            extractor.extract(out);
        } catch (LimitExceededException e) {
            throw new IOException(
                    e.limit() + " exceeded: " + e.maximum() + " in "
                            + e.entryName().orElse("input"),
                    e);
        }
        // end::limit-exceeded[]
    }

    public static void decompressorLimits(Path compressed, Path out) throws IOException {
        // tag::decompressor-limits[]
        try (Decompressor decompressor = Decompressor.builder(compressed)
                .maxRatio(1000)
                .maxTotalSize(1L << 30)
                .build()) {
            decompressor.write(out);
        }
        // end::decompressor-limits[]
    }

    public static void errorHandler(Path archive, Path out) throws IOException {
        // tag::error-handler[]
        try (TarArchiveExtractor extractor = TarArchiveExtractor.builder(archive)
                .errorHandler((entry, failure) -> ErrorHandlerChoice.SKIP)
                .build()) {
            extractor.extract(out);
        }
        // end::error-handler[]
    }

    public static List<UnsupportedEntry> unsupportedEntries(Path archive, Path out) throws IOException {
        // tag::unsupported-entries[]
        List<UnsupportedEntry> skipped = new ArrayList<>();
        try (TarArchiveExtractor extractor = TarArchiveExtractor.builder(archive)
                .unsupportedEntryHandler(skipped::add)
                .build()) {
            extractor.extract(out);
        }
        // end::unsupported-entries[]
        return skipped;
    }

    public static void symlinkPolicy(Path archive, Path out) throws IOException {
        // tag::symlink-policy[]
        try (TarArchiveExtractor extractor = TarArchiveExtractor.builder(archive)
                .escapingSymlinkPolicy(EscapingSymlinkPolicy.RELATIVIZE_ABSOLUTE)
                .build()) {
            extractor.extract(out);
        }
        // end::symlink-policy[]
    }
}
