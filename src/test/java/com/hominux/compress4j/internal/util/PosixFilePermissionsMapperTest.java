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
package com.hominux.compress4j.internal.util;

import static com.hominux.compress4j.internal.util.PosixFilePermissionsMapper.fromUnixMode;
import static com.hominux.compress4j.internal.util.PosixFilePermissionsMapper.toUnixMode;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"java:S2187", "OctalInteger"})
class PosixFilePermissionsMapperTest {

    @Nested
    class FromUnixModeTest {
        private Set<PosixFilePermission> setOf(PosixFilePermission... posixFilePermissions) {
            return new HashSet<>(Arrays.asList(posixFilePermissions));
        }

        @Test
        void noPermissions() {
            assertThat(fromUnixMode(0000)).isEmpty();
        }

        @Test
        void allPermissions() {
            assertThat(fromUnixMode(0777)).containsAll(setOf(PosixFilePermission.values()));
        }

        @Test
        void ownerPermissions() {
            assertThat(fromUnixMode(0400)).containsAll(setOf(OWNER_READ));
            assertThat(fromUnixMode(0200)).containsAll(setOf(OWNER_WRITE));
            assertThat(fromUnixMode(0100)).containsAll(setOf(OWNER_EXECUTE));

            assertThat(fromUnixMode(0700)).containsAll(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE));
            assertThat(fromUnixMode(0600)).containsAll(setOf(OWNER_WRITE, OWNER_READ));
            assertThat(fromUnixMode(0500)).containsAll(setOf(OWNER_EXECUTE, OWNER_READ));
            assertThat(fromUnixMode(0300)).containsAll(setOf(OWNER_EXECUTE, OWNER_WRITE));
        }

        @Test
        void groupPermissions() {
            assertThat(fromUnixMode(0040)).containsAll(setOf(GROUP_READ));
            assertThat(fromUnixMode(0020)).containsAll(setOf(GROUP_WRITE));
            assertThat(fromUnixMode(0010)).containsAll(setOf(GROUP_EXECUTE));

            assertThat(fromUnixMode(0070)).containsAll(setOf(GROUP_READ, GROUP_WRITE, GROUP_EXECUTE));
            assertThat(fromUnixMode(0060)).containsAll(setOf(GROUP_WRITE, GROUP_READ));
            assertThat(fromUnixMode(0050)).containsAll(setOf(GROUP_EXECUTE, GROUP_READ));
            assertThat(fromUnixMode(0030)).containsAll(setOf(GROUP_EXECUTE, GROUP_WRITE));
        }

        @Test
        void othersPermissions() {
            assertThat(fromUnixMode(0004)).containsAll(setOf(OTHERS_READ));
            assertThat(fromUnixMode(0002)).containsAll(setOf(OTHERS_WRITE));
            assertThat(fromUnixMode(0001)).containsAll(setOf(OTHERS_EXECUTE));

            assertThat(fromUnixMode(0007)).containsAll(setOf(OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE));
            assertThat(fromUnixMode(0006)).containsAll(setOf(OTHERS_WRITE, OTHERS_READ));
            assertThat(fromUnixMode(0005)).containsAll(setOf(OTHERS_EXECUTE, OTHERS_READ));
            assertThat(fromUnixMode(0003)).containsAll(setOf(OTHERS_EXECUTE, OTHERS_WRITE));
        }

        @Test
        void permissionsSameForAll() {
            assertThat(fromUnixMode(0444)).containsAll(setOf(OTHERS_READ, GROUP_READ, OWNER_READ));
            assertThat(fromUnixMode(0222)).containsAll(setOf(OTHERS_WRITE, GROUP_WRITE, OWNER_WRITE));
            assertThat(fromUnixMode(0111)).containsAll(setOf(OTHERS_EXECUTE, GROUP_EXECUTE, OWNER_EXECUTE));
        }

        @Test
        void permissionCombinations() {
            assertThat(fromUnixMode(0750))
                    .containsAll(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE));
            assertThat(fromUnixMode(0753))
                    .containsAll(setOf(
                            OWNER_READ,
                            OWNER_WRITE,
                            OWNER_EXECUTE,
                            GROUP_READ,
                            GROUP_EXECUTE,
                            OTHERS_WRITE,
                            OTHERS_EXECUTE));
            assertThat(fromUnixMode(0574))
                    .containsAll(setOf(OWNER_READ, OWNER_EXECUTE, GROUP_READ, GROUP_WRITE, GROUP_EXECUTE, OTHERS_READ));
            assertThat(fromUnixMode(0544)).containsAll(setOf(OWNER_READ, OWNER_EXECUTE, GROUP_READ, OTHERS_READ));
            assertThat(fromUnixMode(0055)).containsAll(setOf(GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE));
        }
    }

    @Nested
    class ToUnixModeTest {
        private Set<PosixFilePermission> setOf(PosixFilePermission... posixFilePermissions) {
            return new HashSet<>(Arrays.asList(posixFilePermissions));
        }

        @Test
        void noPermissions() {
            assertThat(toUnixMode(Set.of())).isZero();
        }

        @Test
        void allPermissions() {
            assertThat(toUnixMode(setOf(PosixFilePermission.values()))).isEqualTo(0777);
        }

        @Test
        void ownerPermissions() {
            assertThat(toUnixMode(setOf(OWNER_READ))).isEqualTo(0400);
            assertThat(toUnixMode(setOf(OWNER_WRITE))).isEqualTo(0200);
            assertThat(toUnixMode(setOf(OWNER_EXECUTE))).isEqualTo(0100);

            assertThat(toUnixMode(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE)))
                    .isEqualTo(0700);
            assertThat(toUnixMode(setOf(OWNER_WRITE, OWNER_READ))).isEqualTo(0600);
            assertThat(toUnixMode(setOf(OWNER_EXECUTE, OWNER_READ))).isEqualTo(0500);
            assertThat(toUnixMode(setOf(OWNER_EXECUTE, OWNER_WRITE))).isEqualTo(0300);
        }

        @Test
        void groupPermissions() {
            assertThat(toUnixMode(setOf(GROUP_READ))).isEqualTo(0040);
            assertThat(toUnixMode(setOf(GROUP_WRITE))).isEqualTo(0020);
            assertThat(toUnixMode(setOf(GROUP_EXECUTE))).isEqualTo(0010);

            assertThat(toUnixMode(setOf(GROUP_READ, GROUP_WRITE, GROUP_EXECUTE)))
                    .isEqualTo(0070);
            assertThat(toUnixMode(setOf(GROUP_WRITE, GROUP_READ))).isEqualTo(0060);
            assertThat(toUnixMode(setOf(GROUP_EXECUTE, GROUP_READ))).isEqualTo(0050);
            assertThat(toUnixMode(setOf(GROUP_EXECUTE, GROUP_WRITE))).isEqualTo(0030);
        }

        @Test
        void othersPermissions() {
            assertThat(toUnixMode(setOf(OTHERS_READ))).isEqualTo(0004);
            assertThat(toUnixMode(setOf(OTHERS_WRITE))).isEqualTo(0002);
            assertThat(toUnixMode(setOf(OTHERS_EXECUTE))).isEqualTo(0001);

            assertThat(toUnixMode(setOf(OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE)))
                    .isEqualTo(0007);
            assertThat(toUnixMode(setOf(OTHERS_WRITE, OTHERS_READ))).isEqualTo(0006);
            assertThat(toUnixMode(setOf(OTHERS_EXECUTE, OTHERS_READ))).isEqualTo(0005);
            assertThat(toUnixMode(setOf(OTHERS_EXECUTE, OTHERS_WRITE))).isEqualTo(0003);
        }

        @Test
        void permissionsSameForAll() {
            assertThat(toUnixMode(setOf(OTHERS_READ, GROUP_READ, OWNER_READ))).isEqualTo(0444);
            assertThat(toUnixMode(setOf(OTHERS_WRITE, GROUP_WRITE, OWNER_WRITE)))
                    .isEqualTo(0222);
            assertThat(toUnixMode(setOf(OTHERS_EXECUTE, GROUP_EXECUTE, OWNER_EXECUTE)))
                    .isEqualTo(0111);
        }

        @Test
        void permissionCombinations() {
            assertThat(toUnixMode(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE)))
                    .isEqualTo(0750);
            assertThat(toUnixMode(setOf(
                            OWNER_READ,
                            OWNER_WRITE,
                            OWNER_EXECUTE,
                            GROUP_READ,
                            GROUP_EXECUTE,
                            OTHERS_WRITE,
                            OTHERS_EXECUTE)))
                    .isEqualTo(0753);
            assertThat(toUnixMode(
                            setOf(OWNER_READ, OWNER_EXECUTE, GROUP_READ, GROUP_WRITE, GROUP_EXECUTE, OTHERS_READ)))
                    .isEqualTo(0574);
            assertThat(toUnixMode(setOf(OWNER_READ, OWNER_EXECUTE, GROUP_READ, OTHERS_READ)))
                    .isEqualTo(0544);
            assertThat(toUnixMode(setOf(GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE)))
                    .isEqualTo(0055);
        }
    }
}
