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

/** What the extractor does after a non-security failure while extracting an entry. */
public enum ErrorHandlerChoice {
    /**
     * Stop and rethrow the failure. Entries extracted before it stay in place, except symlinks that resolve outside the
     * output directory, which are deleted. When the final guard check finds an escaping symlink, from any entry, it
     * throws that failure as the primary exception and attaches the aborting failure as suppressed.
     */
    ABORT,

    /** Skip this entry and continue with the next one. */
    SKIP,

    /** Skip this entry and every later failing entry without consulting the handler again. */
    SKIP_ALL
}
