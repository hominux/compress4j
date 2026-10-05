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

import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Test;

class CountingSeekableByteChannelTest {

    @Test
    void countsBytesRead() throws IOException {
        try (var channel = new CountingSeekableByteChannel(new SeekableInMemoryByteChannel(new byte[100]))) {
            channel.read(ByteBuffer.allocate(30));
            channel.position(90);
            channel.read(ByteBuffer.allocate(30));
            assertThat(channel.count()).isEqualTo(40);
        }
    }
}
