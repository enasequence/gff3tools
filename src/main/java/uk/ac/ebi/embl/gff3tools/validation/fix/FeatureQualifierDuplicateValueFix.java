package uk.ac.ebi.embl.gff3tools.validation.fix;

import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;

import java.util.*;

@Gff3Fix
public class FeatureQualifierDuplicateValueFix{

    private static final String LOCUS_TAG = "locus_tag";
    private static final String OLD_LOCUS_TAG = "old_locus_tag";

    public GFF3Feature fix(GFF3Feature feature) {
        if (feature == null) return null;
        Map<String, Object> attrs = feature.getAttributes();
        if (attrs == null || attrs.isEmpty()) return feature;

        // 1) Current locus_tag (prefer first non-empty if a list sneaks in)
        String locusTag = firstNonEmpty(asStringList(attrs.get(LOCUS_TAG))).orElse(null);

        // 2) All old_locus_tag values
        List<String> oldValues = asStringList(attrs.get(OLD_LOCUS_TAG));
        if (oldValues.isEmpty()) return feature;

        // 3) De-duplicate while preserving order
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> deduped = new ArrayList<>();
        for (String v : oldValues) {
            if (v == null) continue;
            String vv = v; // keep exact value (flatfile behavior); we already trimmed tokens on split
            if (seen.add(vv)) {
                deduped.add(vv);
            }
        }

        // 4) Remove entries equal to current locus_tag (if present)
        if (locusTag != null) {
            // Match exact string (post-token-trim); mirror flatfile semantics (no case folding)
            deduped.removeIf(v -> v.equals(locusTag));
        }

        // 5) Write back
        if (deduped.isEmpty()) {
            attrs.remove(OLD_LOCUS_TAG);
        } else if (deduped.size() == 1) {
            attrs.put(OLD_LOCUS_TAG, deduped.get(0));
        } else {
            attrs.put(OLD_LOCUS_TAG, deduped);
        }

        return feature;
    }

    /** Convert attribute value to a list of strings. Handles String, List, and comma-joined String. */
    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object raw) {
        if (raw == null) return Collections.emptyList();

        if (raw instanceof List<?>) {
            List<?> in = (List<?>) raw;
            List<String> out = new ArrayList<>(in.size());
            for (Object o : in) {
                if (o == null) continue;
                String s = o.toString();
                // If client already stored list elements with commas inside, we *do not* split again.
                out.add(s);
            }
            return out;
        }

        // String case: split on commas per GFF3 multi-value convention
        String s = raw.toString();
        if (s.indexOf(',') < 0) {
            return s.isEmpty() ? Collections.emptyList() : Collections.singletonList(s);
        }
        String[] parts = s.split(",", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            String token = p.trim(); // trim token boundaries (common in hand-built strings)
            if (!token.isEmpty()) out.add(token);
        }
        return out;
    }

    private Optional<String> firstNonEmpty(List<String> vals) {
        for (String v : vals) {
            if (v != null && !v.isEmpty()) return Optional.of(v);
        }
        return Optional.empty();
    }
}

