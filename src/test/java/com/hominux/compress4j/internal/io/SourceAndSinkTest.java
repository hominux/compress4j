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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceAndSinkTest {

    @TempDir
    Path dir;

    @Test
    void countsEveryReadAndSkip() throws IOException {
        var in = new CountingInputStream(new ByteArrayInputStream(new byte[100]));
        in.read();
        in.read(new byte[10]);
        in.skip(5);
        assertThat(in.count()).isEqualTo(16);
    }

    @Test
    void pathSourceIsOwnedAndReadable() throws IOException {
        Path file = Files.writeString(dir.resolve("f"), "abc");
        Source.Opened opened = new Source.OfPath(file).open();
        try (InputStream in = opened.in()) {
            assertThat(opened.owned()).isTrue();
            assertThat(in.readAllBytes()).hasSize(3);
            assertThat(opened.in().count()).isEqualTo(3);
        }
    }

    @Test
    void pathSourceDoesNotTouchTheFileUntilOpen() {
        Source source = new Source.OfPath(dir.resolve("missing"));
        assertThat(source).isNotNull();
        assertThatThrownBy(source::open).isInstanceOf(IOException.class);
    }

    @Test
    void closeIfOwnedClosesOwnedSource() throws IOException {
        Source.Opened opened = new Source.OfPath(Files.writeString(dir.resolve("f"), "abc")).open();
        opened.closeIfOwned(new IOException("boom"));
        assertThatThrownBy(() -> opened.in().read()).isInstanceOf(IOException.class);
    }

    @Test
    void closeIfOwnedClosesOwnedSink() throws IOException {
        Sink.Opened opened = new Sink.OfPath(dir.resolve("o")).open();
        opened.closeIfOwned(new IOException("boom"));
        assertThatThrownBy(() -> opened.out().write(1)).isInstanceOf(IOException.class);
    }

    @Test
    void closeFailureIsSuppressedOnTheReturnedFailure() {
        IOException closeFailure = new IOException("close");
        InputStream failing = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() throws IOException {
                throw closeFailure;
            }
        };
        IOException failure =
                new Source.Opened(new CountingInputStream(failing), true).closeIfOwned(new IOException("boom"));
        assertThat(failure).hasMessage("boom").hasSuppressedException(closeFailure);
    }

    @Test
    void markAndResetRestoreTheCount() throws IOException {
        var in = new CountingInputStream(new ByteArrayInputStream(new byte[100]));
        in.read(new byte[4]);
        in.mark(50);
        in.read(new byte[10]);
        in.reset();
        assertThat(in.count()).isEqualTo(4);
    }

    @Test
    void channelSourceReadsAndIsNotOwned() throws IOException {
        Path file = Files.write(dir.resolve("c"), new byte[] {1, 2});
        try (SeekableByteChannel channel = Files.newByteChannel(file)) {
            Source.Opened opened = new Source.OfChannel(channel).open();
            assertThat(opened.owned()).isFalse();
            assertThat(opened.in().readAllBytes()).hasSize(2);
        }
    }

    @Test
    void pathSinkIsOwnedAndWrites() throws IOException {
        Path file = dir.resolve("p");
        Sink.Opened opened = new Sink.OfPath(file).open();
        try (OutputStream out = opened.out()) {
            assertThat(opened.owned()).isTrue();
            out.write(7);
        }
        assertThat(Files.size(file)).isEqualTo(1);
    }

    @Test
    void streamSourceIsNotOwnedAndIsNotClosedOnFailure() throws IOException {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream caller = new ByteArrayInputStream(new byte[1]) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        Source.Opened opened = new Source.OfStream(caller).open();
        assertThat(opened.owned()).isFalse();
        IOException failure = opened.closeIfOwned(new IOException("boom"));
        assertThat(failure).hasMessage("boom");
        assertThat(closed).isFalse();
    }

    @Test
    void channelSinkWritesThroughAndIsNotOwned() throws IOException {
        Path file = dir.resolve("out");
        try (SeekableByteChannel channel =
                Files.newByteChannel(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            Sink.Opened opened = new Sink.OfChannel(channel).open();
            assertThat(opened.owned()).isFalse();
            opened.out().write(new byte[] {1, 2, 3});
            opened.out().flush();
        }
        assertThat(Files.size(file)).isEqualTo(3);
    }

    @Test
    void streamSinkIsNotOwned() throws IOException {
        assertThat(new Sink.OfStream(new ByteArrayOutputStream()).open().owned())
                .isFalse();
    }
}
