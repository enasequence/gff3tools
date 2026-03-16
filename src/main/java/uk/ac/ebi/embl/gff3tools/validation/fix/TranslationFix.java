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

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.writer.TranslationWriter;
import uk.ac.ebi.embl.gff3tools.translation.TranslationResult;
import uk.ac.ebi.embl.gff3tools.translation.Translator;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.SequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationContext;

@Slf4j
@Gff3Fix(name = "TRANSLATION")
public class TranslationFix {

    @InjectContext
    private ValidationContext validationContext;

    private TranslationContext translationContext;

    @FixMethod(rule = "TRANSLATION", type = ValidationType.ANNOTATION)
    public void validateTranslation(GFF3Annotation annotation, int line) throws ValidationException, IOException {

        if (!validationContext.contains(TranslationContext.class)) {
            return;
        }
        translationContext = validationContext.get(TranslationContext.class);

        Map<String, List<GFF3Feature>> cdsById = annotation.getFeatures().stream()
                .filter(f -> f.getName().equalsIgnoreCase("CDS"))
                .filter(f -> f.getId().isPresent())
                .collect(Collectors.groupingBy(f -> f.getId().get()));

        if (cdsById.isEmpty()) return;

        Path fastaPath = translationContext.getSequenceFastaPath();

        try (Writer fastaWriter =
                Files.newBufferedWriter(fastaPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (List<GFF3Feature> cdsFeatures : cdsById.values()) {
                if (cdsFeatures.isEmpty()) continue;

                translateCds(cdsFeatures);
                writeTranslation(cdsFeatures, fastaWriter);
            }
        }
    }

    private void translateCds(List<GFF3Feature> cdsFeatures) throws ValidationException, IOException {

        // Use first CDS as primary feature for translation
        GFF3Feature gff3Feature = cdsFeatures.get(0);

        // Do not translate exceptional CDS features
        if (gff3Feature.getAttribute("exception").isPresent()) {
            return;
        }

        SequenceProvider sequenceProvider = validationContext.contains(SequenceProvider.class)
                ? validationContext.get(SequenceProvider.class)
                : new FileSequenceProvider(translationContext.getProcessDir());

        byte[] sequenceBytes = sequenceProvider.getSequenceBytes(gff3Feature.accession(), cdsFeatures);

        Translator translator = new Translator(gff3Feature);
        translator.enableAllFixes();
        TranslationResult translationResult = translator.translate(sequenceBytes);

        if (!translationResult.isValid() || translationResult.getConceptualTranslationCodons() <= 0) {
            throw new ValidationException(String.format(
                    "Translation failed at location %d-%d with error: %s",
                    gff3Feature.getStart(), gff3Feature.getEnd(), translationResult.getErrorMessages()));
        }

        String computedTranslation = translationResult.getConceptualTranslation();
        String expectedTranslation = gff3Feature.getAttribute("translation").orElse("");

        // TODO: remove this condition when accepting GFF3.
        if (!expectedTranslation.isEmpty()) {
            if (expectedTranslation.equals(computedTranslation)) {
                gff3Feature.addAttribute("translation", computedTranslation);
                updateAttributes(cdsFeatures);
            } else {
                log.error("Translation mismatch at location {}-{}", gff3Feature.getStart(), gff3Feature.getEnd());
            }
        } else {
            gff3Feature.addAttribute("translation", computedTranslation);
            updateAttributes(cdsFeatures);
        }
    }

    private void updateAttributes(List<GFF3Feature> cdsFeatures) {
        if (cdsFeatures.size() <= 1) return;

        GFF3Feature firstFeature = cdsFeatures.get(0);

        // 3' partiality is computed on the first feature by the translator but belongs
        // semantically to the last join segment — move it there.
        if (firstFeature.isThreePrimePartial()) {
            boolean preserveFivePrime = firstFeature.isFivePrimePartial();
            firstFeature.removeAttributeList(GFF3Attributes.PARTIAL);
            if (preserveFivePrime) {
                firstFeature.setFivePrimePartial();
            }
            GFF3Feature lastFeature = cdsFeatures.get(cdsFeatures.size() - 1);
            if (!lastFeature.isThreePrimePartial()) {
                lastFeature.setThreePrimePartial();
            }
        }

        // Propagate pseudo from the first CDS segment to all subsequent join segments.
        cdsFeatures.subList(1, cdsFeatures.size()).forEach(f -> {
            f.removeAttributeList("pseudo");
            firstFeature.getAttribute("pseudo").ifPresent(v -> f.addAttribute("pseudo", v));
        });
    }

    private void writeTranslation(List<GFF3Feature> cdsFeatures, Writer writer) {

        // Use first CDS as primary feature for writing translation
        GFF3Feature firstCds = cdsFeatures.get(0);
        if (firstCds.getAttribute("translation").isPresent()) {

            String translation = firstCds.getAttribute("translation").get();
            String translationKey = TranslationWriter.getTranslationKey(
                    firstCds.accession(), firstCds.getId().get());
            TranslationWriter.writeTranslation(writer, translationKey, translation);
            cdsFeatures.forEach(f -> f.removeAttributeList("translation"));
        }
    }
}
