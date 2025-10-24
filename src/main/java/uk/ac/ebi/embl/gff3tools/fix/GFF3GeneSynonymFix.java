package uk.ac.ebi.embl.gff3tools.fix;

import java.util.*;
import java.util.stream.Collectors;

import uk.ac.ebi.embl.api.validation.fixer.entry.GeneSynonymFix;
import uk.ac.ebi.embl.gff3tools.fftogff3.FeatureMapping;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Fix(
        name = "GFF3GeneSynonymFix",
        description = "Normalizes gene_synonym values across features sharing the same locus_tag/gene; CDS sets the canonical list",
        enabled = true)
public class GFF3GeneSynonymFix {

    private static final String FF_CDS = "CDS";
    private static final HashSet<String> CDS_SYNONYMS;
    private static final String LOCUS_TAG = GFF3Attributes.LOCUS_TAG;
    private static final String GENE = GFF3Attributes.GENE;
    private static final String GENE_SYNONYM = GFF3Attributes.GENE_SYNONYM;

    public GeneSynonymFix(){
        CDS_SYNONYMS.stream()
                .flatMap(FeatureMapping::getGFF3FeatureCandidateNames(FF_CDS));

        FeatureMapping.getGFF3FeatureCandidateNames(FF_CDS);
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
                .collect(Collectors.toList());
        if (candidates.isEmpty()) return;

        // identifier (locus_tag or gene) -> master list of synonyms
        Map<String, List<String>> id2Synonyms = new HashMap<>();
        // identifiers to skip due to CDS disagreements
        Set<String> unreliable = new HashSet<>();

        // Pass 1: establish master lists from CDS features (order-sensitive equality).
        for (GFF3Feature f : candidates) {
            if (!CDS.equalsIgnoreCase(f.getName())) continue;

            String identifier = firstNonBlank(getAttrScalar(f, LOCUS_TAG), getAttrScalar(f, GENE));
            if (identifier == null) continue;

            List<String> syns = getAttrValues(f, GENE_SYNONYM);

            if (unreliable.contains(identifier)) {
                continue;
            }

            if (id2Synonyms.containsKey(identifier)) {
                List<String> existing = id2Synonyms.get(identifier);
                if (!listsEqualOrdered(existing, syns)) { // order-sensitive
                    unreliable.add(identifier);
                    id2Synonyms.remove(identifier);
                }
            } else {
                id2Synonyms.put(identifier, new ArrayList<>(syns));
            }
        }

        // Pass 2: apply master to all features; if none exists and not unreliable, set from first seen.
        for (GFF3Feature f : candidates) {
            String identifier = firstNonBlank(getAttrScalar(f, LOCUS_TAG), getAttrScalar(f, GENE));
            if (identifier == null || unreliable.contains(identifier)) continue;

            if (id2Synonyms.containsKey(identifier)) {
                List<String> master = id2Synonyms.get(identifier);
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
                id2Synonyms.put(identifier, new ArrayList<>(getAttrValues(f, GENE_SYNONYM)));
            }
        }
    }

    /* ------------ Helpers: attribute access that tolerates String-or-List<Object> ------------ */

    /** Returns a scalar string value for an attribute (first item if stored as list). */
    private static String getAttrScalar(GFF3Feature f, String name) {
        Object raw = safeGet(f, name);
        if (raw == null) return null;
        if (raw instanceof List<?> list) {
            if (list.isEmpty()) return null;
            Object first = list.get(0);
            return first == null ? null : first.toString();
        }
        return raw.toString();
    }

    /** Returns values as a list of strings, splitting a string by commas when necessary. */
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

    /** Writes values back as a List<String> (so the writer will join with ','). Removes if empty. */
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

    private static Object safeGet(GFF3Feature f, String name) {
        Map<String, Object> attrs = f.getAttributes();
        return (attrs == null) ? null : attrs.get(name);
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
