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
package com.hominux.compress4j.archivers.zip;

import org.apache.commons.compress.archivers.zip.Zip64Mode;

/**
 * When the zip writer uses Zip64 extensions.
 *
 * @since 5.0
 */
public enum ZipZip64Mode {
    /** Never use Zip64; an entry or archive that needs it fails the write. */
    NEVER(Zip64Mode.Never),
    /** Use Zip64 for every entry; older readers may not open the archive. */
    ALWAYS(Zip64Mode.Always),
    /** Use Zip64 for every entry and write extra fields that keep the archive readable by more readers. */
    ALWAYS_WITH_COMPATIBILITY(Zip64Mode.AlwaysWithCompatibility),
    /** Use Zip64 only for the entries that need it. */
    AS_NEEDED(Zip64Mode.AsNeeded);

    final Zip64Mode value;

    ZipZip64Mode(Zip64Mode value) {
        this.value = value;
    }
}
