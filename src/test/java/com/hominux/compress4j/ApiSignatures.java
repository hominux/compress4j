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

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.util.Set;
import java.util.stream.Stream;

final class ApiSignatures {

    private static final DescribedPredicate<JavaClass> EXPORTED = DescribedPredicate.describe(
            "in an exported package", c -> !c.getPackageName().contains(".internal"));

    private static final DescribedPredicate<JavaClass> VISIBLE_TYPE = DescribedPredicate.describe(
            "public or protected",
            c -> c.getModifiers().contains(JavaModifier.PUBLIC)
                    || c.getModifiers().contains(JavaModifier.PROTECTED));

    private static final DescribedPredicate<JavaMember> VISIBLE_MEMBER = DescribedPredicate.describe(
            "public or protected",
            m -> m.getModifiers().contains(JavaModifier.PUBLIC)
                    || m.getModifiers().contains(JavaModifier.PROTECTED));

    private static final DescribedPredicate<JavaClass> EXTENSIBLE_BY_DEFAULT = DescribedPredicate.describe(
            "a class that is not an enum or record",
            c -> !c.isInterface() && !c.isEnum() && !c.isAssignableTo(Record.class));

    private ApiSignatures() {}

    static ArchRule membersDoNotExpose(String packageIdentifier, Set<String> allowedMemberSignatures) {
        return ArchRuleDefinition.members()
                .that(VISIBLE_MEMBER)
                .and()
                .areDeclaredInClassesThat(EXPORTED)
                .and()
                .areDeclaredInClassesThat(VISIBLE_TYPE)
                .should(notExpose(packageIdentifier, allowedMemberSignatures));
    }

    static ArchRule typesDoNotExpose(String packageIdentifier) {
        return ArchRuleDefinition.classes().that(EXPORTED).and(VISIBLE_TYPE).should(declareNoTypeIn(packageIdentifier));
    }

    static ArchRule exportedClassesAreFinal(Set<String> extensibleClassNames) {
        return ArchRuleDefinition.classes()
                .that(EXPORTED)
                .and(VISIBLE_TYPE)
                .and(EXTENSIBLE_BY_DEFAULT)
                .and(DescribedPredicate.describe(
                        "not an allowed extension point", c -> !extensibleClassNames.contains(c.getName())))
                .should()
                .haveModifier(JavaModifier.FINAL);
    }

    static ArchRule noPlatformProbes() {
        return ArchRuleDefinition.noClasses()
                .should()
                .accessFieldWhere(DescribedPredicate.describe(
                        "a static platform flag of SystemUtils",
                        access ->
                                access.getTarget().getOwner().isEquivalentTo(org.apache.commons.lang3.SystemUtils.class)
                                        && access.getTarget().getName().startsWith("IS_OS_")));
    }

    private static ArchCondition<JavaMember> notExpose(String packageIdentifier, Set<String> allowedSignatures) {
        return new ArchCondition<>("not expose types in " + packageIdentifier + " except " + allowedSignatures) {
            @Override
            public void check(JavaMember member, ConditionEvents events) {
                if (allowedSignatures.contains(member.getFullName())) {
                    return;
                }
                signatureTypes(member)
                        .flatMap(t -> involved(t, packageIdentifier))
                        .forEach(t -> events.add(violated(member, member.getFullName() + " exposes " + t.getName())));
            }
        };
    }

    private static ArchCondition<JavaClass> declareNoTypeIn(String packageIdentifier) {
        return new ArchCondition<>("extend, implement or bound no type in " + packageIdentifier) {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                declaredTypes(type)
                        .flatMap(t -> involved(t, packageIdentifier))
                        .forEach(t -> events.add(violated(type, type.getName() + " declares " + t.getName())));
            }
        };
    }

    private static Stream<JavaType> declaredTypes(JavaClass type) {
        return Stream.<Stream<? extends JavaType>>of(
                        type.getSuperclass().stream(), type.getInterfaces().stream(), type.getTypeParameters().stream())
                .flatMap(s -> s);
    }

    private static Stream<JavaClass> involved(JavaType type, String packageIdentifier) {
        return type.getAllInvolvedRawTypes().stream().filter(JavaClass.Predicates.resideInAPackage(packageIdentifier));
    }

    private static Stream<JavaType> signatureTypes(JavaMember member) {
        if (member instanceof JavaField field) {
            return Stream.of(field.getType());
        }
        if (member instanceof JavaCodeUnit unit) {
            return Stream.<Stream<? extends JavaType>>of(
                            unit.getParameterTypes().stream(),
                            Stream.of(unit.getReturnType()),
                            unit.getThrowsClause().getTypes().stream(),
                            unit.getTypeParameters().stream())
                    .flatMap(s -> s);
        }
        return Stream.empty();
    }
}
