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

import java.util.*;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Fix(
        name = "LOCUS_TAG_ASSOCIATION",
        description =
                "Adds locus tag attribute to the features with the gene attribute, considering first-seen pair as the correct one")
public class LocusTagAssociationFix {

    public static final String LOCUS_TAG = GFF3Attributes.LOCUS_TAG;
    public static final String GENE = GFF3Attributes.GENE;
    private final Map<String, Map<String, String>> accessionTogeneToLocusTag = new HashMap<>();

    public LocusTagAssociationFix() {}

    @FixMethod(
            rule = "LOCUS_TAG_ADD_OR_CORRECT_TO_FIRST_SEEN",
            type = ValidationType.FEATURE,
            description =
                    "Adds locus tag attribute to the features with the gene attribute, considering first-seen pair as the correct one")
    public void fix(GFF3Feature feature, int line) {
        if (feature == null) return;

        // Grab the first non-blank gene value (commonly one; if multiple exist, use the first).
        String gene = firstNonBlank(feature.getAttributeValueList(GENE));
        if (gene == null) return;

        String accessionNumber = feature.accession();
        String presentLocus = firstNonBlank(feature.getAttributeValueList(LOCUS_TAG));
        if (accessionTogeneToLocusTag.containsKey(accessionNumber)) {
            var geneToLocusTag = accessionTogeneToLocusTag.get(accessionNumber);
            if (geneToLocusTag.containsKey(gene)) {
                // add locus tag or correct to first-seen locus tag
                String known = geneToLocusTag.get(gene);
                List<String> locusValues = new ArrayList<>();
                locusValues.add(known);
                feature.setAttributeValueList(LOCUS_TAG, locusValues);
            } else if (presentLocus != null) {
                // if unremembered locus tag present, remember it (first seen wins)
                geneToLocusTag.putIfAbsent(gene, presentLocus);
                accessionTogeneToLocusTag.replace(accessionNumber, geneToLocusTag);
            }
        } else if (presentLocus != null) {
            var geneToLocusTag = new HashMap<String, String>();
            geneToLocusTag.putIfAbsent(gene, presentLocus);
            accessionTogeneToLocusTag.put(accessionNumber, geneToLocusTag);
        }
    }

    /** Read-only snapshot for tests */
    public Map<String, String> mappingSnapshot(String accessionNumber) {
        return Collections.unmodifiableMap(
                accessionTogeneToLocusTag.getOrDefault(accessionNumber, Collections.emptyMap()));
    }

    private static String firstNonBlank(List<String> values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null) {
                String t = v.trim();
                if (!t.isBlank()) return t;
            }
        }
        return null;
    }
}
