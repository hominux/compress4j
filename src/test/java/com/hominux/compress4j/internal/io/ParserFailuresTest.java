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
package com.hominux.compress4j.internal.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hominux.compress4j.exceptions.MissingArchiveDependencyException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

class ParserFailuresTest {

    private static final String WHAT = "thing";

    private static final class Faulty extends InputStream {
        private final RuntimeException failure;

        private Faulty(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public int read() {
            throw failure;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            throw failure;
        }

        @Override
        public long skip(long n) {
            throw failure;
        }

        @Override
        public int available() {
            throw failure;
        }

        @Override
        public void close() {
            throw failure;
        }
    }

    @Test
    void callReturnsTheResult() throws IOException {
        assertThat(ParserFailures.call(() -> "ok", WHAT)).isEqualTo("ok");
    }

    @Test
    void callKeepsIOExceptions() {
        var failure = new IOException("disk");
        assertThatThrownBy(() -> ParserFailures.call(
                        () -> {
                            throw failure;
                        },
                        WHAT))
                .isSameAs(failure);
    }

    @Test
    void callUnwrapsUncheckedIOExceptions() {
        var cause = new IOException("disk");
        assertThatThrownBy(() -> ParserFailures.call(
                        () -> {
                            throw new UncheckedIOException(cause);
                        },
                        WHAT))
                .isSameAs(cause);
    }

    @Test
    void callTurnsRuntimeExceptionsIntoIOExceptions() {
        var failure = new ArithmeticException("bad");
        assertThatThrownBy(() -> ParserFailures.call(
                        () -> {
                            throw failure;
                        },
                        WHAT))
                .isInstanceOf(IOException.class)
                .hasMessage("Corrupt thing")
                .hasCause(failure);
    }

    @Test
    void callKeepsMissingDependencyFailures() {
        var failure = new MissingArchiveDependencyException("xz is absent");
        assertThatThrownBy(() -> ParserFailures.call(
                        () -> {
                            throw failure;
                        },
                        WHAT))
                .isSameAs(failure);
    }

    @Test
    void shieldedCallbackFailuresReachTheCallerUnchanged() {
        var failure = new IllegalStateException("caller");
        var shielded = ParserFailures.<String>shield(value -> {
            throw failure;
        });
        assertThatThrownBy(() -> ParserFailures.call(
                        () -> {
                            shielded.accept("x");
                            return "unreachable";
                        },
                        WHAT))
                .isSameAs(failure);
    }

    @Test
    void callKeepsErrors() {
        var failure = new StackOverflowError("deep");
        assertThatThrownBy(() -> ParserFailures.call(
                        () -> {
                            throw failure;
                        },
                        WHAT))
                .isSameAs(failure);
    }

    @Test
    void printableEscapesControlCharacters() {
        assertThat(ParserFailures.printable("a\0b\nc")).isEqualTo("a\\u0000b\\u000ac");
    }

    @Test
    void printableCutsLongText() {
        assertThat(ParserFailures.printable("x".repeat(500))).hasSize(203).endsWith("...");
    }

    @Test
    void printableKeepsShortPlainText() {
        assertThat(ParserFailures.printable("dir/file.txt")).isEqualTo("dir/file.txt");
    }

    @Test
    void shieldedCallbacksRunNormally() {
        var seen = new StringBuilder();
        ParserFailures.<String>shield(seen::append).accept("x");
        assertThat(seen).hasToString("x");
    }

    @Test
    void wrappedStreamsDeliverTheirBytes() throws IOException {
        try (var in = ParserFailures.wrap(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), WHAT)) {
            assertThat(in.read()).isEqualTo(1);
            assertThat(in.skip(1)).isEqualTo(1);
            assertThat(in.available()).isEqualTo(2);
            assertThat(in.readAllBytes()).containsExactly(3, 4);
        }
    }

    @Test
    void wrappedReadsTurnRuntimeExceptionsIntoIOExceptions() {
        var failure = new IllegalArgumentException("bad block");
        var in = ParserFailures.wrap(new Faulty(failure), WHAT);
        assertThatThrownBy(in::read).isInstanceOf(IOException.class).hasCause(failure);
        assertThatThrownBy(() -> in.read(new byte[2], 0, 2))
                .isInstanceOf(IOException.class)
                .hasCause(failure);
    }

    @Test
    void wrappedSkipsAvailabilityAndClosesTurnRuntimeExceptionsIntoIOExceptions() {
        var failure = new IllegalArgumentException("bad block");
        var in = ParserFailures.wrap(new Faulty(failure), WHAT);
        assertThatThrownBy(() -> in.skip(1)).isInstanceOf(IOException.class).hasCause(failure);
        assertThatThrownBy(in::available).isInstanceOf(IOException.class).hasCause(failure);
        assertThatThrownBy(in::close).isInstanceOf(IOException.class).hasCause(failure);
    }
}
