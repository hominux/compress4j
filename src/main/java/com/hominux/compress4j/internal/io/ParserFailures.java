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

import com.hominux.compress4j.exceptions.MissingArchiveDependencyException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

/**
 * Reports runtime failures of format parsers and codecs as {@link IOException}s. Shielded callback failures and
 * {@link MissingArchiveDependencyException} pass through unchanged.
 */
public final class ParserFailures {

    /**
     * A call that may throw {@link IOException}.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface IOCallable<T> {
        /**
         * Runs the call.
         *
         * @return the result
         * @throws IOException if the call fails
         */
        T call() throws IOException;
    }

    /** Names the archive in the message of a failure of its headers. */
    public static final String ARCHIVE = "archive";

    /** Names an archive entry in the message of a failure of its content. */
    public static final String ARCHIVE_ENTRY = "archive entry";

    /** Names compressed data in the message of a failure of its codec. */
    public static final String COMPRESSED_DATA = "compressed data";

    private static final int MAX_PRINTED = 200;

    private ParserFailures() {}

    /**
     * Runs a parser call. An {@link UncheckedIOException} becomes its cause and a {@link RuntimeException} that the
     * parser threw becomes an {@link IOException} with that exception as its cause.
     *
     * @param callable the call
     * @param what what is being read; the message is 'Corrupt ' + what
     * @param <T> the result type
     * @return the result of the call
     * @throws IOException if the call fails or the parser throws a RuntimeException; the message is "Corrupt " followed
     *     by {@code what}
     * @throws MissingArchiveDependencyException if a required library is absent
     * @throws RuntimeException if a callback guarded by shield threw it
     */
    public static <T> T call(IOCallable<T> callable, String what) throws IOException {
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw translate(e, what);
        }
    }

    private static IOException translate(RuntimeException e, String what) throws IOException {
        if (e instanceof CallbackFailure callback) {
            throw callback.failure();
        }
        if (e instanceof MissingArchiveDependencyException) {
            throw e;
        }
        if (e instanceof UncheckedIOException unchecked) {
            throw unchecked.getCause();
        }
        return new IOException("Corrupt " + what, e);
    }

    /**
     * Renders untrusted text for a message: control characters become four-digit hexadecimal escapes and long text is
     * cut.
     *
     * @param text the text, such as an entry name or link target
     * @return the printable text
     */
    public static String printable(String text) {
        var out = new StringBuilder();
        text.chars().limit(MAX_PRINTED).forEach(c -> out.append(printable((char) c)));
        return text.length() > MAX_PRINTED ? out.append("...").toString() : out.toString();
    }

    private static String printable(char c) {
        return Character.isISOControl(c) ? "\\u%04x".formatted((int) c) : String.valueOf(c);
    }

    /**
     * Wraps a stream so its reads, skips, availability checks and close report parser RuntimeExceptions as IOException,
     * with the same pass-throughs as {@link #call(IOCallable, String)}.
     *
     * @param in the parser or codec stream
     * @param what what is being read; the message is 'Corrupt ' + what
     * @return the guarded stream
     */
    public static InputStream wrap(InputStream in, String what) {
        return new Guarded(in, what);
    }

    /**
     * Marks a callback so that a {@link RuntimeException} it throws inside {@link #call(IOCallable, String)} reaches
     * the caller unchanged instead of being reported as corrupt input.
     *
     * @param callback the caller-supplied callback
     * @param <T> the callback argument type
     * @return the guarded callback
     */
    public static <T> Consumer<T> shield(Consumer<T> callback) {
        return value -> {
            try {
                callback.accept(value);
            } catch (RuntimeException e) {
                throw new CallbackFailure(e);
            }
        };
    }

    private static final class CallbackFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final transient RuntimeException failure;

        private CallbackFailure(RuntimeException failure) {
            super(failure);
            this.failure = failure;
        }

        private RuntimeException failure() {
            return failure;
        }
    }

    private static final class Guarded extends FilterInputStream {
        private final String what;

        private Guarded(InputStream in, String what) {
            super(in);
            this.what = what;
        }

        @Override
        public int read() throws IOException {
            try {
                return super.read();
            } catch (RuntimeException e) {
                throw translate(e, what);
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return call(() -> super.read(buffer, offset, length), what);
        }

        @Override
        public long skip(long n) throws IOException {
            return call(() -> super.skip(n), what);
        }

        @Override
        public int available() throws IOException {
            return call(super::available, what);
        }

        @Override
        public void close() throws IOException {
            call(
                    () -> {
                        super.close();
                        return Boolean.TRUE;
                    },
                    what);
        }
    }
}
