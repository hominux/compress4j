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

import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NullnessCompileTest {

    private static final String BUILD_ARGS = System.getProperty("compile.errorprone.args", "");

    private static final List<String> BASE_OPTIONS = List.of(
            "-proc:none",
            "-classpath",
            System.getProperty("java.class.path"),
            "-processorpath",
            System.getProperty("java.class.path"),
            "-XDcompilePolicy=simple",
            "-XDaddTypeAnnotationsToSymbol=true",
            "--should-stop=ifError=FLOW",
            "-Xplugin:ErrorProne " + BUILD_ARGS);

    private static final String PACKAGE_INFO = "@org.jspecify.annotations.NullMarked package sample;";

    private static final String RETURNS_NULL =
            "package sample; public class Leak { public String leak() { return null; } }";

    private static final String RETURNS_OPTIONAL = "package sample; public class Leak {"
            + " public java.util.Optional<String> leak() { return java.util.Optional.empty(); } }";

    @Test
    void buildRunsNullAwayAtErrorOnNullMarkedCode() {
        assertThat(BUILD_ARGS)
                .contains("-Xep:NullAway:ERROR")
                .contains("-XepOpt:NullAway:OnlyNullMarked=true")
                .contains("-XepOpt:NullAway:JSpecifyMode=true");
    }

    @Test
    void failsWhenPublicMethodReturnsNullInNullMarkedPackage(@TempDir Path out) {
        assertThat(errors(RETURNS_NULL, out)).singleElement().asString().contains("[NullAway]");
    }

    @Test
    void compilesWhenPublicMethodReturnsOptionalInNullMarkedPackage(@TempDir Path out) {
        assertThat(errors(RETURNS_OPTIONAL, out)).isEmpty();
    }

    private static List<String> errors(String source, Path out) {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = Stream.concat(BASE_OPTIONS.stream(), Stream.of("-d", out.toString()))
                .toList();
        List<Source> units =
                List.of(new Source("sample/package-info", PACKAGE_INFO), new Source("sample/Leak", source));
        ToolProvider.getSystemJavaCompiler()
                .getTask(new StringWriter(), null, diagnostics, options, null, units)
                .call();
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(Locale.ROOT))
                .toList();
    }

    private static final class Source extends SimpleJavaFileObject {
        private final String code;

        Source(String name, String code) {
            super(URI.create("string:///" + name + ".java"), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
