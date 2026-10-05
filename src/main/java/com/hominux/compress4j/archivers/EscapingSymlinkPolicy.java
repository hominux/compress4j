/*
 * Copyright 2024-2026 The Compress4J Project
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

/**
 * How the extractor treats symbolic links whose target is absolute or resolves outside the output directory. Extractors
 * default to {@link #DISALLOW}.
 */
public enum EscapingSymlinkPolicy {
    /** Extracts the link as is, without any check. The link may point outside the output directory. */
    ALLOW,

    /**
     * Rejects targets that are absolute or resolve outside the output directory, including through links created later
     * in the same archive.
     */
    DISALLOW,

    /**
     * Rewrites absolute targets under the output directory, then applies the same checks as {@link #DISALLOW}. For
     * example, a link to {@code /opt/foo} in an archive extracted to {@code /foo/bar} becomes {@code /foo/bar/opt/foo}.
     */
    RELATIVIZE_ABSOLUTE
}
