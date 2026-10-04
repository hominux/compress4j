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

import static com.hominux.compress4j.archivers.ArchiveExtractor.ErrorHandlerChoice.SKIP_ALL;

import com.hominux.compress4j.archivers.ArchiveExtractor.Entry;
import com.hominux.compress4j.archivers.ArchiveExtractor.ErrorHandlerChoice;
import java.io.IOException;
import java.util.Objects;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Turns an {@link IOException} raised while extracting one entry into what the extraction loop does next. */
final class ExtractionErrorPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractionErrorPolicy.class);

    private final BiFunction<Entry, ? super IOException, ErrorHandlerChoice> errorHandler;

    ExtractionErrorPolicy(BiFunction<Entry, ? super IOException, ErrorHandlerChoice> errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * Decides how to proceed after {@code failure}.
     *
     * @param failure the exception that occurred
     * @param ignoreErrors whether {@link ErrorHandlerChoice#SKIP_ALL} was selected for an earlier entry
     * @param entry the entry that caused the exception
     * @return {@link EntryOutcome.IgnoreFurtherErrors} when {@code ignoreErrors} is set or the handler answers
     *     {@code SKIP_ALL}, {@link EntryOutcome.Continue} after {@code SKIP}
     * @throws IOException {@code failure}, when the handler answers {@code ABORT}
     * @throws NullPointerException if the handler returns {@code null}
     */
    EntryOutcome handle(IOException failure, boolean ignoreErrors, Entry entry) throws IOException {
        if (ignoreErrors) {
            LOGGER.debug("Skipped exception because {} was selected earlier", SKIP_ALL, failure);
            return new EntryOutcome.IgnoreFurtherErrors();
        }
        ErrorHandlerChoice choice =
                Objects.requireNonNull(errorHandler.apply(entry, failure), "errorHandler returned null");
        return switch (choice) {
            case ABORT -> throw failure;
            case SKIP -> {
                LOGGER.debug("Skipped exception", failure);
                yield new EntryOutcome.Continue();
            }
            case SKIP_ALL -> {
                LOGGER.debug("SKIP_ALL is selected", failure);
                yield new EntryOutcome.IgnoreFurtherErrors();
            }
        };
    }

    sealed interface EntryOutcome {
        record Continue() implements EntryOutcome {}

        record IgnoreFurtherErrors() implements EntryOutcome {}
    }
}
