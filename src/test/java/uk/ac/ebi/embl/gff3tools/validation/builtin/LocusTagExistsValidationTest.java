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
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;

/** Covers {@link LocusTagExistsValidation#LOCUS_TAG_EXISTS_RULE}. */
public class LocusTagExistsValidationTest {

    private LocusTagExistsValidation locusTagExistsValidation;

    private GFF3Annotation gff3Annotation;

    @BeforeEach
    public void setUp() {
        gff3Annotation = new GFF3Annotation();
        locusTagExistsValidation = new LocusTagExistsValidation();
    }

    private static final String LOCUS_TAG_EXISTS_MESSAGE =
            "/locus_tag must exist for annotated contigs/scaffolds/chromosomes.";

    private GFF3Feature gene() {
        return TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), OntologyTerm.GENE.name(), Map.of());
    }

    private GFF3Feature feature(String name) {
        return TestUtils.createGFF3Feature(name, name, Map.of());
    }

    /**
     * Wires the validation with the given analysis type and master metadata, either of which may
     * be absent, and runs the rule over the supplied features.
     */
    private Executable validation(AnalysisType analysisType, MasterMetadata metadata, GFF3Feature... features) {
        return validation(analysisType, metadata, null, features);
    }

    private Executable validation(
            AnalysisType analysisType, MasterMetadata metadata, String moleculeType, GFF3Feature... features) {
        ValidationContext context = TestUtils.createTestContext();

        if (analysisType != null) {
            context.register(
                    AnalysisContext.class, provider(AnalysisContext.class, new AnalysisContext(analysisType, 10)));
        }
        if (metadata != null) {
            MasterMetadataProvider metadataProvider = new MasterMetadataProvider();
            metadataProvider.addSource(seqId -> Optional.of(metadata));
            context.register(MasterMetadataProvider.class, provider(MasterMetadataProvider.class, metadataProvider));
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

    private MasterMetadata metadata(String dataClass, String contigDataClass, String lineage) {
        MasterMetadata metadata = new MasterMetadata();
        metadata.setDataClass(dataClass);
        metadata.setContigDataclass(contigDataClass);
        metadata.setLineage(lineage);
        return metadata;
    }

    private void assertViolation(Executable validation) {
        ValidationException ex = Assertions.assertThrows(ValidationException.class, validation);
        Assertions.assertTrue(ex.getMessage().contains(LOCUS_TAG_EXISTS_MESSAGE), ex.getMessage());
    }

    @Test
    public void testAnnotatedGenomeWithoutLocusTagFails() {
        assertViolation(validation(null, metadata("STD", null, null), gene()));
    }

    @Test
    public void testAnnotatedGenomeWithLocusTagSucceeds() {
        GFF3Feature tagged = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LOCUS_0001")));

        Assertions.assertDoesNotThrow(validation(null, metadata("STD", null, null), tagged));
    }

    /**
     * A locus_tag submitted with a blank value leaves the entry untagged: {@code GFF3Feature}
     * drops blank attribute values on the way in, so the attribute never reaches the rule. The
     * blank guard in {@code hasLocusTag} is belt-and-braces against a feature built some other
     * way, and is unreachable through the reader.
     */
    @Test
    public void testLocusTagWithBlankValueFails() {
        GFF3Feature blank = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(), OntologyTerm.GENE.name(), Map.of(GFF3Attributes.LOCUS_TAG, List.of(" ")));

        assertViolation(validation(null, metadata("STD", null, null), blank));
    }

    /**
     * A WGS set master entry carries dataClass "SET" and the contig's own class separately.
     * Reading dataClass alone would skip every contig of a WGS assembly - the main case the rule
     * exists for.
     */
    @Test
    public void testWgsContigOfSetMasterFails() {
        assertViolation(validation(null, metadata("SET", "WGS", null), gene()));
    }

    @Test
    public void testNonGenomeDataClassSucceeds() {
        Assertions.assertDoesNotThrow(validation(null, metadata("CON", null, null), gene()));
    }

    /** The analysis type alone identifies a genome, for callers that supply no master entry. */
    @Test
    public void testSequenceAssemblyWithoutMetadataFails() {
        assertViolation(validation(AnalysisType.SEQUENCE_ASSEMBLY, null, gene()));
    }

    @ParameterizedTest
    @EnumSource(
            value = AnalysisType.class,
            names = {"SEQUENCE_ASSEMBLY"},
            mode = EnumSource.Mode.EXCLUDE)
    public void testOtherAnalysisTypesWithoutMetadataSucceed(AnalysisType analysisType) {
        Assertions.assertDoesNotThrow(validation(analysisType, null, gene()));
    }

    /** A transcriptome is never a genome, whatever data class it inherited from a master. */
    @Test
    public void testTranscriptomeAssemblyOverridesGenomeDataClass() {
        Assertions.assertDoesNotThrow(
                validation(AnalysisType.TRANSCRIPTOME_ASSEMBLY, metadata("WGS", null, null), gene()));
    }

    /** Neither signal supplied - every plain CLI invocation. */
    @Test
    public void testWithoutAnalysisTypeOrMetadataSucceeds() {
        Assertions.assertDoesNotThrow(validation(null, null, gene()));
    }

    @Test
    public void testVirusSucceeds() {
        Assertions.assertDoesNotThrow(
                validation(null, metadata("STD", null, "Viruses; Riboviria; Orthornavirae; Pisuviricota."), gene()));
    }

    /** An organism that resolves to something else is not exempt. */
    @Test
    public void testEukaryoteFails() {
        assertViolation(validation(null, metadata("STD", null, "Eukaryota; Metazoa; Chordata; Homo."), gene()));
    }

    /** One repeat_region or misc_feature exempts the whole entry, as it does at ENA. */
    @ParameterizedTest
    @ValueSource(strings = {"repeat_region", "tandem_repeat", "sequence_feature", "biological_region"})
    public void testExemptFeatureSucceeds(String featureName) {
        Assertions.assertDoesNotThrow(validation(null, metadata("STD", null, null), gene(), feature(featureName)));
    }

    /** Nothing but the source feature and a gap: no annotation to tag. */
    @Test
    public void testWithoutAnnotationSucceeds() {
        Assertions.assertDoesNotThrow(
                validation(null, metadata("STD", null, null), feature(OntologyTerm.REGION.name()), feature("gap")));
    }

    /**
     * The assembly molecule types from the manifest: genomic DNA for most assemblies, genomic RNA
     * and viral cRNA for viral ones. None of them rules a genome out.
     */
    @ParameterizedTest
    @ValueSource(strings = {"genomic DNA", "genomic RNA", "viral cRNA"})
    public void testAssemblyMoleculeTypeStillFails(String moleculeType) {
        assertViolation(validation(null, metadata("STD", null, null), moleculeType, gene()));
    }

    /** A transcript-level molecule type is not a genome, whatever the data class says. */
    @ParameterizedTest
    @ValueSource(strings = {"mRNA", "rRNA", "tRNA", "other RNA", "transcribed RNA"})
    public void testTranscriptMoleculeTypeSucceeds(String moleculeType) {
        Assertions.assertDoesNotThrow(validation(null, metadata("STD", null, null), moleculeType, gene()));
    }

    /** The molecule type vetoes the analysis type too, not just the data class. */
    @Test
    public void testTranscriptMoleculeTypeOverridesSequenceAssembly() {
        Assertions.assertDoesNotThrow(validation(AnalysisType.SEQUENCE_ASSEMBLY, null, "mRNA", gene()));
    }

    /** No FASTA header means no molecule type, which must leave the rule as it was. */
    @Test
    public void testUnknownMoleculeTypeLeavesRuleUnchanged() {
        assertViolation(validation(null, metadata("STD", null, null), (String) null, gene()));
    }
}
