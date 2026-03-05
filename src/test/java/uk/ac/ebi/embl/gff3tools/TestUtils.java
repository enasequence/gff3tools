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
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;

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

        GFF3Feature feature = new GFF3Feature(
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
                "");
        feature.addAttribute("ID", id);
        feature.addAttribute("Parent", parent);
        feature.addAttribute("gene", "geneX");
        return feature;
    }

    public static GFF3Feature createGFF3Feature(
            String featureName, String parentFeatureName, Map<String, List<String>> attributes) {

        GFF3Feature feature = new GFF3Feature(
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
                "");
        feature.addAttributes(attributes);
        return feature;
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
                "");
    }

    public static GFF3Feature createGFF3Feature(String featureName, Map<String, List<String>> attributes) {

        GFF3Feature feature = new GFF3Feature(
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
                "");

        feature.addAttributes(attributes);
        return feature;
    }

    public static GFF3Feature createGFF3Feature(
            String featureName, long start, long end, Map<String, List<String>> attributes) {

        GFF3Feature feature = new GFF3Feature(
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
                "");
        feature.addAttributes(attributes);
        return feature;
    }

    public static GFF3Feature createGFF3Feature(
            String featureName, String parentFeatureName, String seqId, Map<String, List<String>> attributes) {

        GFF3Feature feature = new GFF3Feature(
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
                "");

        feature.addAttributes(attributes);
        return feature;
    }

    public static GFF3Feature createGFF3FeatureWithAccession(
            String seqId, String name, Map<String, List<String>> attributes) {
        GFF3Feature feature = new GFF3Feature(
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
                "");

        feature.addAttributes(attributes);
        return feature;
    }

    public static GFF3Feature createGFF3Feature(
            String featureName, String seqId, long start, long end, Map<String, List<String>> attributes) {

        GFF3Feature feature = new GFF3Feature(
                Optional.of(featureName),
                Optional.empty(),
                seqId,
                Optional.empty(),
                ".",
                featureName,
                start,
                end,
                ".",
                "+",
                "");
        feature.addAttributes(attributes);
        return feature;
    }

    /**
     * Creates a ValidationContext with the real OntologyClient and injects it
     * into the target's @InjectContext fields via reflection.
     */
    public static void injectContext(Object target) {
        ValidationContext context = createTestContext();
        injectContext(target, context);
    }

    /**
     * Creates a ValidationContext with a custom OntologyClient and injects it
     * into the target's @InjectContext fields via reflection.
     */
    public static void injectContext(Object target, OntologyClient ontologyClient) {
        ValidationContext context = createTestContext(ontologyClient);
        injectContext(target, context);
    }

    public static ValidationContext createTestContext() {
        return createTestContext(OntologyClient.getInstance());
    }

    @SuppressWarnings("unchecked")
    public static ValidationContext createTestContext(OntologyClient ontologyClient) {
        ValidationContext context = new ValidationContext();
        context.register(OntologyClient.class, new ContextProvider<>() {
            @Override
            public OntologyClient get(ValidationContext ctx) {
                return ontologyClient;
            }

            @Override
            public Class<OntologyClient> type() {
                return OntologyClient.class;
            }
        });
        return context;
    }

    public static void injectContext(Object target, ValidationContext context) {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(InjectContext.class)) {
                    field.setAccessible(true);
                    try {
                        field.set(target, context);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Failed to inject context", e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    public static String defaultAccession() {
        return DEFAULT_ACCESSION;
    }

    public static GFF3Feature createGFF3Feature(
            String id, String parentId, String featureName, String seqId, long start, long end) {
        GFF3Feature feature = new GFF3Feature(
                id != null ? Optional.of(id) : Optional.empty(),
                parentId != null ? Optional.of(parentId) : Optional.empty(),
                seqId,
                Optional.empty(),
                ".",
                featureName,
                start,
                end,
                ".",
                "+",
                ".");
        return feature;
    }
}
