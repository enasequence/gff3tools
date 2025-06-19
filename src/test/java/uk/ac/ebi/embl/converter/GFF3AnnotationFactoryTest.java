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

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import uk.ac.ebi.embl.api.entry.sequence.SequenceFactory;
import uk.ac.ebi.embl.converter.fftogff3.GFF3AnnotationFactory;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;
import uk.ac.ebi.embl.converter.validation.ValidationError;

class GFF3AnnotationFactoryTest {
    static Map<String, Set<String>> featureRelationMap;

    @BeforeAll
    public static void setUp() throws Exception {
        featureRelationMap = ConversionUtils.getFeatureRelationMap();
    }

    @Test
    public void buildFeatureTreeFullMapTest() {
        // TODO: need to fix test
        GFF3AnnotationFactory gFF3AnnotationFactory = new GFF3AnnotationFactory(true);
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
        // TODO: need to fix test
        GFF3AnnotationFactory gFF3AnnotationFactory = new GFF3AnnotationFactory(true);
        List<GFF3Feature> featureList = new ArrayList<>();
        List<GFF3Feature> parentList = new ArrayList<>();
        List<GFF3Feature> childList = new ArrayList<>();
        int numberOfParents = 0;
        int numberOfChild = 0;
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
        GFF3AnnotationFactory gFF3AnnotationFactory = new GFF3AnnotationFactory(true);
        List<String> genes = Arrays.asList("tnpA", "tnpB", "tnpA", "tnpA", "tnpC", "tnpB");
        List<String> ids = Arrays.asList("CDS_tnpA", "CDS_tnpB", "CDS_tnpA_1", "CDS_tnpA_2", "CDS_tnpC", "CDS_tnpB_1");
        String featureName = "CDS";
        int count = 0;
        for (String gene : genes) {
            String id = gFF3AnnotationFactory.getIncrementalId(featureName, gene);
            assertEquals(ids.get(count), id);
            count++;
        }
    }

    @Test
    public void testGetGFF3FeatureName()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        GFF3AnnotationFactory gFF3AnnotationFactory = new GFF3AnnotationFactory(true);
        Method method = GFF3AnnotationFactory.class.getDeclaredMethod("getGFF3FeatureName", Feature.class);
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
        Object unmappedFeatureResult = method.invoke(gFF3AnnotationFactory, unmappedFeature);
        assertEquals("unmapped", unmappedFeatureResult);
    }

    @Test
    public void testGetParentFeature()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ValidationError {
        GFF3AnnotationFactory gFF3AnnotationFactory = new GFF3AnnotationFactory(true);

        EntryFactory entryFactory = new EntryFactory();
        Entry entry = entryFactory.createEntry();

        SequenceFactory sequenceFactory = new SequenceFactory();
        entry.setSequence(sequenceFactory.createSequence());

        FeatureFactory featureFactory = new FeatureFactory();
        LocationFactory locationFactory = new LocationFactory();

        Feature sourceFeature = featureFactory.createFeature("source");
        Join<Location> sourceLocation = new Join<>();
        sourceLocation.addLocation(locationFactory.createLocalRange(1L, 822L));
        sourceFeature.setLocations(sourceLocation);
        entry.addFeature(sourceFeature);

        Feature geneFeature = featureFactory.createFeature("gene");
        geneFeature.addQualifier("gene", "matK");
        Join<Location> geneLocation = new Join<>();
        geneLocation.addLocation(locationFactory.createLocalRange(1L, 822L));
        geneFeature.setLocations(geneLocation);
        entry.addFeature(geneFeature);

        Feature mRNAFeature = featureFactory.createFeature("mRNA");
        mRNAFeature.addQualifier("gene", "matK");
        Join<Location> mRNALocation = new Join<>();
        mRNALocation.addLocation(locationFactory.createLocalRange(1L, 822L));
        mRNAFeature.setLocations(mRNALocation);
        entry.addFeature(mRNAFeature);

        Feature intronFeature = featureFactory.createFeature("intron");
        intronFeature.addQualifier("gene", "matK");
        Join<Location> intronLocation = new Join<>();
        intronLocation.addLocation(locationFactory.createLocalRange(100L, 150L));
        intronFeature.setLocations(intronLocation);
        entry.addFeature(intronFeature);

        Feature repeatRegionFeature = featureFactory.createFeature("repeat_region");
        entry.addFeature(repeatRegionFeature);
        Join<Location> repeatLocation = new Join<>();
        repeatLocation.addLocation(locationFactory.createLocalRange(100L, 150L));
        repeatRegionFeature.setLocations(repeatLocation);
        entry.addFeature(repeatRegionFeature);

        gFF3AnnotationFactory.from(entry);

        Method method = GFF3AnnotationFactory.class.getDeclaredMethod("getParentFeature", String.class, Optional.class);
        method.setAccessible(true);

        Object noExistingFeature = method.invoke(gFF3AnnotationFactory, "boop", Optional.of("matK"));
        assertEquals("", noExistingFeature);

        Object noGeneFeature = method.invoke(gFF3AnnotationFactory, "repeat_region", Optional.empty());
        assertEquals("", noGeneFeature);

        Object firstDegreeParent = method.invoke(gFF3AnnotationFactory, "mRNA", Optional.of("matK"));
        assertEquals("ncRNA_gene_matK", firstDegreeParent);

        Object secondDegreeparent = method.invoke(gFF3AnnotationFactory, "intron", Optional.of("matK"));
        assertEquals("mRNA_matK", secondDegreeparent);
    }
}
