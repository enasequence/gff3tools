# WGS contig CLI example

Example files for manually testing GFF3 → EMBL conversion of a WGS contig entry.

## Files

- `wgs_contig.gff3` — single contig GFF3 with a gene/mRNA/CDS annotation
- `wgs_master.json` — WGS set master entry (`dataClass=SET`, `contigDataclass=WGS`)

## Command

From the repo root:

```bash
java -jar build/libs/gff3tools-*-all.jar conversion \
  --master-entry src/test/resources/cli-examples/wgs/wgs_master.json \
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
| `RP` line on reference | `1-3118321` | Per-contig RP range |
| Source feature | no `/note="common name: ..."` qualifier | Common-name note suppressed for WGS contigs |
