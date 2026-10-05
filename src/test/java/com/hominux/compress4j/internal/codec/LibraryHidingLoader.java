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
package com.hominux.compress4j.internal.codec;

import com.hominux.compress4j.compressors.Compression;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Loads Compress4J from the test classpath with one optional library's packages made invisible. */
public final class LibraryHidingLoader extends URLClassLoader {

    private final List<String> hiddenPackages;

    /**
     * Creates a loader that cannot see the given packages.
     *
     * @param hiddenPackages package prefixes, each ending in a dot
     */
    public LibraryHidingLoader(String... hiddenPackages) {
        super(classpathUrls(), ClassLoader.getPlatformClassLoader());
        this.hiddenPackages = List.of(hiddenPackages);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (hiddenPackages.stream().anyMatch(name::startsWith)) {
            throw new ClassNotFoundException(name);
        }
        return super.loadClass(name, resolve);
    }

    Object compression(String factory) throws Exception {
        return invoke(loadClass(Compression.class.getName()).getMethod(factory), null);
    }

    InputStream decompressing(Object compression, InputStream in) throws Exception {
        return (InputStream) invoke(codecs("decompressing", InputStream.class), compression, in);
    }

    OutputStream compressing(Object compression, OutputStream out) throws Exception {
        return (OutputStream) invoke(codecs("compressing", OutputStream.class), compression, out);
    }

    private Method codecs(String method, Class<?> streamType) throws Exception {
        return loadClass(Codecs.class.getName()).getMethod(method, loadClass(Compression.class.getName()), streamType);
    }

    private static Object invoke(Method method, Object... args) throws Exception {
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
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
}
