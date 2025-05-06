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

import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.converter.fftogff3.GFF3AnnotationFactory;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;

class GFF3AnnotationFactoryTest {
    static Map<String, String> featureRelationMap;

    @BeforeAll
    public static void setUp() throws Exception {
        featureRelationMap = ConversionUtils.getFeatureRelationMap();
    }

    @Test
    public void buildFeatureTreeFullMapTest() {
        GFF3AnnotationFactory gFF3AnnotationFactory = new GFF3AnnotationFactory(true);
        featureRelationMap.forEach((child, parent) -> {
            List<GFF3Feature> featureList = new ArrayList<>();
            GFF3Feature childFeature = TestUtils.createGFF3Feature(Optional.of(child), Optional.of(parent));
            GFF3Feature parentFeature = TestUtils.createGFF3Feature(Optional.of(parent), Optional.empty());
            featureList.add(childFeature);
            featureList.add(parentFeature);

            List<GFF3Feature> gff3Features = gFF3AnnotationFactory.buildFeatureTree(featureList);

            GFF3Feature firstFeature = gff3Features.stream().findFirst().get();
            assertTrue(firstFeature.getChildren().get(0).equals(childFeature));
        });
    }

    @Test
    public void orderRootAndChildrenTest() {
        GFF3AnnotationFactory gFF3AnnotationFactory = new GFF3AnnotationFactory(true);
        List<GFF3Feature> featureList = new ArrayList<>();
        featureRelationMap.forEach((child, parent) -> {
            GFF3Feature childFeature = TestUtils.createGFF3Feature(Optional.of(child), Optional.of(parent));
            GFF3Feature parentFeature = TestUtils.createGFF3Feature(Optional.of(parent), Optional.empty());
            featureList.add(childFeature);
            featureList.add(parentFeature);
        });

        // One feature for each parent and child
        int numberOfFeatures = featureRelationMap.entrySet().size() * 2;
        assertEquals(featureList.size(), numberOfFeatures);
        List<GFF3Feature> rootNode = gFF3AnnotationFactory.buildFeatureTree(featureList);

        // Assert rootNode size = size of parentFeatures got from
        // createGFF3Feature(Optional.of(parent),Optional.empty());
        assertEquals(rootNode.size(), featureRelationMap.size());

        featureList.clear();
        for (GFF3Feature root : rootNode) {
            gFF3AnnotationFactory.orderRootAndChildren(featureList, root);
        }
        assertEquals(featureList.size(), numberOfFeatures);

        Set<String> fetureMapValueSet = new HashSet(featureRelationMap.values());

        long childrenCount = 0;
        for (String parent : fetureMapValueSet) {
            long noOfChildrenFromMap = featureRelationMap.values().stream()
                    .filter(f -> f.equals(parent))
                    .count();
            long noOfChildrenFromTree = rootNode.stream()
                    .filter(f -> f.getId().get().equals(parent))
                    .count();
            // System.out.println(parent+" "+noOfChildrenFromMap+" "+noOfChildrenFromTree);
            assertEquals(noOfChildrenFromMap, noOfChildrenFromTree);
            childrenCount += noOfChildrenFromTree;
        }

        // Assert rootNode children count = count of childFeatures got from
        // createGFF3Feature(Optional.of(child), Optional.of(parent));
        assertEquals(childrenCount, featureRelationMap.size());
    }

    @Test
    public void testGetIncrementalId(){
        GFF3AnnotationFactory gFF3AnnotationFactory = new GFF3AnnotationFactory(true);
        List<String> genes = Arrays.asList("tnpA","tnpB","tnpA","tnpA","tnpC","tnpB");
        List<String> ids = Arrays.asList("CDS_tnpA","CDS_tnpB","CDS_tnpA_1","CDS_tnpA_2","CDS_tnpC","CDS_tnpB_1");
        String featureName = "CDS";
        int count=0;
        for(String gene: genes){
            String id = gFF3AnnotationFactory.getIncrementalId(featureName, gene);
            assertEquals(ids.get(count),id);
            count++;
        }

    }
}
