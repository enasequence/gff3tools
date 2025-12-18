/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.gff3tools.fasta;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.Objects;

public final class FastaTestResources {
    private FastaTestResources() {}

    /** Returns a Path to a resource like ("fasta", "example.txt"). */
    public static Path path(String dir, String fileName) {
        Objects.requireNonNull(dir, "dir");
        Objects.requireNonNull(fileName, "fileName");
        String resource = dir.endsWith("/") ? dir + fileName : dir + "/" + fileName;

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL url = Objects.requireNonNull(cl.getResource(resource), "Missing resource on classpath: " + resource);

        try {
            if ("file".equals(url.getProtocol())) {
                // Gradle tests: build/resources/test/...
                return Paths.get(url.toURI());
            }
            // Fallback for jar: URLs — copy to temp so callers can have a real Path/File
            try (InputStream in = cl.getResourceAsStream(resource)) {
                Objects.requireNonNull(in, "Resource stream is null: " + resource);
                Path tmp = Files.createTempFile("testres-", "-" + fileName);
                tmp.toFile().deleteOnExit();
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                return tmp;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve resource: " + resource, e);
        }
    }

    /** Convenience if you need a File. */
    public static File file(String dir, String fileName) {
        return path(dir, fileName).toFile();
    }

    /** Stream, if you don’t need a File/Path. */
    public static InputStream stream(String dir, String fileName) {
        String resource = dir.endsWith("/") ? dir + fileName : dir + "/" + fileName;
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        return Objects.requireNonNull(in, "Missing resource stream: " + resource);
    }
}
