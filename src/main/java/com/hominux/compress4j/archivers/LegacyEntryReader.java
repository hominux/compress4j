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

import com.hominux.compress4j.internal.archive.EntryReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** Adapts the template methods of {@link LegacyArchiveExtractor} to the entry reader SPI. */
final class LegacyEntryReader implements EntryReader {

    /** The hooks a {@link LegacyArchiveExtractor} subclass implements. */
    interface Hooks {
        Optional<Entry> nextEntry() throws IOException;

        InputStream openEntryStream(Entry entry) throws IOException;

        void closeEntryStream(InputStream stream) throws IOException;

        void closeArchive() throws IOException;
    }

    private final Hooks hooks;
    private Optional<InputStream> current = Optional.empty();

    LegacyEntryReader(Hooks hooks) {
        this.hooks = hooks;
    }

    @Override
    public Optional<Entry> next() throws IOException {
        releaseCurrent();
        return hooks.nextEntry();
    }

    @Override
    public InputStream open(Entry entry) throws IOException {
        InputStream stream = hooks.openEntryStream(entry);
        current = Optional.of(stream);
        return stream;
    }

    @Override
    public void close() throws IOException {
        try {
            releaseCurrent();
        } finally {
            hooks.closeArchive();
        }
    }

    void release(Throwable failure) throws IOException {
        try {
            releaseCurrent();
        } catch (IOException releaseFailure) {
            if (failure == null) {
                throw releaseFailure;
            }
            failure.addSuppressed(releaseFailure);
        }
    }

    private void releaseCurrent() throws IOException {
        Optional<InputStream> toRelease = current;
        current = Optional.empty();
        if (toRelease.isPresent()) {
            hooks.closeEntryStream(toRelease.orElseThrow());
        }
    }
}
