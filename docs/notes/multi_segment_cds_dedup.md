## Multi-segment CDS translation deduplication — needs expert validation

**Date:** 2026-04-07

**Context:** During the spec discovery for moving translation FASTA writing into
`GFF3File.writeGFF3String()`, we identified that `TranslationFix.fixAnnotation()`
sets the `translation` attribute on every segment of a multi-segment CDS join
(TranslationFix.java line 139). This means a 3-exon CDS produces 3 features with
the same ID and identical translation string.

**Potential bug:** `TranslationCommand.writeTranslationEntries()` (lines 174–197)
iterates all features and calls `TranslationWriter.writeTranslation()` once per
feature with no deduplication. This may produce duplicate FASTA records for
multi-segment CDS joins.

**Proposed fix:** Use a `LinkedHashMap<String, String>` (translationKey → sequence)
when collecting translations during the write phase, so duplicate keys from join
segments are naturally deduplicated.

**Action required:** Validate with a domain expert whether:
1. The current behavior (multiple identical FASTA records per join) is actually a bug
2. The proposed deduplication-by-key approach is correct for all biological cases
3. There are edge cases where segments of the same join could legitimately have
   different translations
