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
package com.hominux.compress4j.internal.codec;

import com.hominux.compress4j.compressors.Brotli;
import com.hominux.compress4j.compressors.Bzip2;
import com.hominux.compress4j.compressors.Compression;
import com.hominux.compress4j.compressors.Deflate;
import com.hominux.compress4j.compressors.Deflate64;
import com.hominux.compress4j.compressors.DeflateStrategy;
import com.hominux.compress4j.compressors.Gzip;
import com.hominux.compress4j.compressors.Lz4Block;
import com.hominux.compress4j.compressors.Lz4Framed;
import com.hominux.compress4j.compressors.Lzma;
import com.hominux.compress4j.compressors.None;
import com.hominux.compress4j.compressors.Pack200;
import com.hominux.compress4j.compressors.SnappyFramed;
import com.hominux.compress4j.compressors.SnappyRaw;
import com.hominux.compress4j.compressors.UnixZ;
import com.hominux.compress4j.compressors.Xz;
import com.hominux.compress4j.compressors.Zstd;
import com.hominux.compress4j.internal.io.PlainInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorOutputStream;
import org.apache.commons.compress.compressors.deflate.DeflateParameters;
import org.apache.commons.compress.compressors.deflate64.Deflate64CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorInputStream;
import org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorOutputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200Strategy;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorOutputStream;
import org.apache.commons.compress.compressors.snappy.SnappyCompressorInputStream;
import org.apache.commons.compress.compressors.snappy.SnappyCompressorOutputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;

/** Maps {@link Compression} values to commons-compress streams. */
public final class Codecs {

    /** Rejects a .Z header whose code table would need more memory; compress(1) itself stops at 16-bit codes. */
    private static final int Z_MEMORY_LIMIT_KIB = 64 * 1024;

    private static final int SIGNATURE_LENGTH = 12;
    private static final int BZIP2_HEADER_LENGTH = 10;
    private static final int[] BZIP2_BLOCK_MAGIC = {0x31, 0x41, 0x59, 0x26, 0x53, 0x59};
    private static final int[] BZIP2_END_MAGIC = {0x17, 0x72, 0x45, 0x38, 0x50, 0x90};

    private Codecs() {}

    /**
     * Wraps the stream in the codec's decompressor. If this throws, the caller keeps ownership of {@code in} and must
     * close it.
     *
     * @param compression the codec to read
     * @param in the compressed stream
     * @return the decompressed stream
     * @throws IOException if the codec rejects the stream header
     */
    public static InputStream decompressing(Compression compression, InputStream in) throws IOException {
        if (compression instanceof None) {
            return in;
        }
        if (compression instanceof Gzip g) {
            return GzipCompressorInputStream.builder()
                    .setInputStream(in)
                    .setDecompressConcatenated(g.decompressConcatenated())
                    .setFileNameCharset(g.fileNameCharset())
                    .get();
        }
        if (compression instanceof Bzip2 b) {
            return new BZip2CompressorInputStream(in, b.decompressConcatenated());
        }
        if (compression instanceof Xz x) {
            return OptionalCodecs.xzInput(in, x);
        }
        if (compression instanceof Lzma l) {
            return OptionalCodecs.lzmaInput(in, l);
        }
        if (compression instanceof Lz4Block) {
            return new BlockLZ4CompressorInputStream(in);
        }
        if (compression instanceof Lz4Framed f) {
            return new FramedLZ4CompressorInputStream(in, f.decompressConcatenated());
        }
        if (compression instanceof Zstd) {
            return OptionalCodecs.zstdInput(in);
        }
        if (compression instanceof Deflate d) {
            return new DeflateCompressorInputStream(in, deflateParameters(d));
        }
        if (compression instanceof Deflate64) {
            return new Deflate64CompressorInputStream(in);
        }
        if (compression instanceof SnappyRaw) {
            return new SnappyCompressorInputStream(in);
        }
        if (compression instanceof SnappyFramed) {
            return new FramedSnappyCompressorInputStream(in);
        }
        if (compression instanceof Brotli) {
            return OptionalCodecs.brotliInput(in);
        }
        if (compression instanceof UnixZ) {
            return new ZCompressorInputStream(in, Z_MEMORY_LIMIT_KIB);
        }
        if (compression instanceof Pack200 p) {
            return new Pack200CompressorInputStream(new PlainInputStream(in), strategy(p), p.properties());
        }
        throw new IllegalStateException("Unknown compression " + compression);
    }

    /**
     * Wraps the stream in the codec's compressor. If this throws, the caller keeps ownership of {@code out} and must
     * close it.
     *
     * @param compression the codec to write
     * @param out the destination stream
     * @return the compressing stream
     * @throws IOException if the codec cannot write its header
     * @throws IllegalArgumentException if the codec is read-only or lacks a required option
     */
    public static OutputStream compressing(Compression compression, OutputStream out) throws IOException {
        if (compression instanceof None) {
            return out;
        }
        if (compression instanceof Gzip g) {
            return new GzipCompressorOutputStream(out, gzipParameters(g));
        }
        if (compression instanceof Bzip2 b) {
            return new BZip2CompressorOutputStream(out, b.blockSize());
        }
        if (compression instanceof Xz x) {
            return OptionalCodecs.xzOutput(out, x);
        }
        if (compression instanceof Lzma) {
            return OptionalCodecs.lzmaOutput(out);
        }
        if (compression instanceof Lz4Block) {
            return new BlockLZ4CompressorOutputStream(out);
        }
        if (compression instanceof Lz4Framed) {
            return new FramedLZ4CompressorOutputStream(out);
        }
        if (compression instanceof Zstd z) {
            return OptionalCodecs.zstdOutput(out, z.level());
        }
        if (compression instanceof Deflate d) {
            return new DeflateCompressorOutputStream(out, deflateParameters(d));
        }
        if (compression instanceof SnappyRaw raw) {
            return new SnappyCompressorOutputStream(
                    out, raw.uncompressedSize().orElseThrow(Codecs::snappyRawNeedsSize));
        }
        if (compression instanceof SnappyFramed) {
            return new FramedSnappyCompressorOutputStream(out);
        }
        if (compression instanceof Pack200 p) {
            return OptionalCodecs.pack200Output(out, strategy(p), p.properties());
        }
        if (compression instanceof Deflate64) {
            throw readOnly(compression);
        }
        if (compression instanceof Brotli) {
            throw readOnly(compression);
        }
        if (compression instanceof UnixZ) {
            throw readOnly(compression);
        }
        throw new IllegalStateException("Unknown compression " + compression);
    }

    /**
     * Detects the codec for reading untrusted input: concatenated members are decompressed, and Pack200, which decodes
     * eagerly, is never selected.
     *
     * @param in a stream that supports mark
     * @return the codec to read with, or none when no strong signature matches
     * @throws IOException if reading or resetting the stream fails
     * @throws IllegalArgumentException if the stream does not support mark
     */
    public static Compression detectForReading(InputStream in) throws IOException {
        var selected = detect(in);
        if (selected instanceof Pack200) {
            return Compression.none();
        }
        if (selected instanceof Gzip gzip) {
            return gzip.decompressConcatenated(true);
        }
        if (selected instanceof Bzip2 bzip2) {
            return bzip2.decompressConcatenated(true);
        }
        if (selected instanceof Xz xz) {
            return xz.decompressConcatenated(true);
        }
        if (selected instanceof Lz4Framed lz4) {
            return lz4.decompressConcatenated(true);
        }
        return selected;
    }

    /**
     * Checks that the codec can write, without touching any stream.
     *
     * @param compression the codec to write
     * @throws IllegalArgumentException if the codec is read-only or lacks a required option
     */
    public static void requireWritable(Compression compression) {
        if (!compression.canWrite()) {
            throw readOnly(compression);
        }
        if (compression instanceof SnappyRaw raw) {
            raw.uncompressedSize().orElseThrow(Codecs::snappyRawNeedsSize);
        }
    }

    private static IllegalArgumentException snappyRawNeedsSize() {
        return new IllegalArgumentException(
                "Raw Snappy needs the uncompressed size; set Compression.snappyRaw().uncompressedSize(...)");
    }

    /**
     * Detects the codec from the stream's leading bytes and leaves the stream at its start.
     *
     * @param in a stream that supports mark
     * @return the detected codec, or none when no strong signature matches
     * @throws IOException if reading or resetting the stream fails
     * @throws IllegalArgumentException if the stream does not support mark
     */
    public static Compression detect(InputStream in) throws IOException {
        if (!in.markSupported()) {
            throw new IllegalArgumentException("Compression detection needs a stream that supports mark");
        }
        byte[] signature = peek(in);
        String name;
        try {
            name = CompressorStreamFactory.detect(new ByteArrayInputStream(signature));
        } catch (CompressorException nothingMatched) {
            return Compression.none();
        }
        return switch (name) {
            case CompressorStreamFactory.GZIP -> Compression.gzip();
            case CompressorStreamFactory.BZIP2 -> isBzip2(signature) ? Compression.bzip2() : Compression.none();
            case CompressorStreamFactory.XZ -> Compression.xz();
            case CompressorStreamFactory.ZSTANDARD -> Compression.zstd();
            case CompressorStreamFactory.LZ4_FRAMED -> Compression.lz4Framed();
            case CompressorStreamFactory.Z -> Compression.unixZ();
            case CompressorStreamFactory.SNAPPY_FRAMED -> Compression.snappyFramed();
            case CompressorStreamFactory.PACK200 -> Compression.pack200();
            default -> Compression.none();
        };
    }

    private static boolean isBzip2(byte[] signature) {
        return signature.length >= BZIP2_HEADER_LENGTH
                && signature[0] == 'B'
                && signature[1] == 'Z'
                && signature[2] == 'h'
                && signature[3] >= '1'
                && signature[3] <= '9'
                && (startsAt(signature, BZIP2_BLOCK_MAGIC) || startsAt(signature, BZIP2_END_MAGIC));
    }

    private static boolean startsAt(byte[] signature, int[] magic) {
        for (int i = 0; i < magic.length; i++) {
            if ((signature[4 + i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] peek(InputStream in) throws IOException {
        in.mark(SIGNATURE_LENGTH);
        byte[] signature;
        try {
            signature = in.readNBytes(SIGNATURE_LENGTH);
        } catch (IOException e) {
            try {
                in.reset();
            } catch (IOException resetFailure) {
                e.addSuppressed(resetFailure);
            }
            throw e;
        }
        in.reset();
        return signature;
    }

    private static IllegalArgumentException readOnly(Compression compression) {
        return new IllegalArgumentException(compression.getClass().getSimpleName() + " can only be read");
    }

    private static GzipParameters gzipParameters(Gzip g) {
        GzipParameters parameters = new GzipParameters();
        parameters.setCompressionLevel(g.level());
        parameters.setBufferSize(g.bufferSize());
        parameters.setFileNameCharset(g.fileNameCharset());
        g.fileName().ifPresent(parameters::setFileName);
        g.comment().ifPresent(parameters::setComment);
        parameters.setDeflateStrategy(deflaterStrategy(g.deflateStrategy()));
        g.modificationTime().ifPresent(parameters::setModificationInstant);
        parameters.setOperatingSystem(g.operatingSystem());
        return parameters;
    }

    private static int deflaterStrategy(DeflateStrategy strategy) {
        return switch (strategy) {
            case DEFAULT -> Deflater.DEFAULT_STRATEGY;
            case FILTERED -> Deflater.FILTERED;
            case HUFFMAN_ONLY -> Deflater.HUFFMAN_ONLY;
        };
    }

    private static DeflateParameters deflateParameters(Deflate d) {
        DeflateParameters parameters = new DeflateParameters();
        if (d.level() != Deflater.DEFAULT_COMPRESSION) {
            parameters.setCompressionLevel(d.level());
        }
        parameters.setWithZlibHeader(d.zlibHeader());
        return parameters;
    }

    private static Pack200Strategy strategy(Pack200 p) {
        return p.strategy() == Pack200.Strategy.TEMP_FILE ? Pack200Strategy.TEMP_FILE : Pack200Strategy.IN_MEMORY;
    }
}
