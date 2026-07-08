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
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationRegistry;

class GapRegenerationFixTest {

    private OntologyClient ontologyClient;
    private SequenceLookup sequenceLookup;
    private ValidationContext context;

    @BeforeEach
    void setUp() {
        ontologyClient = mock(OntologyClient.class);
        sequenceLookup = mock(SequenceLookup.class);
        context = new ValidationContext();
        context.register(OntologyClient.class, new ContextProvider<>() {
            @Override
            public OntologyClient get(ValidationContext ctx) {
                return ontologyClient;
            }

            @Override
            public Class<OntologyClient> type() {
                return OntologyClient.class;
            }
        });
        context.register(SequenceLookup.class, new ContextProvider<>() {
            @Override
            public SequenceLookup get(ValidationContext ctx) {
                return sequenceLookup;
            }

            @Override
            public Class<SequenceLookup> type() {
                return SequenceLookup.class;
            }
        });
    }

    private GapRegenerationFix newFix(int minGapLength, String gapType, String linkageEvidence) {
        GapRegenerationFix fix = new GapRegenerationFix(minGapLength, gapType, linkageEvidence);
        ValidationRegistry.injectContext(fix, context);
        return fix;
    }

    private GFF3Feature feature(String name, long start, long end) {
        return new GFF3Feature(
                Optional.empty(), Optional.empty(), "seq1", Optional.empty(), ".", name, start, end, ".", "+", ".");
    }

    /**
     * Real annotations always carry a sequenceRegion (set from the ##sequence-region directive);
     * {@link GFF3Annotation#getAccession()} throws for an annotation with neither a
     * sequenceRegion nor any features, so tests that start from an empty annotation must set one.
     */
    private GFF3Annotation annotationFor(String seqId) {
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion(seqId, Optional.empty(), 1, 1000));
        return annotation;
    }

    @Test
    void removesExistingGapAndAssemblyGapFeatures() throws Exception {
        when(sequenceLookup.getGapRegions("seq1", SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(List.of());
        when(ontologyClient.findTermByNameOrSynonym("gap")).thenReturn(Optional.of(OntologyTerm.GAP.ID));
        when(ontologyClient.findTermByNameOrSynonym("assembly_gap")).thenReturn(Optional.of(OntologyTerm.GAP.ID));
        when(ontologyClient.findTermByNameOrSynonym("exon")).thenReturn(Optional.of("SO:0000147"));

        GFF3Annotation annotation = new GFF3Annotation();
        annotation.addFeature(feature("gap", 1, 5));
        annotation.addFeature(feature("assembly_gap", 20, 25));
        GFF3Feature exon = feature("exon", 30, 40);
        annotation.addFeature(exon);

        newFix(10, null, null).regenerateGaps(annotation, 1);

        assertEquals(List.of(exon), annotation.getFeatures());
    }

    @Test
    void regeneratesGapsFromNRunsAboveMinLength() throws Exception {
        when(sequenceLookup.getGapRegions("seq1", SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(List.of(new GapRegion(10, 20)));

        GFF3Annotation annotation = annotationFor("seq1");
        newFix(10, null, null).regenerateGaps(annotation, 1);

        assertEquals(1, annotation.getFeatures().size());
        GFF3Feature gap = annotation.getFeatures().get(0);
        assertEquals("gap", gap.getName());
        assertEquals(10, gap.getStart());
        assertEquals(20, gap.getEnd());
        assertEquals("gap", gap.getId().orElse(null));
        assertEquals("11", gap.getAttribute(GFF3Attributes.ESTIMATED_LENGTH).orElse(null));
    }

    @Test
    void filtersOutRunsShorterThanMinGapLength() throws Exception {
        when(sequenceLookup.getGapRegions("seq1", SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(List.of(new GapRegion(1, 5))); // length 5

        GFF3Annotation annotation = annotationFor("seq1");
        newFix(10, null, null).regenerateGaps(annotation, 1);

        assertTrue(annotation.getFeatures().isEmpty());
    }

    @Test
    void setsOptionalGapTypeAndLinkageEvidence() throws Exception {
        when(sequenceLookup.getGapRegions("seq1", SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(List.of(new GapRegion(1, 15)));

        GFF3Annotation annotation = annotationFor("seq1");
        newFix(10, "within scaffold", "paired-ends").regenerateGaps(annotation, 1);

        GFF3Feature gap = annotation.getFeatures().get(0);
        assertEquals(
                "within scaffold", gap.getAttribute(GFF3Attributes.GAP_TYPE).orElse(null));
        assertEquals(
                "paired-ends", gap.getAttribute(GFF3Attributes.LINKAGE_EVIDENCE).orElse(null));
    }

    @Test
    void generatesDocumentUniqueIdsAcrossAnnotations() throws Exception {
        when(sequenceLookup.getGapRegions("seq1", SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(List.of(new GapRegion(1, 15), new GapRegion(30, 45)));

        GapRegenerationFix fix = newFix(10, null, null);

        GFF3Annotation first = annotationFor("seq1");
        fix.regenerateGaps(first, 1);
        assertEquals("gap", first.getFeatures().get(0).getId().orElse(null));
        assertEquals("gap_1", first.getFeatures().get(1).getId().orElse(null));

        GFF3Annotation second = annotationFor("seq1");
        fix.regenerateGaps(second, 2);
        assertEquals("gap_2", second.getFeatures().get(0).getId().orElse(null));
        assertEquals("gap_3", second.getFeatures().get(1).getId().orElse(null));
    }

    @Test
    void throwsWhenAccessionNotCoveredByAnySequenceSource() throws Exception {
        // Mirrors CompositeSequenceProvider#sourceFor, which throws IllegalArgumentException
        // when no registered SequenceSource matches the accession. SequenceLookup#knownSeqIds()
        // is not used as a pre-check by GapRegenerationFix (see class javadoc), so failure to
        // resolve gap regions is what triggers the ValidationException here.
        when(sequenceLookup.getGapRegions("seq1", SequenceRangeOption.WHOLE_SEQUENCE))
                .thenThrow(new IllegalArgumentException("No sequence source found for seqId: seq1"));

        GFF3Annotation annotation = new GFF3Annotation();
        GFF3Feature existing = feature("gap", 1, 5);
        annotation.addFeature(existing);

        GapRegenerationFix fix = newFix(10, null, null);
        assertThrows(ValidationException.class, () -> fix.regenerateGaps(annotation, 7));

        // No mutation happened before the throw.
        assertEquals(List.of(existing), annotation.getFeatures());
        verifyNoInteractions(ontologyClient);
    }

    @Test
    void noOpWhenNoSequenceLookupRegistered() throws Exception {
        GapRegenerationFix fix = new GapRegenerationFix();
        ValidationRegistry.injectContext(fix, new ValidationContext());

        GFF3Annotation annotation = new GFF3Annotation();
        GFF3Feature existing = feature("gap", 1, 5);
        annotation.addFeature(existing);

        fix.regenerateGaps(annotation, 1);

        assertEquals(List.of(existing), annotation.getFeatures());
        verifyNoInteractions(sequenceLookup);
    }

    @Test
    void noOpWhenSequenceLookupProviderResolvesToNull() throws Exception {
        ValidationContext ctx = new ValidationContext();
        ctx.register(SequenceLookup.class, new ContextProvider<>() {
            @Override
            public SequenceLookup get(ValidationContext c) {
                return null;
            }

            @Override
            public Class<SequenceLookup> type() {
                return SequenceLookup.class;
            }
        });
        GapRegenerationFix fix = new GapRegenerationFix();
        ValidationRegistry.injectContext(fix, ctx);

        GFF3Annotation annotation = new GFF3Annotation();
        GFF3Feature existing = feature("gap", 1, 5);
        annotation.addFeature(existing);

        fix.regenerateGaps(annotation, 1);

        assertEquals(List.of(existing), annotation.getFeatures());
    }

    @Test
    void sortsRegeneratedFeaturesByStartThenEnd() throws Exception {
        // Registered out of position order.
        when(sequenceLookup.getGapRegions("seq1", SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(List.of(new GapRegion(50, 60), new GapRegion(1, 15)));

        GFF3Annotation annotation = annotationFor("seq1");
        newFix(10, null, null).regenerateGaps(annotation, 1);

        assertEquals(1, annotation.getFeatures().get(0).getStart());
        assertEquals(50, annotation.getFeatures().get(1).getStart());
    }

    @Test
    void constructorRejectsLinkageEvidenceWithoutGapType() {
        assertThrows(IllegalArgumentException.class, () -> new GapRegenerationFix(10, null, "paired-ends"));
    }
}
