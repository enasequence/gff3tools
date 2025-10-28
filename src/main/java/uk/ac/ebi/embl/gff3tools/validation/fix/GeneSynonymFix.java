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

import uk.ac.ebi.embl.gff3tools.fftogff3.FeatureMapping;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Fix(
        name = "GFF3GeneSynonymFix",
        description =
                "Normalizes gene_synonym values across features sharing the same locus_tag/gene; CDS sets the canonical list",
        enabled = false)
public class GeneSynonymFix {

    private static final String FF_CDS = "CDS";
    private static final HashSet<String> CDS_SYNONYMS = new HashSet<>();
    private static final String LOCUS_TAG = GFF3Attributes.LOCUS_TAG;
    private static final String GENE = GFF3Attributes.GENE;
    private static final String GENE_SYNONYM = GFF3Attributes.GENE_SYNONYM;

    public GeneSynonymFix(){
        FeatureMapping.getGFF3FeatureCandidateIdsAndNames(FF_CDS)
                .forEach(CDS_SYNONYMS::add);
    }

    @FixMethod(
            rule = "GFF3GeneSynonymFix",
            type = ValidationType.FEATURE,
            description = "Harmonizes gene_synonym across features keyed by locus_tag/gene; CDS defines the master list",
            enabled = true)
    public void fix(GFF3Annotation annotation, int line){
        if (annotation == null) return;

        // candidates = all features that have locus_tag or gene
        List<GFF3Feature> candidates = annotation.getAllFeatures().stream()
                .filter(f -> f.hasAttribute(LOCUS_TAG) || f.hasAttribute(GENE))
                .toList();
        if (candidates.isEmpty()) return;

        // CDS-only master candidates (authority), by feature type/name (case-insensitive)
        List<GFF3Feature> masterCandidates = candidates.stream()
                .filter(f -> CDS_SYNONYMS.contains(f.getName()))
                .toList();

        Map<String, List<String>> masterByIdentifier = new HashMap<>();
        Set<String> unreliable = new HashSet<>();

        // Pass 1: establish master lists from CDS features (order-sensitive equality).
        for (GFF3Feature f : masterCandidates) {
            String identifier = firstNonBlankValue(f.getAttributeValueList(LOCUS_TAG), f.getAttributeValueList(GENE));
            if (identifier == null || identifier.isBlank()) continue;

            if (unreliable.contains(identifier)) continue;

            List<String> geneSynonyms = f.getAttributeValueList(GENE_SYNONYM);; // may be empty, that's allowed (and will purge others later)
            List<String> existing = masterByIdentifier.get(identifier);

            if (existing == null) {
                masterByIdentifier.put(identifier, new ArrayList<>(geneSynonyms)); // preserve order
            } else {
                if (!listsEqualOrdered(existing, geneSynonyms)) {
                    // conflict (including mere order difference) => mark unreliable and drop the master
                    unreliable.add(identifier);
                    masterByIdentifier.remove(identifier);
                }
            }
        }

        // Pass 2: enforce master on all features (including CDS). If no CDS master exists yet for an identifier,
        // seed it from the first encountered feature (order-dependent fallback like original).
        for (GFF3Feature f : candidates) {
            String identifier = firstNonBlankValue(f.getAttributeValueList(LOCUS_TAG), f.getAttributeValueList(GENE));
            if (identifier == null || identifier.isBlank() || unreliable.contains(identifier)) continue;

            List<String> master = masterByIdentifier.get(identifier);
            if (master != null) {
                List<String> current = new ArrayList<>(getSynonyms(f));

                for (String m : master) {
                    if (!current.contains(m)) current.add(m);
                }
                current.removeIf(s -> !master.contains(s));

                f.setAttributeValueList(GENE_SYNONYM, current);

            } else {
                // No CDS-defined master exists for this identifier; reserve with first seen feature's synonyms
                List<String> current = getSynonyms(f);
                masterByIdentifier.put(identifier, new ArrayList<>(current));
            }
        }
    }

    private String firstNonBlankValue(List<String> a, List<String> b) {
        if(a != null && !a.isEmpty()) return a.get(0);
        if(b != null && !b.isEmpty()) return b.get(0);
        return null;
    }

    private List<String> getSynonyms(GFF3Feature f) {
        List<String> values = f.getAttributeValueList(GENE_SYNONYM);
        List<String> out = new ArrayList<>();
        for (String s : values) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Order-sensitive equality, element-wise. */
    private boolean listsEqualOrdered(List<String> a, List<String> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i), b.get(i))) return false;
        }
        return true;
    }

}
