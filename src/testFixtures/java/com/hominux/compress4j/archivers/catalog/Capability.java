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
package com.hominux.compress4j.archivers.catalog;

/**
 * A format property: the first four are preserved on a round trip, the rest describe I/O modes.
 * {@link #RANDOM_ACCESS_INPUT} marks a reader that reads the whole channel whatever its position; without it, a channel
 * reader starts at the channel's current position.
 */
public enum Capability {
    DIRECTORIES,
    MODES,
    SYMLINKS,
    LAST_MODIFIED,

    /** The writer must know a file's size before its content, as tar, ar and cpio do. */
    REQUIRES_SIZE,

    /** The format reads from a plain, non-seekable input stream. */
    STREAM_INPUT,

    /** The format writes to a plain, non-seekable output stream. */
    STREAM_OUTPUT,
    RANDOM_ACCESS_INPUT
}
