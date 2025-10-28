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
package uk.ac.ebi.embl.gff3tools.validation.fix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

class TransformAttributeToNoteFixTest {

    private String PRODUCT;
    private String PSEUDO;
    private String PSEUDOGENE;
    private String NOTE;

    private TransformAttributeToNoteFix fixer;

    @BeforeEach
    void setUp() {
        PRODUCT = GFF3Attributes.PRODUCT;
        PSEUDO = GFF3Attributes.PSEUDO;
        PSEUDOGENE = GFF3Attributes.PSEUDOGENE;
        NOTE = GFF3Attributes.NOTE;

        fixer = new TransformAttributeToNoteFix();
    }

    @Test
    void movesProductToNote_whenPseudoPresent_andRemovesProduct() {
        Map<String, Object> attrs = new HashMap<>(Map.of(
                PRODUCT, "kinase",
                PSEUDO, "true"));

        GFF3Feature feature = TestUtils.createGFF3Feature("gene", "gene_parent", attrs);

        fixer.fix(feature, 1);

        assertFalse(feature.containsAttribute(PRODUCT));
        assertTrue(feature.containsAttribute(PSEUDO));
        assertEquals("kinase", feature.getAttributeString(NOTE));
    }

    @Test
    void appendsProductToExistingNote_whenPseudogenePresent() {
        Map<String, Object> attrs = new HashMap<>(Map.of(
                PRODUCT, "beta-lactamase",
                PSEUDOGENE, "processed",
                NOTE, "existing-info"));

        GFF3Feature feature = TestUtils.createGFF3Feature("CDS", "mRNA1", attrs);

        fixer.fix(feature, 1);

        assertFalse(feature.containsAttribute(PRODUCT));
        assertEquals("existing-info,beta-lactamase", feature.getAttributeString(NOTE));
    }

    @Test
    void noChange_whenExclusivesAbsent() {
        Map<String, Object> attrs = new HashMap<>(Map.of(PRODUCT, "helicase"));

        GFF3Feature feature = TestUtils.createGFF3Feature("gene", "parent", attrs);

        fixer.fix(feature, 1);

        assertTrue(feature.containsAttribute(PRODUCT));
        assertNull(feature.getAttributeString(NOTE));
    }

    @Test
    void noChange_whenProductAbsent_evenIfExclusivePresent() {
        Map<String, Object> attrs = new HashMap<>(Map.of(PSEUDO, "true"));

        GFF3Feature feature = TestUtils.createGFF3Feature("gene", "parent", attrs);

        fixer.fix(feature, 1);

        assertTrue(feature.containsAttribute(PSEUDO));
        assertFalse(feature.containsAttribute(PRODUCT));
        assertNull(feature.getAttributeString(NOTE));
    }

    @Test
    void removesEmptyProduct_withoutAppending_whenExclusivePresent() {
        Map<String, Object> attrs = new HashMap<>(Map.of(
                PRODUCT, "",
                PSEUDOGENE, "unitary"));

        GFF3Feature feature = TestUtils.createGFF3Feature("gene", "parent", attrs);

        fixer.fix(feature, 1);

        assertFalse(feature.containsAttribute(PRODUCT));
        assertNull(feature.getAttributeString(NOTE));
    }

    @Test
    void handlesBothExclusivesPresent_gracefully() {
        Map<String, Object> attrs = new HashMap<>(Map.of(
                PRODUCT, "transferase",
                PSEUDO, "true",
                PSEUDOGENE, "unknown"));

        GFF3Feature feature = TestUtils.createGFF3Feature("gene", "parent", attrs);

        fixer.fix(feature, 1);

        assertFalse(feature.containsAttribute(PRODUCT));
        assertEquals("transferase", feature.getAttributeString(NOTE));
    }

    @Test
    void nullOrEmptyAttributes_areIgnoredWithoutExplosion() {
        GFF3Feature withNullAttributes = new GFF3Feature(
                Optional.of("gene"),
                Optional.empty(),
                "1234",
                Optional.empty(),
                ".",
                "gene",
                1,
                10,
                ".",
                "+",
                "",
                null);

        GFF3Feature withEmptyAttributes = new GFF3Feature(
                Optional.of("gene"),
                Optional.empty(),
                "1234",
                Optional.empty(),
                ".",
                "gene",
                1,
                10,
                ".",
                "+",
                "",
                new HashMap<>());

        assertDoesNotThrow(() -> fixer.fix(withNullAttributes, 1));
        assertDoesNotThrow(() -> fixer.fix(withEmptyAttributes, 1));
    }
}
