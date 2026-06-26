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

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;

@Slf4j
@Gff3Fix(
        name = "CHROMOSOME_NAME",
        description =
                "Normalises the chromosome_name of the FASTA header registered for the annotation's accession by stripping whitespace, illegal characters and chromosome/linkage-group/plasmid keywords.")
public class ChromosomeNameFix implements Fix {

    @InjectContext
    private ValidationContext context;

    @FixMethod(
            rule = "CHROMOSOME_NAME",
            description =
                    "Normalises the chromosome_name of the FASTA header registered for the annotation's accession by stripping whitespace, illegal characters and chromosome/linkage-group/plasmid keywords.",
            type = ANNOTATION)
    public void fix(GFF3Annotation annotation, int line) {
        // No FASTA header source registered for this run -> nothing to fix.
        if (!context.contains(FastaHeaderProvider.class)) {
            return;
        }

        String accession = annotation.getAccession();
        Optional<FastaHeader> headerOpt = context.get(FastaHeaderProvider.class).getHeader(accession);
        if (headerOpt.isEmpty()) {
            throw new IllegalStateException("No FASTA header found for accession " + accession);
        }

        FastaHeader header = headerOpt.get();
        String chromosomeName = header.getChromosomeName();
        // chromosome_name is optional; nothing to normalise when it is absent.
        if (chromosomeName == null) {
            return;
        }

        String fixed = normalise(chromosomeName);
        if (!fixed.equals(chromosomeName)) {
            log.info(
                    "Normalising chromosome_name for accession {} from '{}' to '{}' at line: {}",
                    accession,
                    chromosomeName,
                    fixed,
                    line);
            header.setChromosomeName(fixed);
        }
    }

    /**
     * Strips, from the chromosome name (in order):
     *
     * <ul>
     *   <li>all whitespace,
     *   <li>the keywords {@code chromosome}, {@code chrom}, {@code chrm}, {@code chr},
     *       {@code linkage-group} / {@code linkage group} and {@code plasmid} (case-insensitive),
     *       leaving the {@code plasmid} in {@code megaplasmid} intact,
     *   <li>the characters {@code \ / | = ;}, each replaced with {@code _},
     *   <li>duplicate {@code _} runs (collapsed to a single {@code _}),
     *   <li>leading and trailing {@code _} characters.
     * </ul>
     */
    private String normalise(String chromosomeName) {
        String value = chromosomeName;

        // Remove all whitespace. This also turns "linkage group" into "linkagegroup".
        value = value.replaceAll("\\s", "");

        // Remove keywords (case-insensitive), longest first so "chromosome"/"chrom" are not
        // partially consumed by the shorter "chr".
        value = value.replaceAll("(?i)chromosome", "");
        value = value.replaceAll("(?i)chrom", "");
        value = value.replaceAll("(?i)chrm", "");
        value = value.replaceAll("(?i)chr", "");
        value = value.replaceAll("(?i)linkage-group", "");
        value = value.replaceAll("(?i)linkagegroup", "");
        // Drop "plasmid" unless it is part of "megaplasmid".
        value = value.replaceAll("(?i)(?<!mega)plasmid", "");

        // Replace illegal characters with underscores.
        value = value.replaceAll("[\\\\/|=;]", "_");

        // Collapse duplicate underscore runs, then trim leading/trailing underscores.
        value = value.replaceAll("_+", "_");
        value = value.replaceAll("^_+", "");
        value = value.replaceAll("_+$", "");

        return value;
    }
}
