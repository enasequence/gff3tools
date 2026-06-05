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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.api.SequenceFormatReader;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.gff3.*;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;

/**
 * Converts a FASTA (or plain) sequence file to a GFF3 file containing only gap features.
 *
 * <p>Each sequence entry is scanned for contiguous runs of {@code N}/{@code n} bases.
 * Every run whose length is at least {@code minGapLength} becomes a {@code gap} feature with an
 * {@code estimated_length} attribute. Runs shorter than {@code minGapLength} are ignored,
 * mirroring the INSDC assembly behaviour implemented in sequencetools
 * ({@code SequenceToGapFeatureBasesFix}).
 *
 * <p>The biological gap type cannot be inferred from a run of {@code N}s (only its length is
 * known), so no {@code gap_type} or {@code linkage_evidence} attribute is emitted by default and
 * the feature maps to a plain INSDC {@code gap}. A caller that genuinely knows the gap type may
 * supply {@code gapType} (and, where the type requires it, {@code linkageEvidence}); the feature
 * then maps to an INSDC {@code assembly_gap}. Value validity is enforced by the validation engine
 * ({@code AssemblyGapValidation}).
 *
 * <p>The converter reads the sequence through a shared {@link FileSequenceSource} that is also
 * registered on the {@link ValidationEngine}. This means the FASTA is opened only once, and the
 * engine's sequence/annotation/fasta-header validations run over the generated GFF3 exactly as
 * they would for a FASTA+GFF3 submission. The source is owned by the engine (closed when the
 * engine closes) so this converter never opens or closes it itself.
 */
@Slf4j
public class FastaToGff3Converter implements Converter {

    /** Default minimum gap length, matching sequencetools {@code Entry.DEFAULT_MIN_GAP_LENGTH}. */
    public static final int DEFAULT_MIN_GAP_LENGTH = 10;

    private final ValidationEngine validationEngine;
    private final FileSequenceSource source;
    private final int minGapLength;
    private final String gapType;
    private final String linkageEvidence;

    public FastaToGff3Converter(ValidationEngine validationEngine, FileSequenceSource source, int minGapLength) {
        this(validationEngine, source, minGapLength, null, null);
    }

    public FastaToGff3Converter(
            ValidationEngine validationEngine,
            FileSequenceSource source,
            int minGapLength,
            String gapType,
            String linkageEvidence) {
        this.validationEngine = validationEngine;
        this.source = source;
        // Defensive: a run of N is only ever a gap if it has at least one base.
        this.minGapLength = Math.max(1, minGapLength);
        // Blank values are treated as "not supplied" so we emit a plain gap.
        this.gapType = isBlank(gapType) ? null : gapType.trim();
        this.linkageEvidence = isBlank(linkageEvidence) ? null : linkageEvidence.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        // The BufferedReader is intentionally unused: the sequence is read exactly once via the
        // shared FileSequenceSource. Triggering initialisation here is a no-op when the engine's
        // providers have already opened the source.
        source.getSeqIdToHeader();
        SequenceFormatReader formatReader = source.getFormatReader();

        GFF3Header header = new GFF3Header(GFF3Header.DEFAULT_VERSION);
        List<GFF3Annotation> annotations = new ArrayList<>();

        for (long ordinal : formatReader.getOrderedIds()) {
            String seqId = findSeqIdForOrdinal(ordinal);
            if (seqId == null) {
                log.warn("No sequence ID found for ordinal {}", ordinal);
                continue;
            }

            long length;
            List<GapRegion> gaps;
            try {
                length = formatReader.getStats(ordinal).totalBases();
                gaps = formatReader.getGapRegions(ordinal);
            } catch (Exception e) {
                throw new ReadException(
                        "Failed to read sequence for ordinal " + ordinal + ": " + e.getMessage(),
                        ReadException.wrapAsIOException(e));
            }

            GFF3Annotation annotation = new GFF3Annotation();
            annotation.setSequenceRegion(new GFF3SequenceRegion(seqId, Optional.empty(), 1, length));

            int gapIndex = 0;
            for (GapRegion gap : gaps) {
                // Rule: only runs of N at least minGapLength long are reported as gaps,
                // matching the INSDC assembly behaviour in sequencetools.
                if (gap.lengthBases() < minGapLength) {
                    continue;
                }
                String id = gapIndex == 0 ? "gap" : "gap_" + gapIndex;
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
                // Rule: gap_type/linkage_evidence are not inferable from sequence and are emitted
                // only when explicitly supplied; otherwise the feature stays a plain INSDC gap.
                if (gapType != null) {
                    feature.addAttribute(GFF3Attributes.GAP_TYPE, gapType);
                }
                if (linkageEvidence != null) {
                    feature.addAttribute(GFF3Attributes.LINKAGE_EVIDENCE, linkageEvidence);
                }
                // Run feature-level fixes/validations (e.g. AssemblyGapValidation) over the
                // generated gap, mirroring the FF->GFF3 path. Generated content has no line number.
                validationEngine.validate(feature, -1);
                annotation.addFeature(feature);
                gapIndex++;
            }

            // Run annotation-level fixes/validations over the generated annotation.
            validationEngine.validate(annotation, -1);
            annotations.add(annotation);
        }

        GFF3File file =
                GFF3File.builder().header(header).annotations(annotations).build();

        file.writeGFF3String(writer);

        // Surface any non-fail-fast errors collected while validating the generated GFF3.
        validationEngine.throwIfErrorsCollected();
    }

    private String findSeqIdForOrdinal(long ordinal) {
        for (Map.Entry<String, Long> entry : source.getSeqIdToOrdinal().entrySet()) {
            if (entry.getValue() == ordinal) {
                return entry.getKey();
            }
        }
        // Plain sequences have no header-derived IDs; fall back to the source key when present.
        return source.getSequenceKey();
    }
}
