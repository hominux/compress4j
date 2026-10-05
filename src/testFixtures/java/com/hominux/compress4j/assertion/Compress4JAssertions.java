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
package com.hominux.compress4j.assertion;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hominux.compress4j.archivers.Entry;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;

public class Compress4JAssertions {
    private Compress4JAssertions() {}

    public static DecompressorEntryAssert assertThat(Entry entry) {
        return new DecompressorEntryAssert(entry);
    }

    public static ListAppenderAssert assertThat(ListAppender<ILoggingEvent> listAppender) {
        return new ListAppenderAssert(listAppender);
    }

    public static DirectoryAssert assertThat(Path actual) {
        return new DirectoryAssert(actual);
    }

    public static TarArchiveEntryAssert assertThat(TarArchiveEntry actual) {
        return new TarArchiveEntryAssert(actual);
    }
}
