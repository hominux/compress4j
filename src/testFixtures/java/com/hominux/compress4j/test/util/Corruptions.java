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
package com.hominux.compress4j.test.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Deterministic damaged variants of a valid file, and a probe for failures that are not {@link IOException}s. */
public final class Corruptions {

    /** The seed every variant list derives from, so a failing variant can be regenerated. */
    public static final long SEED = 20261005L;

    private static final int HEADER_WINDOW = 512;

    /** An action that may fail with any exception. */
    @FunctionalInterface
    public interface Action {
        /**
         * Runs the action.
         *
         * @throws Exception whatever the action fails with
         */
        void run() throws Exception;
    }

    private Corruptions() {}

    /**
     * Truncations and seeded byte flips of {@code valid}.
     *
     * @param valid a well-formed file
     * @param flips the number of flipped variants
     * @return the damaged variants
     */
    public static List<byte[]> variantsOf(byte[] valid, int flips) {
        var variants = new ArrayList<byte[]>();
        for (int cut : new int[] {0, 1, 7, 100, valid.length / 2, valid.length - 1}) {
            variants.add(Arrays.copyOf(valid, Math.clamp(cut, 0, valid.length)));
        }
        var random = new Random(SEED);
        for (int i = 0; i < flips; i++) {
            variants.add(flipped(valid, random));
        }
        return variants;
    }

    /**
     * Seeded random bytes that start with {@code prefix}.
     *
     * @param prefix the leading bytes, typically a format's magic number
     * @param length the total length
     * @param count the number of variants
     * @return the garbage variants
     */
    public static List<byte[]> garbageAfter(byte[] prefix, int length, int count) {
        var random = new Random(SEED);
        var variants = new ArrayList<byte[]>();
        for (int i = 0; i < count; i++) {
            byte[] bytes = new byte[length];
            random.nextBytes(bytes);
            System.arraycopy(prefix, 0, bytes, 0, prefix.length);
            variants.add(bytes);
        }
        return variants;
    }

    /**
     * Runs the action and reports any failure that is not an {@link IOException}.
     *
     * @param action the action
     * @return the failure, or empty when the action succeeds or fails with an {@link IOException}; an
     *     {@link java.io.UncheckedIOException} counts as a failure
     */
    public static Optional<Exception> nonIoFailure(Action action) {
        try {
            action.run();
            return Optional.empty();
        } catch (IOException expected) {
            return Optional.empty();
        } catch (Exception unexpected) {
            return Optional.of(unexpected);
        }
    }

    private static byte[] flipped(byte[] valid, Random random) {
        byte[] bytes = valid.clone();
        int window = Math.clamp(bytes.length, 1, HEADER_WINDOW);
        for (int n = 1 + random.nextInt(8); n > 0 && bytes.length > 0; n--) {
            int at = random.nextInt(random.nextBoolean() ? window : bytes.length);
            bytes[at] = (byte) random.nextInt(256);
        }
        return bytes;
    }
}
