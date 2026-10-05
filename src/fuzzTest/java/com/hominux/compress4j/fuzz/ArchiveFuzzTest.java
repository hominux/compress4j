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
package com.hominux.compress4j.fuzz;

import static com.hominux.compress4j.fuzz.FuzzSupport.LIMITS;
import static com.hominux.compress4j.fuzz.FuzzSupport.drain;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.hominux.compress4j.archivers.ar.ArArchiveExtractor;
import com.hominux.compress4j.archivers.arj.ArjArchiveExtractor;
import com.hominux.compress4j.archivers.cpio.CpioArchiveExtractor;
import com.hominux.compress4j.archivers.dump.DumpArchiveExtractor;
import com.hominux.compress4j.archivers.sevenz.SevenZArchiveExtractor;
import com.hominux.compress4j.archivers.tar.TarArchiveExtractor;
import com.hominux.compress4j.archivers.zip.ZipArchiveExtractor;
import java.io.ByteArrayInputStream;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

class ArchiveFuzzTest {

    @FuzzTest(maxDuration = "60s")
    void tar(byte[] data) {
        drain(() -> TarArchiveExtractor.builder(new ByteArrayInputStream(data))
                .limits(LIMITS)
                .build());
    }

    @FuzzTest(maxDuration = "60s")
    void zip(byte[] data) {
        drain(() -> ZipArchiveExtractor.builder(new SeekableInMemoryByteChannel(data))
                .limits(LIMITS)
                .build());
    }

    @FuzzTest(maxDuration = "60s")
    void zipStreaming(byte[] data) {
        drain(() -> ZipArchiveExtractor.streaming(new ByteArrayInputStream(data))
                .limits(LIMITS)
                .build());
    }

    @FuzzTest(maxDuration = "60s")
    void sevenZ(byte[] data) {
        drain(() -> SevenZArchiveExtractor.builder(new SeekableInMemoryByteChannel(data))
                .limits(LIMITS)
                .build());
    }

    @FuzzTest(maxDuration = "60s")
    void ar(byte[] data) {
        drain(() -> ArArchiveExtractor.builder(new ByteArrayInputStream(data))
                .limits(LIMITS)
                .build());
    }

    @FuzzTest(maxDuration = "60s")
    void cpio(byte[] data) {
        drain(() -> CpioArchiveExtractor.builder(new ByteArrayInputStream(data))
                .limits(LIMITS)
                .build());
    }

    @FuzzTest(maxDuration = "60s")
    void arj(byte[] data) {
        drain(() -> ArjArchiveExtractor.builder(new ByteArrayInputStream(data))
                .limits(LIMITS)
                .build());
    }

    @FuzzTest(maxDuration = "60s")
    void dump(byte[] data) {
        drain(() -> DumpArchiveExtractor.builder(new ByteArrayInputStream(data))
                .limits(LIMITS)
                .build());
    }
}
