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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

/**
 * Validates FASTA header metadata associated with GFF3 annotations.
 *
 * <p>The validation is performed by resolving the annotation accession against the
 * {@link FastaHeaderProvider}. If a matching FASTA header is available, this class
 * checks required header fields and a small set of controlled-value and formatting
 * constraints for topology and chromosome-related attributes.</p>
 */
@Slf4j
@Gff3Validation(name = "FASTA_HEADER")
public class FastaHeaderFormatValidation implements Validation {
    public static final String VALIDATION_RULE = "FASTA_HEADER_SYNTAX_VALIDATION";

    private static final List<String> FORBIDDEN_CHROMOSOME_NAME_PARTS =
            List.of("chr", "chrm", "chrom", "chromosome", "linkage group", "linkage-group", "linkage_group");
    private static final Pattern CHROMOSOME_NAME_PATTERN = Pattern.compile("^([A-Za-z0-9]){1}([A-Za-z0-9_\\.]|-)*$");
    private static final int CHROMOSOME_NAME_LENGTH = 33;

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = VALIDATION_RULE,
            description = "Check if the fields in the FASTA Header are populated correctly",
            type = ANNOTATION,
            priority = ValidationPriority.CRITICAL)
    public void validate(GFF3Annotation annotation, int line) throws ValidationException {
        FastaHeaderProvider fastaHeaderProvider =
                context.contains(FastaHeaderProvider.class) ? context.get(FastaHeaderProvider.class) : null;
        if (fastaHeaderProvider == null) {
            return;
        }

        String id = annotation.getAccession();
        log.debug("Validating FASTA header for accession {}", id);
        Optional<FastaHeader> header = fastaHeaderProvider.getHeader(id);

        if (header.isEmpty()) {
            log.warn("No FASTA header found for accession {}", id);
            return;
        }

        List<String> errors = this.validate(header.get());
        if (!errors.isEmpty()) {
            log.warn("FASTA header validation failed for accession {} with {} violation(s)", id, errors.size());

            throw new ValidationException(
                    "FASTA_HEADER_SYNTAX_VALIDATION",
                    line,
                    "Fasta header with id " + id + " has the following syntax violations : " + String.join(",", errors)
                            + ".");
        }
    }

    /**
     * Validates the given {@link FastaHeader} instance against mandatory fields,
     * allowed values, and format constraints.
     *
     * <p>Validation rules include:</p>
     * <ul>
     *   <li><b>Mandatory fields:</b>
     *     <ul>
     *       <li>description must be non-null and non-blank</li>
     *       <li>molecule_type must be one of the allowed mol_type qualifier values</li>
     *       <li>topology must be either "linear" or "circular"</li>
     *     </ul>
     *   </li>
     *   <li><b>Optional fields (if present):</b>
     *     <ul>
     *       <li>chromosome_type must be one of the allowed values</li>
     *       <li>chromosome_location must match one of the predefined values (case-sensitive)</li>
     *       <li>chromosome_name must:
     *         <ul>
     *           <li>match the required pattern</li>
     *           <li>be shorter than 33 characters</li>
     *           <li>not contain forbidden substrings (case-insensitive)</li>
     *         </ul>
     *       </li>
     *     </ul>
     *   </li>
     *   <li><b>Optional field combinations:</b> chromosome_type requires chromosome_name, and
     *     chromosome_location requires both chromosome_name and chromosome_type. The only valid
     *     combinations are: none present, chromosome_name only, or chromosome_name + chromosome_type
     *     (with chromosome_location optional in that last case).</li>
     * </ul>
     *
     * @param header the {@link FastaHeader} to validate
     * @return a list of validation error messages; empty if the input is valid
     */
    protected static List<String> validate(FastaHeader header) {
        List<String> errors = new ArrayList<>();

        if (header == null) {
            errors.add("FastaHeader must not be null");
            return errors;
        }

        // --- Mandatory fields ---
        if (isBlank(header.getDescription())) {
            errors.add("description is mandatory");
        }

        if (isBlank(header.getMoleculeType())) {
            errors.add("molecule_type is mandatory");
        } else if (ControlledVocabularyUtils.normaliseMolType(header).isEmpty()) {
            errors.add("molecule_type must be one of the allowed mol_type qualifier values");
        }

        if (isBlank(header.getTopology())) {
            errors.add("topology is mandatory");
        } else if (ControlledVocabularyUtils.normaliseTopology(header).isEmpty()) {
            errors.add("topology must be 'linear' or 'circular'");
        }

        // --- Optional: chromosome_type ---
        if (!isBlank(header.getChromosomeType())) {
            if (ControlledVocabularyUtils.normaliseChromosomeType(header).isEmpty()) {
                errors.add("invalid chromosome_type - see allowed values list");
            }
        }

        // --- Optional: chromosome_location ---
        if (!isBlank(header.getChromosomeLocation())) {
            if (ControlledVocabularyUtils.normaliseChromosomeLocation(header).isEmpty()) {
                errors.add("invalid chromosome_location - see allowed values list");
            }
        }

        // --- Optional: chromosome_name ---
        if (!isBlank(header.getChromosomeName())) {
            String name = header.getChromosomeName();

            if (!CHROMOSOME_NAME_PATTERN.matcher(name).matches()) {
                errors.add("invalid chromosome_name format");
            }

            if (name.length() >= CHROMOSOME_NAME_LENGTH) {
                errors.add("chromosome_name must be shorter than " + CHROMOSOME_NAME_LENGTH + " characters");
            }

            String lower = name.toLowerCase();
            for (String forbidden : FORBIDDEN_CHROMOSOME_NAME_PARTS) {
                if (lower.contains(forbidden)) {
                    errors.add("chromosome_name contains forbidden term: " + forbidden);
                    break;
                }
            }

            if (containsForbiddenPlasmidWord(lower)) {
                errors.add(
                        "chromosome_name contains forbidden term which is only allowed as part of word 'megaplasmid': plasmid");
            }
        }

        // --- Optional fields: allowed combinations ---
        validateChromosomeCombination(header, errors);

        return errors;
    }

    /**
     * Validates that the optional chromosome fields appear only in one of the allowed combinations.
     *
     * <p>Mirrors the ENA Chromosome List File column structure (see the <a
     * href="https://ena-docs.readthedocs.io/en/latest/submit/fileprep/assembly.html">ENA assembly
     * submission docs</a>): chromosome_name and chromosome_type are mandatory together to describe a
     * chromosome list entry, and chromosome_location is an independently optional fourth column on
     * top of that entry (a chromosome is assumed nuclear/cytoplasmic when it is absent). Valid
     * combinations are:</p>
     * <ul>
     *   <li>none present &rarr; unplaced contig</li>
     *   <li>chromosome_name only &rarr; unlocalized</li>
     *   <li>chromosome_name and chromosome_type, chromosome_location optional &rarr; chromosome</li>
     * </ul>
     *
     * <p>Equivalently: chromosome_type requires chromosome_name, and chromosome_location requires
     * both chromosome_name and chromosome_type. Any other combination is rejected.</p>
     *
     * @param header the {@link FastaHeader} to inspect
     * @param errors the error list to append to when the combination is invalid
     */
    private static void validateChromosomeCombination(FastaHeader header, List<String> errors) {
        if (header == null) {
            return;
        }

        boolean hasName = !isBlank(header.getChromosomeName());
        boolean hasType = !isBlank(header.getChromosomeType());
        boolean hasLocation = !isBlank(header.getChromosomeLocation());

        boolean validCombination = (!hasType && !hasLocation) || (hasName && hasType);

        if (!validCombination) {
            errors.add("invalid combination of optional chromosome fields - chromosome_type requires "
                    + "chromosome_name, and chromosome_location requires chromosome_name and chromosome_type "
                    + "(allowed combinations: none, chromosome_name only, chromosome_name + chromosome_type, "
                    + "or all three)");
        }
    }

    // "plasmid" is forbidden unless the occurrence is part of the permitted word "megaplasmid".
    private static boolean containsForbiddenPlasmidWord(String chromosomeName) {
        String normalizedName = chromosomeName.toLowerCase(Locale.ROOT);
        int plasmidIndex = normalizedName.indexOf("plasmid");

        while (plasmidIndex >= 0) {
            if (!normalizedName.startsWith("megaplasmid", plasmidIndex - "mega".length())) {
                return true;
            }
            plasmidIndex = normalizedName.indexOf("plasmid", plasmidIndex + 1);
        }

        return false;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
