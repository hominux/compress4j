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
package com.hominux.compress4j.internal.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.hominux.compress4j.exceptions.MissingArchiveDependencyException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchiverDependencyCheckerWithoutBrotliTest {

    private static final String BROTLI_PACKAGE = "org.brotli.";

    private static final class BrotliHidingClassLoader extends URLClassLoader {
        BrotliHidingClassLoader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(BROTLI_PACKAGE)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    private static URL[] classpathUrls() {
        return Stream.of(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(entry -> toUrl(Path.of(entry)))
                .toArray(URL[]::new);
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Throwable checkFailure(String entryName) throws Exception {
        try (var loader = new BrotliHidingClassLoader(classpathUrls())) {
            loader.loadClass(ArchiverDependencyChecker.class.getName())
                    .getMethod("check", String.class)
                    .invoke(null, entryName);
            throw new AssertionError("Expected failure without org.brotli:dec");
        } catch (InvocationTargetException e) {
            return e.getCause();
        }
    }

    private static Throwable buildFailure(String builderClass, Class<?> argType, Object arg, String codec)
            throws Exception {
        try (var loader = new BrotliHidingClassLoader(classpathUrls())) {
            Class<?> compression = loader.loadClass("com.hominux.compress4j.compressors.Compression");
            Object builder = loader.loadClass(builderClass)
                    .getMethod("builder", argType, compression)
                    .invoke(null, arg, compression.getMethod(codec).invoke(null));
            builder.getClass().getMethod("build").invoke(builder);
            throw new AssertionError("Expected failure without org.brotli:dec");
        } catch (InvocationTargetException e) {
            return e.getCause();
        }
    }

    private static void assertMissingBrotli(Throwable failure) {
        assertThat(failure.getClass().getName()).isEqualTo(MissingArchiveDependencyException.class.getName());
        assertThat(failure).hasMessage(DependencyCheckerTestConstants.EXPECTED_MESSAGE_BROTLI);
    }

    @Test
    void checkerRejectsBrotli() throws Exception {
        assertMissingBrotli(checkFailure("br"));
    }

    @Test
    void decompressorBuildRejectsMissingBrotli() throws Exception {
        assertMissingBrotli(buildFailure(
                "com.hominux.compress4j.compressors.Decompressor",
                InputStream.class,
                new ByteArrayInputStream(new byte[0]),
                "brotli"));
    }
}
