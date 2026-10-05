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
package com.hominux.compress4j;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import java.util.stream.Stream;

@AnalyzeClasses(
        packages = "com.hominux.compress4j",
        importOptions = {ImportOption.DoNotIncludeTests.class, ArchitectureTest.ExcludeTestFixtures.class})
class ArchitectureTest {

    static final String INTERNAL = "com.hominux.compress4j.internal..";
    static final String COMMONS_COMPRESS = "org.apache.commons.compress..";
    private static final String ARCHIVERS = "com.hominux.compress4j.archivers.";
    private static final String SPI = "com.hominux.compress4j.internal.archive.";
    private static final String[] FORMAT_PACKAGES = Stream.of("ar", "arj", "cpio", "dump", "sevenz", "tar", "zip")
            .map(format -> ARCHIVERS + format + "..")
            .toArray(String[]::new);

    // Protected hooks that take internal SPI types; only the format subclasses in this module call them.
    static final Set<String> SPI_HOOKS = Set.of(
            ARCHIVERS + "ArchiveCreator.<init>(" + ARCHIVERS + "ArchiveCreator$Builder, " + SPI + "EntryWriter)",
            ARCHIVERS + "ArchiveExtractor$Builder.readerContext()",
            ARCHIVERS + "ArchiveExtractor.<init>(" + ARCHIVERS + "ArchiveExtractor$Builder, " + SPI
                    + "EntryReader, java.util.function.LongSupplier)");

    // UnsafeInputException is sealed: its permitted subclasses are the only extensions.
    static final Set<String> EXTENSION_POINTS = Set.of(
            "com.hominux.compress4j.exceptions.UnsafeInputException",
            ARCHIVERS + "ArchiveExtractor",
            ARCHIVERS + "ArchiveExtractor$Builder",
            ARCHIVERS + "ArchiveCreator",
            ARCHIVERS + "ArchiveCreator$Builder");

    @ArchTest
    static final ArchRule exportedMembersDoNotExposeCommonsCompress =
            ApiSignatures.membersDoNotExpose(COMMONS_COMPRESS, Set.of());

    @ArchTest
    static final ArchRule exportedTypesDoNotDeclareCommonsCompress = ApiSignatures.typesDoNotExpose(COMMONS_COMPRESS);

    @ArchTest
    static final ArchRule exportedMembersDoNotExposeInternalTypes =
            ApiSignatures.membersDoNotExpose(INTERNAL, SPI_HOOKS);

    @ArchTest
    static final ArchRule exportedTypesDoNotDeclareInternalTypes = ApiSignatures.typesDoNotExpose(INTERNAL);

    @ArchTest
    static final ArchRule internalDoesNotDependOnFormatPackages = noClasses()
            .that()
            .resideInAPackage(INTERNAL)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(FORMAT_PACKAGES);

    @ArchTest
    static final ArchRule exportedClassesAreFinal = ApiSignatures.exportedClassesAreFinal(EXTENSION_POINTS);

    @ArchTest
    static final ArchRule noStaticPlatformProbes = ApiSignatures.noPlatformProbes();

    static final class ExcludeTestFixtures implements ImportOption {
        @Override
        public boolean includes(Location location) {
            return !location.contains("testFixtures") && !location.contains("test-fixtures");
        }
    }
}
