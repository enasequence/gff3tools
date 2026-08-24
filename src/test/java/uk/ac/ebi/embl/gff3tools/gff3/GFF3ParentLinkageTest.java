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
package uk.ac.ebi.embl.gff3tools.gff3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;

class GFF3ParentLinkageTest {

    private static final String GFF3 = "reader/parent_linkage.gff3";
    private static final String GENE_MATCHING_CDS = "reader/parent_linkage_gene_matching_cds.gff3";
    private static final String SPLICED_GENE_PARTLY_MATCHING_CDS = "reader/parent_linkage_spliced_gene.gff3";

    @Test
    void readerPopulatesParentIdFromTheFirstParentValueOnly() throws Exception {
        Map<String, GFF3Feature> byId = readFeaturesById();
        GFF3Feature cds = byId.get("cds1");
        List<String> parents =
                cds.getAttributeList(GFF3Attributes.ATTRIBUTE_PARENT).orElseThrow();

        assertEquals(List.of("gene1", "mrna1"), parents);
        assertEquals(Optional.of(parents.get(0)), cds.getParentId());
        assertEquals(1, parents.indexOf(cds.getParentId().orElseThrow()) + 1);

        assertEquals(Optional.empty(), byId.get("gene1").getParentId());
        assertEquals(Optional.of("gene1"), byId.get("mrna1").getParentId());
    }

    @Test
    void readerKeepsEveryParentValueInTheAttribute() throws Exception {
        Map<String, GFF3Feature> byId = readFeaturesById();

        assertEquals(Optional.empty(), byId.get("gene1").getAttributeList(GFF3Attributes.ATTRIBUTE_PARENT));
        assertEquals(
                Optional.of(List.of("gene1")), byId.get("mrna1").getAttributeList(GFF3Attributes.ATTRIBUTE_PARENT));
        assertEquals(
                Optional.of(List.of("gene1", "mrna1")),
                byId.get("cds1").getAttributeList(GFF3Attributes.ATTRIBUTE_PARENT));
    }

    @Test
    void readerDoesNotLinkTheParentObject() throws Exception {
        Map<String, GFF3Feature> byId = readFeaturesById();

        assertNull(byId.get("gene1").getParent());
        assertNull(byId.get("mrna1").getParent());
        assertNull(byId.get("cds1").getParent());
    }

    @Test
    void readerDoesNotLinkChildObjects() throws Exception {
        Map<String, GFF3Feature> byId = readFeaturesById();

        assertTrue(byId.get("gene1").getChildren().isEmpty());
        assertTrue(byId.get("mrna1").getChildren().isEmpty());
        assertFalse(byId.get("gene1").hasChildren());
        assertFalse(byId.get("mrna1").hasChildren());
    }

    @Test
    void fixesClearParentIdWhenTheParentGeneIsRemoved() throws Exception {
        Map<String, GFF3Feature> byId = readFeaturesById(GENE_MATCHING_CDS);

        assertFalse(byId.containsKey("gene1"));
        assertEquals(Optional.empty(), byId.get("cds1").getParentId());
        assertEquals(Optional.empty(), byId.get("cds1").getAttributeList(GFF3Attributes.ATTRIBUTE_PARENT));
    }

    @Test
    void fixesClearParentIdEvenWhenTheSplicedParentGeneSurvives() throws Exception {
        Map<String, GFF3Feature> byId = readFeaturesById(SPLICED_GENE_PARTLY_MATCHING_CDS);

        assertTrue(byId.containsKey("gene1"));
        assertEquals(Optional.empty(), byId.get("cds1").getParentId());
        assertEquals(Optional.empty(), byId.get("cds1").getAttributeList(GFF3Attributes.ATTRIBUTE_PARENT));
    }

    @Test
    void validationCommandFlowLeavesParentUnpopulated() throws Exception {
        Map<String, GFF3Feature> byId = new LinkedHashMap<>();

        Path gff3Path = TestUtils.getResourceFile(GFF3).toPath();
        try (ValidationEngine engine = new ValidationEngineBuilder()
                        .overrideMethodRules(Map.of())
                        .failFast(false)
                        .build();
                BufferedReader reader = Files.newBufferedReader(gff3Path);
                GFF3FileReader gff3Reader = new GFF3FileReader(engine, reader, gff3Path)) {
            gff3Reader.readHeader();
            gff3Reader.read(annotation -> {
                for (GFF3Feature feature : annotation.getFeatures()) {
                    feature.getId().ifPresent(id -> byId.put(id, feature));
                }
            });
        }

        assertEquals(3, byId.size());
        assertNull(byId.get("gene1").getParent());
        assertNull(byId.get("mrna1").getParent());
        assertNull(byId.get("cds1").getParent());
        assertTrue(byId.get("gene1").getChildren().isEmpty());
        assertTrue(byId.get("mrna1").getChildren().isEmpty());
        assertEquals(Optional.of("gene1"), byId.get("mrna1").getParentId());
    }

    private Map<String, GFF3Feature> readFeaturesById() throws Exception {
        return readFeaturesById(GFF3);
    }

    private Map<String, GFF3Feature> readFeaturesById(String resourceName) throws Exception {
        Map<String, GFF3Feature> byId = new LinkedHashMap<>();
        Path gff3Path = TestUtils.getResourceFile(resourceName).toPath();
        try (BufferedReader reader = Files.newBufferedReader(gff3Path);
                GFF3FileReader gff3Reader =
                        new GFF3FileReader(new ValidationEngineBuilder().build(), reader, gff3Path)) {
            gff3Reader.readHeader();
            GFF3Annotation annotation;
            while ((annotation = gff3Reader.readAnnotation()) != null) {
                for (GFF3Feature feature : annotation.getFeatures()) {
                    feature.getId().ifPresent(id -> byId.put(id, feature));
                }
            }
        }
        return byId;
    }
}
