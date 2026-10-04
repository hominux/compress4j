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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

/**
 * One entry of an archive being read, with access to its content.
 *
 * <p>The content is readable only while this item is the current one: once the stream that produced it advances,
 * {@link #content()} throws {@link IllegalStateException} and reads from a previously returned content stream throw
 * {@link java.io.IOException}. Closing the content stream does not close the archive.
 *
 * @since 5.0
 */
public final class ArchiveItem {

    private final ArchiveExtractor.Entry entry;
    private final EntryPipeline pipeline;
    private final long position;

    ArchiveItem(ArchiveExtractor.Entry entry, EntryPipeline pipeline, long position) {
        this.entry = entry;
        this.pipeline = pipeline;
        this.position = position;
    }

    /**
     * Returns the entry metadata.
     *
     * @return the entry, after strip-components has been applied
     */
    public ArchiveExtractor.Entry entry() {
        return entry;
    }

    /**
     * Returns the entry's content; empty for directories and symlinks. Repeated calls on a current file item return the
     * same stream.
     *
     * @return the content stream, metered against the extractor's limits
     * @throws IllegalStateException if the stream has advanced past this item
     * @throws java.io.UncheckedIOException if the format cannot open the entry's content
     */
    public InputStream content() {
        return pipeline.content(this);
    }

    /**
     * Describes this item for {@link ArchiveCreator#add}. The returned file source reads this item's content, so it
     * must be written before the stream advances, as {@link ArchiveCreator#addAll} does.
     *
     * @return the entry as a source; last-modified falls back to now when the archive records none
     * @throws IllegalStateException if the entry is a symlink whose archive records no target
     */
    public EntrySource toSource() {
        FileTime modified = entry.lastModified().orElseGet(() -> FileTime.from(Instant.now()));
        int mode = entry.mode() & 07777;
        return switch (entry.type()) {
            case DIR -> new EntrySource.Directory(entry.name(), mode, modified);
            case SYMLINK -> new EntrySource.Symlink(entry.name(), linkTarget(), mode, modified);
            case FILE -> new EntrySource.File(entry.name(), mode, modified, entry.size(), this::openContent);
        };
    }

    private InputStream openContent() throws IOException {
        try {
            return content();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private String linkTarget() {
        return entry.linkTarget()
                .orElseThrow(() -> new IllegalStateException("Symlink '" + entry.name() + "' has no target"));
    }

    long position() {
        return position;
    }
}
