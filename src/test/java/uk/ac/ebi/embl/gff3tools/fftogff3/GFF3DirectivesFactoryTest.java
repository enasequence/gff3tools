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
package uk.ac.ebi.embl.gff3tools.fftogff3;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;
import uk.ac.ebi.embl.api.entry.qualifier.OrganismQualifier;
import uk.ac.ebi.embl.api.entry.qualifier.QualifierFactory;
import uk.ac.ebi.embl.gff3tools.exception.NoSourcePresentException;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Species;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;
import uk.ac.ebi.ena.taxonomy.taxon.TaxonFactory;

class GFF3DirectivesFactoryTest {

    private final GFF3DirectivesFactory factory = new GFF3DirectivesFactory();
    private final EntryFactory entryFactory = new EntryFactory();
    private final FeatureFactory featureFactory = new FeatureFactory();
    private final QualifierFactory qualifierFactory = new QualifierFactory();
    private final TaxonFactory taxonFactory = new TaxonFactory();

    private Entry entryWithOrganism(String name, Long taxId) {
        Entry entry = entryFactory.createEntry();
        SourceFeature source = featureFactory.createSourceFeature();
        OrganismQualifier organism = qualifierFactory.createOrganismQualifier(name);
        if (taxId != null) {
            Taxon taxon = taxonFactory.createTaxon();
            taxon.setTaxId(taxId);
            taxon.setScientificName(name);
            organism.setTaxon(taxon);
        }
        source.addQualifier(organism);
        entry.addFeature(source);
        return entry;
    }

    @Test
    void masterMetadataWithTaxonProducesIdBasedUrl() throws NoSourcePresentException {
        MasterMetadata meta = new MasterMetadata();
        meta.setTaxon("9606");

        GFF3Species species = factory.createSpecies(entryWithOrganism("Homo sapiens", 9606L), meta);

        assertEquals("https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=9606", species.species());
    }

    @Test
    void masterMetadataWithScientificNameOnlyProducesNameBasedUrl() throws NoSourcePresentException {
        MasterMetadata meta = new MasterMetadata();
        meta.setScientificName("Drosophila melanogaster");

        GFF3Species species = factory.createSpecies(entryWithOrganism("Homo sapiens", 9606L), meta);

        assertEquals(
                "https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?name=Drosophila melanogaster",
                species.species());
    }

    @Test
    void masterMetadataPrefersTaxonOverScientificName() throws NoSourcePresentException {
        MasterMetadata meta = new MasterMetadata();
        meta.setTaxon("7227");
        meta.setScientificName("Drosophila melanogaster");

        GFF3Species species = factory.createSpecies(entryWithOrganism("Homo sapiens", 9606L), meta);

        assertEquals("https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=7227", species.species());
    }

    @Test
    void nonNumericTaxonFallsBackToScientificName() throws NoSourcePresentException {
        MasterMetadata meta = new MasterMetadata();
        meta.setTaxon("not-a-number");
        meta.setScientificName("Homo sapiens");

        GFF3Species species = factory.createSpecies(entryWithOrganism("Drosophila melanogaster", 7227L), meta);

        assertEquals("https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?name=Homo sapiens", species.species());
    }

    /**
     * Regression: when MasterMetadata has neither scientificName nor taxon, the
     * factory must fall through to the entry's source feature instead of returning
     * a species directive with a null URL.
     */
    @Test
    void emptyMasterMetadataFallsBackToEntrySourceFeature() throws NoSourcePresentException {
        MasterMetadata meta = new MasterMetadata();

        GFF3Species species = factory.createSpecies(entryWithOrganism("Homo sapiens", 9606L), meta);

        assertEquals("https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=9606", species.species());
    }

    @Test
    void nullMasterMetadataReadsEntrySourceFeature() throws NoSourcePresentException {
        GFF3Species species = factory.createSpecies(entryWithOrganism("Homo sapiens", 9606L), null);

        assertEquals("https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=9606", species.species());
    }

    @Test
    void entrySourceFeatureWithoutTaxonUsesNameBasedUrl() throws NoSourcePresentException {
        GFF3Species species = factory.createSpecies(entryWithOrganism("Mus musculus", null), null);

        assertEquals("https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?name=Mus musculus", species.species());
    }

    @Test
    void missingSourceFeatureThrowsWhenNoMasterMetadata() {
        Entry entry = entryFactory.createEntry();

        assertThrows(NoSourcePresentException.class, () -> factory.createSpecies(entry, null));
    }
}
