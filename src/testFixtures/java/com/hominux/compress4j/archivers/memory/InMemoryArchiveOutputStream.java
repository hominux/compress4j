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
import static com.hominux.compress4j.archivers.Entry.Type.SYMLINK;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import org.apache.commons.compress.archivers.ArchiveOutputStream;

public class InMemoryArchiveOutputStream extends ArchiveOutputStream<InMemoryArchiveEntry> {
    private static final ObjectMapper mapper = new ObjectMapper();
    private int someOption = 0;

    public InMemoryArchiveOutputStream(OutputStream outputStream) {
        super(outputStream);
    }

    @Override
    public void closeArchiveEntry() {
        /* no-op */
    }

    @Override
    public InMemoryArchiveEntry createArchiveEntry(File inputFile, String entryName) throws IOException {
        if (inputFile.isDirectory()) {
            return InMemoryArchiveEntry.builder()
                    .name(inputFile.getName())
                    .type(DIR)
                    .mode(0)
                    .build();
        } else if (Files.isSymbolicLink(inputFile.toPath())) {
            return InMemoryArchiveEntry.builder()
                    .name(inputFile.getName())
                    .type(SYMLINK)
                    .linkName("")
                    .build();
        } else {
            return InMemoryArchiveEntry.builder()
                    .name(inputFile.getName())
                    .content(Files.readString(inputFile.toPath()))
                    .size(inputFile.length())
                    .build();
        }
    }

    @Override
    public void putArchiveEntry(InMemoryArchiveEntry entry) throws IOException {
        out.write(mapper.writeValueAsBytes(entry));
    }

    public int getSomeOption() {
        return someOption;
    }

    public void setSomeOption(int someOption) {
        this.someOption = someOption;
    }
}
