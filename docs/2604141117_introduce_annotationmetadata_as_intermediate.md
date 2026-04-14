# AnnotationMetadata: Unified Metadata Provider for GFF3 Conversions

**Status**: Draft
**Created**: 2026-04-14T11:17:14Z
**Timestamp**: 2604141117

---

## PART I: Requirements

### Problem Statement

GFF3 conversion currently uses two separate, incompatible metadata inputs depending on direction:

- **GFF3 → EMBL**: `FastaHeaderProvider` chains up to three sources (FASTA-embedded headers, CLI `--fasta-header` JSON) and supplies a 6-field `FastaHeader` (description, moleculeType, topology, chromosomeType, chromosomeLocation, chromosomeName) to `GFF3Mapper`.
- **EMBL → GFF3**: `FFToGff3Converter` accepts a raw EMBL flatfile via `-m` and parses it into a bare `Entry` object, passing it to `GFF3DirectivesFactory.createSpecies()` to extract the `organism` qualifier.

Neither path can accept the richer MasterEntry JSON format that EBI submission pipelines produce. The MasterEntry format is a superset of `FastaHeader`, containing full taxonomy, accession metadata, references, publication cross-references, and assembly-level fields that currently have no path into the EMBL output.

The result is that users converting pipeline-generated GFF3 files must either construct a minimal `FastaHeader` JSON (losing most metadata) or pass a full EMBL flatfile (only supported in the EMBL → GFF3 direction). There is no unified, format-agnostic entry point for rich metadata in either conversion direction.

---

### Requirements

**R1 — AnnotationMetadata model**
Introduce `AnnotationMetadata` as a new POJO that is the union of all metadata fields needed by both conversion directions. The model must cover every field in the MasterEntry JSON schema (`id`, `accession`, `secondaryAccessions`, `description`, `title`, `version`, `moleculeType`, `topology`, `division`, `dataClass`, `project`, `sample`, `taxon`, `scientificName`, `commonName`, `lineage`, `keywords`, `comment`, `publications`, `chromosomeName`, `chromosomeType`, `chromosomeLocation`, `references`, `firstPublic`, `lastUpdated`, `assemblyLevel`, `assemblyType`). `topology` and `division` are optional fields in the MasterEntry JSON schema. `topology` defaults to `LINEAR` when absent from all sources. Fields present in both FastaHeader and MasterEntry use the same semantics.

**R2 — AnnotationMetadataProvider (chain-of-responsibility)**
Replace `FastaHeaderProvider` with `AnnotationMetadataProvider`, a chain-of-responsibility provider that:
- Accepts multiple `AnnotationMetadataSource` instances in priority order.
- Returns a single merged `AnnotationMetadata` for a given `seqId` by collecting from all sources field-by-field (highest-priority source wins per field; `null` fields in a higher-priority source do not shadow non-null fields in lower-priority sources).
- Replaces the current first-match-wins strategy in `FastaHeaderProvider`.

Three built-in sources replace the existing `FastaHeaderSource` implementations:
- `EmbeddedFastaMetadataSource` — reads `FastaHeader`-format JSON from FASTA sequence headers; maps `FastaHeader` fields onto `AnnotationMetadata` fields by name.
- `CliJsonMetadataSource` — reads a `FastaHeader`-format JSON file supplied via the legacy `--fasta-header` option; maps to `AnnotationMetadata`.
- `MasterEntryJsonMetadataSource` — reads a MasterEntry JSON file and deserializes it directly into `AnnotationMetadata`; acts as a global fallback (same `seqId` for all lookups).

**R3 — MasterEntry JSON deserialization**
`AnnotationMetadata` must be deserializable directly from a MasterEntry JSON document using Jackson. All scalar fields map 1:1. Complex sub-objects (`references`, `publications`) map to dedicated nested value types (`ReferenceData`, `CrossReference`). Case-insensitive property matching must be enabled (consistent with existing `FastaHeader` parsing). Unknown JSON properties must be ignored to allow forward-compatible schema evolution.

**R4 — Full GFF3 → EMBL field mapping in GFF3Mapper**
`GFF3Mapper` must consume `AnnotationMetadataProvider` instead of `FastaHeaderProvider`. The `applyFastaHeader()` method must be replaced with `applyAnnotationMetadata()` that maps all `AnnotationMetadata` fields to EMBL entry lines. Mappings required:

| AnnotationMetadata field | EMBL target |
|---|---|
| `description` | DE line (`entry.setDescription`) |
| `title` | DE line (used when `description` is absent) |
| `moleculeType` | ID line field 4 + `/mol_type` source qualifier |
| `topology` | ID line field 3 (`LINEAR` / `CIRCULAR`) |
| `chromosomeType` + `chromosomeName` | source feature qualifier (existing logic unchanged) |
| `chromosomeLocation` | `/organelle` source qualifier (existing logic unchanged) |
| `division` | ID line taxonomic division field |
| `dataClass` | ID line data class field |
| `accession` | primary accession (`entry.setPrimaryAccession`) when GFF3 `##sequence-region` provides no accession |
| `secondaryAccessions` | AC line (`entry.setSecondaryAccessions`) |
| `version` | ID line version |
| `keywords` | KW line |
| `comment` | CC line |
| `taxon` | source feature `/db_xref "taxon:<value>"` |
| `scientificName` | source feature `/organism` |
| `commonName` | source feature `/note` with prefix `"common name: "` |
| `lineage` | OC line |
| `project` | PR line project accession |
| `references` | RF lines (referenceNumber, title, authors, location, consortium) |
| `publications` | DR lines (source + id as cross-reference) |
| `firstPublic` | DT first public line |
| `lastUpdated` | DT last updated line |
Fields absent from a source (null) are silently skipped; no error is raised for missing optional fields.

Note: `assemblyLevel` and `assemblyType` are present in `AnnotationMetadata` (for completeness and round-tripping) but are **not mapped** to any EMBL line — they are assembly-wide metadata with no standard per-sequence EMBL representation.

**R5 — --master-entry CLI option**
Add `--master-entry` as a new named CLI option (with `-m` as the short alias, replacing the existing unnamed `-m` master file path parameter in `AbstractCommand`). The option accepts a file path. File type is inferred from extension:
- `.json` → parsed as MasterEntry JSON into `AnnotationMetadata` via `MasterEntryJsonMetadataSource`.
- `.embl` or `.ff` → parsed as EMBL flatfile into `Entry` and then converted to `AnnotationMetadata` via an `EmblEntryMetadataSource` adapter (for backward compatibility).

The `--master-entry` option is accepted in both conversion directions (GFF3 → EMBL and EMBL → GFF3).

**R6 — --fasta-header deprecation and mutual exclusion**
`--fasta-header` (in `SequenceOptions`) is deprecated but remains functional when used alone. Using both `--fasta-header` and `--master-entry` simultaneously is a CLI error; `FileConversionCommand` must fail fast with a descriptive message before any conversion begins.

**R7 — EMBL → GFF3 direction uses AnnotationMetadata**
`FFToGff3Converter` and `GFF3DirectivesFactory` must accept `AnnotationMetadata` instead of a raw `Entry` master. `GFF3DirectivesFactory.createSpecies()` derives the taxonomy URL from `AnnotationMetadata.scientificName` and `AnnotationMetadata.taxon` instead of reading an `organism` qualifier from a raw `Entry`.

**R8 — Backward compatibility**
All existing test scenarios using `FastaHeader` JSON or EMBL flatfile master entry must continue to pass without modification to test input files. The `--fasta-header` path continues to produce the same output as today.

---

### Success Criteria

1. A GFF3 file can be converted to EMBL using `--master-entry master.json` (MasterEntry JSON) and produce an EMBL flatfile containing DE, ID, KW, CC, OC, PR, DR, DT, RF, and source qualifier fields populated from the JSON.
2. An EMBL file can be converted to GFF3 using `--master-entry master.json` (MasterEntry JSON) and produce a GFF3 file with the correct `##species` directive derived from `scientificName`/`taxon`.
3. An EMBL file can be converted to GFF3 using `--master-entry master.embl` and produce the same output as today (backward compatibility).
4. Using both `--fasta-header` and `--master-entry` simultaneously exits with a non-zero status and an error message referencing both option names.
5. Using `--fasta-header` alone produces the same output as the current implementation.
6. Per-seqId FASTA-embedded headers take precedence over `--master-entry` for fields that overlap with `FastaHeader` (description, moleculeType, topology, chromosomeType, chromosomeLocation, chromosomeName).
7. All existing unit and integration tests pass without modification to their input files.

---

### Out of Scope

- **MasterEntry JSON writing**: This spec covers reading MasterEntry JSON only. No requirement to serialise `AnnotationMetadata` back to MasterEntry JSON format.
- **Remote metadata sources**: Plugin-supplied `AnnotationMetadataSource` implementations (e.g. fetching from ENA REST API) are a future concern. The provider interface must be designed for extensibility but no remote source is implemented here.
- **Validation of MasterEntry field values**: Content validation (e.g. checking that `moleculeType` is a valid INSDC vocabulary term) is handled downstream by the existing EMBL validation pipeline, not in the new metadata layer.
- **Per-seqId MasterEntry files**: MasterEntry JSON is always a global (single-entry) file. Per-seqId MasterEntry lookup is out of scope.
- **`--fasta-header` removal**: The option is deprecated but not removed in this implementation.
- **Changes to GFF3 output format**: The GFF3 → EMBL direction is the primary consumer of the new metadata. Extensions to the GFF3 output (e.g. writing metadata back to GFF3 directives) are not in scope.

---

### Open Questions

**~~OQ1~~ — `topology` field in MasterEntry** *(Resolved)*
The MasterEntry JSON schema will include an optional `topology` field. When no topology is provided by any source (neither MasterEntry JSON nor FASTA-embedded header), the default is `LINEAR`.

**~~OQ2~~ — `assemblyLevel` / `assemblyType` EMBL encoding** *(Resolved)*
These are assembly-wide metadata with no standard per-sequence EMBL representation. They are carried on `AnnotationMetadata` for completeness but not mapped to any EMBL line.

**~~OQ3~~ — `EmblEntryMetadataSource` field coverage** *(Resolved)*
`EmblEntryMetadataSource` must map all available `Entry` fields to `AnnotationMetadata` (not just the subset consumed by `GFF3DirectivesFactory`). This enables a richer round-trip and keeps the adapter consistent with the full `AnnotationMetadata` model.

**~~OQ4~~ — `publications` (CrossReference) DR line format** *(Resolved)*
`publications` maps to DR lines. `source` maps to the database name, `id` to the primary identifier. `url` is not written to the DR line.

---

## PART II: High-Level Implementation Plan

| Phase | Focus | Effort |
|-------|-------|--------|
| Phase 1 | Introduce `AnnotationMetadata` model and Jackson deserialization from MasterEntry JSON and FastaHeader JSON | 1 day |
| Phase 2 | Implement `AnnotationMetadataProvider` with field-level merging and three built-in sources (`EmbeddedFastaMetadataSource`, `CliJsonMetadataSource`, `MasterEntryJsonMetadataSource`) | 1 day |
| Phase 3 | Migrate `GFF3Mapper` from `FastaHeaderProvider` to `AnnotationMetadataProvider`; expand `applyAnnotationMetadata()` to map all new fields to EMBL lines | 2 days |
| Phase 4 | Add `--master-entry` CLI option with extension-based dispatch; enforce `--fasta-header` / `--master-entry` mutual exclusion; wire both conversion directions through `FileConversionCommand` | 1 day |
| Phase 5 | Migrate EMBL → GFF3 direction: `EmblEntryMetadataSource` adapter, update `FFToGff3Converter` and `GFF3DirectivesFactory` to consume `AnnotationMetadata` | 1 day |
| Phase 6 | Tests: unit tests for each source and the merged provider; integration tests for both conversion directions with MasterEntry JSON and backward-compat EMBL master | 1.5 days |
