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
package uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ControlledVocabularyUtilsTest {
    @Test
    void normaliseMolTypeReturnsEnumForControlledVocabularyValue() {
        FastaHeader header = new FastaHeader();
        header.setMoleculeType("genomic DNA");

        Optional<ControlledVocabularyUtils.MolType> molType = ControlledVocabularyUtils.normaliseMolType(header);

        assertEquals(Optional.of(ControlledVocabularyUtils.MolType.GENOMIC_DNA), molType);
    }

    @Test
    void normaliseMolTypeReturnsEmptyForUnknownValue() {
        FastaHeader header = new FastaHeader();
        header.setMoleculeType("DNA");

        Optional<ControlledVocabularyUtils.MolType> molType = ControlledVocabularyUtils.normaliseMolType(header);

        assertTrue(molType.isEmpty());
    }

    @Test
    void normaliseMolTypeReturnsEmptyForNullHeader() {
        Optional<ControlledVocabularyUtils.MolType> molType = ControlledVocabularyUtils.normaliseMolType(null);

        assertTrue(molType.isEmpty());
    }

    @Test
    void molTypeExposesOriginalControlledVocabularyValue() {
        assertEquals("viral cRNA", ControlledVocabularyUtils.MolType.VIRAL_CRNA.getValue());
    }

    @Test
    void normaliseTopologyReturnsEnumForControlledVocabularyValue() {
        FastaHeader header = new FastaHeader();
        header.setTopology("circular");

        Optional<ControlledVocabularyUtils.Topology> topology = ControlledVocabularyUtils.normaliseTopology(header);

        assertEquals(Optional.of(ControlledVocabularyUtils.Topology.CIRCULAR), topology);
    }

    @Test
    void normaliseTopologyReturnsEmptyForUnknownValue() {
        FastaHeader header = new FastaHeader();
        header.setTopology("triangle");

        Optional<ControlledVocabularyUtils.Topology> topology = ControlledVocabularyUtils.normaliseTopology(header);

        assertTrue(topology.isEmpty());
    }

    @Test
    void normaliseTopologyReturnsEmptyForNullHeader() {
        Optional<ControlledVocabularyUtils.Topology> topology = ControlledVocabularyUtils.normaliseTopology(null);

        assertTrue(topology.isEmpty());
    }

    @Test
    void topologyExposesOriginalControlledVocabularyValue() {
        assertEquals("linear", ControlledVocabularyUtils.Topology.LINEAR.getValue());
    }

    @Test
    void normaliseChromosomeTypeReturnsEnumForControlledVocabularyValue() {
        FastaHeader header = new FastaHeader();
        header.setChromosomeType("linkage_group");

        Optional<ControlledVocabularyUtils.ChromosomeType> chromosomeType =
                ControlledVocabularyUtils.normaliseChromosomeType(header);

        assertEquals(Optional.of(ControlledVocabularyUtils.ChromosomeType.LINKAGE_GROUP), chromosomeType);
    }

    @Test
    void normaliseChromosomeTypeReturnsEmptyForUnknownValue() {
        FastaHeader header = new FastaHeader();
        header.setChromosomeType("chromosome arm");

        Optional<ControlledVocabularyUtils.ChromosomeType> chromosomeType =
                ControlledVocabularyUtils.normaliseChromosomeType(header);

        assertTrue(chromosomeType.isEmpty());
    }

    @Test
    void normaliseChromosomeTypeReturnsEmptyForNullHeader() {
        Optional<ControlledVocabularyUtils.ChromosomeType> chromosomeType =
                ControlledVocabularyUtils.normaliseChromosomeType(null);

        assertTrue(chromosomeType.isEmpty());
    }

    @Test
    void chromosomeTypeExposesOriginalControlledVocabularyValue() {
        assertEquals("multipartite", ControlledVocabularyUtils.ChromosomeType.MULTIPARTITE.getValue());
    }

    @Test
    void normaliseChromosomeLocationReturnsEnumForControlledVocabularyValue() {
        FastaHeader header = new FastaHeader();
        header.setChromosomeLocation("Mitochondrion");

        Optional<ControlledVocabularyUtils.ChromosomeLocation> chromosomeLocation =
                ControlledVocabularyUtils.normaliseChromosomeLocation(header);

        assertEquals(Optional.of(ControlledVocabularyUtils.ChromosomeLocation.MITOCHONDRION), chromosomeLocation);
    }

    @Test
    void normaliseChromosomeLocationReturnsEmptyForUnknownValue() {
        FastaHeader header = new FastaHeader();
        header.setChromosomeLocation("mars");

        Optional<ControlledVocabularyUtils.ChromosomeLocation> chromosomeLocation =
                ControlledVocabularyUtils.normaliseChromosomeLocation(header);

        assertTrue(chromosomeLocation.isEmpty());
    }

    @Test
    void normaliseChromosomeLocationReturnsEmptyForNullHeader() {
        Optional<ControlledVocabularyUtils.ChromosomeLocation> chromosomeLocation =
                ControlledVocabularyUtils.normaliseChromosomeLocation(null);

        assertTrue(chromosomeLocation.isEmpty());
    }

    @Test
    void chromosomeLocationExposesOriginalControlledVocabularyValue() {
        assertEquals("Chromatophore", ControlledVocabularyUtils.ChromosomeLocation.CHROMATOPHORE.getValue());
    }

    @Test
    void canonicaliseMatchesIgnoringCase() {
        assertEquals(
                Optional.of("genomic DNA"),
                ControlledVocabularyUtils.canonicalise(ControlledVocabularyUtils.MolType.class, "GENOMIC DNA"));
        assertEquals(
                Optional.of("linear"),
                ControlledVocabularyUtils.canonicalise(ControlledVocabularyUtils.Topology.class, "Linear"));
        assertEquals(
                Optional.of("Mitochondrion"),
                ControlledVocabularyUtils.canonicalise(
                        ControlledVocabularyUtils.ChromosomeLocation.class, "mitochondrion"));
    }

    @Test
    void canonicaliseTrimsEdgeWhitespace() {
        assertEquals(
                Optional.of("circular"),
                ControlledVocabularyUtils.canonicalise(ControlledVocabularyUtils.Topology.class, "  circular\t"));
    }

    @Test
    void canonicaliseTransformsDashToUnderscore() {
        assertEquals(
                Optional.of("linkage_group"),
                ControlledVocabularyUtils.canonicalise(
                        ControlledVocabularyUtils.ChromosomeType.class, "Linkage-Group"));
    }

    @Test
    void canonicaliseReturnsEmptyForUnknownValue() {
        assertTrue(ControlledVocabularyUtils.canonicalise(ControlledVocabularyUtils.Topology.class, "triangle")
                .isEmpty());
    }

    @Test
    void canonicaliseDoesNotTreatSpaceAsUnderscore() {
        // Only dashes are converted to underscores; "linkage group" stays unmatched.
        assertTrue(
                ControlledVocabularyUtils.canonicalise(ControlledVocabularyUtils.ChromosomeType.class, "linkage group")
                        .isEmpty());
    }

    @Test
    void canonicaliseReturnsEmptyForNullValue() {
        assertTrue(ControlledVocabularyUtils.canonicalise(ControlledVocabularyUtils.MolType.class, null)
                .isEmpty());
    }
}
