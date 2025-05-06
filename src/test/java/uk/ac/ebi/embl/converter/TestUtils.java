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
package uk.ac.ebi.embl.converter;

import uk.ac.ebi.embl.converter.gff3.GFF3Feature;


import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

public class TestUtils {

    public static BufferedReader getResourceReader(String resourceName) throws IOException {
        FileReader reader = new FileReader(resourceName);
        return new BufferedReader(reader);
    }

    public static Map<String, Path> getTestFiles(String resourceName, String extension) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourceName);
        Map<String, Path> inFiles = new LinkedHashMap<>();
        if (resource != null) {
            File folder = new File(resource.getPath());
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                if (file.getName().endsWith(extension)) {
                    inFiles.put(file.getName().replace(extension, ""), file.toPath());
                }
            }
        } else {
            System.out.println("Directory not found!");
        }
        return inFiles;
    }

    public static File getResourceFile(String resourceName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourceName);
        if (resource != null) {
            return new File(resource.getPath());
        }
        return null;
    }

    public static GFF3Feature createGFF3Feature(Optional<String> featureName, Optional<String> parentFeatureName) {
        String id = featureName.map(v -> v + "_gene").orElse("_gene");
        String parent = parentFeatureName.map(v -> v + "_gene").orElse("_gene");

        return new GFF3Feature(
                featureName,
                parentFeatureName,
                "1234",
                ".",
                featureName.get(),
                1,
                800,
                ".",
                "+",
                "",
                new HashMap<>(Map.of("ID", id, "Parent", parent, "gene", "geneX")));
    }

    public static GFF3Feature createGFF3Feature(String featureName, String parentFeatureName,Map<String, Object> attributes) {

        return new GFF3Feature(
                Optional.of(featureName),
                Optional.of(parentFeatureName),
                "1234",
                ".",
                featureName,
                1,
                800,
                ".",
                "+",
                "",
                attributes);
    }
}
