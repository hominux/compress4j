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
package com.hominux.compress4j.archivers.cpio;

import org.apache.commons.compress.archivers.cpio.CpioConstants;

/**
 * The cpio header formats a {@link CpioArchiveCreator} can write.
 *
 * @since 5.0
 */
public enum CpioFormat {
    /** The SVR4 format without checksum ({@code newc}). */
    NEW(CpioConstants.FORMAT_NEW),
    /** The portable ASCII format ({@code odc}). */
    OLD_ASCII(CpioConstants.FORMAT_OLD_ASCII),
    /** The old binary format. */
    OLD_BINARY(CpioConstants.FORMAT_OLD_BINARY);

    final short value;

    CpioFormat(short value) {
        this.value = value;
    }

    static int requireBlockSize(int blockSize) {
        if (blockSize < 1) {
            throw new IllegalArgumentException("blockSize must be positive: " + blockSize);
        }
        return blockSize;
    }
}
