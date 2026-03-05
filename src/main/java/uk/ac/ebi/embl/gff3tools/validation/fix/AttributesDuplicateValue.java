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

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

@Slf4j
@Gff3Fix(
        name = "REMOVE_ATTRIBUTES_DUPLICATE_VALUE",
        description = "Remove the duplicate values in the old_locus_tag and locus_tag")
public class AttributesDuplicateValue {

    @FixMethod(
            rule = "REMOVE_ATTRIBUTES_DUPLICATE_VALUE",
            description = "Remove the duplicate values in the old_locus_tag and locus_tag",
            type = FEATURE)
    public GFF3Feature fixFeature(GFF3Feature feature, int line) {
        if (feature == null || feature.getAttributeKeys().isEmpty()) {
            return feature;
        }

        List<String> oldLocusTags =
                feature.getAttributeList(GFF3Attributes.OLD_LOCUS_TAG).orElse(new ArrayList<>());
        if (oldLocusTags.isEmpty()) {
            return feature;
        }

        String locusTagObj = feature.getAttribute(GFF3Attributes.LOCUS_TAG).orElse(null);
        String currentLocusTag = locusTagObj != null ? locusTagObj.toString().trim() : null;

        Set<String> cleanedTags = new LinkedHashSet<>();
        for (String tag : oldLocusTags) {
            if (tag != null) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty() && (!trimmed.equals(currentLocusTag))) {
                    cleanedTags.add(trimmed);
                }
            }
        }

        if (oldLocusTags.size() != cleanedTags.size()) {

            if (cleanedTags.isEmpty()) {
                log.info("Removing duplicate or blank values from {} at line: {}", GFF3Attributes.OLD_LOCUS_TAG, line);
                feature.removeAttributeList(GFF3Attributes.OLD_LOCUS_TAG);
            } else {
                log.info("Set {} attribute at line: {}", GFF3Attributes.OLD_LOCUS_TAG, line);
                feature.setAttributeList(GFF3Attributes.OLD_LOCUS_TAG, new ArrayList<>(cleanedTags));
            }
        }
        return feature;
    }
}
