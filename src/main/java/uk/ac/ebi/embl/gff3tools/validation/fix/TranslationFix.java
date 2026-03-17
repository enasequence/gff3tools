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

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.FEATURE;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
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

/**
 * Fix that generates protein translations from CDS features using a {@link SequenceReader}.
 *
 * <p>Runs at {@link ValidationPriority#LOW} so that structural fixes
 * (locus tag, pseudogene, attribute corrections) are applied first.
 */
@Slf4j
@Gff3Fix(name = "TRANSLATION", description = "Generate protein translations from CDS features")
public class TranslationFix {

    @InjectContext
    private ValidationContext context;

    @FixMethod(
            rule = "TRANSLATION",
            description = "Translate CDS features and set the translation attribute",
            type = FEATURE,
            priority = ValidationPriority.LOW)
    public void fixFeature(GFF3Feature feature, int line) throws ValidationException {
        if (!OntologyTerm.CDS.name().equals(feature.getName())) {
            return;
        }

        SequenceReader sequenceReader;
        try {
            sequenceReader = context.get(SequenceReader.class);
        } catch (IllegalArgumentException e) {
            // No SequenceReader provider registered — skip translation
            return;
        }
        if (sequenceReader == null) {
            // Provider registered but no reader set — skip translation
            return;
        }

        try {
            String nucleotideSlice = sequenceReader.getSequenceSlice(
                    IdType.SUBMISSION_ID,
                    feature.getSeqId(),
                    feature.getStart(),
                    feature.getEnd(),
                    SequenceRangeOption.WHOLE_SEQUENCE);

            Translator translator = new Translator(feature);
            translator.enableAllFixes();
            TranslationResult result = translator.translate(nucleotideSlice.getBytes());

            if (result.hasErrors()) {
                throw new ValidationException(
                        "TRANSLATION", line, "Translation failed for CDS: " + result.getErrorMessages());
            }

            String translation = result.getConceptualTranslation();
            if (!translation.isEmpty()) {
                feature.setAttributeList("translation", List.of(translation));
                log.debug("Set translation attribute on CDS feature at line {}", line);
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException(
                    "TRANSLATION",
                    line,
                    "Failed to translate CDS feature on sequence '%s': %s"
                            .formatted(feature.getSeqId(), e.getMessage()));
        }
    }
}
