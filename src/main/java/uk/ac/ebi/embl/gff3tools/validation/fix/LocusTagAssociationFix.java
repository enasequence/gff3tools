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

import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.GENE;
import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.LOCUS_TAG;

import java.util.*;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Fix(
        name = "LOCUS_TAG_ASSOCIATION",
        description =
                "Adds locus tag attribute to the features with the gene attribute, considering first-seen pair as the correct one")
public class LocusTagAssociationFix {

    @FixMethod(
            rule = "LOCUS_TAG_ADD_TO_FEATURES_SHARING_THE_GENE",
            type = ValidationType.ANNOTATION,
            description =
                    "Adds locus tag attribute to the features with the gene attribute, considering first-seen pair as the correct one")
    public void fix(GFF3Annotation gff3Annotation, int line) {
        Map<String, String> geneToLocusTag = new HashMap<>();
        for (GFF3Feature feature : gff3Annotation.getFeatures()) {
            if (feature == null) return;

            String gene = feature.getAttributeByName(GENE).orElse(null);
            if (gene == null) return;

            String presentLocus = feature.getAttributeByName(LOCUS_TAG).orElse(null);

            if (geneToLocusTag.containsKey(gene) && (presentLocus == null || presentLocus.isEmpty())) {
                // add locus tag to features which dont have it
                String known = geneToLocusTag.get(gene);
                List<String> locusValues = new ArrayList<>();
                locusValues.add(known);
                feature.setAttributeValueList(LOCUS_TAG, locusValues);
            } else if (presentLocus != null) {
                // if unremembered locus tag present, remember it (first seen wins)
                geneToLocusTag.putIfAbsent(gene, presentLocus);
            }
        }
    }
}
