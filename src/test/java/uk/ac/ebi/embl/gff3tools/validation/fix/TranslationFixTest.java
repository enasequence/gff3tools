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
package uk.ac.ebi.embl.gff3tools.validation.fix;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SubmissionType;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationRegistry;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceProvider;

class TranslationFixTest {

    private TranslationFix fix;
    private SequenceReader mockReader;
    private ValidationContext context;

    @BeforeEach
    void setUp() {
        fix = new TranslationFix();
        mockReader = mock(SequenceReader.class);
        when(mockReader.submissionType()).thenReturn(SubmissionType.FASTA);
        when(mockReader.getOrderedIds(any())).thenReturn(java.util.List.of("seq1"));
        context = new ValidationContext();

        CompositeSequenceProvider compositeProvider = new CompositeSequenceProvider();
        FileSequenceProvider fileProvider = new FileSequenceProvider(mockReader);
        compositeProvider.addSource(fileProvider);
        context.register(SequenceReader.class, compositeProvider);

        ValidationRegistry.injectContext(fix, context);
    }

    @Test
    void skipsNonCdsFeatures() throws Exception {
        GFF3Feature feature = createFeature("gene", "seq1", 1, 100, "+");
        fix.fixFeature(feature, 1);
        // Should not interact with reader
        verifyNoInteractions(mockReader);
    }

    @Test
    void skipsWhenNoProviderRegistered() throws Exception {
        TranslationFix fixWithoutProvider = new TranslationFix();
        ValidationContext emptyContext = new ValidationContext();
        ValidationRegistry.injectContext(fixWithoutProvider, emptyContext);

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        // Should not throw — just skip
        fixWithoutProvider.fixFeature(feature, 1);
    }

    @Test
    void skipsWhenNoSourcesRegistered() throws Exception {
        TranslationFix fixWithEmptyComposite = new TranslationFix();
        ValidationContext ctx = new ValidationContext();
        CompositeSequenceProvider emptyComposite = new CompositeSequenceProvider();
        ctx.register(SequenceReader.class, emptyComposite);
        ValidationRegistry.injectContext(fixWithEmptyComposite, ctx);

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        fixWithEmptyComposite.fixFeature(feature, 1);
    }

    @Test
    void translatesCdsFeatureForwardStrand() throws Exception {
        // ATG = M (start), AAA = K, TAA = * (stop) → conceptual = "MK"
        String sequence = "ATGAAATAA";
        when(mockReader.getSequenceSlice(
                        eq(IdType.SUBMISSION_ID), eq("seq1"), eq(1L), eq(9L), eq(SequenceRangeOption.WHOLE_SEQUENCE)))
                .thenReturn(sequence);

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        fix.fixFeature(feature, 1);

        assertTrue(feature.hasAttribute("translation"));
        assertEquals("MK", feature.getAttribute("translation").orElse(""));
    }

    @Test
    void translatesCdsFeatureComplementStrand() throws Exception {
        // Complement of TTA TTT CAT → ATG AAA TAA → M K *
        String sequence = "TTATTTCAT";
        when(mockReader.getSequenceSlice(
                        eq(IdType.SUBMISSION_ID), eq("seq1"), eq(1L), eq(9L), eq(SequenceRangeOption.WHOLE_SEQUENCE)))
                .thenReturn(sequence);

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "-");
        fix.fixFeature(feature, 1);

        assertTrue(feature.hasAttribute("translation"));
        assertEquals("MK", feature.getAttribute("translation").orElse(""));
    }

    @Test
    void skipsPseudoFeatures() throws Exception {
        // Pseudo features produce empty conceptual translation
        String sequence = "ATGAAATAA";
        when(mockReader.getSequenceSlice(any(), any(), anyLong(), anyLong(), any()))
                .thenReturn(sequence);

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        feature.addAttribute("pseudo", "true");
        fix.fixFeature(feature, 1);

        // Pseudo features are non-translating: conceptual translation is empty
        assertFalse(feature.hasAttribute("translation"));
    }

    private GFF3Feature createFeature(String name, String seqId, long start, long end, String strand) {
        return new GFF3Feature(
                Optional.of(name + "_id"),
                Optional.empty(),
                seqId,
                Optional.empty(),
                ".",
                name,
                start,
                end,
                ".",
                strand,
                "0");
    }
}
