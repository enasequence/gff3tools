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

import static uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils.ChromosomeType.*;
import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.*;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

@Slf4j
@Gff3Validation(name = "CHROMOSOME_NAME")
public class ChromosomeNameValidation implements Validation {
    public static final String CHROMOSOME_NAME_UNIQUE_RULE = "CHROMOSOME_NAME_UNIQUE";
    public static final String CHROMOSOME_OR_LINKAGE_GROUP_NAME_NOT_UNASSIGNED_RULE =
            "CHROMOSOME_OR_LINKAGE_GROUP_NAME_NOT_UNASSIGNED";
    public static final String PLASMID_CHROMOSOME_NAME_FORMAT_RULE = "PLASMID_CHROMOSOME_NAME_FORMAT";

    private static final Set<String> UNASSIGNED_CHROMOSOME_NAME_WORDS = Set.of("unknown", "un", "unk");
    private static final Pattern HISTORICAL_PLASMID_NAME_PATTERN = Pattern.compile(
            "F\\d*" // F factor: 'F' or 'F' + digits
                    + "|R[A-Za-z]*\\d+" // R / RP / RK factors: 'R' + optional letter(s) + digits, e.g. R1, RP4, RK2
                    + "|Col[A-Za-z]+\\d*(-[A-Za-z0-9]+)?" // Col factors, e.g. ColE1, ColIb, ColIb-P9, ColV
                    + "|Ti|Ri" // Ti / Ri plasmids
                    + "|[Mm]egaplasmid"); // megaplasmid - allowed as a word or part of a word

    @InjectContext
    private ValidationContext context;

    private final Map<String, String> chromosomeNameAccessions = new HashMap<>();

    @ValidationMethod(
            rule = CHROMOSOME_NAME_UNIQUE_RULE,
            description = "Check that chromosome_name values in FASTA headers are unique",
            type = ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateChromosomeNameUnique(GFF3Annotation annotation, int line) throws ValidationException {
        String id = annotation.getAccession();

        Optional<String> chromosomeName = fastaHeaderFor(id).flatMap(ChromosomeNameValidation::chromosomeName);
        if (chromosomeName.isEmpty()) {
            return;
        }

        String normalizedChromosomeName = chromosomeName.get();
        String existingAccession = chromosomeNameAccessions.putIfAbsent(normalizedChromosomeName, id);
        if (existingAccession != null && !existingAccession.equals(id)) {
            log.warn("Duplicate chromosome_name '{}' found for accession {}", normalizedChromosomeName, id);
            throw new ValidationException(
                    CHROMOSOME_NAME_UNIQUE_RULE,
                    line,
                    "Duplicate chromosome_name '%s' found for FASTA headers with ids %s and %s"
                            .formatted(normalizedChromosomeName, existingAccession, id));
        }
    }

    @ValidationMethod(
            rule = CHROMOSOME_OR_LINKAGE_GROUP_NAME_NOT_UNASSIGNED_RULE,
            description = "Check that chromosome and linkage group entries do not use unassigned chromosome names",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void validateChromosomeOrLinkageGroupNameAssigned(GFF3Annotation annotation, int line)
            throws ValidationException {
        Optional<FastaHeader> headerOpt = fastaHeaderFor(annotation.getAccession());
        if (headerOpt.isEmpty()) {
            return;
        }

        FastaHeader header = headerOpt.get();
        if (!hasChromosomeType(header, Set.of(CHROMOSOME, LINKAGE_GROUP))) {
            return;
        }

        Optional<String> chromosomeName = chromosomeName(header);
        if (chromosomeName.isEmpty()) {
            return;
        }

        String normalizedChromosomeName = chromosomeName.get();
        if (isUnknownChromosomeName(normalizedChromosomeName)) {
            throw new ValidationException(
                    CHROMOSOME_OR_LINKAGE_GROUP_NAME_NOT_UNASSIGNED_RULE,
                    line,
                    "chromosome_name '%s' is not permitted for chromosome_type '%s'. Each unplaced sequence should be separate and without an assignment."
                            .formatted(normalizedChromosomeName, header.getChromosomeType()));
        }
    }

    @ValidationMethod(
            rule = PLASMID_CHROMOSOME_NAME_FORMAT_RULE,
            description = "Check that plasmid entries use permitted plasmid names",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void validatePlasmidChromosomeNameFormat(GFF3Annotation annotation, int line) throws ValidationException {
        Optional<FastaHeader> headerOpt = fastaHeaderFor(annotation.getAccession());
        if (headerOpt.isEmpty()) {
            return;
        }

        FastaHeader header = headerOpt.get();
        if (!isPlasmid(header)) {
            return;
        }

        Optional<String> chromosomeName = chromosomeName(header);
        if (chromosomeName.isEmpty()) {
            return;
        }

        String normalizedChromosomeName = chromosomeName.get();
        if (!isValidPlasmidName(normalizedChromosomeName)) {
            throw new ValidationException(
                    PLASMID_CHROMOSOME_NAME_FORMAT_RULE,
                    line,
                    "chromosome_name '%s' is not a permitted plasmid name. Plasmid names must start with lower case 'p', except permitted historical names such as F1. Unnamed plasmid names such as unnamed or unnamed1 are allowed."
                            .formatted(normalizedChromosomeName));
        }
    }

    private Optional<FastaHeader> fastaHeaderFor(String id) {
        return context.contains(FastaHeaderProvider.class)
                ? context.get(FastaHeaderProvider.class).getHeader(id)
                : Optional.empty();
    }

    private static Optional<String> chromosomeName(FastaHeader header) {
        return Optional.ofNullable(header.getChromosomeName()).map(String::trim).filter(name -> !name.isEmpty());
    }

    private static boolean isPlasmid(FastaHeader header) {
        return hasChromosomeType(header, Set.of(PLASMID));
    }

    private static boolean hasChromosomeType(
            FastaHeader header, Set<ControlledVocabularyUtils.ChromosomeType> chromosomeTypes) {
        return ControlledVocabularyUtils.normaliseChromosomeType(header)
                .filter(chromosomeTypes::contains)
                .isPresent();
    }

    private static boolean isUnknownChromosomeName(String chromosomeName) {
        return "0".equals(chromosomeName)
                || Arrays.stream(chromosomeName.split("[^A-Za-z0-9]+"))
                        .map(word -> word.toLowerCase(Locale.ROOT))
                        .anyMatch(UNASSIGNED_CHROMOSOME_NAME_WORDS::contains);
    }

    /** Checks conditions for valid name. The check for whether the forbidden plasmid name does not contain
     * word plasmid except in case where it's part of "megaplasmid" is done in {@link FastaHeaderFormatValidation}
     * @param chromosomeName name of the chromosome
     * @return true if plasmid name is valid, false otherwise
     */
    private static boolean isValidPlasmidName(String chromosomeName) {
        return chromosomeName.matches("unnamed\\d*") // unnamed allowed format
                || HISTORICAL_PLASMID_NAME_PATTERN.matcher(chromosomeName).matches() // historical names
                || chromosomeName.startsWith("p");
    }
}
