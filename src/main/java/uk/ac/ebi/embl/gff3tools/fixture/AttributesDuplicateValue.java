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
package uk.ac.ebi.embl.gff3tools.fixture;

import java.util.*;
import java.util.stream.Collectors;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class AttributesDuplicateValue implements FeatureFix {

    // TODO: Need to add message or log mechanism to provide fixed details
    @Override
    public GFF3Feature fixFeature(GFF3Feature feature) {
        if (feature == null || feature.getAttributes() == null) {
            return feature;
        }

        Object oldLocusTagObj = feature.getAttributes().get(GFF3Attributes.OLD_LOCUS_TAG);
        Object locusTagObj = feature.getAttributes().get(GFF3Attributes.LOCUS_TAG);

        if (oldLocusTagObj == null) return feature;

        List<String> oldLocusTags;
        if (oldLocusTagObj instanceof String s) {
            oldLocusTags = Arrays.stream(s.split(","))
                    .map(String::trim)
                    .filter(str -> !str.isEmpty())
                    .collect(Collectors.toList());
        } else if (oldLocusTagObj instanceof List<?>) {
            oldLocusTags = ((List<?>) oldLocusTagObj)
                    .stream()
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .map(String::trim)
                            .filter(str -> !str.isEmpty())
                            .collect(Collectors.toList());
        } else {
            return feature;
        }

        Set<String> uniqueOldTags = new LinkedHashSet<>(oldLocusTags);

        if (locusTagObj != null) {
            String locusTag = locusTagObj.toString().trim();
            uniqueOldTags.remove(locusTag);
        }

        if (!uniqueOldTags.equals(new LinkedHashSet<>(oldLocusTags))) {
            Map<String, Object> updatedAttributes = new HashMap<>(feature.getAttributes());
            feature = feature.setAttribute(updatedAttributes, feature);
        }

        return feature;
    }
}
