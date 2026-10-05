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

import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream.UnicodeExtraFieldPolicy;

/**
 * When the zip writer adds InfoZIP Unicode extra fields for entry names and the comment.
 *
 * @since 5.0
 */
public enum ZipUnicodeExtraFields {
    /** Never add the fields. */
    NEVER(UnicodeExtraFieldPolicy.NEVER),
    /** Add the fields to every entry. */
    ALWAYS(UnicodeExtraFieldPolicy.ALWAYS),
    /** Add the fields only to entries whose name or comment the encoding cannot represent. */
    NOT_ENCODEABLE(UnicodeExtraFieldPolicy.NOT_ENCODEABLE);

    final UnicodeExtraFieldPolicy value;

    ZipUnicodeExtraFields(UnicodeExtraFieldPolicy value) {
        this.value = value;
    }
}
