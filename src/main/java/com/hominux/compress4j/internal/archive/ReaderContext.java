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
package com.hominux.compress4j.internal.archive;

import com.hominux.compress4j.ExtractionLimits;
import com.hominux.compress4j.archivers.UnsupportedEntry;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What a format reader needs from the extractor that owns it.
 *
 * @param limits the extraction limits
 * @param unsupported told about every entry the reader skips
 */
public record ReaderContext(ExtractionLimits limits, Consumer<UnsupportedEntry> unsupported) {

    /**
     * Reports an entry the reader skips because its type cannot be extracted.
     *
     * @param name the entry name as stored in the archive
     * @param kind a readable description of the entry type
     */
    public void reportUnsupported(String name, String kind) {
        unsupported.accept(new UnsupportedEntry(name, kind));
    }

    /**
     * Reads exactly {@code declaredSize} bytes. Fails before allocating when {@code declaredSize} exceeds a configured
     * maximum entry size.
     *
     * @param entryName the entry being read, reported on a breach
     * @param in the stream to read from
     * @param declaredSize the number of bytes the archive declares
     * @return the bytes read
     * @throws IOException if reading fails, the stream ends early, or {@code declaredSize} is negative or above
     *     {@link Integer#MAX_VALUE}
     * @throws LimitExceededException if {@code declaredSize} exceeds the maximum entry size
     */
    public byte[] readDeclared(String entryName, InputStream in, long declaredSize) throws IOException {
        if (limits.maxEntrySize() != ExtractionLimits.UNLIMITED && declaredSize > limits.maxEntrySize()) {
            throw new LimitExceededException(Limit.ENTRY_SIZE, limits.maxEntrySize(), Optional.of(entryName));
        }
        if (declaredSize < 0 || declaredSize > Integer.MAX_VALUE) {
            throw new IOException("entry '" + entryName + "' declares an unreadable size: " + declaredSize);
        }
        byte[] bytes = in.readNBytes((int) declaredSize);
        if (bytes.length < declaredSize) {
            throw new EOFException(
                    "entry '" + entryName + "' ends after " + bytes.length + " of " + declaredSize + " bytes");
        }
        return bytes;
    }
}
