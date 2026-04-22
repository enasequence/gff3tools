# AnnotationMetadata: Unified Metadata Provider for GFF3 Conversions

**Status**: Implemented (commits `351003c4`, `d1b05611`)
**Created**: 2026-04-14T11:17:14Z
**Timestamp**: 2604141117

---

## PART I: Design

### Problem (historical)

Before this work, GFF3 conversion had two separate, incompatible metadata inputs:

- **GFF3 → EMBL**: `FastaHeaderProvider` supplied a 6-field `FastaHeader` (description, moleculeType, topology, chromosomeType, chromosomeLocation, chromosomeName) to `GFF3Mapper`.
- **EMBL → GFF3**: `FFToGff3Converter` accepted a raw EMBL flatfile via `-m` and extracted `organism` from the parsed `Entry`.

Neither path accepted MasterEntry JSON — the richer format produced by EBI submission pipelines, a superset of `FastaHeader` covering full taxonomy, references, and assembly metadata. Users had to either downsize to `FastaHeader` JSON (losing metadata) or use an EMBL flatfile (one-direction only). No unified entry point existed.

---

### As shipped

**D1 — AnnotationMetadata model**
`uk.ac.ebi.embl.gff3tools.metadata.AnnotationMetadata` — 33-field POJO covering all MasterEntry JSON fields:

Core: `id`, `accession`, `secondaryAccessions`, `description`, `title`, `version`, `moleculeType`, `topology`, `division`, `dataClass`, `sequenceLength`, `project`, `sample`, `taxon`, `scientificName`, `commonName`, `lineage`, `keywords`, `comment`, `publications`, `references`, `chromosomeName`, `chromosomeType`, `chromosomeLocation`, `firstPublic`, `firstPublicRelease`, `lastUpdated`, `lastUpdatedRelease`, `md5`, `runAccession`, `assemblyLevel`, `assemblyType`, `searchFields`.

`topology` and `division` are optional in MasterEntry JSON. When `topology` is absent, EMBL writing defaults to `LINEAR` downstream.

**D2 — AnnotationMetadataProvider**
`uk.ac.ebi.embl.gff3tools.metadata.AnnotationMetadataProvider` — chain-of-responsibility with **first-match-wins** semantics (not field-level merging). Sources are queried in registration order; the first source returning a non-empty `Optional<AnnotationMetadata>` wins entirely. `getGlobalMetadata()` queries with empty seqId for the EMBL → GFF3 direction where no per-sequence context exists.

Two concrete sources are in use:
- `MasterEntryJsonMetadataSource` — wraps pre-deserialized `AnnotationMetadata` from a MasterEntry JSON file; global fallback (same metadata for all seqIds).
- `EmblEntryMetadataSource` — adapts a parsed EMBL `Entry` to `AnnotationMetadata` for backward-compatibility with `.embl`/`.ff` master files.

Note: the original spec proposed per-seqId FASTA-embedded + CLI FASTA JSON sources with field-level merging. Those were removed from the metadata pipeline in commit `d1b05611`. The FASTA-header plumbing (`FastaHeaderProvider`, `FastaHeaderSource`, `CliFastaHeaderSource`, `FileFastaHeaderSource`) remains in the codebase and is still the intended home for FASTA header data — it is now simply a separate concern from metadata.

**D3 — Jackson deserialization**
`AnnotationMetadata` deserializes directly from MasterEntry JSON. Scalars map 1:1; nested value types (`ReferenceData`, `CrossReference`) cover `references`/`publications`. Case-insensitive matching is enabled (consistent with `FastaHeader`). Unknown JSON properties are ignored for forward compatibility.

**D4 — GFF3 → EMBL field mapping**
`GFF3Mapper` consumes an `AnnotationMetadataProvider` via the `applyAnnotationMetadata()` method (`GFF3Mapper.java:311`). Null fields are silently skipped; no error is raised for missing optional fields.

| AnnotationMetadata field | EMBL target |
|---|---|
| `description` | DE (`entry.setDescription`) |
| `title` | DE (fallback when `description` absent) |
| `moleculeType` | `sequence.setMoleculeType` + source `/mol_type` qualifier |
| `topology` | `sequence` topology (via `mapTopology`) |
| `chromosomeType` + `chromosomeName` | source `/chromosome` qualifier (via `mapChromosomeType`) |
| `chromosomeName` alone | source `/chromosome` qualifier |
| `chromosomeLocation` | source `/organelle` qualifier (via `mapChromosomeLocation`) |
| `division` | `entry.setDivision` |
| `dataClass` | `entry.setDataClass` |
| `accession` | `entry.setPrimaryAccession` when GFF3 `##sequence-region` provides none |
| `secondaryAccessions` | AC (`entry.addSecondaryAccession`) |
| `version` | `entry.setVersion` (+ `sequence.setVersion` if region has no version) |
| `keywords` | KW (`entry.addKeyword`) |
| `comment` | CC (`entry.setComment`) |
| `taxon` | source `/db_xref "taxon:<value>"` |
| `scientificName` | source `/organism` |
| `commonName` | source `/note` prefixed `"common name: "` |
| `lineage` | OC via `sourceFt.getTaxon().setLineage(...)`; also populates `scientificName`, `commonName`, `taxId` on the `Taxon` if unset |
| `project` | PR (`entry.addProjectAccession`) |
| `references` | RF (referenceNumber, title, authors, location, consortium) |
| `md5` | DR `MD5` cross-reference |
| `runAccession` | DR `ENA` cross-reference per run |
| `publications` | DR cross-reference per entry; `BioProject` entries skipped (handled via `project` → PR) |
| `firstPublic` | DT first public date |
| `firstPublicRelease` | DT first public release |
| `lastUpdated` | DT last updated date |
| `lastUpdatedRelease` | DT last updated release |
| `sequenceLength` | `entry.setIdLineSequenceLength`; sets `annotationOnlyCON` for non-SET entries so the ID writer emits the length |
| `sample` | DR `BioSample` cross-reference (only for `SAMEA`-prefixed accessions; ERS skipped) |
| `searchFields` | source feature qualifiers (e.g. `geo_loc_name`, `collection_date`, `isolate`) |

Fields in the model but not mapped to any EMBL line: `id`, `assemblyLevel`, `assemblyType` — no standard per-sequence EMBL representation.

**D5 — `--master-entry` CLI option**
`AbstractCommand` exposes `--master-entry` / `-m` as a unified metadata input. File type is dispatched by extension in `parseMasterEntrySource()` (`AbstractCommand.java:249`):

- `.json` → `MasterEntryJsonMetadataSource`
- `.embl`, `.ff` → `EmblEntryMetadataSource` (parses via EMBL flatfile reader)

Accepted in both conversion directions.

**D6 — `--fasta-header` scope**
`--fasta-header` is a valid, supported option — it feeds `FastaHeaderProvider` as a global FASTA-header fallback. It is **not** a metadata source: the contents of the file do not populate `AnnotationMetadata` and do not influence EMBL metadata lines. Metadata comes exclusively from `--master-entry`. Because the two options address different concerns, they can be supplied together without conflict; no mutual-exclusion check is enforced.

**D7 — EMBL → GFF3 direction**
`FFToGff3Converter` accepts a pre-built `AnnotationMetadata` in its constructor and passes it to `GFF3FileFactory`. `GFF3DirectivesFactory.createSpecies()` derives the taxonomy URL from `AnnotationMetadata.scientificName` and `AnnotationMetadata.taxon`, falling back to the `Entry`'s source feature `/organism` qualifier if metadata is null.

**D8 — Backward compatibility**
Existing test fixtures using `.embl`/`.ff` master files via `-m` continue to work unchanged through `EmblEntryMetadataSource`. `--fasta-header` continues to populate the FASTA header provider as before; what changed is only that it no longer contributes to `AnnotationMetadata` — any metadata previously sourced from `--fasta-header` JSON must now be supplied via `--master-entry`.

---

### Success Criteria

1. GFF3 → EMBL with `--master-entry master.json` populates DE, ID, KW, CC, OC, PR, DR, DT, RF, and source qualifiers from the JSON. ✔
2. EMBL → GFF3 with `--master-entry master.json` produces the correct `##species` directive from `scientificName`/`taxon`. ✔
3. EMBL → GFF3 with `--master-entry master.embl` matches prior output (via `EmblEntryMetadataSource`). ✔
4. ~~`--fasta-header` + `--master-entry` exits non-zero.~~ **Dropped** — the options address different concerns (FASTA header vs metadata) and may be combined.
5. `--fasta-header` continues to feed `FastaHeaderProvider` (its behaviour pre-refactor minus any metadata effect). ✔
6. ~~Per-seqId FASTA headers override `--master-entry` for metadata fields.~~ **Dropped** — FASTA-embedded headers no longer contribute to metadata; `--master-entry` is the sole metadata source.
7. All existing tests pass. ✔

---

### Out of Scope

- Writing MasterEntry JSON (read-only).
- Remote sources (ENA REST etc.) — `AnnotationMetadataSource` interface is extensible but no remote source is implemented.
- Content validation of field values (handled downstream by EMBL validation).
- Per-seqId MasterEntry files (always global, single-entry).
- Removing `--fasta-header` — retained as a non-metadata FASTA header input.
- Changes to GFF3 output format beyond the `##species` directive.

---

### Resolved Decisions

- **Topology default**: `LINEAR` when absent from all sources (applied by downstream EMBL writing).
- **assemblyLevel / assemblyType**: carried on the model, not mapped to any EMBL line.
- **EmblEntryMetadataSource coverage**: maps all `Entry` fields (not just those used by `createSpecies()`).
- **Publications DR format**: `source` → database name, `id` → primary identifier; `url` not written. `BioProject` entries are suppressed because `project` already populates the PR line.
- **Provider merge strategy**: first-match-wins over sources (not field-level merge). Simpler to reason about and sufficient since MasterEntry and EMBL-flatfile sources are both global.
- **FASTA-header metadata sources**: removed from the metadata pipeline. `--master-entry` is the sole metadata input. `FastaHeaderProvider` and `--fasta-header` are retained for FASTA header data, which is a distinct concern.

---

## PART II: Key Files

| Concern | File |
|---|---|
| POJO + Jackson deserialization | `src/main/java/uk/ac/ebi/embl/gff3tools/metadata/AnnotationMetadata.java` |
| Provider (first-match-wins) | `src/main/java/uk/ac/ebi/embl/gff3tools/metadata/AnnotationMetadataProvider.java` |
| Source interface | `src/main/java/uk/ac/ebi/embl/gff3tools/metadata/AnnotationMetadataSource.java` |
| MasterEntry JSON source | `src/main/java/uk/ac/ebi/embl/gff3tools/metadata/MasterEntryJsonMetadataSource.java` |
| EMBL flatfile adapter | `src/main/java/uk/ac/ebi/embl/gff3tools/metadata/EmblEntryMetadataSource.java` |
| GFF3 → EMBL mapping | `src/main/java/uk/ac/ebi/embl/gff3tools/gff3toff/GFF3Mapper.java` (`applyAnnotationMetadata`) |
| EMBL → GFF3 consumer | `src/main/java/uk/ac/ebi/embl/gff3tools/fftogff3/FFToGff3Converter.java` |
| `##species` directive | `src/main/java/uk/ac/ebi/embl/gff3tools/fftogff3/GFF3DirectivesFactory.java` (`createSpecies`) |
| `--master-entry` CLI + extension dispatch | `src/main/java/uk/ac/ebi/embl/gff3tools/cli/AbstractCommand.java` (`parseMasterEntrySource`) |
| `--fasta-header` option (non-metadata FASTA header input) | `src/main/java/uk/ac/ebi/embl/gff3tools/cli/SequenceOptions.java` |
