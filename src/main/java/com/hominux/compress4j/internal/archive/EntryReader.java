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

import com.hominux.compress4j.archivers.Entry;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** Raw access to a format's entries, in archive order, before any extraction rule applies. */
public interface EntryReader extends Closeable {

    /**
     * Advances to the next supported entry. Entries of unsupported types are reported through the {@link ReaderContext}
     * and skipped.
     *
     * @return the next entry, or empty when the archive has no more
     * @throws IOException if reading fails
     */
    Optional<Entry> next() throws IOException;

    /**
     * Opens the content of the current entry. The stream stays valid until {@link #next()} or {@link #close()}; the
     * reader owns it, so callers must not close it.
     *
     * @param entry the current entry
     * @return the entry content
     * @throws IOException if the content cannot be opened
     */
    InputStream open(Entry entry) throws IOException;
}
