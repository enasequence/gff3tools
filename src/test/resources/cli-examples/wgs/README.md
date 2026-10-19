# WGS contig CLI example

Example files for manually testing GFF3 → EMBL conversion of a WGS contig entry.

## Files

- `wgs_contig.gff3` — single contig GFF3 with a gene/mRNA/CDS annotation
- `wgs_master.json` — WGS set master entry (`dataClass=SET`, `contigDataclass=WGS`)
- `wgs_master.embl` — the same WGS set master as an EMBL flat file; the `WGS` line marks it as a WGS set
  (the `ID` line `SET` alone does not). Produces the same contig-level output as the JSON master
  (`ID`/`KW`/`DR ... SET`); references, runs and BioSample are only mapped from the JSON master.

## Command

From the repo root:

```bash
# JSON master (maps references, runs and BioSample as well)
java -jar build/libs/gff3tools-*-all.jar conversion \
  --master-entry src/test/resources/cli-examples/wgs/wgs_master.json \
  src/test/resources/cli-examples/wgs/wgs_contig.gff3 \
  /tmp/wgs_contig_out.embl

# EMBL master (same contig-level output, but no references/runs/BioSample mapping)
java -jar build/libs/gff3tools-*-all.jar conversion \
  --master-entry src/test/resources/cli-examples/wgs/wgs_master.embl \
  src/test/resources/cli-examples/wgs/wgs_contig.gff3 \
  /tmp/wgs_contig_out.embl
```

## What to verify in the output

| Field | Expected value | What it tests |
|-------|---------------|---------------|
| `ID` line data class | `WGS` | `contigDataclass` used instead of `SET` |
| `ID` line length | `3118321 BP` | Per-contig length from `##sequence-region`, not the SET-level count (`11435`) |
| `KW` line | contains `WGS.` | WGS keyword added when absent from master |
| `DR` lines | two `ENA; ...; SET.` entries: `CAXMYH010000000` and `CAXMYH000000000` | Master accession + root-set cross-references |
| `RP` line on reference | `1-3118321` | Per-contig RP range (JSON master only; the EMBL master carries no references) |
| Source feature | no `/note="common name: ..."` qualifier | Common-name note suppressed for WGS contigs |
