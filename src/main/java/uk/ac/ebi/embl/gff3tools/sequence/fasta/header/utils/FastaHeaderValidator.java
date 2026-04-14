package uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class FastaHeaderValidator {

    private static final Set<String> TOPOLOGY_VALUES = Set.of("linear", "circular");

    private static final Set<String> CHROMOSOME_TYPE_VALUES = Set.of(
            "chromosome", "plasmid", "linkage_group",
            "monopartite", "segmented", "multipartite"
    );

    private static final Set<String> CHROMOSOME_LOCATION_VALUES = Set.of(
            "Macronuclear", "Nucleomorph", "Mitochondrion", "Kinetoplast",
            "Chloroplast", "Chromoplast", "Plastid", "Virion", "Phage",
            "Proviral", "Prophage", "Viroid", "Cyanelle", "Apicoplast",
            "Leucoplast", "Proplastid", "Hydrogenosome", "Chromatophore"
    );

    private static final List<String> FORBIDDEN_CHROMOSOME_NAME_PARTS = List.of(
            "chr", "chrm", "chrom", "chromosome",
            "linkage group", "linkage-group", "linkage_group",
            "plasmid"
    );

    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_#\\-.]*$");

    /**
     * Validates the given {@link FastaHeader} instance against mandatory fields,
     * allowed values, and format constraints.
     *
     * <p>Validation rules include:</p>
     * <ul>
     *   <li><b>Mandatory fields:</b>
     *     <ul>
     *       <li>description must be non-null and non-blank</li>
     *       <li>molecule_type must be non-null and non-blank</li>
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
     * </ul>
     *
     * @param header the {@link FastaHeader} to validate
     * @return a list of validation error messages; empty if the input is valid
     */
    public static List<String> validate(FastaHeader header) {
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
        }

        if (isBlank(header.getTopology())) {
            errors.add("topology is mandatory");
        } else if (!TOPOLOGY_VALUES.contains(header.getTopology())) {
            errors.add("topology must be 'linear' or 'circular'");
        }

        // --- Optional: chromosome_type ---
        if (!isBlank(header.getChromosomeType())) {
            if (!CHROMOSOME_TYPE_VALUES.contains(header.getChromosomeType())) {
                errors.add("invalid chromosome_type - see allowed values list");
            }
        }

        // --- Optional: chromosome_location ---
        if (!isBlank(header.getChromosomeLocation())) {
            if (!CHROMOSOME_LOCATION_VALUES.contains(header.getChromosomeLocation())) {
                errors.add("invalid chromosome_location - see allowed values list");
            }
        }

        // --- Optional: chromosome_name ---
        if (!isBlank(header.getChromosomeName())) {
            String name = header.getChromosomeName();

            if (!NAME_PATTERN.matcher(name).matches()) {
                errors.add("invalid chromosome_name format");
            }

            if (name.length() >= 33) {
                errors.add("chromosome_name must be shorter than 33 characters");
            }

            String lower = name.toLowerCase();
            for (String forbidden : FORBIDDEN_CHROMOSOME_NAME_PARTS) {
                if (lower.contains(forbidden)) {
                    errors.add("chromosome_name contains forbidden term: " + forbidden);
                    break;
                }
            }
        }

        return errors;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

