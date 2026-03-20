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

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.translation.TranslationResult;
import uk.ac.ebi.embl.gff3tools.translation.Translator;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * Fix that generates protein translations from CDS features using a {@link SequenceReader}.
 *
 * <p>Runs at {@link ValidationPriority#LOW} so that structural fixes
 * (locus tag, pseudogene, attribute corrections) are applied first.
 *
 * <p>Operates at the ANNOTATION level so that multi-segment CDS features (joins)
 * are translated as a single concatenated sequence rather than individually.
 */
@Slf4j
@Gff3Fix(name = "TRANSLATION", description = "Generate protein translations from CDS features")
public class TranslationFix {

    @InjectContext
    private ValidationContext context;

    @FixMethod(
            rule = "TRANSLATION",
            description = "Translate CDS features and set the translation attribute",
            type = ANNOTATION,
            priority = ValidationPriority.LOW)
    public void fixAnnotation(GFF3Annotation annotation, int line) throws ValidationException {
        SequenceReader sequenceReader;
        try {
            sequenceReader = context.get(SequenceReader.class);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (sequenceReader == null) {
            return;
        }

        // Group CDS features by ID (features with the same ID form a join)
        Map<String, List<GFF3Feature>> cdsGroups = annotation.getFeatures().stream()
                .filter(f -> OntologyTerm.CDS.name().equals(f.getName()))
                .collect(Collectors.groupingBy(
                        f -> f.getId().orElse("__no_id_" + f.getStart() + "_" + f.getEnd()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (List<GFF3Feature> segments : cdsGroups.values()) {
            translateCdsGroup(segments, sequenceReader, line);
        }
    }

    private void translateCdsGroup(List<GFF3Feature> segments, SequenceReader sequenceReader, int line)
            throws ValidationException {
        // Sort segments by genomic start position
        List<GFF3Feature> sorted = new ArrayList<>(segments);
        sorted.sort(Comparator.comparingLong(GFF3Feature::getStart));

        GFF3Feature representative = sorted.get(0);
        String oldTranslation = representative.getAttribute("translation").orElse(null);

        // Skip CDS features with exception attribute (e.g. ribosomal slippage)
        if (representative.getAttribute(GFF3Attributes.EXCEPTION).isPresent()) {
            return;
        }

        try {
            // Concatenate sequence slices from all segments in genomic order
            StringBuilder concatenated = new StringBuilder();
            for (GFF3Feature segment : sorted) {
                String slice = sequenceReader.getSequenceSlice(
                        IdType.SUBMISSION_ID,
                        segment.getSeqId(),
                        segment.getStart(),
                        segment.getEnd(),
                        SequenceRangeOption.WHOLE_SEQUENCE);
                concatenated.append(slice);
            }

            Translator translator = new Translator(representative);
            translator.enableAllFixes();
            TranslationResult result =
                    translator.translate(concatenated.toString().getBytes());

            if (result.hasErrors()) {
                throw new ValidationException(
                        "TRANSLATION", line, "Translation failed for CDS: " + result.getErrorMessages());
            }

            String translation = result.getConceptualTranslation();
            if (!translation.isEmpty()) {
                for (GFF3Feature segment : segments) {
                    segment.setAttributeList("translation", List.of(translation));
                }
                log.debug("Set translation attribute on CDS feature group at line {}", line);
                recordTranslationState(representative, line, oldTranslation, translation);
            }

            propagateJoinAttributes(segments);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException(
                    "TRANSLATION",
                    line,
                    "Failed to translate CDS feature on sequence '%s': %s"
                            .formatted(representative.getSeqId(), e.getMessage()));
        }
    }

    /**
     * For multi-segment CDS (joins), the Translator computes partiality and pseudo on the
     * first (representative) feature. This method moves 3' partial to the last segment where
     * it semantically belongs, and propagates pseudo from the first segment to all others.
     */
    private void propagateJoinAttributes(List<GFF3Feature> segments) {
        if (segments.size() <= 1) {
            return;
        }

        GFF3Feature first = segments.get(0);
        GFF3Feature last = segments.get(segments.size() - 1);

        // 3' partiality is computed on the first feature by the Translator but belongs
        // semantically to the last join segment — move it there.
        if (first.isThreePrimePartial()) {
            boolean preserveFivePrime = first.isFivePrimePartial();
            first.removeAttributeList(GFF3Attributes.PARTIAL);
            if (preserveFivePrime) {
                first.setFivePrimePartial();
            }
            if (!last.isThreePrimePartial()) {
                last.setThreePrimePartial();
            }
        }

        // Propagate pseudo from the first CDS segment to all subsequent join segments.
        first.getAttribute(GFF3Attributes.PSEUDO).ifPresent(pseudoValue -> {
            for (int i = 1; i < segments.size(); i++) {
                segments.get(i).removeAttributeList(GFF3Attributes.PSEUDO);
                segments.get(i).addAttribute(GFF3Attributes.PSEUDO, pseudoValue);
            }
        });
    }

    private void recordTranslationState(GFF3Feature feature, int line, String oldTranslation, String newTranslation) {
        TranslationState state;
        try {
            state = context.get(TranslationState.class);
        } catch (IllegalArgumentException e) {
            // No TranslationState provider registered — skip recording
            return;
        }
        String key =
                TranslationState.buildKey(feature.getSeqId(), feature.getId().orElse(null), line);
        state.record(key, oldTranslation, newTranslation);
    }
}
