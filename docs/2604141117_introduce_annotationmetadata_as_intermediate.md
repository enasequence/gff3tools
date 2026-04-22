# AnnotationMetadata: Unified Metadata Provider for GFF3 Conversions

**Status**: Draft
**Created**: 2026-04-14T11:17:14Z
**Timestamp**: 2604141117

---

## PART I: Requirements

### Problem

GFF3 conversion has two separate, incompatible metadata inputs:

- **GFF3 → EMBL**: `FastaHeaderProvider` supplies a 6-field `FastaHeader` (description, moleculeType, topology, chromosomeType, chromosomeLocation, chromosomeName) to `GFF3Mapper`.
- **EMBL → GFF3**: `FFToGff3Converter` accepts a raw EMBL flatfile via `-m` and extracts `organism` from the parsed `Entry`.

Neither path accepts MasterEntry JSON — the richer format produced by EBI submission pipelines, a superset of `FastaHeader` covering full taxonomy, references, and assembly metadata. Users must either downsize to `FastaHeader` JSON (losing metadata) or use an EMBL flatfile (one-direction only). No unified entry point exists.

---

### Requirements

**R1 — AnnotationMetadata model**
New POJO unioning all fields needed by both directions. Covers the full MasterEntry JSON schema: `id`, `accession`, `secondaryAccessions`, `description`, `title`, `version`, `moleculeType`, `topology`, `division`, `dataClass`, `project`, `sample`, `taxon`, `scientificName`, `commonName`, `lineage`, `keywords`, `comment`, `publications`, `chromosomeName`, `chromosomeType`, `chromosomeLocation`, `references`, `firstPublic`, `lastUpdated`, `assemblyLevel`, `assemblyType`. `topology` and `division` are optional; `topology` defaults to `LINEAR` when absent from all sources. Shared fields keep `FastaHeader` semantics.

**R2 — AnnotationMetadataProvider**
Replaces `FastaHeaderProvider`. Chain-of-responsibility: accepts multiple `AnnotationMetadataSource` instances in priority order and returns a single merged `AnnotationMetadata` per `seqId` by collecting field-by-field (highest-priority non-null wins; nulls don't shadow). Replaces the previous first-match-wins strategy.

Built-in sources:
- `EmbeddedFastaMetadataSource` — `FastaHeader`-format JSON from FASTA headers.
- `CliJsonMetadataSource` — `FastaHeader`-format JSON via legacy `--fasta-header`.
- `MasterEntryJsonMetadataSource` — MasterEntry JSON as a global fallback (same `seqId` for all lookups).

**R3 — Jackson deserialization**
`AnnotationMetadata` must deserialize directly from MasterEntry JSON. Scalars 1:1; nested value types (`ReferenceData`, `CrossReference`) for `references`/`publications`. Case-insensitive matching enabled (same as `FastaHeader`). Unknown properties ignored.

**R4 — Full GFF3 → EMBL field mapping**
`GFF3Mapper` consumes `AnnotationMetadataProvider`. `applyFastaHeader()` becomes `applyAnnotationMetadata()`, mapping:

| AnnotationMetadata field | EMBL target |
|---|---|
| `description` | DE (`entry.setDescription`) |
| `title` | DE (used when `description` is absent) |
| `moleculeType` | ID field 4 + `/mol_type` source qualifier |
| `topology` | ID field 3 (`LINEAR`/`CIRCULAR`) |
| `chromosomeType` + `chromosomeName` | source qualifier (unchanged) |
| `chromosomeLocation` | `/organelle` source qualifier (unchanged) |
| `division` | ID taxonomic division |
| `dataClass` | ID data class |
| `accession` | primary accession when GFF3 `##sequence-region` provides none |
| `secondaryAccessions` | AC |
| `version` | ID version |
| `keywords` | KW |
| `comment` | CC |
| `taxon` | source `/db_xref "taxon:<value>"` |
| `scientificName` | source `/organism` |
| `commonName` | source `/note` prefixed `"common name: "` |
| `lineage` | OC |
| `project` | PR project accession |
| `references` | RF (referenceNumber, title, authors, location, consortium) |
| `publications` | DR (source + id) |
| `firstPublic` | DT first public |
| `lastUpdated` | DT last updated |

Null fields silently skipped. `assemblyLevel`/`assemblyType` are carried but not mapped — no standard per-sequence EMBL representation.

**R5 — `--master-entry` CLI option**
New named option (short `-m`, replacing the existing unnamed `-m` in `AbstractCommand`). Path; type inferred from extension:
- `.json` → `MasterEntryJsonMetadataSource`
- `.embl`/`.ff` → `EmblEntryMetadataSource` adapter (backward compat)

Accepted in both conversion directions.

**R6 — `--fasta-header` deprecation**
Deprecated but functional alone. Combining `--fasta-header` and `--master-entry` is a CLI error; `FileConversionCommand` fails fast before conversion.

**R7 — EMBL → GFF3 direction**
`FFToGff3Converter` and `GFF3DirectivesFactory` consume `AnnotationMetadata` instead of `Entry`. `createSpecies()` derives the taxonomy URL from `scientificName`/`taxon`.

**R8 — Backward compatibility**
All existing tests pass unmodified. `--fasta-header` alone produces identical output.

---

### Success Criteria

1. GFF3 → EMBL with `--master-entry master.json` populates DE, ID, KW, CC, OC, PR, DR, DT, RF, and source qualifiers from the JSON.
2. EMBL → GFF3 with `--master-entry master.json` produces the correct `##species` directive from `scientificName`/`taxon`.
3. EMBL → GFF3 with `--master-entry master.embl` matches today's output (backward compat).
4. `--fasta-header` + `--master-entry` together exits non-zero with a message naming both options.
5. `--fasta-header` alone matches today's output.
6. Per-seqId FASTA headers override `--master-entry` for the six overlapping `FastaHeader` fields.
7. All existing tests pass unmodified.

---

### Out of Scope

- Writing MasterEntry JSON (read-only).
- Remote sources (ENA REST etc.) — interface must be extensible, nothing implemented.
- Content validation of field values (handled downstream by EMBL validation).
- Per-seqId MasterEntry files (always global, single-entry).
- Removing `--fasta-header`.
- Changes to GFF3 output format.

---

### Resolved Questions

- **Topology default**: `LINEAR` when absent from all sources.
- **assemblyLevel / assemblyType**: carried on model, not mapped to EMBL.
- **EmblEntryMetadataSource coverage**: maps all `Entry` fields, not just those used by `createSpecies()`.
- **Publications DR format**: `source` → database, `id` → primary id; `url` not written.

---

## PART II: Implementation Plan

| Phase | Focus | Effort |
|---|---|---|
| 1 | `AnnotationMetadata` model + Jackson deserialization (MasterEntry + FastaHeader JSON) | 1d |
| 2 | `AnnotationMetadataProvider` with field-level merge + 3 built-in sources | 1d |
| 3 | Migrate `GFF3Mapper` to `AnnotationMetadataProvider`; expand `applyAnnotationMetadata()` for all new fields | 2d |
| 4 | `--master-entry` CLI option with extension dispatch; mutual exclusion with `--fasta-header`; wire both directions | 1d |
| 5 | EMBL → GFF3: `EmblEntryMetadataSource` adapter; update `FFToGff3Converter` + `GFF3DirectivesFactory` | 1d |
| 6 | Tests: per-source + merged provider unit tests; integration tests both directions with MasterEntry JSON and EMBL master | 1.5d |
