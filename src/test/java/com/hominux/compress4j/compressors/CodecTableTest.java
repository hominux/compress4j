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
package com.hominux.compress4j.compressors;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.compressors.catalog.CodecCatalog;
import com.hominux.compress4j.compressors.catalog.CodecFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CodecTableTest {

    private static final Path TABLE = Path.of("docs/modules/ROOT/partials/codecs.adoc");

    static String render() {
        String rows = CodecCatalog.all().map(CodecTableTest::row).collect(Collectors.joining("\n"));
        return "[cols=\"2,1,1,1,3\",options=\"header\"]\n|===\n|Codec |Read |Write |Detected |Compression value\n\n"
                + rows + "\n|===\n";
    }

    private static String row(CodecFormat format) {
        return "|" + format.name() + " |✓ |" + (format.compression().canWrite() ? "✓" : "") + " |"
                + (format.detectable() ? "✓" : "") + " |`Compression." + factory(format.compression()) + "()`";
    }

    private static String factory(Compression compression) {
        return switch (compression) {
            case None c -> "none";
            case Gzip c -> "gzip";
            case Bzip2 c -> "bzip2";
            case Xz c -> "xz";
            case Lzma c -> "lzma";
            case Lz4Block c -> "lz4Block";
            case Lz4Framed c -> "lz4Framed";
            case Zstd c -> "zstd";
            case Deflate c -> "deflate";
            case Deflate64 c -> "deflate64";
            case SnappyRaw c -> "snappyRaw";
            case SnappyFramed c -> "snappyFramed";
            case Brotli c -> "brotli";
            case UnixZ c -> "unixZ";
            case Pack200 c -> "pack200";
        };
    }

    @Test
    void publishedTableMatchesTheVerifiedCatalog() throws IOException {
        String expected = render();
        String published = Files.exists(TABLE)
                ? Files.readString(TABLE, StandardCharsets.UTF_8).replace("\r\n", "\n")
                : "";
        assertThat(published)
                .as("Regenerate %s with this content:%n%s", TABLE, expected)
                .isEqualTo(expected);
    }
}
