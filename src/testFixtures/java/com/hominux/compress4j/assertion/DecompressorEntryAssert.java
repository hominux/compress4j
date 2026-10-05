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

import com.hominux.compress4j.archivers.Entry;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

public class DecompressorEntryAssert extends AbstractAssert<DecompressorEntryAssert, Entry> {

    protected DecompressorEntryAssert(Entry entry) {
        super(entry, DecompressorEntryAssert.class);
    }

    public static DecompressorEntryAssert assertThat(Entry entry) {
        return new DecompressorEntryAssert(entry);
    }

    public DecompressorEntryAssert hasName(String name) {
        Assertions.assertThat(actual.name()).isEqualTo(name);
        return this;
    }

    public DecompressorEntryAssert hasLinkName(String linkName) {
        Assertions.assertThat(actual.linkTarget()).isEqualTo(java.util.Optional.ofNullable(linkName));
        return this;
    }

    public DecompressorEntryAssert hasMode(long mod) {
        Assertions.assertThat(actual.mode()).isEqualTo(mod);
        return this;
    }

    public DecompressorEntryAssert hasMode(Set<PosixFilePermission> permissions) {
        Set<PosixFilePermission> actualPermissions = UnixModes.permissions(actual.mode());
        Assertions.assertThat(actualPermissions).containsAll(permissions);
        return this;
    }

    public DecompressorEntryAssert hasType(Entry.Type type) {
        Assertions.assertThat(actual.type()).isEqualTo(type);
        return this;
    }
}
