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
package com.hominux.compress4j.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.hominux.compress4j.exceptions.LimitExceededException.Limit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UnsafeInputExceptionTest {

    @Test
    void bothSecurityExceptionsAreUnsafeInputIOExceptions() {
        assertThat(new LimitExceededException(Limit.ENTRIES, 5, Optional.empty()))
                .isInstanceOf(UnsafeInputException.class)
                .isInstanceOf(IOException.class);
        assertThat(new UnsafeEntryException("x")).isInstanceOf(UnsafeInputException.class);
    }

    @Test
    void limitExceptionCarriesItsData() {
        var e = new LimitExceededException(Limit.ENTRY_SIZE, 1024, Optional.of("big.bin"));
        assertThat(e.limit()).isEqualTo(Limit.ENTRY_SIZE);
        assertThat(e.maximum()).isEqualTo(1024);
        assertThat(e.entryName()).contains("big.bin");
        assertThat(e).hasMessage("Entry 'big.bin' exceeded the entry size limit of 1024 (raise it with maxEntrySize)");
    }

    @Test
    void messageWithoutEntryName() {
        var e = new LimitExceededException(Limit.RATIO, 100, Optional.empty());
        assertThat(e.entryName()).isEmpty();
        assertThat(e).hasMessage("Input exceeded the expansion ratio limit of 100 (raise it with maxRatio)");
    }

    @ParameterizedTest
    @EnumSource(Limit.class)
    void everyLimitNamesItsBuilderMethod(Limit limit) {
        var expected = Map.of(
                Limit.ENTRIES, "maxEntries",
                Limit.ENTRY_SIZE, "maxEntrySize",
                Limit.TOTAL_SIZE, "maxTotalSize",
                Limit.RATIO, "maxRatio");
        assertThat(new LimitExceededException(limit, 1, Optional.empty())).hasMessageContaining(expected.get(limit));
    }

    @Test
    void unsafeEntryKeepsCause() {
        var cause = new IOException("root");
        assertThat(new UnsafeEntryException("bad link", cause))
                .hasMessage("bad link")
                .hasCause(cause);
    }

    @Test
    void hierarchyIsClosed() {
        assertThat(UnsafeInputException.class.isSealed()).isTrue();
        assertThat(UnsafeInputException.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(LimitExceededException.class, UnsafeEntryException.class);
    }

    @Test
    void nullArgumentsAreRejectedWithTheirNames() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LimitExceededException(null, 1, Optional.empty()))
                .withMessageContaining("limit");
        assertThatNullPointerException()
                .isThrownBy(() -> new LimitExceededException(Limit.ENTRIES, 1, null))
                .withMessageContaining("entryName");
    }

    @Test
    void namedInstanceSurvivesSerialization() throws Exception {
        var copy = roundTrip(new LimitExceededException(Limit.TOTAL_SIZE, 9, Optional.of("a.bin")));
        assertThat(copy.limit()).isEqualTo(Limit.TOTAL_SIZE);
        assertThat(copy.maximum()).isEqualTo(9);
        assertThat(copy.entryName()).contains("a.bin");
        assertThat(copy).hasMessage("Entry 'a.bin' exceeded the total size limit of 9 (raise it with maxTotalSize)");
    }

    @Test
    void unnamedInstanceSurvivesSerialization() throws Exception {
        var copy = roundTrip(new LimitExceededException(Limit.ENTRIES, 3, Optional.empty()));
        assertThat(copy.limit()).isEqualTo(Limit.ENTRIES);
        assertThat(copy.maximum()).isEqualTo(3);
        assertThat(copy.entryName()).isEmpty();
        assertThat(copy).hasMessage("Input exceeded the entry count limit of 3 (raise it with maxEntries)");
    }

    private static LimitExceededException roundTrip(LimitExceededException original) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (LimitExceededException) in.readObject();
        }
    }
}
