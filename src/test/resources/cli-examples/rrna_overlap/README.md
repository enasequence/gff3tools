# rRNA overlap CLI example

Example file for `RRNA_FEATURE_OVERLAP`, which only reports overlaps on the same strand.

## Files

- `rrna_cds_opposite_strand.gff3` — an rRNA on `-` (12006..16752) with a CDS on `+` (13566..14138) inside it

## Command

From the repo root:

```bash
java -jar build/libs/gff3tools-*-all.jar validation \
  src/test/resources/cli-examples/rrna_overlap/rrna_cds_opposite_strand.gff3
```

## What to verify in the output

Validation passes with exit code `0`. Changing the CDS strand to `-` makes it fail with
`RRNA_FEATURE_OVERLAP` (exit code `20`).
