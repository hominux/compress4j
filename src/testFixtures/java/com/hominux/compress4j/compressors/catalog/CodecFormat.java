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
package com.hominux.compress4j.compressors.catalog;

import com.hominux.compress4j.compressors.Compression;
import java.util.Optional;

/**
 * A codec under test.
 *
 * @param name the display name
 * @param compression the codec with default options
 * @param detectable whether {@code Decompressor} picks the codec from the stream header
 * @param reportsTruncation whether reading a truncated stream fails instead of ending early
 * @param sample the committed sample file under {@code compression/}, empty if none exists
 */
public record CodecFormat(
        String name, Compression compression, boolean detectable, boolean reportsTruncation, Optional<String> sample) {
    @Override
    public String toString() {
        return name;
    }
}
