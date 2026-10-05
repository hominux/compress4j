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
 * How the tar writer stores numbers too large for the classic header, such as sizes above 8 GiB.
 *
 * @since 5.0
 */
public enum TarBigNumberMode {
    /** Fail on a big number. */
    ERROR(TarArchiveOutputStream.BIGNUMBER_ERROR),
    /** Use the star/GNU binary encoding. */
    STAR(TarArchiveOutputStream.BIGNUMBER_STAR),
    /** Use POSIX (PAX) headers. */
    POSIX(TarArchiveOutputStream.BIGNUMBER_POSIX);

    final int value;

    TarBigNumberMode(int value) {
        this.value = value;
    }
}
