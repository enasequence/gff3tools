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

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Slf4j
@Gff3Fix(name = "LOCUS_TAG_TO_UPPERCASE", description = "Update the locus_tag value to upper case")
public class LocusTagFix {

    @FixMethod(
            rule = "LOCUS_TAG_TO_UPPERCASE",
            description = "Update the locus_tag value to upper case",
            type = FEATURE)
    public void fixFeature(GFF3Feature feature, int line) {
        String locusTag = feature.getAttributeByName(GFF3Attributes.LOCUS_TAG);
        if (locusTag == null || locusTag.isBlank()) {
            return;
        }

        String locusTagUpperCase = locusTag.toUpperCase(Locale.ROOT);

        if (!locusTagUpperCase.equals(locusTag)) {
            log.info("Updating the {} to upper case at line {}", GFF3Attributes.LOCUS_TAG, line);
            feature.setAttribute(GFF3Attributes.LOCUS_TAG, locusTagUpperCase);
        }
    }
}
