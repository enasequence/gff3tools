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
package uk.ac.ebi.embl.gff3tools.fix;

import java.util.*;
import java.util.stream.Collectors;

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
    public void fix(GFF3Annotation annotation) {
        if (annotation == null) return;

        List<GFF3Feature> candidates = annotation.getAllFeatures().stream()
                .filter(f -> f.containsAttribute(LOCUS_TAG) || f.containsAttribute(GENE))
                .toList();
        if (candidates.isEmpty()) return;

        List<GFF3Feature> masterCandidates = candidates.stream()
                .filter(f -> CDS_SYNONYMS.contains(f.getName()))
                .toList();

        Map<String, List<String>> geneSynonymValues = new HashMap<>();
        Set<String> unreliable = new HashSet<>();

        // Pass 1: establish master lists from CDS features (order-sensitive equality).
        for (GFF3Feature f : masterCandidates) {
            String identifier = firstNonBlank(f.getAttributeByName(LOCUS_TAG), f.getAttributeByName(GENE));
            if (identifier == null) continue;

            List<String> syns = getAttrValues(f, GENE_SYNONYM);

            if (unreliable.contains(identifier)) {
                continue;
            }

            if (geneSynonymValues.containsKey(identifier)) {
                List<String> existing = geneSynonymValues.get(identifier);
                if (!listsEqualOrdered(existing, syns)) { // order-sensitive
                    unreliable.add(identifier);
                    geneSynonymValues.remove(identifier);
                }
            } else {
                geneSynonymValues.put(identifier, new ArrayList<>(syns));
            }
        }

        // Pass 2: apply master to all features; if none exists and not unreliable, set from first seen.
        for (GFF3Feature f : candidates) {
            String identifier = firstNonBlank(f.getAttributeByName(LOCUS_TAG), f.getAttributeByName(GENE));
            if (identifier == null || unreliable.contains(identifier)) continue;

            if (geneSynonymValues.containsKey(identifier)) {
                List<String> master = geneSynonymValues.get(identifier);
                List<String> current = getAttrValues(f, GENE_SYNONYM);

                // Add missing (preserving master order)
                for (String m : master) {
                    if (!current.contains(m)) current.add(m);
                }

                // Remove extras not present in master
                current.removeIf(s -> !master.contains(s));

                setAttrValues(f, GENE_SYNONYM, current);

            } else {
                // Reserve identifier with this feature's current synonym list (order-dependent).
                geneSynonymValues.put(identifier, new ArrayList<>(getAttrValues(f, GENE_SYNONYM)));
            }
        }
    }


    private static List<String> getAttrValues(GFF3Feature f, String name) {
        Object raw = safeGet(f, name);
        List<String> out = new ArrayList<>();
        if (raw == null) return out;

        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) continue;
                String s = o.toString().trim();
                if (!s.isEmpty()) out.add(s);
            }
        } else {
            String s = raw.toString().trim();
            if (!s.isEmpty()) {
                for (String p : s.split(",")) {
                    String v = p.trim();
                    if (!v.isEmpty()) out.add(v);
                }
            }
        }
        return out;
    }

    private static void setAttrValues(GFF3Feature f, String name, List<String> values) {
        // Deduplicate while preserving order.
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String v : values) {
            if (v == null) continue;
            String t = v.trim();
            if (!t.isEmpty()) set.add(t);
        }
        List<String> cleaned = new ArrayList<>(set);
        if (cleaned.isEmpty()) {
            f.removeAttribute(name);
        } else {
            f.getAttributes().put(name, cleaned);
        }
    }

    private static boolean listsEqualOrdered(List<String> a, List<String> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

}
