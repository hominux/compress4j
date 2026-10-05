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

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

/** Closes streams a builder opened itself when building from them fails. */
public final class BuildFailureCleanup {

    /**
     * A build step that may throw {@link IOException}.
     *
     * @param <T> the built type
     */
    @FunctionalInterface
    public interface IOSupplier<T> {

        /**
         * Produces the value.
         *
         * @return the value
         * @throws IOException if an I/O error occurs
         */
        T get() throws IOException;
    }

    private BuildFailureCleanup() {
        /* no-op */
    }

    /**
     * Builds the value. On failure, closes {@code owned}, records any close failure as suppressed on the original
     * exception, and rethrows the original unchanged. An empty {@code owned} (caller-supplied stream) is never closed.
     *
     * @param owned the stream the builder opened itself
     * @param build produces the value
     * @param <T> the built type
     * @return the built value
     * @throws IOException if the build fails with an I/O error
     */
    public static <T> T build(Optional<? extends Closeable> owned, IOSupplier<T> build) throws IOException {
        try (var cleanup = new CloseUnlessBuilt(owned)) {
            var value = build.get();
            cleanup.built();
            return value;
        }
    }

    private static final class CloseUnlessBuilt implements Closeable {
        private final Optional<? extends Closeable> owned;
        private boolean built;

        CloseUnlessBuilt(Optional<? extends Closeable> owned) {
            this.owned = owned;
        }

        void built() {
            built = true;
        }

        @Override
        public void close() throws IOException {
            if (!built && owned.isPresent()) {
                owned.get().close();
            }
        }
    }
}
