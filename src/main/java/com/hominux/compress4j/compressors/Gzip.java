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

import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Gzip and its header and compression options. Obtain the default with {@link Compression#gzip()}. */
public final class Gzip implements Compression {
    private final int level;
    private final int bufferSize;
    private final DeflateStrategy deflateStrategy;
    private final boolean decompressConcatenated;
    private final Header header;

    record Header(
            Optional<String> fileName,
            Optional<String> comment,
            Optional<Instant> modificationTime,
            int operatingSystem,
            Charset fileNameCharset) {
        Header {
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(comment, "comment");
            Objects.requireNonNull(modificationTime, "modificationTime");
            Checks.gzipOperatingSystem("gzip operatingSystem", operatingSystem);
            Checks.gzipTime("gzip modificationTime", modificationTime);
            Objects.requireNonNull(fileNameCharset, "fileNameCharset");
        }

        Header fileName(Optional<String> value) {
            return new Header(value, comment, modificationTime, operatingSystem, fileNameCharset);
        }

        Header comment(Optional<String> value) {
            return new Header(fileName, value, modificationTime, operatingSystem, fileNameCharset);
        }

        Header modificationTime(Optional<Instant> value) {
            return new Header(fileName, comment, value, operatingSystem, fileNameCharset);
        }

        Header operatingSystem(int value) {
            return new Header(fileName, comment, modificationTime, value, fileNameCharset);
        }

        Header fileNameCharset(Charset value) {
            return new Header(fileName, comment, modificationTime, operatingSystem, value);
        }
    }

    Gzip(int level, int bufferSize, DeflateStrategy deflateStrategy, boolean decompressConcatenated, Header header) {
        Checks.range("gzip level", level, -1, 9);
        Checks.positive("gzip bufferSize", bufferSize);
        Objects.requireNonNull(deflateStrategy, "deflateStrategy");
        this.level = level;
        this.bufferSize = bufferSize;
        this.deflateStrategy = deflateStrategy;
        this.decompressConcatenated = decompressConcatenated;
        this.header = header;
    }

    /**
     * Returns the compression level, from -1 (default) to 9.
     *
     * @return the compression level, from -1 (default) to 9
     */
    public int level() {
        return level;
    }

    /**
     * Returns a copy with the compression level, from -1 (default) to 9.
     *
     * @param value the compression level, from -1 (default) to 9
     * @return the changed copy
     * @throws IllegalArgumentException if the value is below -1 or above 9
     */
    public Gzip level(int value) {
        return new Gzip(value, bufferSize, deflateStrategy, decompressConcatenated, header);
    }

    /**
     * Returns the deflater buffer size in bytes.
     *
     * @return the deflater buffer size in bytes
     */
    public int bufferSize() {
        return bufferSize;
    }

    /**
     * Returns a copy with the deflater buffer size in bytes, above 0.
     *
     * @param value the deflater buffer size in bytes, above 0
     * @return the changed copy
     * @throws IllegalArgumentException if the value is not above 0
     */
    public Gzip bufferSize(int value) {
        return new Gzip(level, value, deflateStrategy, decompressConcatenated, header);
    }

    /**
     * Returns the original file name stored in the header, if any.
     *
     * @return the original file name stored in the header, if any
     */
    public Optional<String> fileName() {
        return header.fileName();
    }

    /**
     * Returns a copy with the original file name stored in the header, empty for none.
     *
     * @param value the original file name stored in the header, empty for none
     * @return the changed copy
     * @throws NullPointerException if the value is null
     */
    public Gzip fileName(Optional<String> value) {
        return new Gzip(level, bufferSize, deflateStrategy, decompressConcatenated, header.fileName(value));
    }

    /**
     * Returns a copy with the original file name stored in the header.
     *
     * @param value the original file name stored in the header
     * @return the changed copy
     * @throws NullPointerException if the value is null
     */
    public Gzip fileName(String value) {
        return fileName(Optional.of(value));
    }

    /**
     * Returns the comment stored in the header, if any.
     *
     * @return the comment stored in the header, if any
     */
    public Optional<String> comment() {
        return header.comment();
    }

    /**
     * Returns a copy with the comment stored in the header, empty for none.
     *
     * @param value the comment stored in the header, empty for none
     * @return the changed copy
     * @throws NullPointerException if the value is null
     */
    public Gzip comment(Optional<String> value) {
        return new Gzip(level, bufferSize, deflateStrategy, decompressConcatenated, header.comment(value));
    }

    /**
     * Returns a copy with the comment stored in the header.
     *
     * @param value the comment stored in the header
     * @return the changed copy
     * @throws NullPointerException if the value is null
     */
    public Gzip comment(String value) {
        return comment(Optional.of(value));
    }

    /**
     * Returns the deflate strategy.
     *
     * @return the deflate strategy
     */
    public DeflateStrategy deflateStrategy() {
        return deflateStrategy;
    }

    /**
     * Returns a copy with the deflate strategy.
     *
     * @param value the deflate strategy
     * @return the changed copy
     * @throws NullPointerException if the value is null
     */
    public Gzip deflateStrategy(DeflateStrategy value) {
        return new Gzip(level, bufferSize, value, decompressConcatenated, header);
    }

    /**
     * Returns the modification time stored in the header, if any.
     *
     * @return the modification time stored in the header, if any
     */
    public Optional<Instant> modificationTime() {
        return header.modificationTime();
    }

    /**
     * Returns a copy with the modification time stored in the header. The header holds whole seconds, so fractional
     * seconds are truncated toward the epoch second. Use an empty {@code Optional} to omit the time: the header stores
     * 0 as "no timestamp", so {@link Instant#EPOCH} is rejected rather than written like an empty time.
     *
     * @param value the modification time stored in the header, empty to omit it
     * @return the changed copy
     * @throws IllegalArgumentException if a present value lies outside 1970-01-01T00:00:01Z to 2106-02-07T06:28:15Z,
     *     the range of a 32-bit unsigned count of seconds without 0
     * @throws NullPointerException if the value is null
     */
    public Gzip modificationTime(Optional<Instant> value) {
        return new Gzip(level, bufferSize, deflateStrategy, decompressConcatenated, header.modificationTime(value));
    }

    /**
     * Returns a copy with the modification time stored in the header, truncated to whole seconds.
     *
     * @param value the modification time stored in the header
     * @return the changed copy
     * @throws IllegalArgumentException if the value lies outside 1970-01-01T00:00:01Z to 2106-02-07T06:28:15Z
     * @throws NullPointerException if the value is null
     */
    public Gzip modificationTime(Instant value) {
        return modificationTime(Optional.of(value));
    }

    /**
     * Returns the RFC 1952 operating-system code stored in the header, 255 meaning unknown.
     *
     * @return the RFC 1952 operating-system code stored in the header, 255 meaning unknown
     */
    public int operatingSystem() {
        return header.operatingSystem();
    }

    /**
     * Returns a copy with the RFC 1952 operating-system code stored in the header: 0 to 13 for the assigned systems, or
     * 255 for unknown.
     *
     * @param value the RFC 1952 operating-system code stored in the header, 0 to 13 or 255
     * @return the changed copy
     * @throws IllegalArgumentException if the value is not 0 to 13 or 255
     */
    public Gzip operatingSystem(int value) {
        return new Gzip(level, bufferSize, deflateStrategy, decompressConcatenated, header.operatingSystem(value));
    }

    /**
     * Returns whether reading continues across concatenated gzip members.
     *
     * @return whether reading continues across concatenated gzip members
     */
    public boolean decompressConcatenated() {
        return decompressConcatenated;
    }

    /**
     * Returns a copy with whether reading continues across concatenated gzip members.
     *
     * @param value whether reading continues across concatenated gzip members
     * @return the changed copy
     */
    public Gzip decompressConcatenated(boolean value) {
        return new Gzip(level, bufferSize, deflateStrategy, value, header);
    }

    /**
     * Returns the charset of the stored file name and comment.
     *
     * @return the charset of the stored file name and comment
     */
    public Charset fileNameCharset() {
        return header.fileNameCharset();
    }

    /**
     * Returns a copy with the charset of the stored file name and comment.
     *
     * @param value the charset of the stored file name and comment
     * @return the changed copy
     * @throws NullPointerException if the value is null
     */
    public Gzip fileNameCharset(Charset value) {
        return new Gzip(level, bufferSize, deflateStrategy, decompressConcatenated, header.fileNameCharset(value));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Gzip that
                && level == that.level
                && bufferSize == that.bufferSize
                && deflateStrategy == that.deflateStrategy
                && decompressConcatenated == that.decompressConcatenated
                && header.equals(that.header);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, bufferSize, deflateStrategy, decompressConcatenated, header);
    }

    @Override
    public String toString() {
        return "Gzip[level=" + level + ", bufferSize=" + bufferSize + ", fileName=" + header.fileName
                + ", comment=" + header.comment + ", deflateStrategy=" + deflateStrategy
                + ", modificationTime=" + header.modificationTime + ", operatingSystem=" + header.operatingSystem
                + Checks.DECOMPRESS_CONCATENATED + decompressConcatenated + ", fileNameCharset="
                + header.fileNameCharset + "]";
    }
}
