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

import com.hominux.compress4j.archivers.catalog.ArchiveFormat;
import com.hominux.compress4j.archivers.catalog.Capability;
import com.hominux.compress4j.archivers.catalog.FormatCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CapabilityTableTest {

    private static final Path TABLE = Path.of("docs/modules/ROOT/partials/capabilities.adoc");

    static String render() {
        String header = "|Format |"
                + Arrays.stream(Capability.values())
                        .map(c -> c.name().toLowerCase().replace('_', ' '))
                        .collect(Collectors.joining(" |"));
        String rows = FormatCatalog.all().map(CapabilityTableTest::row).collect(Collectors.joining("\n"));
        return "[cols=\"2," + "1,".repeat(Capability.values().length - 1) + "1\",options=\"header\"]\n|===\n" + header
                + "\n\n" + rows + "\n|===\n";
    }

    private static String row(ArchiveFormat format) {
        return "|" + format.name() + " |"
                + Arrays.stream(Capability.values())
                        .map(c -> format.has(c) ? "✓" : "")
                        .collect(Collectors.joining(" |"));
    }

    private static String published() throws IOException {
        return Files.exists(TABLE)
                ? Files.readString(TABLE, StandardCharsets.UTF_8).replace("\r\n", "\n")
                : "";
    }

    @Test
    void publishedTableMatchesTheVerifiedCatalog() throws IOException {
        String expected = render();
        assertThat(published())
                .as("Regenerate %s with this content:%n%s", TABLE, expected)
                .isEqualTo(expected);
    }
}
