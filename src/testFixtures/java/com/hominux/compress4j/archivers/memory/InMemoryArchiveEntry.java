/*
 * Copyright 2025-2026 The Compress4J Project
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
package com.hominux.compress4j.archivers.memory;

import static com.hominux.compress4j.archivers.Entry.Type.DIR;
import static com.hominux.compress4j.archivers.Entry.Type.FILE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.hominux.compress4j.archivers.Entry.Type;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.jspecify.annotations.Nullable;

public final class InMemoryArchiveEntry implements ArchiveEntry {
    private final String name;
    private final Date lastModifiedDate;
    private final String content;
    private final @Nullable String linkName;
    private final int mode;
    private final long size;
    private final boolean directory;
    private final Type type;

    @JsonCreator
    private InMemoryArchiveEntry() {
        this("", "", FILE, 0, null, 0, new Date());
    }

    /**
     * Creates a new {@code InMemoryArchiveEntry}.
     *
     * @param name the entry's name
     * @param content the entry's content
     * @param type the entry's type
     * @param linkName the entry's link name
     * @param size the entry's size
     * @param lastModifiedDate the entry's last modified date
     */
    private InMemoryArchiveEntry(
            String name,
            String content,
            Type type,
            int mode,
            @Nullable String linkName,
            long size,
            Date lastModifiedDate) {
        this.name = name;
        this.content = content;
        this.type = type;
        this.directory = type == DIR;
        this.linkName = linkName;
        this.mode = mode;
        this.size = size;
        this.lastModifiedDate = lastModifiedDate;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets this entry's content.
     *
     * @return This entry's content.
     */
    public String getContent() {
        return content;
    }

    /** @inheritDoc */
    @Override
    public Date getLastModifiedDate() {
        return lastModifiedDate;
    }

    /** @inheritDoc */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Gets this entry's link name.
     *
     * @return This entry's link name.
     */
    public @Nullable String getLinkName() {
        return linkName;
    }

    /**
     * Gets this entry's link mode.
     *
     * @return This entry's link mode.
     */
    public int getMode() {
        return mode;
    }

    /** @inheritDoc */
    @Override
    public long getSize() {
        return size;
    }

    /** @inheritDoc */
    @Override
    public boolean isDirectory() {
        return directory;
    }

    /**
     * Gets the type of the entry.
     *
     * @return The type of the entry.
     */
    public Type getType() {
        return type;
    }

    public static class Builder {
        private String name;
        private Date lastModifiedDate = new Date();
        private String content;
        private @Nullable String linkName;
        private int mode = 0;
        private long size = 0;
        private Type type = FILE;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder lastModifiedDate(FileTime modTime) {
            this.lastModifiedDate = Date.from(modTime.toInstant());
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder linkName(@Nullable String linkName) {
            this.linkName = linkName;
            return this;
        }

        public Builder mode(int mode) {
            this.mode = mode;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public InMemoryArchiveEntry build() {
            return new InMemoryArchiveEntry(name, content, type, mode, linkName, size, lastModifiedDate);
        }
    }
}
