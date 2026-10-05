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
package com.hominux.compress4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.archivers.ArchiveExtractor;
import java.io.File;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.lang.module.ModuleFinder;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ModuleDescriptorTest {

    private static final String BASE = "com.hominux.compress4j.";
    private static final List<String> DEPENDENCY_JAR_PREFIXES =
            List.of("commons-compress", "commons-codec", "commons-io", "commons-lang3", "slf4j-api");

    private static Path moduleRoot() throws URISyntaxException {
        return Path.of(ArchiveExtractor.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    }

    private static Path[] moduleRootAndDependencyJars() throws URISyntaxException {
        Stream<String> entries = Stream.of(System.getProperty("java.class.path").split(File.pathSeparator));
        return Stream.concat(
                        Stream.of(moduleRoot()), entries.map(Path::of).filter(ModuleDescriptorTest::isDependencyJar))
                .toArray(Path[]::new);
    }

    private static boolean isDependencyJar(Path entry) {
        String name = entry.getFileName().toString();
        return name.endsWith(".jar") && DEPENDENCY_JAR_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private static ModuleDescriptor descriptor() throws URISyntaxException {
        Path root = moduleRoot();
        return ModuleFinder.of(root)
                .find("com.hominux.compress4j")
                .orElseThrow()
                .descriptor();
    }

    private static Set<Modifier> modifiersOf(ModuleDescriptor descriptor, String module) {
        return descriptor.requires().stream()
                .filter(r -> r.name().equals(module))
                .map(Requires::modifiers)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void exportsOnlyPublicPackages() throws Exception {
        Set<String> exported =
                descriptor().exports().stream().map(Exports::source).collect(Collectors.toSet());

        assertThat(exported)
                .containsExactlyInAnyOrder(
                        "com.hominux.compress4j",
                        BASE + "archivers",
                        BASE + "archivers.ar",
                        BASE + "archivers.cpio",
                        BASE + "archivers.sevenz",
                        BASE + "archivers.tar",
                        BASE + "archivers.zip",
                        BASE + "compressors",
                        BASE + "compressors.pack200",
                        BASE + "compressors.zstd",
                        BASE + "compressors.lzma",
                        BASE + "compressors.z",
                        BASE + "compressors.deflate64",
                        BASE + "compressors.brotli",
                        BASE + "archivers.arj",
                        BASE + "archivers.dump",
                        BASE + "exceptions");
    }

    @Test
    void keepsOnlyUtilsAndInternalPackagesUnexported() throws Exception {
        ModuleDescriptor descriptor = descriptor();
        Set<String> exported =
                descriptor.exports().stream().map(Exports::source).collect(Collectors.toSet());

        assertThat(descriptor.packages()).containsAll(exported);
        assertThat(descriptor.packages().stream().filter(p -> !exported.contains(p)))
                .containsExactlyInAnyOrder(
                        BASE + "utils", BASE + "internal.codec", BASE + "internal.io", BASE + "internal.limits");
    }

    @Test
    void resolvesWithoutOptionalXzModule() throws Exception {
        ModuleFinder finder = ModuleFinder.of(moduleRootAndDependencyJars());

        Configuration configuration = Configuration.resolve(
                finder,
                List.of(ModuleLayer.boot().configuration()),
                ModuleFinder.of(),
                Set.of("com.hominux.compress4j"));

        assertThat(configuration.findModule("org.tukaani.xz")).isEmpty();
        assertThat(configuration.findModule("com.github.luben.zstd_jni")).isEmpty();
    }

    @Test
    void declaresOptionalDependenciesAsStatic() throws Exception {
        assertThat(modifiersOf(descriptor(), "org.tukaani.xz")).contains(Modifier.STATIC, Modifier.TRANSITIVE);
        assertThat(modifiersOf(descriptor(), "com.github.luben.zstd_jni"))
                .contains(Modifier.STATIC, Modifier.TRANSITIVE);
        assertThat(modifiersOf(descriptor(), "jakarta.annotation")).contains(Modifier.STATIC, Modifier.TRANSITIVE);
    }

    @Test
    void reExportsTypesUsedInPublicSignatures() throws Exception {
        assertThat(modifiersOf(descriptor(), "org.apache.commons.compress")).contains(Modifier.TRANSITIVE);
        assertThat(modifiersOf(descriptor(), "org.apache.commons.io")).contains(Modifier.TRANSITIVE);
    }

    @Test
    void keepsInternalDependenciesPrivate() throws Exception {
        assertThat(modifiersOf(descriptor(), "org.apache.commons.lang3")).isEmpty();
        assertThat(modifiersOf(descriptor(), "org.slf4j")).isEmpty();
    }
}
