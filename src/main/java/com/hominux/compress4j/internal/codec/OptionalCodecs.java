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

import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.internal.util.ArchiverDependencyChecker;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorOutputStream;
import org.apache.commons.compress.compressors.pack200.Pack200Strategy;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.tukaani.xz.LZMA2Options;

/** Codecs that need optional libraries; loaded only on use so a missing library fails at use, not at class load. */
final class OptionalCodecs {

    static final int DEFAULT_MEMORY_LIMIT_KIB = 256 * 1024;

    private OptionalCodecs() {}

    static InputStream xzInput(InputStream in, Compression.Xz x) throws IOException {
        ArchiverDependencyChecker.checkXZ();
        return XZCompressorInputStream.builder()
                .setInputStream(in)
                .setDecompressConcatenated(x.decompressConcatenated())
                .setMemoryLimitKiB(x.memoryLimitKiB().orElse(DEFAULT_MEMORY_LIMIT_KIB))
                .get();
    }

    static OutputStream xzOutput(OutputStream out, Compression.Xz x) throws IOException {
        ArchiverDependencyChecker.checkXZ();
        return XZCompressorOutputStream.builder()
                .setOutputStream(out)
                .setLzma2Options(new LZMA2Options(x.preset()))
                .get();
    }

    static InputStream lzmaInput(InputStream in, Compression.Lzma l) throws IOException {
        ArchiverDependencyChecker.checkLZMA();
        return LZMACompressorInputStream.builder()
                .setInputStream(in)
                .setMemoryLimitKiB(l.memoryLimitKiB().orElse(DEFAULT_MEMORY_LIMIT_KIB))
                .get();
    }

    static OutputStream lzmaOutput(OutputStream out) throws IOException {
        ArchiverDependencyChecker.checkLZMA();
        return LZMACompressorOutputStream.builder().setOutputStream(out).get();
    }

    static InputStream zstdInput(InputStream in) throws IOException {
        ArchiverDependencyChecker.checkZstd();
        return new ZstdCompressorInputStream(in);
    }

    static OutputStream zstdOutput(OutputStream out, int level) throws IOException {
        ArchiverDependencyChecker.checkZstd();
        return ZstdCompressorOutputStream.builder()
                .setOutputStream(out)
                .setLevel(level)
                .get();
    }

    static OutputStream pack200Output(OutputStream out, Pack200Strategy strategy, Map<String, String> properties)
            throws IOException {
        ArchiverDependencyChecker.checkAsm();
        return new Pack200CompressorOutputStream(out, strategy, properties);
    }

    static InputStream brotliInput(InputStream in) throws IOException {
        ArchiverDependencyChecker.checkBrotli();
        return new BrotliCompressorInputStream(in);
    }
}
