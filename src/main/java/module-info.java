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

/**
 * Archiving and compression API on top of Apache Commons Compress.
 *
 * <p>Commons Compress is an automatic module, so consumers are bound to the name {@code org.apache.commons.compress}.
 * The XZ formats need {@code org.tukaani.xz} and the Zstandard formats need {@code com.github.luben.zstd_jni} on the
 * module path.
 */
module com.hominux.compress4j {
    requires transitive org.apache.commons.compress;
    requires transitive org.apache.commons.io;
    requires org.apache.commons.lang3;
    requires org.slf4j;
    requires static transitive jakarta.annotation;
    requires static transitive org.tukaani.xz;
    requires static transitive com.github.luben.zstd_jni;

    exports com.hominux.compress4j;
    exports com.hominux.compress4j.archivers;
    exports com.hominux.compress4j.archivers.ar;
    exports com.hominux.compress4j.archivers.arj;
    exports com.hominux.compress4j.archivers.cpio;
    exports com.hominux.compress4j.archivers.dump;
    exports com.hominux.compress4j.archivers.sevenz;
    exports com.hominux.compress4j.archivers.tar;
    exports com.hominux.compress4j.archivers.zip;
    exports com.hominux.compress4j.compressors;
    exports com.hominux.compress4j.exceptions;
}
