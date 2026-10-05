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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;

/** Writes entries to a format. {@link #close()} finishes the archive and closes the sink. */
public interface EntryWriter extends Closeable {

    /**
     * Whether {@link #writeFile} needs the size known up front.
     *
     * @return {@code true} if {@link #writeFile} requires a known size
     */
    boolean requiresSize();

    /**
     * Writes a directory entry.
     *
     * @param name the entry name
     * @param mode the Unix permission bits, or {@code 0} when unknown
     * @param lastModified the modification time
     * @throws IOException if writing fails
     */
    void writeDirectory(String name, int mode, FileTime lastModified) throws IOException;

    /**
     * Writes a file entry.
     *
     * @param name the entry name
     * @param content the file content
     * @param size the content length in bytes; present whenever {@link #requiresSize()} is true
     * @param mode the Unix permission bits, or {@code 0} when unknown
     * @param lastModified the modification time
     * @throws IOException if writing fails
     */
    void writeFile(String name, InputStream content, OptionalLong size, int mode, FileTime lastModified)
            throws IOException;

    /**
     * Writes a symbolic link entry.
     *
     * @param name the entry name
     * @param target the link target
     * @param mode the Unix permission bits, or {@code 0} when unknown
     * @param lastModified the modification time
     * @throws IOException if writing fails
     */
    void writeSymlink(String name, String target, int mode, FileTime lastModified) throws IOException;
}
