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

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.api.entry.location.Join;
import uk.ac.ebi.embl.api.entry.location.Location;
import uk.ac.ebi.embl.api.entry.location.LocationFactory;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.api.entry.sequence.SequenceFactory;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.fftogff3.FeatureMapping;
import uk.ac.ebi.embl.gff3tools.fftogff3.GFF3AnnotationFactory;
import uk.ac.ebi.embl.gff3tools.fftogff3.GFF3DirectivesFactory;
import uk.ac.ebi.embl.gff3tools.gff3.*;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.validation.*;

class GFF3AnnotationFactoryTest {
    static Map<String, Set<String>> featureRelationMap;

    @BeforeAll
    public static void setUp() throws Exception {
        featureRelationMap = ConversionUtils.getFeatureRelationMap();
    }

    @Test
    public void buildFeatureTreeFullMapTest() {
        ValidationEngineBuilder builder = new ValidationEngineBuilder();
        GFF3DirectivesFactory directivesFactory = new GFF3DirectivesFactory();
        GFF3AnnotationFactory gFF3AnnotationFactory =
                new GFF3AnnotationFactory(builder.build(), directivesFactory, null);
        featureRelationMap.forEach((childName, parentSet) -> {
            List<GFF3Feature> featureList = new ArrayList<>();
            parentSet.forEach(parentName -> {
                GFF3Feature childFeature = TestUtils.createGFF3Feature(Optional.of(childName), Optional.of(parentName));
                GFF3Feature parentFeature = TestUtils.createGFF3Feature(Optional.of(parentName), Optional.empty());
                featureList.add(childFeature);
                featureList.add(parentFeature);
            });

            List<GFF3Feature> gff3Features = gFF3AnnotationFactory.buildFeatureTree(featureList);

            List<GFF3Feature> parentList = gff3Features.stream()
                    .filter(f -> !f.getChildren().isEmpty())
                    .collect(Collectors.toList());

            // Assert parent list
            assertEquals(parentList.size(), parentSet.size());

            // Assert children
            parentList.forEach(parent -> {
                assertTrue(parent.getChildren().stream().findFirst().isPresent());
                assertEquals(
                        parent.getChildren().stream().findFirst().get().getId().get(), childName);
            });
        });
    }

    @Test
    public void orderRootAndChildrenTest() {
        ValidationEngineBuilder builder = new ValidationEngineBuilder();
        GFF3AnnotationFactory gFF3AnnotationFactory =
                new GFF3AnnotationFactory(builder.build(), new GFF3DirectivesFactory(), null);
        List<GFF3Feature> featureList = new ArrayList<>();
        List<GFF3Feature> parentList = new ArrayList<>();
        List<GFF3Feature> childList = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : featureRelationMap.entrySet()) {
            String childName = entry.getKey();
            Set<String> parentSet = entry.getValue();

            for (String parentName : parentSet) {
                GFF3Feature childFeature = TestUtils.createGFF3Feature(Optional.of(childName), Optional.of(parentName));
                GFF3Feature parentFeature = TestUtils.createGFF3Feature(Optional.of(parentName), Optional.empty());

                childList.add(childFeature);
                parentList.add(parentFeature);
            }
        }

        featureList.addAll(childList);
        featureList.addAll(parentList);
        List<GFF3Feature> rootNode = gFF3AnnotationFactory.buildFeatureTree(featureList);

        // Assert rootNode size = size of parentFeatures got from
        assertEquals(rootNode.size(), parentList.size());

        featureList.clear();
        for (GFF3Feature root : rootNode) {
            gFF3AnnotationFactory.orderRootAndChildren(featureList, root);
        }
        assertEquals(featureList.size(), childList.size() + parentList.size());

        // parent feature will not have parentId
        long parentCount =
                featureList.stream().filter(f -> !f.getParentId().isPresent()).count();
        // child feature will have parentId
        long childCount =
                featureList.stream().filter(f -> f.getParentId().isPresent()).count();

        assertEquals(parentCount, parentList.size());
        assertEquals(childCount, childList.size());
    }

    @Test
    public void testGetIncrementalId() {
        ValidationEngineBuilder builder = new ValidationEngineBuilder();
        GFF3AnnotationFactory gFF3AnnotationFactory =
                new GFF3AnnotationFactory(builder.build(), new GFF3DirectivesFactory(), null);
        List<String> genes = Arrays.asList("tnpA", "tnpB", "tnpA", "tnpA", "tnpC", "tnpB", "ppk_2", "ppk_2", "ppk_2");
        List<String> ids = Arrays.asList(
                "CDS_tnpA",
                "CDS_tnpB",
                "CDS_tnpA_1",
                "CDS_tnpA_2",
                "CDS_tnpC",
                "CDS_tnpB_1",
                "CDS_ppk_2.S",
                "CDS_ppk_2.S_1",
                "CDS_ppk_2.S_2");
        String featureName = "CDS";
        int count = 0;
        for (String gene : genes) {
            String id = gFF3AnnotationFactory.getIncrementalId(featureName, Optional.of(gene));
            assertEquals(ids.get(count), id);
            count++;
        }
    }

    @Test
    public void testGetGFF3FeatureName()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ValidationEngineBuilder builder = new ValidationEngineBuilder();
        GFF3AnnotationFactory gFF3AnnotationFactory =
                new GFF3AnnotationFactory(builder.build(), new GFF3DirectivesFactory(), null);

        Method method = FeatureMapping.class.getDeclaredMethod("getGFF3FeatureName", Feature.class);
        method.setAccessible(true);

        FeatureFactory featureFactory = new FeatureFactory();

        Feature mappedFeature = featureFactory.createFeature("gene");
        Object mappedFeatureResult = method.invoke(gFF3AnnotationFactory, mappedFeature);
        assertEquals("ncRNA_gene", mappedFeatureResult);

        Feature mappedFeatureWithQualifiers1 = featureFactory.createFeature("ncRNA");
        mappedFeatureWithQualifiers1.addQualifier("ncRNA_class", "snoRNA");
        mappedFeatureWithQualifiers1.addQualifier("note", "C_D_box_snoRNA");
        Object mappedFeatureWithQualifiersResult1 = method.invoke(gFF3AnnotationFactory, mappedFeatureWithQualifiers1);
        assertEquals("C_D_box_snoRNA", mappedFeatureWithQualifiersResult1);

        Feature mappedFeatureWithQualifiers2 = featureFactory.createFeature("ncRNA");
        mappedFeatureWithQualifiers2.addQualifier("ncRNA_class", "snoRNA");
        Object mappedFeatureWithQualifiersResult2 = method.invoke(gFF3AnnotationFactory, mappedFeatureWithQualifiers2);
        assertEquals("snoRNA", mappedFeatureWithQualifiersResult2);

        Feature unmappedFeature = featureFactory.createFeature("unmapped");
        try {
            method.invoke(gFF3AnnotationFactory, unmappedFeature);
            fail("Should have thrown an exception");
        } catch (InvocationTargetException ignored) {
        }
    }

    @Test
    public void testGetParentFeature()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ValidationException {
        GFF3DirectivesFactory directivesFactory = new GFF3DirectivesFactory();
        ValidationEngineBuilder builder = new ValidationEngineBuilder();
        GFF3AnnotationFactory gFF3AnnotationFactory =
                new GFF3AnnotationFactory(builder.build(), directivesFactory, Path.of("translation.fasta"));

        EntryFactory entryFactory = new EntryFactory();
        Entry entry = entryFactory.createEntry();
        entry.setPrimaryAccession("abc");

        SequenceFactory sequenceFactory = new SequenceFactory();
        Sequence sequence = sequenceFactory.createSequence();
        sequence.setAccession("abc");
        entry.setSequence(sequence);

        Map<String, String> qualifiers = new HashMap<>();
        qualifiers.put("gene", "matK");
        createAndAddFeature(entry, "source", null);

        createAndAddFeature(entry, "gene", qualifiers);
        createAndAddFeature(entry, "mRNA", qualifiers);
        createAndAddFeature(entry, "intron", qualifiers);

        Map<String, String> repeatQualifiers = new HashMap<>();
        repeatQualifiers.put("rpt_type", "other");
        createAndAddFeature(entry, "repeat_region", repeatQualifiers);

        gFF3AnnotationFactory.from(entry);

        executeAndValidateGetParentFeature(gFF3AnnotationFactory, "boop", "matK", "");
        executeAndValidateGetParentFeature(gFF3AnnotationFactory, "repeat_region", "", "");
        executeAndValidateGetParentFeature(gFF3AnnotationFactory, "mRNA", "matK", "ncRNA_gene_matK");
        executeAndValidateGetParentFeature(gFF3AnnotationFactory, "intron", "matK", "mRNA_matK");

        // New GFF3AnnotationFactory object but adding features to entry
        gFF3AnnotationFactory =
                new GFF3AnnotationFactory(builder.build(), directivesFactory, Path.of("translation.fasta"));
        createAndAddFeature(entry, "gene", qualifiers);
        createAndAddFeature(entry, "mRNA", qualifiers);
        createAndAddFeature(entry, "intron", qualifiers);

        gFF3AnnotationFactory.from(entry);

        executeAndValidateGetParentFeature(gFF3AnnotationFactory, "mRNA", "matK", "ncRNA_gene_matK_1");
        executeAndValidateGetParentFeature(gFF3AnnotationFactory, "intron", "matK", "mRNA_matK_1");
    }

    private void createAndAddFeature(Entry entry, String featureName, Map<String, String> qualifiers) {
        FeatureFactory featureFactory = new FeatureFactory();
        Feature sourceFeature = featureFactory.createFeature(featureName);
        if (qualifiers != null) {
            for (String qualifier : qualifiers.keySet()) {
                sourceFeature.addQualifier(qualifier, qualifiers.get(qualifier));
            }
        }
        Join<Location> sourceLocation = new Join<>();
        sourceLocation.addLocation(new LocationFactory().createLocalRange(1L, 822L));
        sourceFeature.setLocations(sourceLocation);
        entry.addFeature(sourceFeature);
    }

    private void executeAndValidateGetParentFeature(
            GFF3AnnotationFactory gFF3AnnotationFactory, String featureName, String geneName, String expectedParentId)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method getParentFeature =
                GFF3AnnotationFactory.class.getDeclaredMethod("getParentFeature", String.class, Optional.class);
        getParentFeature.setAccessible(true);

        Optional<String> result =
                (Optional<String>) getParentFeature.invoke(gFF3AnnotationFactory, featureName, Optional.of(geneName));
        assertEquals(expectedParentId, result.orElse(""));
    }
}
