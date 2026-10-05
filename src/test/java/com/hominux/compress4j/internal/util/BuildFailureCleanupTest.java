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
package com.hominux.compress4j.internal.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BuildFailureCleanupTest {

    @Test
    void shouldReturnBuiltValueAndKeepOwnedStreamOpen() throws IOException {
        var stream = mock(Closeable.class);

        var result = BuildFailureCleanup.build(Optional.of(stream), () -> "built");

        assertThat(result).isEqualTo("built");
        verify(stream, never()).close();
    }

    @Test
    void shouldCloseOwnedStreamWhenBuildThrowsIOException() throws IOException {
        var stream = mock(Closeable.class);
        var failure = new IOException("boom");

        assertThatThrownBy(() -> BuildFailureCleanup.build(Optional.of(stream), () -> {
                    throw failure;
                }))
                .isSameAs(failure);

        verify(stream).close();
    }

    @Test
    void shouldCloseOwnedStreamWhenBuildThrowsRuntimeException() throws IOException {
        var stream = mock(Closeable.class);
        var failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> BuildFailureCleanup.build(Optional.of(stream), () -> {
                    throw failure;
                }))
                .isSameAs(failure);

        verify(stream).close();
    }

    @Test
    void shouldCloseOwnedStreamWhenBuildThrowsError() throws IOException {
        var stream = mock(Closeable.class);
        var failure = new NoClassDefFoundError("boom");

        assertThatThrownBy(() -> BuildFailureCleanup.build(Optional.of(stream), () -> {
                    throw failure;
                }))
                .isSameAs(failure);

        verify(stream).close();
    }

    @Test
    void shouldNotTouchCallerSuppliedStream() {
        var failure = new IOException("boom");

        assertThatThrownBy(() -> BuildFailureCleanup.build(Optional.empty(), () -> {
                    throw failure;
                }))
                .isSameAs(failure);
    }

    @Test
    void shouldSuppressCleanupFailureOnTheOriginalException() throws IOException {
        var stream = mock(Closeable.class);
        var cleanupFailure = new IOException("close failed");
        doThrow(cleanupFailure).when(stream).close();
        var failure = new IOException("boom");

        assertThatThrownBy(() -> BuildFailureCleanup.build(Optional.of(stream), () -> {
                    throw failure;
                }))
                .isSameAs(failure)
                .hasSuppressedException(cleanupFailure);
    }
}
