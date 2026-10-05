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
package com.hominux.compress4j.exceptions;

/**
 * Exception thrown when a required archive dependency is missing from the classpath. This typically occurs when
 * attempting to use archive formats that require additional libraries that are not present in the current runtime
 * environment.
 */
public final class MissingArchiveDependencyException extends RuntimeException {

    /**
     * Constructs a new exception with the specified detail message. The cause is not initialized.
     *
     * @param message the detail message
     */
    public MissingArchiveDependencyException(String message) {
        super(message);
    }
}
