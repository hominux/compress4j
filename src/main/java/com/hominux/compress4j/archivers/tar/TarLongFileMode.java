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
package com.hominux.compress4j.archivers.tar;

import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * How the tar writer stores entry names longer than 100 bytes.
 *
 * @since 5.0
 */
public enum TarLongFileMode {
    /** Fail on a long name. */
    ERROR(TarArchiveOutputStream.LONGFILE_ERROR),
    /** Truncate long names. */
    TRUNCATE(TarArchiveOutputStream.LONGFILE_TRUNCATE),
    /** Use GNU long-name entries. */
    GNU(TarArchiveOutputStream.LONGFILE_GNU),
    /** Use POSIX (PAX) headers. */
    POSIX(TarArchiveOutputStream.LONGFILE_POSIX);

    final int value;

    TarLongFileMode(int value) {
        this.value = value;
    }
}
