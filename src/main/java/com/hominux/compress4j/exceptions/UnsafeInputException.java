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

import java.io.IOException;

/**
 * Signals input rejected for safety rather than because it is corrupt or unreadable: an entry that would escape the
 * output directory, or input that exceeds a configured limit. Extraction error handlers never receive it.
 *
 * @since 5.0
 */
public abstract sealed class UnsafeInputException extends IOException
        permits LimitExceededException, UnsafeEntryException {

    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    protected UnsafeInputException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying failure
     */
    protected UnsafeInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
