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
package uk.ac.ebi.embl.gff3tools;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class TestUtils {

    public static final String DEFAULT_ACCESSION = "1234";

    public static BufferedReader getResourceReaderWithPath(String path) throws IOException {
        FileReader reader = new FileReader(path);
        return new BufferedReader(reader);
    }

    public static BufferedReader getResourceReader(String resourceName) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourceName);
        if (resource != null) {
            return getResourceReaderWithPath(resource.getPath());
        }
        return null;
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
                DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                featureName.get(),
                1,
                800,
                ".",
                "+",
                "",
                new HashMap<>(Map.of("ID", id, "Parent", parent, "gene", "geneX")));
    }

    public static GFF3Feature createGFF3Feature(
            String featureName, String parentFeatureName, Map<String, Object> attributes) {

        return new GFF3Feature(
                Optional.of(featureName),
                Optional.of(parentFeatureName),
                DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                featureName,
                1,
                800,
                ".",
                "+",
                "",
                attributes);
    }

    public static GFF3Feature createGFF3Feature(String featureName, long start, long end) {

        return new GFF3Feature(
                Optional.of(featureName),
                Optional.empty(),
                DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                featureName,
                start,
                end,
                ".",
                "+",
                "",
                new HashMap<>());
    }

    public static GFF3Feature createGFF3Feature(String featureName, Map<String, Object> attributes) {

        return new GFF3Feature(
                Optional.of(featureName),
                Optional.empty(),
                DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                featureName,
                1,
                800,
                ".",
                "+",
                "",
                attributes);
    }

    public static GFF3Feature createGFF3Feature(
            String featureName, long start, long end, Map<String, Object> attributes) {

        return new GFF3Feature(
                Optional.of(featureName),
                Optional.empty(),
                DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                featureName,
                start,
                end,
                ".",
                "+",
                "",
                attributes);
    }

    public static GFF3Feature createGFF3Feature(
            String featureName, String parentFeatureName, String seqId, Map<String, Object> attributes) {

        return new GFF3Feature(
                Optional.of(featureName),
                Optional.of(parentFeatureName),
                seqId,
                Optional.empty(),
                ".",
                featureName,
                1,
                800,
                ".",
                "+",
                "",
                attributes);
    }

    public static GFF3Feature createGFF3FeatureWithAccession(
            String seqId, String name, Map<String, Object> attributes) {
        return new GFF3Feature(
                Optional.of(name),
                Optional.empty(),
                seqId, // seqId -> controls accession()
                Optional.empty(), // version
                ".",
                name,
                1,
                100,
                ".",
                "+",
                "",
                new HashMap<>(attributes) // mutable!
                );
    }

    public static String defaultAccession() {
        return DEFAULT_ACCESSION;
    }
}
