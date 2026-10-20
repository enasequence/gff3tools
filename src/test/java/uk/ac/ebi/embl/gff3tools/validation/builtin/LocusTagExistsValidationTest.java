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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;
import uk.ac.ebi.embl.gff3tools.validation.provider.TaxonProvider;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;
import uk.ac.ebi.ena.taxonomy.taxon.TaxonFactory;

class LocusTagExistsValidationTest {

    private static final AnalysisType ASSEMBLY = AnalysisType.SEQUENCE_ASSEMBLY;

    private static final String LOCUS_TAG_EXISTS_MESSAGE =
            "/locus_tag must exist for annotated contigs/scaffolds/chromosomes.";

    private static final String VIRUS_LINEAGE = "Viruses; Riboviria; Orthornavirae; Pisuviricota.";
    private static final String EUKARYOTE_LINEAGE = "Eukaryota; Metazoa; Chordata; Homo.";
    private static final String PROKARYOTE_LINEAGE = "Bacteria; Pseudomonadati; Pseudomonadota; Escherichia.";

    private LocusTagExistsValidation locusTagExistsValidation;

    private GFF3Annotation gff3Annotation;

    @BeforeEach
    void setUp() {
        gff3Annotation = new GFF3Annotation();
        locusTagExistsValidation = new LocusTagExistsValidation();
    }

    private GFF3Feature gene() {
        return TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), OntologyTerm.GENE.name(), Map.of());
    }

    private GFF3Feature feature(String name) {
        return TestUtils.createGFF3Feature(name, name, Map.of());
    }

    private Executable validation(AnalysisType analysisType, GFF3Feature... features) {
        return validation(analysisType, (String) null, features);
    }

    private Executable validation(AnalysisType analysisType, String moleculeType, GFF3Feature... features) {
        return validation(analysisType, moleculeType, (Taxon) null, features);
    }

    /**
     * Registers a {@link TaxonProvider} returning a real {@link Taxon} rather than a mock, so the
     * SDK's own {@code isChildOf} matching is exercised instead of stubbed. A non-null taxon with a
     * null lineage stands for an organism that resolves but places nowhere.
     */
    private Executable validation(
            AnalysisType analysisType, String moleculeType, Taxon taxon, GFF3Feature... features) {
        ValidationContext context = TestUtils.createTestContext();

        if (taxon != null) {
            context.register(TaxonProvider.class, new TaxonProvider() {
                @Override
                public Optional<Taxon> resolve(String accession) {
                    return Optional.of(taxon);
                }

                @Override
                public TaxonProvider get(ValidationContext ctx) {
                    return this;
                }
            });
        }

        if (analysisType != null) {
            context.register(
                    AnalysisContext.class, provider(AnalysisContext.class, new AnalysisContext(analysisType, 10)));
        }
        if (moleculeType != null) {
            FastaHeader header = new FastaHeader();
            header.setMoleculeType(moleculeType);
            FastaHeaderProvider headerProvider = new FastaHeaderProvider();
            headerProvider.addSource(seqId -> Optional.of(header));
            context.register(FastaHeaderProvider.class, headerProvider);
        }

        TestUtils.injectContext(locusTagExistsValidation, context);
        gff3Annotation.setFeatures(List.of(features));
        return () -> locusTagExistsValidation.validateLocusTagExists(gff3Annotation, 1);
    }

    private <T> ContextProvider<T> provider(Class<T> type, T value) {
        return new ContextProvider<>() {
            @Override
            public T get(ValidationContext ctx) {
                return value;
            }

            @Override
            public Class<T> type() {
                return type;
            }
        };
    }

    private void assertViolation(Executable validation) {
        ValidationException ex = Assertions.assertThrows(ValidationException.class, validation);
        Assertions.assertTrue(ex.getMessage().contains(LOCUS_TAG_EXISTS_MESSAGE), ex.getMessage());
        Assertions.assertEquals(LocusTagExistsValidation.LOCUS_TAG_EXISTS_RULE, ex.getValidationRule());
    }

    @Test
    void testAnnotatedGenomeWithoutLocusTagFails() {
        assertViolation(validation(ASSEMBLY, gene()));
    }

    @Test
    void testAnnotatedGenomeWithLocusTagSucceeds() {
        GFF3Feature tagged = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LOCUS_0001")));

        Assertions.assertDoesNotThrow(validation(ASSEMBLY, tagged));
    }

    @Test
    void testLocusTagWithBlankValueFails() {
        GFF3Feature blank = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(), OntologyTerm.GENE.name(), Map.of(GFF3Attributes.LOCUS_TAG, List.of(" ")));

        assertViolation(validation(ASSEMBLY, blank));
    }

    @ParameterizedTest
    @EnumSource(
            value = AnalysisType.class,
            names = {"SEQUENCE_ASSEMBLY"},
            mode = EnumSource.Mode.EXCLUDE)
    void testOtherAnalysisTypesSucceed(AnalysisType analysisType) {
        Assertions.assertDoesNotThrow(validation(analysisType, gene()));
    }

    @Test
    void testWithoutAnalysisTypeSucceeds() {
        Assertions.assertDoesNotThrow(validation(null, gene()));
    }

    @Test
    void testVirusSucceeds() {
        Assertions.assertDoesNotThrow(validation(ASSEMBLY, null, taxon(VIRUS_LINEAGE), gene()));
    }

    @Test
    void testEukaryoteFails() {
        assertViolation(validation(ASSEMBLY, null, taxon(EUKARYOTE_LINEAGE), gene()));
    }

    @Test
    void testProkaryoteFails() {
        assertViolation(validation(ASSEMBLY, null, taxon(PROKARYOTE_LINEAGE), gene()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"repeat_region", "tandem_repeat", "sequence_feature", "biological_region", "sequence_comparison"
            })
    void testExemptFeatureSucceeds(String featureName) {
        Assertions.assertDoesNotThrow(validation(ASSEMBLY, gene(), feature(featureName)));
    }

    @Test
    void testWithoutAnnotationSucceeds() {
        Assertions.assertDoesNotThrow(validation(ASSEMBLY, feature(OntologyTerm.REGION.name()), feature("gap")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"genomic DNA", "genomic RNA", "viral cRNA", "other DNA", "other RNA"})
    void testAssemblyMoleculeTypeStillFails(String moleculeType) {
        assertViolation(validation(ASSEMBLY, moleculeType, gene()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mRNA", "rRNA", "tRNA", "transcribed RNA"})
    void testTranscriptMoleculeTypeSucceeds(String moleculeType) {
        Assertions.assertDoesNotThrow(validation(ASSEMBLY, moleculeType, gene()));
    }

    @Test
    void testUnrecognisedMoleculeTypeLeavesRuleUnchanged() {
        assertViolation(validation(ASSEMBLY, "not a molecule type", gene()));
    }

    @Test
    void testAbsentMoleculeTypeLeavesRuleUnchanged() {
        assertViolation(validation(ASSEMBLY, (String) null, gene()));
    }

    private Taxon taxon(String lineage) {
        Taxon taxon = new TaxonFactory().createTaxon();
        taxon.setLineage(lineage);
        return taxon;
    }

    /**
     * {@code Taxon.isChildOf} matches any element of the lineage, not just the first. Nothing in
     * either repo records a real taxonomy-REST lineage, so the rule must not assume the domain leads.
     */
    @Test
    void testTaxonProviderVirusSucceedsWhenDomainDoesNotLeadTheLineage() {
        Assertions.assertDoesNotThrow(
                validation(ASSEMBLY, null, taxon("cellular organisms; Viruses; Riboviria."), gene()));
    }

    /** A taxon that resolves without a lineage places the organism nowhere, so the rule still fires. */
    @Test
    void testTaxonWithNoLineageFails() {
        assertViolation(validation(ASSEMBLY, null, taxon(null), gene()));
    }
}
