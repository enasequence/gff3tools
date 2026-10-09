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
package uk.ac.ebi.embl.gff3tools.gff3.reader;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.provider.TaxonAccessionRegistry;
import uk.ac.ebi.embl.gff3tools.validation.provider.TaxonomyIdentifier;

class GFF3FileReaderTaxonTest {

    private ValidationEngine newValidationEngine() {
        return new ValidationEngineBuilder().build();
    }

    @Test
    void speciesDirectiveWithNcbiUrl_registersSequenceRegionAccession() throws Exception {
        String content = "##gff-version 3\n"
                + "##species https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=9662\n"
                + "##sequence-region OV277441.1 1 1000\n"
                + "OV277441.1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n";

        ValidationEngine validationEngine = newValidationEngine();
        try (GFF3FileReader reader =
                new GFF3FileReader(validationEngine, new StringReader(content), Path.of("input.gff3"))) {
            reader.readHeader();
            while (reader.readAnnotation() != null) {
                // drain
            }
        }

        TaxonAccessionRegistry registry = validationEngine.getContext().get(TaxonAccessionRegistry.class);
        TaxonomyIdentifier identifier = registry.find("OV277441.1").orElseThrow();
        assertInstanceOf(TaxonomyIdentifier.ByTaxId.class, identifier);
        assertEquals(9662L, ((TaxonomyIdentifier.ByTaxId) identifier).taxId());
    }

    @Test
    void sequenceRegionSeenBeforeAnySpecies_isNotRegistered() throws Exception {
        // ##species is expected to precede ##sequence-region/features in valid GFF3 (as ENA
        // submissions do); an accession with no feature lines after the species directive is
        // seen is simply left unresolved rather than backfilled.
        String content = "##gff-version 3\n"
                + "##sequence-region OV277441.1 1 1000\n"
                + "##species https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=9662\n";

        ValidationEngine validationEngine = newValidationEngine();
        try (GFF3FileReader reader =
                new GFF3FileReader(validationEngine, new StringReader(content), Path.of("input.gff3"))) {
            reader.readHeader();
            while (reader.readAnnotation() != null) {
                // drain
            }
        }

        TaxonAccessionRegistry registry = validationEngine.getContext().get(TaxonAccessionRegistry.class);
        assertTrue(registry.find("OV277441.1").isEmpty());
    }

    @Test
    void missingSequenceRegion_featureAccessionStillRegisteredDefensively() throws Exception {
        String content = "##gff-version 3\n"
                + "##species https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=9662\n"
                + "OV277441.1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n";

        ValidationEngine validationEngine = new ValidationEngineBuilder().build();
        assertDoesNotThrow(() -> {
            try (GFF3FileReader reader =
                    new GFF3FileReader(validationEngine, new StringReader(content), Path.of("input.gff3"))) {
                reader.readHeader();
                while (reader.readAnnotation() != null) {
                    // drain
                }
            }
        });

        TaxonAccessionRegistry registry = validationEngine.getContext().get(TaxonAccessionRegistry.class);
        TaxonomyIdentifier identifier = registry.find("OV277441.1").orElseThrow();
        assertEquals(9662L, ((TaxonomyIdentifier.ByTaxId) identifier).taxId());
    }

    @Test
    void bareNumericSpecies_registersSequenceRegionAccession() throws Exception {
        String content = "##gff-version 3\n"
                + "##species 9662\n"
                + "##sequence-region OV277441.1 1 1000\n"
                + "OV277441.1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n";

        ValidationEngine validationEngine = newValidationEngine();
        try (GFF3FileReader reader =
                new GFF3FileReader(validationEngine, new StringReader(content), Path.of("input.gff3"))) {
            reader.readHeader();
            while (reader.readAnnotation() != null) {
                // drain
            }
        }

        TaxonAccessionRegistry registry = validationEngine.getContext().get(TaxonAccessionRegistry.class);
        TaxonomyIdentifier identifier = registry.find("OV277441.1").orElseThrow();
        assertEquals(9662L, ((TaxonomyIdentifier.ByTaxId) identifier).taxId());
    }

    @Test
    void speciesChangeBetweenAccessions_doesNotCorruptEarlierAccession() throws Exception {
        String content = "##gff-version 3\n"
                + "##species https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=9606\n"
                + "##sequence-region AAA.1 1 1000\n"
                + "AAA.1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n"
                + "##species https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=562\n"
                + "##sequence-region BBB.1 1 1000\n"
                + "BBB.1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene2\n";

        ValidationEngine validationEngine = newValidationEngine();
        try (GFF3FileReader reader =
                new GFF3FileReader(validationEngine, new StringReader(content), Path.of("input.gff3"))) {
            reader.readHeader();
            while (reader.readAnnotation() != null) {
                // drain
            }
        }

        TaxonAccessionRegistry registry = validationEngine.getContext().get(TaxonAccessionRegistry.class);
        assertEquals(9606L, ((TaxonomyIdentifier.ByTaxId) registry.find("AAA.1").orElseThrow()).taxId());
        assertEquals(562L, ((TaxonomyIdentifier.ByTaxId) registry.find("BBB.1").orElseThrow()).taxId());
    }

    @Test
    void oversizedNumericSpecies_doesNotCrashAndLeavesRegistryUnchanged() throws Exception {
        String content = "##gff-version 3\n"
                + "##species 1234567890123456789012345\n"
                + "##sequence-region OV277441.1 1 1000\n"
                + "OV277441.1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n";

        ValidationEngine validationEngine = newValidationEngine();
        assertDoesNotThrow(() -> {
            try (GFF3FileReader reader =
                    new GFF3FileReader(validationEngine, new StringReader(content), Path.of("input.gff3"))) {
                reader.readHeader();
                while (reader.readAnnotation() != null) {
                    // drain
                }
            }
        });

        TaxonAccessionRegistry registry = validationEngine.getContext().get(TaxonAccessionRegistry.class);
        assertTrue(registry.find("OV277441.1").isEmpty());
    }

    @Test
    void noSpeciesDirective_registryUnchanged() throws Exception {
        String content = "##gff-version 3\n"
                + "##sequence-region OV277441.1 1 1000\n"
                + "OV277441.1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n";

        ValidationEngine validationEngine = newValidationEngine();
        try (GFF3FileReader reader =
                new GFF3FileReader(validationEngine, new StringReader(content), Path.of("input.gff3"))) {
            reader.readHeader();
            while (reader.readAnnotation() != null) {
                // drain
            }
        }

        TaxonAccessionRegistry registry = validationEngine.getContext().get(TaxonAccessionRegistry.class);
        assertTrue(registry.find("OV277441.1").isEmpty());
    }

    @Test
    void unparseableSpeciesValue_doesNotCrashOrRegister() throws Exception {
        String content = "##gff-version 3\n"
                + "##species some free text organism name\n"
                + "##sequence-region OV277441.1 1 1000\n"
                + "OV277441.1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n";

        ValidationEngine validationEngine = newValidationEngine();
        try (GFF3FileReader reader =
                new GFF3FileReader(validationEngine, new StringReader(content), Path.of("input.gff3"))) {
            reader.readHeader();
            while (reader.readAnnotation() != null) {
                // drain
            }
        }

        TaxonAccessionRegistry registry = validationEngine.getContext().get(TaxonAccessionRegistry.class);
        assertTrue(registry.find("OV277441.1").isEmpty());
    }

    @Test
    void speciesUrlWithAdditionalQueryParams_extractsTaxId() throws Exception {
        String content = "##gff-version 3\n"
                + "##species https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?mode=Info&id=9662&lvl=3\n"
                + "##sequence-region OV277441.1 1 1000\n"
                + "OV277441.1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n";

        ValidationEngine validationEngine = newValidationEngine();
        try (GFF3FileReader reader =
                new GFF3FileReader(validationEngine, new StringReader(content), Path.of("input.gff3"))) {
            reader.readHeader();
            while (reader.readAnnotation() != null) {
                // drain
            }
        }

        TaxonAccessionRegistry registry = validationEngine.getContext().get(TaxonAccessionRegistry.class);
        assertEquals(
                9662L, ((TaxonomyIdentifier.ByTaxId) registry.find("OV277441.1").orElseThrow()).taxId());
    }
}
