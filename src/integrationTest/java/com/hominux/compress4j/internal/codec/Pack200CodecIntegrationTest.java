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
package com.hominux.compress4j.internal.codec;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.compressors.Compression;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class Pack200CodecIntegrationTest {

    private static final byte[] CONTENT = "Test content for Pack200 compression".getBytes();

    private static byte[] jar() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) {
            // Pack200 needs the entry size up front, which a streamed (deflated) entry does not carry.
            var entry = new JarEntry("test.txt");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(CONTENT.length);
            entry.setCompressedSize(CONTENT.length);
            var crc = new CRC32();
            crc.update(CONTENT);
            entry.setCrc(crc.getValue());
            jar.putNextEntry(entry);
            jar.write(CONTENT);
            jar.closeEntry();
        }
        return bytes.toByteArray();
    }

    @ParameterizedTest
    @EnumSource(Compression.Pack200.Strategy.class)
    void writesAndReadsAJar(Compression.Pack200.Strategy strategy) throws IOException {
        var pack200 = Compression.pack200().strategy(strategy);
        var packed = new ByteArrayOutputStream();
        try (OutputStream out = Codecs.compressing(pack200, packed)) {
            out.write(jar());
        }
        try (var jar =
                new JarInputStream(Codecs.decompressing(pack200, new ByteArrayInputStream(packed.toByteArray())))) {
            assertThat(jar.getNextJarEntry().getName()).isEqualTo("test.txt");
            assertThat(jar.readAllBytes()).isEqualTo(CONTENT);
        }
    }
}
