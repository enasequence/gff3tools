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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;
import uk.ac.ebi.embl.gff3tools.validation.provider.TaxonProvider;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;
import uk.ac.ebi.ena.taxonomy.taxon.TaxonFactory;

class ProkaryotePartialLocationValidationTest {

    private static final String SEQ_ID = TestUtils.DEFAULT_ACCESSION;
    private static final long SEQUENCE_LENGTH = 1000L;

    private static final String BACTERIA_LINEAGE = "Bacteria; Pseudomonadati; Pseudomonadota; Escherichia.";
    private static final String ARCHAEA_LINEAGE = "Archaea; Methanobacteriota; Methanobacteria.";
    private static final String EUKARYOTE_LINEAGE = "Eukaryota; Metazoa; Chordata; Homo.";

    private ProkaryotePartialLocationValidation validation;

    private SequenceLookup lookup;

    @BeforeEach
    void setUp() throws Exception {
        validation = new ProkaryotePartialLocationValidation();
        lookup = mock(SequenceLookup.class);
        when(lookup.getSequenceLength(SEQ_ID)).thenReturn(SEQUENCE_LENGTH);
    }

    @Test
    void startPartialWithinTwoBasesOfSequenceStartSuccess() {
        GFF3Feature cds = partialCds(3, 300, "+", "start");
        assertDoesNotThrow(validation(prokaryote(), cds));
    }

    @Test
    void startPartialThreeBasesFromSequenceStartFails() {
        GFF3Feature cds = partialCds(4, 300, "+", "start");
        ValidationException ex = assertThrows(ValidationException.class, validation(prokaryote(), cds));
        assertEquals(ProkaryotePartialLocationValidation.PROKARYOTE_PARTIAL_LOCATION_RULE, ex.getValidationRule());
        assertTrue(ex.getMessage().contains("is partial at base 4"));
    }

    @Test
    void endPartialWithinTwoBasesOfSequenceEndSuccess() {
        GFF3Feature cds = partialCds(700, 998, "+", "end");
        assertDoesNotThrow(validation(prokaryote(), cds));
    }

    @Test
    void endPartialThreeBasesFromSequenceEndFails() {
        GFF3Feature cds = partialCds(700, 997, "+", "end");
        ValidationException ex = assertThrows(ValidationException.class, validation(prokaryote(), cds));
        assertTrue(ex.getMessage().contains("is partial at base 997"));
    }

    @Test
    void startPartialTwoBasesAfterGapSuccess() throws Exception {
        when(lookup.getGapRegions(SEQ_ID, 100L, 102L)).thenReturn(List.of(new GapRegion(90L, 100L)));
        GFF3Feature cds = partialCds(103, 300, "+", "start");
        assertDoesNotThrow(validation(prokaryote(), cds));
    }

    @Test
    void endPartialTwoBasesBeforeGapSuccess() throws Exception {
        when(lookup.getGapRegions(SEQ_ID, 501L, 503L)).thenReturn(List.of(new GapRegion(503L, 520L)));
        GFF3Feature cds = partialCds(300, 500, "+", "end");
        assertDoesNotThrow(validation(prokaryote(), cds));
    }

    @Test
    void startPartialNextToShortNRunFails() throws Exception {
        when(lookup.getGapRegions(SEQ_ID, 98L, 100L)).thenReturn(List.of(new GapRegion(95L, 100L)));
        GFF3Feature cds = partialCds(101, 300, "+", "start");
        assertThrows(ValidationException.class, validation(prokaryote(), cds));
    }

    @Test
    void minGapSizeIsTakenFromAnalysisContext() throws Exception {
        when(lookup.getGapRegions(SEQ_ID, 98L, 100L)).thenReturn(List.of(new GapRegion(95L, 100L)));
        GFF3Feature cds = partialCds(101, 300, "+", "start");
        ValidationContext context = context(prokaryote());
        TestUtils.registerProvider(context, AnalysisContext.class, new AnalysisContext(AnalysisType.UNKNOWN, 5));
        assertDoesNotThrow(validation(context, cds));
    }

    /** partial=start always means the lower coordinate, even on the minus strand. */
    @Test
    void startPartialOnMinusStrandChecksLowerCoordinate() {
        GFF3Feature cds = partialCds(400, 998, "-", "start");
        ValidationException ex = assertThrows(ValidationException.class, validation(prokaryote(), cds));
        assertTrue(ex.getMessage().contains("is partial at base 400"));
    }

    @Test
    void featureWithoutPartialSuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature("CDS1", OntologyTerm.CDS.name(), SEQ_ID, 400, 600, "+");
        assertDoesNotThrow(validation(prokaryote(), cds));
    }

    @Test
    void archaeaIsCheckedAsProkaryote() {
        GFF3Feature cds = partialCds(400, 600, "+", "end");
        assertThrows(ValidationException.class, validation(taxonProvider(taxon(ARCHAEA_LINEAGE)), cds));
    }

    @Test
    void eukaryoteIsNotChecked() {
        GFF3Feature cds = partialCds(400, 600, "+", "start", "end");
        assertDoesNotThrow(validation(taxonProvider(taxon(EUKARYOTE_LINEAGE)), cds));
    }

    @Test
    void unresolvedOrganismIsNotChecked() {
        GFF3Feature cds = partialCds(400, 600, "+", "start", "end");
        assertDoesNotThrow(validation(taxonProvider(null), cds));
    }

    @Test
    void noTaxonProviderIsNotChecked() {
        GFF3Feature cds = partialCds(400, 600, "+", "start", "end");
        assertDoesNotThrow(validation((TaxonProvider) null, cds));
    }

    @Test
    void noSequenceLookupIsNotChecked() {
        GFF3Feature cds = partialCds(400, 600, "+", "start", "end");
        ValidationContext context = TestUtils.createTestContext();
        context.register(TaxonProvider.class, prokaryote());
        assertDoesNotThrow(validation(context, cds));
    }

    @Test
    void everyViolationIsReportedInOneMessage() {
        GFF3Feature first = partialCds(100, 200, "+", "start", "end");
        GFF3Feature second = partialCds(400, 600, "+", "end");
        ValidationException ex = assertThrows(ValidationException.class, validation(prokaryote(), first, second));
        assertTrue(ex.getMessage().contains("is partial at base 100"));
        assertTrue(ex.getMessage().contains("is partial at base 200"));
        assertTrue(ex.getMessage().contains("is partial at base 600"));
    }

    private GFF3Feature partialCds(long start, long end, String strand, String... partials) {
        GFF3Feature cds = TestUtils.createGFF3Feature("CDS1", OntologyTerm.CDS.name(), SEQ_ID, start, end, strand);
        for (String partial : partials) {
            cds.addAttribute(GFF3Attributes.PARTIAL, partial);
        }
        return cds;
    }

    private Executable validation(TaxonProvider taxonProvider, GFF3Feature... features) {
        return validation(context(taxonProvider), features);
    }

    private Executable validation(ValidationContext context, GFF3Feature... features) {
        TestUtils.injectContext(validation, context);
        return () -> validation.validatePartialLocation(
                TestUtils.createGFF3Annotation(SEQ_ID, 1, SEQUENCE_LENGTH, features), 1);
    }

    /** A context with the mocked sequence; a null taxonProvider registers none. */
    private ValidationContext context(TaxonProvider taxonProvider) {
        ValidationContext context = TestUtils.createTestContext();
        TestUtils.registerProvider(context, SequenceLookup.class, lookup);
        if (taxonProvider != null) {
            context.register(TaxonProvider.class, taxonProvider);
        }
        return context;
    }

    private TaxonProvider prokaryote() {
        return taxonProvider(taxon(BACTERIA_LINEAGE));
    }

    /** Resolves every accession to taxon, or to nothing when it is null. */
    private TaxonProvider taxonProvider(Taxon taxon) {
        return new TaxonProvider() {
            @Override
            public Optional<Taxon> resolve(String accession) {
                return Optional.ofNullable(taxon);
            }

            @Override
            public TaxonProvider get(ValidationContext ctx) {
                return this;
            }
        };
    }

    private Taxon taxon(String lineage) {
        Taxon taxon = new TaxonFactory().createTaxon();
        taxon.setLineage(lineage);
        return taxon;
    }
}
