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

import static com.hominux.compress4j.archivers.ArchiveExtractor.ErrorHandlerChoice.ABORT;
import static com.hominux.compress4j.archivers.ArchiveExtractor.ErrorHandlerChoice.SKIP;
import static com.hominux.compress4j.archivers.ArchiveExtractor.ErrorHandlerChoice.SKIP_ALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.archivers.ExtractionErrorPolicy.EntryOutcome;
import com.hominux.compress4j.exceptions.LimitExceededException;
import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import com.hominux.compress4j.exceptions.UnsafeEntryException;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExtractionErrorPolicyTest {

    private static final ArchiveExtractor.Entry ENTRY = new ArchiveExtractor.Entry("a.txt", false);
    private static final IOException FAILURE = new IOException("boom");

    @Test
    void handle_skipsWithoutAskingTheHandlerAfterSkipAll() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        ExtractionErrorPolicy policy = new ExtractionErrorPolicy((e, x) -> {
            calls.incrementAndGet();
            return ABORT;
        });
        assertThat(policy.handle(FAILURE, true, ENTRY)).isInstanceOf(EntryOutcome.IgnoreFurtherErrors.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void handle_rethrowsOnAbort() {
        ExtractionErrorPolicy policy = new ExtractionErrorPolicy((e, x) -> ABORT);
        assertThatThrownBy(() -> policy.handle(FAILURE, false, ENTRY)).isSameAs(FAILURE);
    }

    @Test
    void handle_returnsSkipChoices() throws IOException {
        assertThat(new ExtractionErrorPolicy((e, x) -> SKIP).handle(FAILURE, false, ENTRY))
                .isInstanceOf(EntryOutcome.Continue.class);
        assertThat(new ExtractionErrorPolicy((e, x) -> SKIP_ALL).handle(FAILURE, false, ENTRY))
                .isInstanceOf(EntryOutcome.IgnoreFurtherErrors.class);
    }

    @Test
    void handlerReturningNullIsRejected() {
        ExtractionErrorPolicy policy = new ExtractionErrorPolicy((e, x) -> null);
        assertThatThrownBy(() -> policy.handle(FAILURE, false, ENTRY))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("errorHandler");
    }

    @Test
    void unsafeInputIsNeverShownToTheHandler() {
        ExtractionErrorPolicy policy = new ExtractionErrorPolicy((e, x) -> SKIP);
        var limit = new LimitExceededException(Limit.ENTRIES, 1, Optional.empty());
        var unsafe = new UnsafeEntryException("bad");
        assertThatThrownBy(() -> policy.handle(limit, false, ENTRY)).isSameAs(limit);
        assertThatThrownBy(() -> policy.handle(unsafe, true, ENTRY)).isSameAs(unsafe);
    }
}
