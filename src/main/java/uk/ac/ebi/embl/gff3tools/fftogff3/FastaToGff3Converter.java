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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.api.SequenceFormatReader;
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
 * <p>This converter builds one empty {@link GFF3Annotation} per sequence entry, with its
 * {@code ##sequence-region} directive set, and hands it to the validation engine. The registered
 * {@code GapGenerationFix} reads the sequence's runs of {@code N}/{@code n} bases from the engine's
 * {@code SequenceLookup} context and populates the gap features; this converter does not scan for
 * gaps itself. Minimum run length and the optional {@code gap_type} / {@code linkage_evidence} come
 * from the {@code AnalysisContext} the caller registers on the engine.
 *
 * <p>Because the gaps come from a fix, disabling {@code fix.GAP_GENERATION} makes this conversion
 * emit sequence-region directives and no features at all; the converter logs a warning when it
 * produced nothing.
 *
 * <p>The converter reads the sequence through a shared {@link FileSequenceSource} that is also
 * registered on the {@link ValidationEngine}. This means the FASTA is opened only once, and the
 * engine's sequence/annotation/fasta-header validations run over the generated GFF3 exactly as
 * they would for a FASTA+GFF3 submission. The source is owned by the engine (closed when the
 * engine closes) so this converter never opens or closes it itself.
 */
@Slf4j
public class FastaToGff3Converter implements Converter {

    private final ValidationEngine validationEngine;
    private final FileSequenceSource source;

    public FastaToGff3Converter(ValidationEngine validationEngine, FileSequenceSource source) {
        this.validationEngine = validationEngine;
        this.source = source;
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

        // Build the ordinal -> seqId lookup once (O(n)) instead of scanning the map per ordinal.
        Map<Long, String> ordinalToSeqId = buildOrdinalToSeqId();

        for (long ordinal : formatReader.getOrderedIds()) {
            String seqId = ordinalToSeqId.getOrDefault(ordinal, source.getSequenceKey());
            if (seqId == null) {
                log.warn("No sequence ID found for ordinal {}", ordinal);
                continue;
            }

            long length;
            try {
                length = formatReader.getStats(ordinal).totalBases();
            } catch (Exception e) {
                throw new ReadException(
                        "Failed to read sequence for ordinal " + ordinal + ": " + e.getMessage(),
                        ReadException.wrapAsIOException(e));
            }

            GFF3Annotation annotation = new GFF3Annotation();
            annotation.setSequenceRegion(new GFF3SequenceRegion(seqId, Optional.empty(), 1, length));

            // GapGenerationFix populates this annotation's gap features from the sequence; the
            // other annotation-level fixes and validations run over it here too.
            validationEngine.validate(annotation, -1);
            annotations.add(annotation);
        }

        if (annotations.isEmpty()
                || annotations.stream().allMatch(a -> a.getFeatures().isEmpty())) {
            log.warn("No gap features were generated for any sequence. If the input does contain runs of N"
                    + " bases, check that the GAP_GENERATION fix is enabled.");
        }

        GFF3File file =
                GFF3File.builder().header(header).annotations(annotations).build();

        file.writeGFF3String(writer);

        // Surface any non-fail-fast errors collected while validating the generated GFF3.
        validationEngine.throwIfErrorsCollected();
    }

    private Map<Long, String> buildOrdinalToSeqId() {
        Map<Long, String> ordinalToSeqId = new HashMap<>();
        for (Map.Entry<String, Long> entry : source.getSeqIdToOrdinal().entrySet()) {
            // Plain sequences have no header-derived IDs; the loop body adds nothing and callers
            // fall back to source.getSequenceKey().
            ordinalToSeqId.put(entry.getValue(), entry.getKey());
        }
        return ordinalToSeqId;
    }
}
