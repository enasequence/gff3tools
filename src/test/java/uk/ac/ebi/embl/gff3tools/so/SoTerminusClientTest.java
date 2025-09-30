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
package uk.ac.ebi.embl.gff3tools.so;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SoTerminusClientTest {

    private static SoTerminusClient soTerminusClient;

    @BeforeAll
    public static void setUp() {
        soTerminusClient = new SoTerminusClient();
    }

    @Test
    public void testIsFeatureSoTerm_ValidFeatureSoId() {
        // SO:0000316 represents "exon", which is a child of SO:0000110 "sequence_feature"
        assertTrue(soTerminusClient.isFeatureSoTerm("SO:0000316"));
    }

    @Test
    public void testIsFeatureSoTerm_ValidNonFeatureSoId() {
        // SO:0000000 is not a child of SO:0000110 "sequence_feature"
        assertFalse(soTerminusClient.isFeatureSoTerm("SO:0000000"));
    }

    @Test
    public void testIsFeatureSoTerm_InvalidSoIdFormat() {
        assertFalse(soTerminusClient.isFeatureSoTerm("SO:123"));
        assertFalse(soTerminusClient.isFeatureSoTerm("INVALID:0000123"));
        assertFalse(soTerminusClient.isFeatureSoTerm("SO:ABCDEFG"));
    }

    @Test
    public void testIsFeatureSoTerm_NonExistentSoId() {
        assertFalse(soTerminusClient.isFeatureSoTerm("SO:9999999"));
    }

    @Test
    public void testIsFeatureSoTerm_TermByName_Feature() {
        // "gene" is a feature SO term (SO:0000704)
        assertTrue(soTerminusClient.isFeatureSoTerm("gene"));
    }

    @Test
    public void testIsFeatureSoTerm_TermByName_NonFeature() {
        // "Sequence_Ontology" is not a feature SO term (SO:0001040)
        assertFalse(soTerminusClient.isFeatureSoTerm("Sequence_Ontology"));
    }

    @Test
    public void testIsFeatureSoTerm_TermBySynonym_Feature() {
        // "CDS" is a synonym for "coding_sequence" (SO:0000316), which is a feature
        assertTrue(soTerminusClient.isFeatureSoTerm("CDS"));
    }

    @Test
    public void testIsFeatureSoTerm_TermBySynonym_NonFeature() {
        // "centromeric_region" is a synonym for "centromere" (SO:0000577), which is not a feature
        assertFalse(soTerminusClient.isFeatureSoTerm("centromeric_region"));
    }

    @Test
    public void testIsFeatureSoTerm_NullInput() {
        assertFalse(soTerminusClient.isFeatureSoTerm(null));
    }

    @Test
    public void testIsFeatureSoTerm_EmptyInput() {
        assertFalse(soTerminusClient.isFeatureSoTerm(""));
    }

    @Test
    public void testIsFeatureSoTerm_CaseInsensitiveName() {
        // "Gene" (capitalized) should also work for "gene"
        assertTrue(soTerminusClient.isFeatureSoTerm("Gene"));
    }

    @Test
    public void testIsFeatureSoTerm_CaseInsensitiveSynonym() {
        // "cds" (lowercase) should also work for "CDS"
        assertTrue(soTerminusClient.isFeatureSoTerm("cds"));
    }

    @Test
    public void debugGeneAnnotations() {
        System.out.println("Debugging annotations for 'gene' (SO:0000704):");
        soTerminusClient.debugTermAnnotations("SO:0000704");
    }
}
