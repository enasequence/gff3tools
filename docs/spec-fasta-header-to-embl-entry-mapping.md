# Spec: FASTA Header JSON Fields → EMBL Entry Mapping During GFF3-to-EMBL Conversion

## Overview

When converting GFF3 files to EMBL flat-file format, users may supply a FASTA sequence file
via `--sequence`. Each FASTA record's header carries a structured JSON payload (parsed by
`JsonHeaderParser` into a `FastaHeader` object). This feature uses the parsed header data to
populate the corresponding EMBL `Entry` fields — description, molecule type, topology, and
chromosome-related qualifiers — removing the need for a separate master file or
post-processing step.

A CLI option (`--fasta-header`) also allows users to supply header metadata via a JSON file,
without needing a FASTA file or to supplement headers not embedded in the FASTA file.

---

## Functional Requirements

1. **FR-1 — Description mapping**
   Populate `Entry.description` (DE line) from the `description` field.

2. **FR-2 — Molecule-type mapping**
   Populate `Sequence.moleculeType` (ID line field 4) and add `/mol_type` qualifier on the
   source feature from the `molecule_type` field.

3. **FR-3 — Topology mapping**
   Parse `topology` case-insensitively (`"linear"` / `"circular"`) and set
   `Sequence.Topology`. Unrecognised values log a warning and are skipped.

4. **FR-4 — Chromosome name mapping**
   When `chromosome_name` is present, add a `/chromosome` qualifier to the source feature.

5. **FR-5 — Chromosome type and location mapping**
   `chromosome_type` maps to a source feature qualifier:

   | `chromosome_type` | Qualifier        |
   |--------------------|-----------------|
   | Chromosome         | `/chromosome`   |
   | Plasmid            | `/plasmid`      |
   | Segment            | `/segment`      |
   | Linkage Group      | `/linkage_group`|
   | Monopartite        | _(no qualifier)_|

   `chromosome_location` maps to `/organelle` (lowercased). `"Nuclear"` produces no qualifier.
   All other values are passed through; downstream EMBL validation checks qualifier values.

6. **FR-6 — Per-entry lookup**
   Each GFF3 annotation's seqId is looked up against headers independently. A multi-entry
   GFF3 file can reference multiple FASTA sequences, each with its own header metadata.

7. **FR-7 — Graceful fallback**
   If no header source is available or no header is found for the current seqId, the entry
   is written with default placeholders. No error is raised; a debug-level log is emitted.
   Note: FASTA files with non-JSON headers will still fail at parse time
   (`JsonHeaderParser.validateHeaderLine()` throws for headers lacking the `|` separator).

8. **FR-8 — Validation of topology and molecule_type**
   Unrecognised topology values log a warning and are skipped. Invalid `molecule_type` values
   are passed through as-is (the EMBL writer validates downstream).

9. **FR-9 — Backwards compatibility**
   Existing behaviour when neither `--sequence` nor `--fasta-header` is provided, or when
   sequences are provided as plain (non-FASTA) files, remains unchanged.

10. **FR-10 — Direct CLI header input (`--fasta-header`)**
    Accepts a path to a JSON file whose contents are a valid `FastaHeader` JSON object (same
    field names and Jackson `@JsonProperty` / `@JsonAlias` variants as the in-file payload).
    The parsed header is applied globally as a fallback for all seqIds.

11. **FR-11 — Precedence when both `--sequence` and `--fasta-header` are provided**
    FASTA-embedded headers take precedence per-seqId. The `--fasta-header` value acts as a
    global fallback for seqIds not found in the FASTA file.

---

## Design

### FastaHeader Field → EMBL Target Table

| FastaHeader field      | Mandatory | Target EMBL API call                                              | EMBL output location        |
|------------------------|-----------|-------------------------------------------------------------------|-----------------------------|
| `description`          | Yes       | `entry.setDescription(new Text(value))`                           | DE line                     |
| `molecule_type`        | Yes       | `sequence.setMoleculeType(value)` + `sourceFt.addQualifier("mol_type", value)` | ID line field 4 + FT source |
| `topology`             | Yes       | `sequence.setTopology(Sequence.Topology.LINEAR/CIRCULAR)`         | ID line field 3             |
| `chromosome_name`      | No        | `sourceFt.addQualifier("chromosome", value)`                      | FT source /chromosome       |
| `chromosome_type`      | No        | mapped to `/plasmid`, `/segment`, `/chromosome`, or `/linkage_group` | FT source qualifier      |
| `chromosome_location`  | No        | `sourceFt.addQualifier("organelle", lowercased_value)` (when non-nuclear) | FT source /organelle |

### Header Source Architecture

Header resolution is separate from sequence retrieval:

- **`FastaHeaderSource`** — interface: `Optional<FastaHeader> getHeader(String seqId)`
- **`FileFastaHeaderSource`** — backed by the header map parsed from `FileSequenceSource`
- **`CliFastaHeaderSource`** — returns a single CLI-supplied header for any seqId
- **`FastaHeaderProvider`** — composite that chains sources in priority order (first non-empty wins)

`SequenceSource` and `CompositeSequenceProvider` are unchanged.

### Header Source Precedence

1. **FASTA-embedded headers** (`FileFastaHeaderSource`) — highest priority, per-seqId
2. **CLI global header** (`CliFastaHeaderSource`) — lowest priority, global fallback

### Data Flow

```
--sequence seqs.fasta          --fasta-header header.json
        │                                    │
        ▼                                    ▼
FileSequenceSource              (parse JSON / read file)
  seqIdToOrdinal: {seq1→0}              │
  seqIdToHeader:  {seq1→…}              │
        │                               │
        ▼                               ▼
FileFastaHeaderSource            CliFastaHeaderSource
  .getHeader(seqId)                .getHeader(seqId) → same FastaHeader for any seqId
  ← registered first               ← registered last (lowest priority)
        │                               │
        └──────────────┬────────────────┘
                       ▼
            FastaHeaderProvider
              .getHeader(seqId)
              (first non-empty wins → FASTA headers take precedence)
                       │
                       ▼
         Gff3ToFFConverter ──→  GFF3Mapper(gff3Reader, headerProvider)
                                       │
                                       ▼
                          mapGFF3ToEntry(annotation)
                                │
                                ├── setPrimaryAccession()
                                ├── setSequence()
                                ├── applyFastaHeader()
                                │     ├── entry.setDescription()
                                │     ├── sequence.setMoleculeType()
                                │     ├── sequence.setTopology()
                                │     ├── sourceFt /mol_type qualifier
                                │     ├── sourceFt /chromosome qualifier
                                │     ├── sourceFt /organelle qualifier
                                │     └── sourceFt /plasmid (or other) qualifier
                                └── mapGFF3Features()
                                           │
                                           ▼
                                    EmblEntryWriter.write()
```

---

## Open Questions

- **`molecule_type` vocabulary normalisation**: Should the implementation enforce the ENA
  controlled vocabulary or continue to pass through raw values from the header?
- **EMBL `dataClass` and `division` fields**: Should any header field drive
  `entry.setDataClass()` or `entry.setDivision()`? Currently left as defaults.
- **`--fasta-header` per-seqId mapping**: The current design applies a single CLI-supplied
  header globally. A future extension could accept a JSON map of
  `{ "seqId": { ...header... } }` for per-seqId overrides.
