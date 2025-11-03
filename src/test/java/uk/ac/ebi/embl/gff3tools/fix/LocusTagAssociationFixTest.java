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
package uk.ac.ebi.embl.gff3tools.fix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

/**
 * Tests for LocusTagAssociationFix.
 *
 * Notes:
 *  - These tests assume streaming order matters (single fix instance reused).
 *  - First-seen gene→locus_tag mapping wins.
 *  - No conflict resolution, per GFF3 1:1 assumption.
 *  - Uses TestUtils to create features with attribute maps.
 */
public class LocusTagAssociationFixTest {

    private static final String GENE = GFF3Attributes.GENE;
    private static final String LOCUS_TAG = GFF3Attributes.LOCUS_TAG;

    @Test
    void propagatesLocusTagToLaterFeaturesWithSameGene() {
        LocusTagAssociationFix fix = new LocusTagAssociationFix();
        GFF3Feature f1 = TestUtils.createGFF3Feature("f1", new HashMap<>(Map.of(GENE, "geneX", LOCUS_TAG, "LT001")));
        GFF3Feature f2 = TestUtils.createGFF3Feature("f2", new HashMap<>(Map.of(GENE, "geneX")));

        fix.fix(f1, 1);
        fix.fix(f2, 2);

        assertEquals(List.of("LT001"), f2.getAttributeValueList(LOCUS_TAG));
        assertEquals("LT001", fix.mappingSnapshot().get("geneX"));
    }

    @Test
    void doesNotOverrideExistingLocusTag_FirstSeenWins() {
        LocusTagAssociationFix fix = new LocusTagAssociationFix();
        GFF3Feature a = TestUtils.createGFF3Feature("a", new HashMap<>(Map.of(GENE, "geneX", LOCUS_TAG, "LT001")));
        GFF3Feature b = TestUtils.createGFF3Feature("b", new HashMap<>(Map.of(GENE, "geneX", LOCUS_TAG, "LT999")));

        fix.fix(a, 1);
        fix.fix(b, 2);

        assertEquals(List.of("LT001"), b.getAttributeValueList(LOCUS_TAG)); // fixed to first seen
        assertEquals("LT001", fix.mappingSnapshot().get("geneX")); // first-seen mapping exists in fix memory
    }

    @Test
    void noChangeWhenNoGeneAttribute() {
        LocusTagAssociationFix fix = new LocusTagAssociationFix();
        GFF3Feature a = TestUtils.createGFF3Feature("a", new HashMap<>());

        fix.fix(a, 1);

        assertTrue(a.getAttributeValueList(LOCUS_TAG).isEmpty());
        assertTrue(fix.mappingSnapshot().isEmpty());
    }

    @Test
    void supportsMultipleGeneValues_UsesFirstNonBlank() {
        LocusTagAssociationFix fix = new LocusTagAssociationFix();

        Map<String, Object> attrs1 = new HashMap<>();
        attrs1.put(GENE, List.of("geneA", "geneB"));
        attrs1.put(LOCUS_TAG, "LT_A");
        GFF3Feature f1 = TestUtils.createGFF3Feature("f1", attrs1);

        Map<String, Object> attrs2 = new HashMap<>();
        attrs2.put(GENE, List.of("geneA", "geneC"));
        GFF3Feature f2 = TestUtils.createGFF3Feature("f2", attrs2);

        fix.fix(f1, 1);
        fix.fix(f2, 2);

        assertEquals(List.of("LT_A"), f2.getAttributeValueList(LOCUS_TAG));
        assertEquals("LT_A", fix.mappingSnapshot().get("geneA"));
    }
}
