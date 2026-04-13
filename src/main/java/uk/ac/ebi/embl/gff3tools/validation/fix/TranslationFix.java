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
import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.FEATURE;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
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
 * Fix that generates protein translations from CDS features using a {@link SequenceLookup}.
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
            rule = "REMOVE_TRANSLATION_ATTRIBUTE",
            description = "Capture existing translation attribute into TranslationState and remove it from the feature",
            type = FEATURE,
            priority = ValidationPriority.HIGH)
    public void fixFeature(GFF3Feature feature, int line) {
        if (!feature.hasAttribute(GFF3Attributes.TRANSLATION)) {
            return;
        }

        String translation = feature.getAttribute(GFF3Attributes.TRANSLATION).orElse(null);
        if (translation != null && context.contains(TranslationState.class)) {
            String key = TranslationState.buildKey(
                    feature.accession(), feature.getId().orElse(null));
            if (key != null) {
                TranslationState state = context.get(TranslationState.class);
                // Record the pre-existing translation; new translation computed later by TranslationFix
                state.record(key, translation, null);
            }
        }

        log.debug("Removing translation attribute from feature at line {}", line);
        feature.removeAttributeList(GFF3Attributes.TRANSLATION);
    }

    @FixMethod(
            rule = "TRANSLATION",
            description = "Translate CDS features and set the translation attribute",
            type = ANNOTATION,
            priority = ValidationPriority.LOW)
    public void fixAnnotation(GFF3Annotation annotation, int line) throws ValidationException {
        if (!context.contains(SequenceLookup.class)) {
            return;
        }
        SequenceLookup sequenceLookup = context.get(SequenceLookup.class);
        if (sequenceLookup == null) {
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
            translateCdsGroup(segments, sequenceLookup, line);
        }
    }

    private void translateCdsGroup(List<GFF3Feature> segments, SequenceLookup sequenceLookup, int line)
            throws ValidationException {

        boolean isTransSpliced = segments.stream().anyMatch(s -> s.hasAttribute(GFF3Attributes.TRANS_SPLICING));

        List<GFF3Feature> sorted = new ArrayList<>(segments);
        if (!isTransSpliced) {
            // Sort segments by genomic start position when they are not trans-spliced.
            sorted.sort(Comparator.comparingLong(GFF3Feature::getStart));
        }

        GFF3Feature representative = sorted.get(0);
        String oldTranslation = lookupOldTranslation(representative);

        // Skip CDS features with exception attribute (e.g. ribosomal slippage).
        // Check ALL segments — any segment carrying the exception applies to the whole join.
        boolean hasException = sorted.stream()
                .anyMatch(s -> s.getAttribute(GFF3Attributes.EXCEPTION).isPresent());
        if (hasException) {
            // record the old translation as new translation in case of exception
            recordTranslationState(representative, line, oldTranslation, oldTranslation);
            return;
        }

        try {
            // Concatenate sequence slices from all segments in genomic order
            StringBuilder concatenated = new StringBuilder();
            for (GFF3Feature segment : sorted) {
                String slice =
                        sequenceLookup.getSequenceSlice(segment.accession(), segment.getStart(), segment.getEnd());
                concatenated.append(slice);
            }

            Translator translator = new Translator(representative);
            translator.enableAllFixes();
            TranslationResult result =
                    translator.translate(concatenated.toString().getBytes());

            if (!result.isValid()) {
                throw new ValidationException(
                        "TRANSLATION", line, "Translation failed for CDS: " + result.getErrorMessages());
            }

            String translation = result.getConceptualTranslation();
            if (!translation.isEmpty()) {
                log.debug("Recording translation for CDS feature group at line {}", line);
                recordTranslationState(representative, line, oldTranslation, translation);
            }

            propagateJoinAttributes(sorted, result);
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
     * Redistributes partiality and pseudo attributes across multi-segment CDS join segments.
     * 5'/3' partial are assigned to the strand-correct segment (first/last depends on strand),
     * and pseudo is propagated from the first segment to all others.
     */
    private void propagateJoinAttributes(List<GFF3Feature> segments, TranslationResult result) {
        if (segments.isEmpty()) {
            return;
        }

        GFF3Feature first = segments.get(0);
        GFF3Feature last = segments.get(segments.size() - 1);

        // Partiality based on translation is set to first or last segment based on strand
        if (result.isFixedFivePrimePartial()) {
            GFF3Feature fivePrimeTarget = first.isComplement() ? last : first;
            if (!fivePrimeTarget.isFivePrimePartial()) {
                fivePrimeTarget.setFivePrimePartial();
            }
        }

        if (result.isFixedThreePrimePartial()) {
            GFF3Feature threePrimeTarget = first.isComplement() ? first : last;
            if (!threePrimeTarget.isThreePrimePartial()) {
                threePrimeTarget.setThreePrimePartial();
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

    private String lookupOldTranslation(GFF3Feature feature) {
        if (!context.contains(TranslationState.class)) {
            return null;
        }
        String key =
                TranslationState.buildKey(feature.accession(), feature.getId().orElse(null));
        if (key == null) {
            log.debug(
                    "Could not build translation key for feature {} — skipping lookup",
                    feature.getId().orElse("unknown"));
            return null;
        }
        TranslationState.TranslationEntry entry =
                context.get(TranslationState.class).get(key);
        return entry != null ? entry.oldTranslation() : null;
    }

    private void recordTranslationState(GFF3Feature feature, int line, String oldTranslation, String newTranslation) {
        if (!context.contains(TranslationState.class)) {
            return;
        }
        String key =
                TranslationState.buildKey(feature.accession(), feature.getId().orElse(null));
        if (key == null) {
            return;
        }
        TranslationState state = context.get(TranslationState.class);
        state.record(key, oldTranslation, newTranslation);
    }
}
