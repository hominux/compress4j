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
package com.hominux.compress4j.compressors;

/** Brotli; Compress4J reads it but cannot write it. */
public final class Brotli implements Compression {
    Brotli() {}

    @Override
    public boolean canWrite() {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Brotli;
    }

    @Override
    public int hashCode() {
        return "Brotli".hashCode();
    }

    @Override
    public String toString() {
        return "Brotli[]";
    }
}
