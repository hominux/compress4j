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
package com.hominux.compress4j.archivers.sevenz;

/**
 * How the 7z writer compresses entry content.
 *
 * @since 5.0
 */
public enum SevenZMethod {
    /** Store content uncompressed. */
    COPY(org.apache.commons.compress.archivers.sevenz.SevenZMethod.COPY),
    /** Compress content with LZMA2. */
    LZMA2(org.apache.commons.compress.archivers.sevenz.SevenZMethod.LZMA2),
    /** Compress content with LZMA. */
    LZMA(org.apache.commons.compress.archivers.sevenz.SevenZMethod.LZMA),
    /** Compress content with bzip2. */
    BZIP2(org.apache.commons.compress.archivers.sevenz.SevenZMethod.BZIP2),
    /** Compress content with Deflate. */
    DEFLATE(org.apache.commons.compress.archivers.sevenz.SevenZMethod.DEFLATE);

    final org.apache.commons.compress.archivers.sevenz.SevenZMethod value;

    SevenZMethod(org.apache.commons.compress.archivers.sevenz.SevenZMethod value) {
        this.value = value;
    }
}
