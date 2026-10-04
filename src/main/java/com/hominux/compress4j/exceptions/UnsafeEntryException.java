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
 * Signals an entry whose name or link target would reach outside the extraction directory, or an unsafe name passed to
 * an archive creator.
 *
 * @since 5.0
 */
public final class UnsafeEntryException extends UnsafeInputException {

    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public UnsafeEntryException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying failure
     */
    public UnsafeEntryException(String message, Throwable cause) {
        super(message, cause);
    }
}
