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

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.provider.AccessionMap;

@Slf4j
@Gff3Fix(
        name = "ACCESSION_REPLACEMENT",
        description =
                "Replaces sequence IDs with ENA-assigned accessions. Context-gated: no-op when no AccessionMap provider is registered.")
public class AccessionReplacementFix {

    @InjectContext
    private ValidationContext context;

    @FixMethod(
            rule = "ACCESSION_REPLACEMENT_FEATURE",
            description = "Replaces seqId and seqIdVersion on each feature with the mapped accession",
            type = FEATURE,
            priority = ValidationPriority.CRITICAL)
    public void replaceFeatureAccession(GFF3Feature feature, int line) {
        AccessionMap accessionMap = resolveAccessionMap();
        if (accessionMap == null) return;

        String oldAccession = feature.accession();
        String newAccession = accessionMap.get(oldAccession);
        if (newAccession == null) return;

        String[] parsed = parseAccession(newAccession);
        feature.setSeqId(parsed[0]);
        feature.setSeqIdVersion(parseVersion(parsed[1]));

        log.debug("Replaced feature accession {} -> {} at line {}", oldAccession, newAccession, line);
    }

    @FixMethod(
            rule = "ACCESSION_REPLACEMENT_ANNOTATION",
            description = "Replaces the accession in the sequence-region directive",
            type = ANNOTATION,
            priority = ValidationPriority.CRITICAL)
    public void replaceSequenceRegion(GFF3Annotation annotation, int line) {
        AccessionMap accessionMap = resolveAccessionMap();
        if (accessionMap == null) return;

        GFF3SequenceRegion sr = annotation.getSequenceRegion();
        if (sr == null) return;

        String newAccession = accessionMap.get(sr.accession());
        if (newAccession == null) return;

        String[] parsed = parseAccession(newAccession);
        annotation.setSequenceRegion(new GFF3SequenceRegion(parsed[0], parseVersion(parsed[1]), sr.start(), sr.end()));

        log.debug("Replaced sequence-region accession {} -> {} at line {}", sr.accession(), newAccession, line);
    }

    static String[] parseAccession(String accession) {
        int dot = accession.lastIndexOf('.');
        if (dot > 0 && dot < accession.length() - 1) {
            String suffix = accession.substring(dot + 1);
            try {
                Integer.parseInt(suffix);
                return new String[] {accession.substring(0, dot), suffix};
            } catch (NumberFormatException e) {
                // Not a version number, treat whole string as id
            }
        }
        return new String[] {accession, null};
    }

    private static Optional<Integer> parseVersion(String version) {
        if (version == null) return Optional.empty();
        return Optional.of(Integer.parseInt(version));
    }

    private AccessionMap resolveAccessionMap() {
        try {
            return context.get(AccessionMap.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
