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

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.TranslationKey;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3TranslationReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;

/**
 * Warns about {@code ##FASTA} records of an annotation's accession whose {@code >accession|featureId}
 * header matches none of that annotation's features. They are still written to the output; this only
 * says that nothing in the annotation claims them.
 *
 * <p>Only the headers are compared: the proteins are never read.
 */
@Slf4j
@Gff3Validation(name = FastaTranslationWithoutFeatureValidation.RULE)
public class FastaTranslationWithoutFeatureValidation {

    public static final String RULE = "FASTA_TRANSLATION_WITHOUT_FEATURE";

    /** How many unmatched headers the warning names before it only counts the rest. */
    static final int HEADERS_NAMED = 10;

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE,
            description = "Warn about ##FASTA records of an annotation that match none of its features",
            type = ANNOTATION,
            severity = RuleSeverity.WARN)
    public void validateAnnotation(GFF3Annotation annotation, int line) throws ValidationException {
        String accession = annotation.getAccession();
        Set<String> headers = fastaHeaders(accession);
        if (headers.isEmpty()) {
            return;
        }

        Set<String> featureKeys = annotation.getFeatures().stream()
                .map(feature -> feature.getId().map(id -> TranslationKey.of(feature.accession(), id)))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        List<String> unmatched = headers.stream()
                .filter(header -> !featureKeys.contains(header))
                .sorted()
                .toList();

        if (!unmatched.isEmpty()) {
            throw new ValidationException(RULE, line, message(accession, unmatched));
        }
    }

    /**
     * The {@code ##FASTA} headers of this accession, or none when there is no GFF3 reader or its
     * {@code ##FASTA} cannot be read. An unreadable section is reported when the output is written.
     */
    private Set<String> fastaHeaders(String accession) {
        if (accession == null || !context.contains(GFF3TranslationReader.class)) {
            return Set.of();
        }
        try {
            return context.get(GFF3TranslationReader.class)
                    .getTranslationOffsetsForAccession(accession)
                    .keySet();
        } catch (ReadException e) {
            log.debug("##FASTA translations could not be read: {}", e.getMessage());
            return Set.of();
        }
    }

    private static String message(String accession, List<String> unmatched) {
        String named = String.join(", ", unmatched.subList(0, Math.min(HEADERS_NAMED, unmatched.size())));
        String more =
                unmatched.size() > HEADERS_NAMED ? " and %d more".formatted(unmatched.size() - HEADERS_NAMED) : "";
        return "%d ##FASTA record(s) for \"%s\" match no feature of that sequence: %s%s"
                .formatted(unmatched.size(), accession, named, more);
    }
}
