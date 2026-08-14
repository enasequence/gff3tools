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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

public class LocationValidationTest {

    private static final String SEQ_ID = "chr1";

    private GFF3Feature feature;

    private LocationValidation locationValidation;

    private GFF3Annotation gff3Annotation;

    @BeforeEach
    public void setUp() {
        locationValidation = new LocationValidation();
        TestUtils.injectContext(locationValidation);
        gff3Annotation = new GFF3Annotation();
    }

    private void injectLookup(SequenceLookup mockLookup) {
        injectLookup(mockLookup, null);
    }

    private void injectLookup(SequenceLookup mockLookup, FastaHeaderProvider headerProvider) {
        ValidationContext context = TestUtils.createTestContext();
        context.register(SequenceLookup.class, new ContextProvider<>() {
            @Override
            public SequenceLookup get(ValidationContext ctx) {
                return mockLookup;
            }

            @Override
            public Class<SequenceLookup> type() {
                return SequenceLookup.class;
            }
        });
        if (headerProvider != null) {
            context.register(FastaHeaderProvider.class, headerProvider);
        }
        TestUtils.injectContext(locationValidation, context);
    }

    private void injectLookupReturning(String seqId, long len) throws Exception {
        injectLookup(mockLookupReturning(seqId, len));
    }

    private void injectLookupReturning(String seqId, long len, String topology) throws Exception {
        injectLookup(mockLookupReturning(seqId, len), headerProviderFor(seqId, topology));
    }

    private void injectLookupReturningWithoutHeaderFor(String seqId, long len) throws Exception {
        injectLookup(mockLookupReturning(seqId, len), headerProviderFor("other", "circular"));
    }

    private SequenceLookup mockLookupReturning(String seqId, long len) throws Exception {
        SequenceLookup mockLookup = mock(SequenceLookup.class);
        when(mockLookup.getSequenceLength(seqId, SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(len);
        return mockLookup;
    }

    private FastaHeaderProvider headerProviderFor(String seqId, String topology) {
        FastaHeader header = new FastaHeader();
        header.setTopology(topology);

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(id -> seqId.equals(id) ? Optional.of(header) : Optional.empty());
        return provider;
    }

    private void injectLookupThrowing(String seqId) throws Exception {
        SequenceLookup mockLookup = mock(SequenceLookup.class);
        when(mockLookup.getSequenceLength(seqId, SequenceRangeOption.WHOLE_SEQUENCE))
                .thenThrow(new RuntimeException("seqId not found"));
        injectLookup(mockLookup);
    }

    private void injectNoLookup() {
        TestUtils.injectContext(locationValidation);
    }

    @Nested
    class ValidateLocationRange {

        @Test
        void endAboveStartSuccess() {
            feature = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 1L, 18L);
            Assertions.assertDoesNotThrow(() -> locationValidation.validateLocationRange(feature, 1));
        }

        @Test
        void endBelowStartFailure() {
            feature = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 34L, 13L);
            ValidationException exception =
                    assertThrows(ValidationException.class, () -> locationValidation.validateLocationRange(feature, 1));
            assertTrue(exception.getMessage().contains("Invalid start/end for accession"));
        }

        @Test
        void endBelowStartWithCircularRnaAttributeSuccess() {
            feature = TestUtils.createGFF3Feature(
                    OntologyTerm.CDS_REGION.name(), 4800L, 200L, Map.of(GFF3Attributes.CIRCULAR_RNA, List.of("true")));
            Assertions.assertDoesNotThrow(() -> locationValidation.validateLocationRange(feature, 1));
        }

        @Test
        void endAboveStartWithCircularRnaAttributeSuccess() {
            feature = TestUtils.createGFF3Feature(
                    OntologyTerm.CDS_REGION.name(), 10L, 100L, Map.of(GFF3Attributes.CIRCULAR_RNA, List.of("true")));
            Assertions.assertDoesNotThrow(() -> locationValidation.validateLocationRange(feature, 1));
        }
    }

    @Nested
    class ValidateCdsLocation {

        @Test
        void propeptideInsideCdsSuccess() {
            GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 100L, 500L);
            GFF3Feature prop = TestUtils.createGFF3Feature(OntologyTerm.PROPEPTIDE_REGION_OF_CDS.name(), 200L, 350L);

            gff3Annotation.setFeatures(List.of(cds, prop));

            Assertions.assertDoesNotThrow(() -> locationValidation.validateCdsLocation(gff3Annotation, 1));
        }

        @Test
        void propeptideOutsideCdsFailure() {
            GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 300L, 500L);
            GFF3Feature prop = TestUtils.createGFF3Feature(OntologyTerm.PROPEPTIDE_REGION_OF_CDS.name(), 100L, 200L);

            gff3Annotation.setFeatures(List.of(cds, prop));

            ValidationException exception = assertThrows(
                    ValidationException.class, () -> locationValidation.validateCdsLocation(gff3Annotation, 3));

            assertTrue(exception.getMessage().contains("not inside any CDS"));
        }

        @Test
        void propeptideNotOverlappingPeptideSuccess() {
            GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 1L, 500L);
            GFF3Feature sig = TestUtils.createGFF3Feature(OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(), 300L, 350L);
            GFF3Feature prop = TestUtils.createGFF3Feature(OntologyTerm.PROPEPTIDE_REGION_OF_CDS.name(), 100L, 200L);

            gff3Annotation.setFeatures(List.of(cds, sig, prop));

            Assertions.assertDoesNotThrow(() -> locationValidation.validateCdsLocation(gff3Annotation, 4));
        }

        @Test
        void propeptideOverlappingPeptideFailure() {
            GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 1L, 500L);
            GFF3Feature sig = TestUtils.createGFF3Feature(OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(), 100L, 150L);
            GFF3Feature prop = TestUtils.createGFF3Feature(OntologyTerm.PROPEPTIDE_REGION_OF_CDS.name(), 120L, 200L);

            gff3Annotation.setFeatures(List.of(cds, sig, prop));

            ValidationException ex = assertThrows(
                    ValidationException.class, () -> locationValidation.validateCdsLocation(gff3Annotation, 4));

            assertTrue(ex.getMessage().contains("overlaps with peptide features"));
        }
    }

    @Nested
    class ValidateFeatureEndWithinSequence {

        @Test
        void endEqualsSequenceLengthSuccess() throws Exception {
            long seqLen = 1000L;
            injectLookupReturning(SEQ_ID, seqLen);
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, seqLen, Map.of());
            assertDoesNotThrow(() -> locationValidation.validateFeatureEndWithinSequence(feature, 1));
        }

        @Test
        void endWithinSequenceLengthSuccess() throws Exception {
            long seqLen = 1000L;
            injectLookupReturning(SEQ_ID, seqLen);
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, seqLen - 1, Map.of());
            assertDoesNotThrow(() -> locationValidation.validateFeatureEndWithinSequence(feature, 1));
        }

        @Test
        void endExceedsSequenceLengthFailure() throws Exception {
            long seqLen = 1000L;
            injectLookupReturning(SEQ_ID, seqLen);
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, seqLen + 1, Map.of());
            ValidationException ex = assertThrows(
                    ValidationException.class, () -> locationValidation.validateFeatureEndWithinSequence(feature, 1));
            assertTrue(ex.getMessage().contains("end position"));
        }

        @Test
        void endExceedsSequenceLengthOnLinearTopologyFailure() throws Exception {
            long seqLen = 1000L;
            injectLookupReturning(SEQ_ID, seqLen, "linear");
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, seqLen + 1, Map.of());
            assertThrows(
                    ValidationException.class, () -> locationValidation.validateFeatureEndWithinSequence(feature, 1));
        }

        @Test
        void endExceedsSequenceLengthOnCircularTopologySuccess() throws Exception {
            long seqLen = 149696L;
            injectLookupReturning(SEQ_ID, seqLen, "circular");
            GFF3Feature feature = TestUtils.createGFF3Feature("CDS", SEQ_ID, 149000L, 150662L, Map.of());
            assertDoesNotThrow(() -> locationValidation.validateFeatureEndWithinSequence(feature, 1));
        }

        @Test
        void endExceedsSequenceLengthOnNonCanonicalCircularTopologySuccess() throws Exception {
            long seqLen = 1000L;
            injectLookupReturning(SEQ_ID, seqLen, "Circular");
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 900L, seqLen + 100, Map.of());
            assertDoesNotThrow(() -> locationValidation.validateFeatureEndWithinSequence(feature, 1));
        }

        @Test
        void endExceedsSequenceLengthOnUnrecognisedTopologyFailure() throws Exception {
            long seqLen = 1000L;
            injectLookupReturning(SEQ_ID, seqLen, "spherical");
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, seqLen + 1, Map.of());
            assertThrows(
                    ValidationException.class, () -> locationValidation.validateFeatureEndWithinSequence(feature, 1));
        }

        @Test
        void endExceedsSequenceLengthWithoutHeaderForAccessionFailure() throws Exception {
            long seqLen = 1000L;
            injectLookupReturningWithoutHeaderFor(SEQ_ID, seqLen);
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, seqLen + 1, Map.of());
            assertThrows(
                    ValidationException.class, () -> locationValidation.validateFeatureEndWithinSequence(feature, 1));
        }

        @Test
        void lookupThrowsIllegalState() throws Exception {
            injectLookupThrowing(SEQ_ID);
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, 500L, Map.of());
            assertThrows(
                    IllegalStateException.class, () -> locationValidation.validateFeatureEndWithinSequence(feature, 1));
        }

        @Test
        void noLookupSkipped() throws Exception {
            injectNoLookup();
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, 999999L, Map.of());
            assertDoesNotThrow(() -> locationValidation.validateFeatureEndWithinSequence(feature, 1));
        }
    }

    @Nested
    class ValidateFeatureEndAboveZero {

        @Test
        void endEqualsOneSuccess() throws Exception {
            injectNoLookup();
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, 1L, Map.of());
            assertDoesNotThrow(() -> locationValidation.validateFeatureEndAboveZero(feature, 1));
        }

        @Test
        void endBelowOneFailure() throws Exception {
            injectNoLookup();
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, 0L, Map.of());
            ValidationException ex = assertThrows(
                    ValidationException.class, () -> locationValidation.validateFeatureEndAboveZero(feature, 1));
            assertTrue(ex.getMessage().contains("end position"));
        }

    }

    @Nested
    class ValidateFeatureStartAboveZero {

        @Test
        void startEqualsOneSuccess() throws Exception {
            injectNoLookup();
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, 500L, Map.of());
            assertDoesNotThrow(() -> locationValidation.validateFeatureStartAboveZero(feature, 1));
        }

        @Test
        void startBelowOneFailure() throws Exception {
            injectNoLookup();
            GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 0L, 500L, Map.of());
            ValidationException ex = assertThrows(
                    ValidationException.class, () -> locationValidation.validateFeatureStartAboveZero(feature, 1));
            assertTrue(ex.getMessage().contains("start position"));
        }

        @Test
        void startBelowOneWithCircularRnaAttributeFailure() {
            feature = TestUtils.createGFF3Feature(
                    OntologyTerm.CDS_REGION.name(), 0L, 100L, Map.of(GFF3Attributes.CIRCULAR_RNA, List.of("true")));
            ValidationException exception = assertThrows(
                    ValidationException.class, () -> locationValidation.validateFeatureStartAboveZero(feature, 1));
            assertTrue(exception.getMessage().contains("start position"));
        }
    }
}
