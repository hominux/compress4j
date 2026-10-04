/*
 * Copyright 2024-2026 The Compress4J Project
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
package com.hominux.compress4j.archivers.tar;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

/**
 * Tar Base ArchiveExtractor
 *
 * @since 2.2
 */
public abstract class BaseTarArchiveExtractor extends ArchiveExtractor<TarArchiveInputStream> {
    /**
     * Create a new {@link BaseTarArchiveExtractor} with the given input stream.
     *
     * @param builder - the archive input stream builder
     * @param <B> The type of {@link BaseTarArchiveExtractorBuilder} to build a {@link BaseTarArchiveExtractor} from.
     * @param <C> The type of the {@link BaseTarArchiveExtractor} to build
     * @throws IOException if an I/O error occurred
     */
    protected <B extends BaseTarArchiveExtractorBuilder<B, C>, C extends ArchiveExtractor<TarArchiveInputStream>>
            BaseTarArchiveExtractor(B builder) throws IOException {
        super(builder);
    }

    /**
     * Creates a new {@code BaseTarArchiveExtractor}
     *
     * @param tarArchiveInputStream - the {@code TarArchiveInputStream} to the tar file
     */
    protected BaseTarArchiveExtractor(TarArchiveInputStream tarArchiveInputStream) {
        super(tarArchiveInputStream);
    }

    /** {@inheritDoc} */
    @Override
    protected Optional<Entry> nextEntry() throws IOException {
        return getNextTarArchiveEntry().map(BaseTarArchiveExtractor::toEntry);
    }

    private static Entry toEntry(TarArchiveEntry te) {
        Entry base = new Entry(te.getName(), type(te), te.getMode()).withLinkTarget(te.getLinkName());
        return base.withMetadata(te.getLastModifiedDate(), base.type() == Entry.Type.FILE ? te.getSize() : 0);
    }

    /** {@inheritDoc} */
    @Override
    protected InputStream openEntryStream(Entry entry) {
        return archiveInputStream;
    }

    /**
     * Returns the next supported entry, classified by its type flag. Supported are regular files (flags {@code '0'},
     * NUL and {@code '7'}, plus GNU sparse files), directories and symbolic links. Hard links, character devices, block
     * devices and FIFOs are reported through {@link #reportUnsupported} as such and skipped, as is every other flag
     * ({@code "unknown type"}). A global PAX header ({@code 'g'}) is archive metadata: it is skipped without a report.
     *
     * @return the next {@code TarArchiveEntry}, or empty at the end of the archive
     * @throws IOException – if the next entry could not be read
     */
    private Optional<TarArchiveEntry> getNextTarArchiveEntry() throws IOException {
        TarArchiveEntry te;
        while ((te = archiveInputStream.getNextEntry()) != null) {
            if (te.isGlobalPaxHeader()) {
                continue;
            }
            Optional<String> unsupported = unsupportedKind(te);
            if (unsupported.isEmpty()) {
                return Optional.of(te);
            }
            reportUnsupported(te.getName(), unsupported.orElseThrow());
        }
        return Optional.empty();
    }

    private static Optional<String> unsupportedKind(TarArchiveEntry te) {
        if (te.isLink()) {
            return Optional.of("hard link");
        } else if (te.isCharacterDevice()) {
            return Optional.of("character device");
        } else if (te.isBlockDevice()) {
            return Optional.of("block device");
        } else if (te.isFIFO()) {
            return Optional.of("fifo");
        } else if (isSupported(te)) {
            return Optional.empty();
        }
        return Optional.of("unknown type");
    }

    private static boolean isSupported(TarArchiveEntry te) {
        return te.isDirectory() || te.isSymbolicLink() || isRegularFile(te);
    }

    private static boolean isRegularFile(TarArchiveEntry te) {
        byte flag = te.getLinkFlag();
        return flag == TarConstants.LF_OLDNORM
                || flag == TarConstants.LF_NORMAL
                || flag == TarConstants.LF_CONTIG
                || te.isGNUSparse();
    }

    private static Entry.Type type(TarArchiveEntry te) {
        if (te.isSymbolicLink()) {
            return Entry.Type.SYMLINK;
        } else if (te.isDirectory()) {
            return Entry.Type.DIR;
        } else {
            return Entry.Type.FILE;
        }
    }

    /**
     * Base builder to build a TAR/TAR.GZ extractor
     *
     * @param <B> the type of the Builder.
     * @param <C> the type of the ArchiveExtractor.
     */
    public abstract static class BaseTarArchiveExtractorBuilder<
                    B extends BaseTarArchiveExtractorBuilder<B, C>, C extends ArchiveExtractor<TarArchiveInputStream>>
            extends ArchiveExtractorBuilder<TarArchiveInputStream, B, C> {

        /** Input stream to read from for extraction. */
        protected final InputStream inputStream;

        /**
         * Create a new ArchiveExtractorBuilder.
         *
         * @param inputStream the input stream
         */
        protected BaseTarArchiveExtractorBuilder(InputStream inputStream) {
            this(inputStream, false);
        }

        /**
         * Create a new builder with the given input stream.
         *
         * @param inputStream the input stream
         * @param owned whether the builder opened {@code inputStream} itself, so a failed build closes it
         */
        protected BaseTarArchiveExtractorBuilder(InputStream inputStream, boolean owned) {
            super(inputStream, owned);
            this.inputStream = inputStream;
        }

        /**
         * Build a {@code A} from the given {@code InputStream}. If you want to combine an archive format with a
         * compression format - like when reading a `tar.gz` file - you wrap the {@code ArchiveInputStream} around
         * {@code CompressorInputStream} for example:
         *
         * <pre>{@code
         * return new TarArchiveInputStream(new GzipCompressorInputStream(inputStream));
         * }</pre>
         *
         * @param inputStream - the {@code InputStream} to the compressed file
         * @return a {@code A} from the given {@code InputStream}
         */
        protected TarArchiveInputStream buildTarArchiveInputStream(InputStream inputStream) {
            return new TarArchiveInputStream(inputStream);
        }
    }
}
