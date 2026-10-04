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
package com.hominux.compress4j.archivers;

import com.hominux.compress4j.exceptions.UnsafeEntryException;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Normalises archive entry names and rejects names that would resolve outside an extraction directory. */
final class EntryNames {

    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:");

    private EntryNames() {}

    static String checked(String rawName) throws UnsafeEntryException {
        String name = ArchiveCreator.sanitiseName(rawName);
        if (name.indexOf('\0') >= 0
                || DRIVE.matcher(name).find()
                || Arrays.asList(name.split("/")).contains("..")) {
            throw new UnsafeEntryException("Unsafe entry name: " + rawName);
        }
        return name;
    }
}
