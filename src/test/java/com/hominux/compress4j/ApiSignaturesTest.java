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

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.internal.archive.EntryReader;
import com.hominux.compress4j.internal.archive.EntryWriter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Set;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.lang3.SystemUtils;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;

class ApiSignaturesTest {

    public static class InternalReturn {
        public EntryReader reader() {
            throw new UnsupportedOperationException();
        }
    }

    public static class InternalParameter {
        public void accept(EntryWriter writer) {
            throw new UnsupportedOperationException();
        }
    }

    public static class InternalField {
        public EntryReader reader;
    }

    public static class InternalGenericArgument {
        public List<EntryReader> readers() {
            throw new UnsupportedOperationException();
        }
    }

    public static class InternalMethodTypeParameter {
        public <T extends EntryReader> void generic() {
            throw new UnsupportedOperationException();
        }
    }

    public static class InternalClassTypeParameter<T extends EntryReader> {}

    public static class CommonsClassTypeParameter<T extends ArchiveEntry> {}

    public static class ProtectedHook {
        protected ProtectedHook(EntryReader reader) {
            throw new UnsupportedOperationException();
        }
    }

    public static class CommonsReturn {
        public ArchiveEntry entry() {
            throw new UnsupportedOperationException();
        }
    }

    public static class CommonsThrows {
        public void read() throws ArchiveException {
            throw new UnsupportedOperationException();
        }
    }

    public abstract static class InternalSupertype implements EntryReader {}

    public static class Clean {
        public String name() {
            return "clean";
        }
    }

    public static class Open {}

    public static final class Closed {}

    public enum Kind {
        ONE
    }

    public interface Contract {}

    public record Pair(String left, String right) {}

    public static class ProbesPlatform {
        public boolean windows() {
            return SystemUtils.IS_OS_WINDOWS;
        }
    }

    private static List<String> violations(ArchRule rule, Class<?>... fixtures) {
        return rule.evaluate(new ClassFileImporter().importClasses(fixtures))
                .getFailureReport()
                .getDetails();
    }

    private static ListAssert<String> internalViolations(Class<?> fixture) {
        return assertThat(violations(ApiSignatures.membersDoNotExpose(ArchitectureTest.INTERNAL, Set.of()), fixture));
    }

    private static ListAssert<String> internalTypeViolations(Class<?> fixture) {
        return assertThat(violations(ApiSignatures.typesDoNotExpose(ArchitectureTest.INTERNAL), fixture));
    }

    private static ListAssert<String> commonsTypeViolations(Class<?> fixture) {
        return assertThat(violations(ApiSignatures.typesDoNotExpose(ArchitectureTest.COMMONS_COMPRESS), fixture));
    }

    @Test
    void namesTheInternalClassReturnedFromAPublicMethod() {
        internalViolations(InternalReturn.class)
                .singleElement()
                .asString()
                .contains("InternalReturn.reader()", "internal.archive.EntryReader");
    }

    @Test
    void namesTheInternalClassTakenByAPublicMethod() {
        internalViolations(InternalParameter.class).singleElement().asString().contains("internal.archive.EntryWriter");
    }

    @Test
    void rejectsAPublicFieldOfAnInternalType() {
        internalViolations(InternalField.class)
                .singleElement()
                .asString()
                .contains("InternalField.reader", "internal.archive.EntryReader");
    }

    @Test
    void rejectsAnInternalTypeUsedAsAGenericArgument() {
        internalViolations(InternalGenericArgument.class)
                .singleElement()
                .asString()
                .contains("internal.archive.EntryReader");
    }

    @Test
    void rejectsAnInternalBoundOnAMethodTypeParameter() {
        internalViolations(InternalMethodTypeParameter.class)
                .singleElement()
                .asString()
                .contains("internal.archive.EntryReader");
    }

    @Test
    void rejectsAProtectedMemberThatIsNotAllowlisted() {
        internalViolations(ProtectedHook.class)
                .singleElement()
                .asString()
                .contains("ProtectedHook.<init>", "internal.archive.EntryReader");
    }

    @Test
    void acceptsAMemberAllowlistedByItsFullSignature() {
        var signature = ProtectedHook.class.getName() + ".<init>(" + EntryReader.class.getName() + ")";
        var rule = ApiSignatures.membersDoNotExpose(ArchitectureTest.INTERNAL, Set.of(signature));

        assertThat(violations(rule, ProtectedHook.class)).isEmpty();
    }

    @Test
    void rejectsACommonsCompressReturnType() {
        var rule = ApiSignatures.membersDoNotExpose(ArchitectureTest.COMMONS_COMPRESS, Set.of());

        assertThat(violations(rule, CommonsReturn.class))
                .singleElement()
                .asString()
                .contains("org.apache.commons.compress.archivers.ArchiveEntry");
    }

    @Test
    void rejectsACommonsCompressThrownType() {
        var rule = ApiSignatures.membersDoNotExpose(ArchitectureTest.COMMONS_COMPRESS, Set.of());

        assertThat(violations(rule, CommonsThrows.class))
                .singleElement()
                .asString()
                .contains("ArchiveException");
    }

    @Test
    void rejectsAnInternalSupertype() {
        internalTypeViolations(InternalSupertype.class)
                .singleElement()
                .asString()
                .contains("internal.archive.EntryReader");
    }

    @Test
    void rejectsAnInternalBoundOnAClassTypeParameter() {
        internalTypeViolations(InternalClassTypeParameter.class)
                .singleElement()
                .asString()
                .contains("internal.archive.EntryReader");
    }

    @Test
    void rejectsACommonsCompressBoundOnAClassTypeParameter() {
        commonsTypeViolations(CommonsClassTypeParameter.class)
                .singleElement()
                .asString()
                .contains("org.apache.commons.compress.archivers.ArchiveEntry");
    }

    @Test
    void acceptsAClassThatExposesNothingInternal() {
        internalViolations(Clean.class).isEmpty();
        internalTypeViolations(Clean.class).isEmpty();
    }

    @Test
    void theDocumentedHooksAreTheOnlyInternalExposureInTheRealApi() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .withImportOption(new ArchitectureTest.ExcludeTestFixtures())
                .importPackages("com.hominux.compress4j");
        var details = ApiSignatures.membersDoNotExpose(ArchitectureTest.INTERNAL, Set.of())
                .evaluate(classes)
                .getFailureReport()
                .getDetails();

        assertThat(details)
                .hasSize(ArchitectureTest.SPI_HOOKS.size())
                .allSatisfy(d -> assertThat(ArchitectureTest.SPI_HOOKS).anyMatch(d::startsWith));
    }

    @Test
    void rejectsANonFinalPublicClass() {
        var rule = ApiSignatures.exportedClassesAreFinal(Set.of());

        assertThat(violations(rule, Open.class)).singleElement().asString().contains("Open");
    }

    @Test
    void acceptsFinalClassesEnumsInterfacesRecordsAndNamedExtensionPoints() {
        var rule = ApiSignatures.exportedClassesAreFinal(Set.of(Open.class.getName()));

        assertThat(violations(rule, Closed.class, Kind.class, Contract.class, Pair.class, Open.class))
                .isEmpty();
    }

    @Test
    void rejectsAccessToAStaticPlatformFlag() {
        assertThat(violations(ApiSignatures.noPlatformProbes(), ProbesPlatform.class))
                .singleElement()
                .asString()
                .contains("IS_OS_WINDOWS");
    }

    @Test
    void acceptsCodeThatDoesNotProbeThePlatform() {
        assertThat(violations(ApiSignatures.noPlatformProbes(), Clean.class)).isEmpty();
    }
}
