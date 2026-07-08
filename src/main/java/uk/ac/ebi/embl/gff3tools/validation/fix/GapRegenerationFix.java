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

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.fftogff3.FastaToGff3Converter;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;

/**
 * Discards whatever {@code gap}/{@code assembly_gap} (SO:0000730) features are present on an
 * annotation and rebuilds them purely from the runs of N bases reported by the {@link
 * SequenceLookup} for that annotation's accession.
 *
 * <p>Enabled by default (class and method); the sequence is the source of truth for gaps, so the
 * only runtime gate is whether a {@link SequenceLookup} is available in the {@link
 * ValidationContext} — when it is not, this fix is a complete no-op. This uniform rule applies
 * across the {@code validation}, {@code conversion} and {@code fix} commands: any command that is
 * given a sequence regenerates gaps in-memory; commands without one leave gap features untouched.
 *
 * <p>Runs at {@link ValidationPriority#HIGH}, before other annotation-level fixes, so the
 * annotation's feature list is settled early. Gap regeneration does not interact with the other
 * fixes, so exact ordering relative to them is not load-bearing.
 *
 * <p>A single instance of this fix is registered per validation run (either the classpath-scanned
 * default instance or a single CLI-parameterised instance registered via {@code withFix}), so the
 * {@link #gapCounter} instance field produces document-wide unique gap IDs ({@code gap}, {@code
 * gap_1}, {@code gap_2}, ...) across every annotation processed in that run, matching GFF3's
 * requirement that IDs be unique within a file and mirroring {@link FastaToGff3Converter}'s
 * existing document-wide counter.
 */
@Slf4j
@Gff3Fix(name = "REGENERATE_GAPS", description = "Discard existing gap features and rebuild them from FASTA N-runs")
public class GapRegenerationFix implements Fix {

    @InjectContext
    private ValidationContext context;

    private final int minGapLength;
    private final String gapType;
    private final String linkageEvidence;

    // Document-wide counter for generated gap IDs; see class javadoc for why this is instance
    // state rather than a local variable.
    private int gapCounter = 0;

    /** No-arg constructor required for classpath discovery; uses default parameters. */
    public GapRegenerationFix() {
        this(FastaToGff3Converter.DEFAULT_MIN_GAP_LENGTH, null, null);
    }

    public GapRegenerationFix(int minGapLength, String gapType, String linkageEvidence) {
        // Defensive: a run of N is only ever a gap if it has at least one base.
        this.minGapLength = Math.max(1, minGapLength);
        // Blank values are treated as "not supplied" so we emit a plain gap.
        this.gapType = isBlank(gapType) ? null : gapType.trim();
        this.linkageEvidence = isBlank(linkageEvidence) ? null : linkageEvidence.trim();
        // Defensive: linkage_evidence is only meaningful alongside a gap_type. Full value
        // validity (including which gap_types require it) is enforced up front by
        // GapOptionsValidator; this guard keeps the class safe when constructed directly.
        if (this.linkageEvidence != null && this.gapType == null) {
            throw new IllegalArgumentException("linkageEvidence requires a gapType to be supplied");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FixMethod(
            rule = "REGENERATE_GAPS",
            description = "Discard existing gap features and rebuild them from FASTA N-runs",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void regenerateGaps(GFF3Annotation annotation, int line) throws ValidationException {
        if (!context.contains(SequenceLookup.class)) {
            return;
        }
        SequenceLookup lookup = context.get(SequenceLookup.class);
        if (lookup == null) {
            return;
        }

        String seqId = annotation.getAccession();

        // Resolve gap regions before mutating the annotation, so an accession that has no
        // matching sequence source (surfaced by SequenceLookup as an exception, e.g.
        // CompositeSequenceProvider's "no sequence source found") leaves the annotation
        // untouched. Note: SequenceLookup#knownSeqIds() cannot be used as a pre-check here — a
        // keyless plain-sequence source deliberately reports no known seqIds while still
        // matching any accession, which is why FileSequenceSource#hasSequence, not
        // knownSeqIds(), is the source of truth callers like CompositeSequenceProvider use.
        List<GapRegion> gaps;
        try {
            gaps = lookup.getGapRegions(seqId, SequenceRangeOption.WHOLE_SEQUENCE);
        } catch (Exception e) {
            throw new ValidationException(
                    "REGENERATE_GAPS",
                    line,
                    "Failed to retrieve gap regions for accession \"" + seqId + "\": " + e.getMessage());
        }

        removeExistingGapFeatures(annotation);

        for (GapRegion gap : gaps) {
            if (gap.lengthBases() < minGapLength) {
                continue;
            }
            annotation.addFeature(buildGapFeature(seqId, gap));
            gapCounter++;
        }

        annotation.sortFeatures();
    }

    private void removeExistingGapFeatures(GFF3Annotation annotation) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        // Copy-iterate: removeFeature mutates annotation.getFeatures() and would otherwise
        // throw ConcurrentModificationException.
        for (GFF3Feature feature : new ArrayList<>(annotation.getFeatures())) {
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isPresent() && OntologyTerm.GAP.ID.equals(soIdOpt.get())) {
                annotation.removeFeature(feature);
            }
        }
    }

    private GFF3Feature buildGapFeature(String seqId, GapRegion gap) {
        String id = gapCounter == 0 ? "gap" : "gap_" + gapCounter;
        GFF3Feature feature = new GFF3Feature(
                Optional.of(id),
                Optional.empty(),
                seqId,
                Optional.empty(),
                ".",
                "gap",
                gap.startBase,
                gap.endBase,
                ".",
                "+",
                ".");
        feature.addAttribute(GFF3Attributes.ATTRIBUTE_ID, id);
        feature.addAttribute(GFF3Attributes.ESTIMATED_LENGTH, String.valueOf(gap.lengthBases()));
        if (gapType != null) {
            feature.addAttribute(GFF3Attributes.GAP_TYPE, gapType);
        }
        if (linkageEvidence != null) {
            feature.addAttribute(GFF3Attributes.LINKAGE_EVIDENCE, linkageEvidence);
        }
        return feature;
    }
}
