/*
 * Copyright 2024-2026 The Compress4J Project
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
package com.hominux.compress4j.internal.util;

import static org.apache.commons.compress.compressors.CompressorStreamFactory.BROTLI;
import static org.apache.commons.compress.compressors.CompressorStreamFactory.LZMA;
import static org.apache.commons.compress.compressors.CompressorStreamFactory.XZ;
import static org.apache.commons.compress.compressors.CompressorStreamFactory.ZSTANDARD;

import com.hominux.compress4j.exceptions.MissingArchiveDependencyException;
import org.apache.commons.compress.compressors.brotli.BrotliUtils;
import org.apache.commons.compress.compressors.lzma.LZMAUtils;
import org.apache.commons.compress.compressors.xz.XZUtils;
import org.apache.commons.compress.compressors.zstandard.ZstdUtils;

/** Checks for the availability of dependencies required by the archivers. */
public class ArchiverDependencyChecker {
    private static final String YOU_NEED_BROTLI_DEC = youNeed("Google Brotli Dec", "https://github.com/google/brotli/");
    private static final String YOU_NEED_XZ_JAVA = youNeed("XZ for Java", "https://tukaani.org/xz/java.html");
    private static final String YOU_NEED_ZSTD_JNI = youNeed("Zstd JNI", "https://github.com/luben/zstd-jni");

    private static final String YOU_NEED_ASM = youNeed("ASM", "https://asm.ow2.io/");

    private ArchiverDependencyChecker() {
        /* no-op */
    }

    private static String youNeed(final String name, final String url) {
        return " In addition to Apache Commons Compress you need the " + name + " library - see " + url;
    }

    /** Checks if Brotli compression is available. */
    public static void checkBrotli() {
        if (!BrotliUtils.isBrotliCompressionAvailable()) {
            throw new MissingArchiveDependencyException("Brotli compression is not available." + YOU_NEED_BROTLI_DEC);
        }
    }

    /** Checks if LZMA compression is available. */
    public static void checkLZMA() {
        if (!LZMAUtils.isLZMACompressionAvailable()) {
            throw new MissingArchiveDependencyException("LZMA compression is not available." + YOU_NEED_XZ_JAVA);
        }
    }

    /** Checks if XZ compression is available. */
    public static void checkXZ() {
        if (!XZUtils.isXZCompressionAvailable()) {
            throw new MissingArchiveDependencyException("XZ compression is not available." + YOU_NEED_XZ_JAVA);
        }
    }

    /** Checks if Zstandard compression is available. */
    public static void checkZstd() {
        if (!ZstdUtils.isZstdCompressionAvailable()) {
            throw new MissingArchiveDependencyException("Zstandard compression is not available." + YOU_NEED_ZSTD_JNI);
        }
    }

    /** Checks if writing Pack200 is available. */
    public static void checkAsm() {
        try {
            Class.forName("org.objectweb.asm.ClassReader", false, ArchiverDependencyChecker.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            throw new MissingArchiveDependencyException("Pack200 compression is not available." + YOU_NEED_ASM);
        }
    }

    /**
     * Checks if the dependency for the given archiver is available.
     *
     * @param name the name of the archiver
     */
    public static void check(String name) {
        switch (name.toLowerCase()) {
            case BROTLI -> checkBrotli();
            case LZMA -> checkLZMA();
            case XZ -> checkXZ();
            case ZSTANDARD -> checkZstd();
            default -> {
                /* no-op */
            }
        }
    }
}
