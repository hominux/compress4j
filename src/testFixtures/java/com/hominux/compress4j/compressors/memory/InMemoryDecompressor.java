/*
 * Copyright 2025-2026 The Compress4J Project
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
package com.hominux.compress4j.compressors.memory;

import com.hominux.compress4j.compressors.CodecDecompressor;
import java.io.IOException;
import java.io.InputStream;

public class InMemoryDecompressor extends CodecDecompressor<InMemoryDecompressorInputStream> {
    public InMemoryDecompressor(InMemoryDecompressorInputStream compressorInputStream) {
        super(compressorInputStream);
    }

    public InMemoryDecompressor(InMemoryDecompressorBuilder builder) throws IOException {
        super(builder);
    }

    public static InMemoryDecompressorBuilder builder(InMemoryDecompressorInputStream inputStream) {
        return new InMemoryDecompressorBuilder(inputStream);
    }

    public static class InMemoryDecompressorBuilder
            extends DecompressorBuilder<
                    InMemoryDecompressorInputStream, InMemoryDecompressor, InMemoryDecompressorBuilder> {

        public InMemoryDecompressorBuilder(InputStream inputStream) {
            super(inputStream);
        }

        public InMemoryDecompressorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
        }

        @Override
        public InMemoryDecompressorInputStream buildCompressorInputStream() {
            return new InMemoryDecompressorInputStream(inputStream);
        }

        @Override
        public InMemoryDecompressorBuilder getThis() {
            return this;
        }

        @Override
        public InMemoryDecompressor build() throws IOException {
            return new InMemoryDecompressor(this);
        }
    }
}
