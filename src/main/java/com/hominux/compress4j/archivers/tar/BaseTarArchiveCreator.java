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
package com.hominux.compress4j.archivers.tar;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.BIGNUMBER_ERROR;
import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_ERROR;

import com.hominux.compress4j.archivers.ArchiveCreator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.OptionalLong;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.io.IOUtils;

/**
 * Base class for tar/tar.gz compressors
 *
 * @since 2.2
 */
public abstract class BaseTarArchiveCreator extends ArchiveCreator<TarArchiveOutputStream> {
    /**
     * Create a new {@link BaseTarArchiveCreator} with the given output stream and options.
     *
     * @param builder the archive output stream builder
     * @param <B> The type of {@link BaseTarArchiveCreatorBuilder} to build a {@link BaseTarArchiveCreator} from.
     * @param <C> The type of the {@link BaseTarArchiveCreator} to build
     * @throws IOException if an I/O error occurred
     */
    protected <B extends BaseTarArchiveCreatorBuilder<B, C>, C extends ArchiveCreator<TarArchiveOutputStream>>
            BaseTarArchiveCreator(B builder) throws IOException {
        super(builder);
    }

    /**
     * Create a new {@link BaseTarArchiveCreator}.
     *
     * @param archiveOutputStream the archive output stream
     */
    protected BaseTarArchiveCreator(TarArchiveOutputStream archiveOutputStream) {
        super(archiveOutputStream);
    }

    /** {@inheritDoc} */
    @Override
    protected boolean requiresSize() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected void writeDirectory(String name, int mode, FileTime lastModified) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(name + '/');
        e.setModTime(lastModified);
        if (mode != 0) {
            e.setMode(UnixStat.DIR_FLAG | mode);
        }
        archiveOutputStream.putArchiveEntry(e);
        archiveOutputStream.closeArchiveEntry();
    }

    /** {@inheritDoc} */
    @Override
    protected void writeFile(String name, InputStream content, OptionalLong size, int mode, FileTime lastModified)
            throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(name);
        e.setSize(size.orElseThrow());
        e.setModTime(lastModified);
        if (mode != 0) {
            e.setMode(mode);
        }
        archiveOutputStream.putArchiveEntry(e);
        IOUtils.copy(content, archiveOutputStream);
        archiveOutputStream.closeArchiveEntry();
    }

    /** {@inheritDoc} */
    @Override
    protected void writeSymlink(String name, String target, int mode, FileTime lastModified) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(name, TarConstants.LF_SYMLINK);
        e.setLinkName(target);
        e.setSize(0);
        e.setModTime(lastModified);
        if (mode != 0) {
            e.setMode(mode);
        }
        archiveOutputStream.putArchiveEntry(e);
        archiveOutputStream.closeArchiveEntry();
    }

    /**
     * Base builder to build a TAR/TAR.GZ creator
     *
     * @param <B> the type of the Builder.
     * @param <C> the type of the ArchiveCreator.
     */
    public abstract static class BaseTarArchiveCreatorBuilder<
                    B extends BaseTarArchiveCreatorBuilder<B, C>, C extends ArchiveCreator<TarArchiveOutputStream>>
            extends ArchiveCreatorBuilder<TarArchiveOutputStream, B, C> {

        /** The block size to use for the tar archive. Must be a multiple of 512 bytes. */
        protected int blockSize = -511;

        /** The encoding to use for file names. Default is UTF-8. */
        protected String encoding = UTF_8.name();

        /**
         * The long file mode. This can be LONGFILE_ERROR(0), LONGFILE_TRUNCATE(1), LONGFILE_GNU(2) or
         * LONGFILE_POSIX(3). This specifies the treatment of long file names (names &gt;= TarConstants.NAMELEN).
         * Default is LONGFILE_ERROR.
         */
        protected int longFileMode = LONGFILE_ERROR;

        /**
         * The big number mode. This can be BIGNUMBER_ERROR(0), BIGNUMBER_STAR(1) or BIGNUMBER_POSIX(2). This specifies
         * the treatment of big files (sizes &gt; TarConstants.MAXSIZE) and other numeric values too big to fit into a
         * traditional tar header. Default is BIGNUMBER_ERROR.
         */
        protected int bigNumberMode = BIGNUMBER_ERROR;

        /** Whether to add a PAX extension header for non-ASCII file names. Default is false. */
        protected boolean addPaxHeadersForNonAsciiNames;

        /**
         * Create a new {@link ArchiveCreatorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         */
        protected BaseTarArchiveCreatorBuilder(OutputStream outputStream) {
            super(outputStream);
        }

        /**
         * Create a new {@link ArchiveCreatorBuilder} with the given output stream.
         *
         * @param outputStream the output stream
         * @param owned whether the builder opened {@code outputStream} itself, so a failed build closes it
         */
        protected BaseTarArchiveCreatorBuilder(OutputStream outputStream, boolean owned) {
            super(outputStream, owned);
        }

        /**
         * Sets the block size
         *
         * @param blockSize the block size to use. Must be a multiple of 512 bytes.
         * @return the instance of the {@link TarArchiveCreator.TarArchiveCreatorBuilder}
         */
        public B blockSize(int blockSize) {
            this.blockSize = blockSize;
            return getThis();
        }

        /**
         * Sets name of the encoding to use for file names
         *
         * @param encoding name of the encoding to use for file names
         * @return the instance of the {@link TarArchiveCreator.TarArchiveCreatorBuilder}
         */
        public B encoding(String encoding) {
            this.encoding = encoding;
            return getThis();
        }

        /**
         * Sets whether to add a PAX extension header for non-ASCII file names.
         *
         * @param b whether to add a PAX extension header for non-ASCII file names.
         * @return the instance of the {@link TarArchiveCreator.TarArchiveCreatorBuilder}
         */
        public B addPaxHeadersForNonAsciiNames(final boolean b) {
            addPaxHeadersForNonAsciiNames = b;
            return getThis();
        }

        /**
         * Sets the big number mode. This can be BIGNUMBER_ERROR(0), BIGNUMBER_STAR(1) or BIGNUMBER_POSIX(2). This
         * specifies the treatment of big files (sizes &gt; TarConstants.MAXSIZE) and other numeric values too big to
         * fit into a traditional tar header. Default is BIGNUMBER_ERROR.
         *
         * @param bigNumberMode the mode to use
         * @return the instance of the {@link TarArchiveCreator.TarArchiveCreatorBuilder}
         */
        public B bigNumberMode(final int bigNumberMode) {
            this.bigNumberMode = bigNumberMode;
            return getThis();
        }

        /**
         * Sets the long file mode. This can be LONGFILE_ERROR(0), LONGFILE_TRUNCATE(1), LONGFILE_GNU(2) or
         * LONGFILE_POSIX(3). This specifies the treatment of long file names (names &gt;= TarConstants.NAMELEN).
         * Default is LONGFILE_ERROR.
         *
         * @param longFileMode the mode to use
         * @return the instance of the {@link TarArchiveCreator.TarArchiveCreatorBuilder}
         */
        public B longFileMode(final int longFileMode) {
            this.longFileMode = longFileMode;
            return getThis();
        }

        /**
         * builds a new TarArchiveOutputStream. Entries can be included in the archive using the putEntry method, and
         * then the archive should be closed using its close method. In addition, options can be applied to the
         * underlying stream. E.g. compression level.
         *
         * @param outputStream underlying output stream to which to write the archive.
         * @return new archive object for use in putEntry
         */
        protected TarArchiveOutputStream buildTarArchiveOutputStream(OutputStream outputStream) {
            TarArchiveOutputStream out = new TarArchiveOutputStream(outputStream, blockSize, encoding);
            out.setAddPaxHeadersForNonAsciiNames(addPaxHeadersForNonAsciiNames);
            out.setLongFileMode(longFileMode);
            out.setBigNumberMode(bigNumberMode);
            return out;
        }
    }
}
