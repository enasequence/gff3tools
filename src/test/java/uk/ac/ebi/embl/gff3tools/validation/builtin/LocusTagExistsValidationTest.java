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
import java.util.Set;
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
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils.MolType;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;
import uk.ac.ebi.embl.gff3tools.validation.provider.LocusTagProvider;
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

    /** The organism for tests that are not about the organism: resolvable, and not a virus. */
    private static final String DEFAULT_LINEAGE = "Archaea; Methanobacteriota; Methanobacteria.";

    private LocusTagExistsValidation locusTagExistsValidation;

    private GFF3Annotation gff3Annotation;

    private GFF3Feature feature;

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
        return validation(analysisType, moleculeType, taxonProvider(taxon(DEFAULT_LINEAGE)), features);
    }

    /** A {@code null} {@code taxonProvider} registers none, as in client-side processing. */
    private Executable validation(
            AnalysisType analysisType, String moleculeType, TaxonProvider taxonProvider, GFF3Feature... features) {
        ValidationContext context = TestUtils.createTestContext();

        if (taxonProvider != null) {
            context.register(TaxonProvider.class, taxonProvider);
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

    /**
     * A {@link TaxonProvider} resolving every accession to {@code taxon}, or to nothing when it is
     * {@code null}. It returns a real {@link Taxon} rather than a mock, so the SDK's own
     * {@code isChildOf} matching is exercised instead of stubbed.
     */
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
        Assertions.assertDoesNotThrow(validation(ASSEMBLY, null, taxonProvider(taxon(VIRUS_LINEAGE)), gene()));
    }

    @Test
    void testEukaryoteFails() {
        assertViolation(validation(ASSEMBLY, null, taxonProvider(taxon(EUKARYOTE_LINEAGE)), gene()));
    }

    @Test
    void testProkaryoteFails() {
        assertViolation(validation(ASSEMBLY, null, taxonProvider(taxon(PROKARYOTE_LINEAGE)), gene()));
    }

    /** Without taxonomy the organism cannot be ruled a non-virus, so the rule does not fire. */
    @Test
    void testWithoutTaxonProviderSucceeds() {
        Assertions.assertDoesNotThrow(validation(ASSEMBLY, null, (TaxonProvider) null, gene()));
    }

    /** A lookup that finds nothing, including a taxonomy service failure, also skips the rule. */
    @Test
    void testUnresolvedTaxonSucceeds() {
        Assertions.assertDoesNotThrow(validation(ASSEMBLY, null, taxonProvider(null), gene()));
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

    /**
     * Like {@code sequencetools}' {@code LocustagExistsCheck}, the rule places no condition on the
     * molecule type: whether a molecule type is valid for an assembly is
     * {@code MOLECULE_TYPE_ANALYSIS_TYPE}'s concern.
     */
    @ParameterizedTest
    @EnumSource(MolType.class)
    void testMoleculeTypeDoesNotAffectRule(MolType moleculeType) {
        assertViolation(validation(ASSEMBLY, moleculeType.getValue(), gene()));
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
                validation(ASSEMBLY, null, taxonProvider(taxon("cellular organisms; Viruses; Riboviria.")), gene()));
    }

    /** A taxon that resolves without a lineage places the organism nowhere, so the rule still fires. */
    @Test
    void testTaxonWithNoLineageFails() {
        assertViolation(validation(ASSEMBLY, null, taxonProvider(taxon(null)), gene()));
    }

    // --- locus_tag prefix format tests ---

    @Test
    void testPrefixValidFormat() {
        // GPNCGGLF = 8 chars, starts with letter — from a real EMBL-to-GFF3 conversion
        feature = TestUtils.createGFF3Feature("mRNA", Map.of(GFF3Attributes.LOCUS_TAG, List.of("GPNCGGLF_LOCUS1")));

        Assertions.assertDoesNotThrow(() -> locusTagExistsValidation.validateLocusTagPrefix(feature, 1));
    }

    @Test
    void testPrefixMinLength() {
        feature = TestUtils.createGFF3Feature("mRNA", Map.of(GFF3Attributes.LOCUS_TAG, List.of("ABC_001")));

        Assertions.assertDoesNotThrow(() -> locusTagExistsValidation.validateLocusTagPrefix(feature, 1));
    }

    @Test
    void testPrefixMaxLength() {

        feature = TestUtils.createGFF3Feature("mRNA", Map.of(GFF3Attributes.LOCUS_TAG, List.of("ABCDEFGHIJKL_001")));

        Assertions.assertDoesNotThrow(() -> locusTagExistsValidation.validateLocusTagPrefix(feature, 1));
    }

    @Test
    void testPrefixWithDigits() {

        feature = TestUtils.createGFF3Feature("mRNA", Map.of(GFF3Attributes.LOCUS_TAG, List.of("BC03BB108_2133")));

        Assertions.assertDoesNotThrow(() -> locusTagExistsValidation.validateLocusTagPrefix(feature, 1));
    }

    @Test
    void testPrefixNoUnderscore() {

        feature = TestUtils.createGFF3Feature("mRNA", Map.of(GFF3Attributes.LOCUS_TAG, List.of("GPNCGGLF")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> locusTagExistsValidation.validateLocusTagPrefix(feature, 1));

        Assertions.assertTrue(ex.getMessage().contains("must be in format PREFIX_tag"));
        Assertions.assertEquals(LocusTagExistsValidation.LOCUS_TAG_PREFIX_RULE, ex.getValidationRule());
    }

    @Test
    void testPrefixTooShort() {
        feature = TestUtils.createGFF3Feature("mRNA", Map.of(GFF3Attributes.LOCUS_TAG, List.of("AB_001")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> locusTagExistsValidation.validateLocusTagPrefix(feature, 1));

        Assertions.assertTrue(ex.getMessage().contains("must be 3-12 alphanumeric characters starting with a letter"));
        Assertions.assertEquals(LocusTagExistsValidation.LOCUS_TAG_PREFIX_RULE, ex.getValidationRule());
    }

    @Test
    void testPrefixTooLong() {
        feature = TestUtils.createGFF3Feature("mRNA", Map.of(GFF3Attributes.LOCUS_TAG, List.of("ABCDEFGHIJKLM_001")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> locusTagExistsValidation.validateLocusTagPrefix(feature, 1));

        Assertions.assertTrue(ex.getMessage().contains("must be 3-12 alphanumeric characters starting with a letter"));
        Assertions.assertEquals(LocusTagExistsValidation.LOCUS_TAG_PREFIX_RULE, ex.getValidationRule());
    }

    @Test
    void testPrefixStartsWithDigit() {

        feature = TestUtils.createGFF3Feature("mRNA", Map.of(GFF3Attributes.LOCUS_TAG, List.of("1ABCDEF_001")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> locusTagExistsValidation.validateLocusTagPrefix(feature, 1));

        Assertions.assertTrue(ex.getMessage().contains("must be 3-12 alphanumeric characters starting with a letter"));
        Assertions.assertEquals(LocusTagExistsValidation.LOCUS_TAG_PREFIX_RULE, ex.getValidationRule());
    }

    @Test
    void testPrefixEmptyTag() {

        feature = TestUtils.createGFF3Feature("mRNA", Map.of(GFF3Attributes.LOCUS_TAG, List.of("GPNCGGLF_")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> locusTagExistsValidation.validateLocusTagPrefix(feature, 1));

        Assertions.assertTrue(ex.getMessage().contains("tag after underscore must not be empty"));
        Assertions.assertEquals(LocusTagExistsValidation.LOCUS_TAG_PREFIX_RULE, ex.getValidationRule());
    }

    // --- locus_tag registered prefix tests ---

    private static LocusTagProvider prefixProvider(Set<String> prefixes) {
        return new LocusTagProvider() {
            @Override
            public Set<String> getPrefixes() {
                return prefixes;
            }

            @Override
            public LocusTagProvider get(ValidationContext ctx) {
                return this;
            }
        };
    }

    @Test
    void testRegisteredLocusTagPrefix_noProviderRegistered_ruleInert() {
        gff3Annotation
                .getFeatures()
                .add(TestUtils.createGFF3Feature("gene", Map.of(GFF3Attributes.LOCUS_TAG, List.of("ANYPREFIX_001"))));
        ValidationContext context = TestUtils.createTestContext();
        TestUtils.injectContext(locusTagExistsValidation, context);
        Assertions.assertDoesNotThrow(
                () -> locusTagExistsValidation.validateRegisteredLocusTagPrefix(gff3Annotation, 1));
    }

    @Test
    void testRegisteredLocusTagPrefix_registeredPrefix_passes() {
        gff3Annotation
                .getFeatures()
                .add(TestUtils.createGFF3Feature("gene", Map.of(GFF3Attributes.LOCUS_TAG, List.of("MYPFX_001"))));
        ValidationContext context = TestUtils.createTestContext();
        context.register(LocusTagProvider.class, prefixProvider(Set.of("MYPFX")));
        TestUtils.injectContext(locusTagExistsValidation, context);
        Assertions.assertDoesNotThrow(
                () -> locusTagExistsValidation.validateRegisteredLocusTagPrefix(gff3Annotation, 1));
    }

    @Test
    void testRegisteredLocusTagPrefix_unregisteredPrefix_throwsWithCorrectRule() {
        gff3Annotation
                .getFeatures()
                .add(TestUtils.createGFF3Feature("gene", Map.of(GFF3Attributes.LOCUS_TAG, List.of("WRONG_001"))));
        ValidationContext context = TestUtils.createTestContext();
        context.register(LocusTagProvider.class, prefixProvider(Set.of("MYPFX")));
        TestUtils.injectContext(locusTagExistsValidation, context);
        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> locusTagExistsValidation.validateRegisteredLocusTagPrefix(gff3Annotation, 1));
        Assertions.assertEquals(LocusTagExistsValidation.LOCUS_TAG_REGISTERED_PREFIX_RULE, ex.getValidationRule());
        Assertions.assertTrue(ex.getMessage().contains("WRONG"), ex.getMessage());
    }

    @Test
    void testRegisteredLocusTagPrefix_InvalidFormatCaptureEarly() {
        gff3Annotation
                .getFeatures()
                .add(TestUtils.createGFF3Feature("gene", Map.of(GFF3Attributes.LOCUS_TAG, List.of("WRONG_001"))));
        ValidationContext context = TestUtils.createTestContext();
        context.register(LocusTagProvider.class, prefixProvider(Set.of("MYPFX")));
        TestUtils.injectContext(locusTagExistsValidation, context);
        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> locusTagExistsValidation.validateRegisteredLocusTagPrefix(gff3Annotation, 1));
        Assertions.assertEquals(LocusTagExistsValidation.LOCUS_TAG_REGISTERED_PREFIX_RULE, ex.getValidationRule());
        Assertions.assertTrue(ex.getMessage().contains("WRONG"), ex.getMessage());
    }

    @Test
    void testRegisteredLocusTagPrefix_caseInsensitiveMatch() {
        gff3Annotation
                .getFeatures()
                .add(TestUtils.createGFF3Feature("gene", Map.of(GFF3Attributes.LOCUS_TAG, List.of("mypfx_001"))));
        ValidationContext context = TestUtils.createTestContext();
        context.register(LocusTagProvider.class, prefixProvider(Set.of("MYPFX")));
        TestUtils.injectContext(locusTagExistsValidation, context);
        Assertions.assertDoesNotThrow(
                () -> locusTagExistsValidation.validateRegisteredLocusTagPrefix(gff3Annotation, 1));
    }

    @Test
    void testRegisteredLocusTagPrefix_biosampleAccessionAllowed() {
        gff3Annotation
                .getFeatures()
                .add(TestUtils.createGFF3Feature("gene", Map.of(GFF3Attributes.LOCUS_TAG, List.of("SAMEA123456_001"))));
        ValidationContext context = TestUtils.createTestContext();
        context.register(LocusTagProvider.class, prefixProvider(Set.of("SAMEA123456")));
        TestUtils.injectContext(locusTagExistsValidation, context);
        Assertions.assertDoesNotThrow(
                () -> locusTagExistsValidation.validateRegisteredLocusTagPrefix(gff3Annotation, 1));
    }

    @Test
    void testRegisteredLocusTagPrefix_featureWithoutLocusTag_passes() {
        gff3Annotation.getFeatures().add(TestUtils.createGFF3Feature("gene", Map.of()));
        ValidationContext context = TestUtils.createTestContext();
        context.register(LocusTagProvider.class, prefixProvider(Set.of("MYPFX")));
        TestUtils.injectContext(locusTagExistsValidation, context);
        Assertions.assertDoesNotThrow(
                () -> locusTagExistsValidation.validateRegisteredLocusTagPrefix(gff3Annotation, 1));
    }

    @Test
    void testRegisteredLocusTagPrefix_noUnderscore_skipsForFormatValidation() {
        gff3Annotation
                .getFeatures()
                .add(TestUtils.createGFF3Feature("gene", Map.of(GFF3Attributes.LOCUS_TAG, List.of("GPNCGGLF"))));

        ValidationContext context = TestUtils.createTestContext();
        context.register(LocusTagProvider.class, prefixProvider(Set.of("MYPFX")));
        TestUtils.injectContext(locusTagExistsValidation, context);

        Assertions.assertDoesNotThrow(
                () -> locusTagExistsValidation.validateRegisteredLocusTagPrefix(gff3Annotation, 1));
    }

    @Test
    void testRegisteredLocusTagPrefix_mixedCaseRegisteredPrefix_matchesCaseInsensitively() {
        gff3Annotation
                .getFeatures()
                .add(TestUtils.createGFF3Feature("gene", Map.of(GFF3Attributes.LOCUS_TAG, List.of("mypfx_001"))));

        ValidationContext context = TestUtils.createTestContext();
        context.register(LocusTagProvider.class, prefixProvider(Set.of("MyPfX")));
        TestUtils.injectContext(locusTagExistsValidation, context);

        Assertions.assertDoesNotThrow(
                () -> locusTagExistsValidation.validateRegisteredLocusTagPrefix(gff3Annotation, 1));
    }

    @Test
    void testRegisteredLocusTagPrefix_multipleUnderscores_usesFirstPrefix() {
        gff3Annotation
                .getFeatures()
                .add(TestUtils.createGFF3Feature("gene", Map.of(GFF3Attributes.LOCUS_TAG, List.of("ABC_DEF_001"))));

        ValidationContext context = TestUtils.createTestContext();
        context.register(LocusTagProvider.class, prefixProvider(Set.of("ABC")));
        TestUtils.injectContext(locusTagExistsValidation, context);

        Assertions.assertDoesNotThrow(
                () -> locusTagExistsValidation.validateRegisteredLocusTagPrefix(gff3Annotation, 1));
    }
}
