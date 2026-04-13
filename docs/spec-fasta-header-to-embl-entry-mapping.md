# Spec: FASTA Header JSON Fields → EMBL Entry Mapping During GFF3-to-EMBL Conversion

## Overview

When converting GFF3 files to EMBL flat-file format, users may supply a FASTA sequence file
via `--sequence`. Each FASTA record's header already carries a structured JSON payload
(parsed by `JsonHeaderParser` into a `FastaHeader` object). However, the metadata fields in
that payload — `description`, `molecule_type`, `topology`, `chromosome_type`,
`chromosome_location`, and `chromosome_name` — are currently discarded after the sequence ID
is extracted. This means every generated EMBL entry has placeholder values (`XXX`) in the ID
line and a generic description.

This feature uses the already-parsed JSON header data to populate the corresponding EMBL
`Entry` fields, removing the need for a separate master file or post-processing step.

In addition, a new CLI option (`--fasta-header`) allows users to supply header metadata
via a path to a JSON file — without needing a FASTA file at all, or to supplement headers
not embedded in the FASTA file.

> **Code evidence**: `GFF3Mapper.mapGFF3Feature()` (line 131–136) already contains a comment
> stating that circular topology "will be provided by the fasta headers", confirming this
> feature is the intended resolution.

---

## Requirements

### Functional Requirements

1. **FR-1 — Description mapping**  
   When a `FastaHeader` for a given seqId is available, populate `Entry.description` with the
   `description` field value (DE line).

2. **FR-2 — Molecule-type mapping**  
   Populate `Sequence.moleculeType` (ID line, 4th field) and add a `/mol_type` qualifier on
   the source `Feature` using the `molecule_type` field value.

3. **FR-3 — Topology mapping**  
   Parse `topology` (case-insensitively: `"linear"` or `"circular"`) and set
   `Sequence.topology` to `Sequence.Topology.LINEAR` or `Sequence.Topology.CIRCULAR`
   respectively (ID line, 3rd field).

4. **FR-4 — Chromosome name mapping**  
   When `chromosome_name` is present in the header, add a `/chromosome` qualifier to the
   source feature.

5. **FR-5 — Chromosome type and location mapping**  
   When `chromosome_type` is present, map it to the appropriate EMBL source feature qualifier
   (e.g., `/plasmid` for type "plasmid"). When `chromosome_location` is present, map it to the
   `/organelle` qualifier on the source feature (e.g., "mitochondrion" → `/organelle`). The
   exact mapping table for all recognised type and location values must be defined before
   implementation (see Open Questions).

6. **FR-6 — Per-entry lookup**  
   Each GFF3 annotation's seqId is looked up against the FASTA headers independently. A
   multi-entry GFF3 file can reference multiple FASTA sequences, each with its own header
   metadata.

7. **FR-7 — Graceful fallback**  
   If no header source is available (neither `--sequence` nor `--fasta-header` is supplied),
   or no header is found for the current seqId, the entry is written with the current default
   placeholders. No error is raised; a debug-level log message is emitted.  
   Note: `JsonHeaderParser.validateHeaderLine()` currently throws `FastaHeaderParserException`
   for any header line that lacks the `|` separator (i.e. headers with no JSON payload). The
   fallback applies at the lookup stage (seqId not found in the header map), not at parse time;
   a FASTA file with non-JSON headers will still fail to parse.

8. **FR-8 — Validation of topology and molecule_type**  
   If the topology string is not `"linear"` or `"circular"`, log a warning and skip topology
   mapping. Invalid `molecule_type` values are passed through as-is (the EMBL writer
   validates them downstream).

9. **FR-9 — Backwards compatibility**  
   Existing behaviour when neither `--sequence` nor `--fasta-header` is provided, or when
   sequences are provided as plain (non-FASTA) files, must remain unchanged.

10. **FR-10 — Direct CLI header input (`--fasta-header`)**  
    A new CLI option `--fasta-header` shall be provided that accepts a path to a JSON file
    whose contents are a valid `FastaHeader` JSON object.

    The JSON object follows the same schema as the in-file FASTA header JSON payload
    (same field names, same Jackson `@JsonProperty` / `@JsonAlias` variants). The parsed
    `FastaHeader` is applied globally — that is, it is used as the header for **all** seqIds
    for which no higher-priority source provides a header.

    This option enables users to supply header metadata without a FASTA file, e.g.:
    ```
    gff3tools convert --gff3 annotations.gff3 --fasta-header header.json
    ```

11. **FR-11 — Precedence when both `--sequence` and `--fasta-header` are provided**  
    When both options are supplied:
    - FASTA-embedded headers (from `--sequence`) take precedence for any seqId whose header
      is found in the FASTA file.
    - The `--fasta-header` value acts as a **global fallback** for seqIds whose headers are
      not found in the FASTA file.

    This design means `--fasta-header` supplements FASTA-embedded headers rather than
    overriding them, which is the least-surprising behaviour for mixed-source files: specific
    per-sequence metadata wins over global defaults.

    Rationale: if a user wants to override a specific FASTA-embedded header, they should edit
    the FASTA file. The global CLI header is intended as a convenience for cases where not all
    sequences have JSON headers, or when no FASTA file is available at all.

---

## Design

### FastaHeader Field → EMBL Target Table

| FastaHeader field      | Mandatory | Target EMBL API call                                              | EMBL output location        |
|------------------------|-----------|-------------------------------------------------------------------|-----------------------------|
| `description`          | Yes       | `entry.setDescription(new Text(value))`                           | DE line                     |
| `molecule_type`        | Yes       | `sequence.setMoleculeType(value)` + `sourceFt.addQualifier("mol_type", value)` | ID line field 4 + FT source |
| `topology`             | Yes       | `sequence.setTopology(Sequence.Topology.LINEAR/CIRCULAR)`         | ID line field 3             |
| `chromosome_name`      | No        | `sourceFt.addQualifier("chromosome", value)`                      | FT source /chromosome       |
| `chromosome_type`      | No        | mapped to `/plasmid`, `/segment`, or other qualifier (see §Open Questions) | FT source qualifier |
| `chromosome_location`  | No        | `sourceFt.addQualifier("organelle", mapped_value)` (when non-nuclear) | FT source /organelle   |

`FastaHeader` is defined in
`uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader` and uses Jackson
`@JsonProperty` / `@JsonAlias` annotations to accept multiple spelling variants
(e.g. `molecule_type`, `molecule-type`, `molecule type`).

### Header Source Abstraction

Header resolution is a separate concern from sequence retrieval and is modelled with its own
interface hierarchy:

- **`FastaHeaderSource`** — a standalone interface responsible solely for resolving a
  `FastaHeader` for a given sequence ID. It has no connection to `SequenceSource`.
- **`FileFastaHeaderSource`** — implements `FastaHeaderSource`; backed by the header map
  already parsed inside `FileSequenceSource`.
- **`CliFastaHeaderSource`** — implements `FastaHeaderSource`; holds a single `FastaHeader`
  supplied via `--fasta-header` and returns it for **any** seqId query.
- **`FastaHeaderProvider`** — a composite that chains multiple `FastaHeaderSource` instances
  in priority order and returns the first non-empty `Optional<FastaHeader>`.

The `SequenceSource` interface is **not modified** — it remains focused on sequence data
only. The `CompositeSequenceProvider` is likewise **not modified** for header lookup; it
continues to manage sequence retrieval.

### Header Source Precedence

Sources are registered in `FastaHeaderProvider` in the following order (highest priority first):

1. **FASTA-embedded headers** (`FileFastaHeaderSource`) — registered when `--sequence` is
   provided and the file contains JSON headers. Returns the per-seqId `FastaHeader` from the
   FASTA file; returns `Optional.empty()` for unknown seqIds.
2. **CLI global header** (`CliFastaHeaderSource`) — registered when `--fasta-header` is
   provided. Returns the same single `FastaHeader` for **any** seqId query.

Because the chain short-circuits on the first non-empty `Optional`, FASTA-embedded headers
win for any seqId they cover; the CLI header fills the gaps.

### Component Changes

#### 1. `FastaHeaderSource` — new interface

Create a new, focused interface:

```java
public interface FastaHeaderSource {
    Optional<FastaHeader> getHeader(String seqId);
}
```

This interface carries no sequence-retrieval responsibilities.

#### 2. `FileFastaHeaderSource` — new class backed by `FileSequenceSource` header data

`FileSequenceSource.buildIdMapping()` already parses every header into a `ParsedHeader`.
Extend `FileSequenceSource` to also store `Map<String, FastaHeader> seqIdToHeader` during
that scan:

```
// existing:
seqIdToOrdinal.put(submissionId, ordinal);

// new:
seqIdToHeader.put(submissionId, parsed.getHeader());
```

Then create a separate `FileFastaHeaderSource` class that wraps this map:

```java
public class FileFastaHeaderSource implements FastaHeaderSource {

    private final Map<String, FastaHeader> seqIdToHeader;

    public FileFastaHeaderSource(Map<String, FastaHeader> seqIdToHeader) {
        this.seqIdToHeader = seqIdToHeader;
    }

    @Override
    public Optional<FastaHeader> getHeader(String seqId) {
        return Optional.ofNullable(seqIdToHeader.get(seqId));
    }
}
```

`FileConversionCommand` constructs a `FileFastaHeaderSource` from the map exposed by
`FileSequenceSource` and registers it with `FastaHeaderProvider`.

#### 3. `CliFastaHeaderSource` — new class for CLI-supplied global header

```java
public class CliFastaHeaderSource implements FastaHeaderSource {

    private final FastaHeader header;

    public CliFastaHeaderSource(FastaHeader header) {
        this.header = header;
    }

    @Override
    public Optional<FastaHeader> getHeader(String seqId) {
        return Optional.of(header);
    }
}
```

`FileConversionCommand` reads the JSON file at the path supplied via `--fasta-header`,
deserialises it into a `FastaHeader` via Jackson's `ObjectMapper`, constructs a
`CliFastaHeaderSource`, and registers it with `FastaHeaderProvider` **after** any
`FileFastaHeaderSource` so that FASTA-embedded headers take precedence.

#### 4. `FastaHeaderProvider` — new composite

```java
public class FastaHeaderProvider {

    private final List<FastaHeaderSource> sources;

    public FastaHeaderProvider(List<FastaHeaderSource> sources) {
        this.sources = sources;
    }

    public Optional<FastaHeader> getHeader(String seqId) {
        for (FastaHeaderSource source : sources) {
            Optional<FastaHeader> header = source.getHeader(seqId);
            if (header.isPresent()) return header;
        }
        return Optional.empty();
    }
}
```

`FileConversionCommand` builds a `FastaHeaderProvider` (potentially with zero sources if
neither `--sequence` nor `--fasta-header` is supplied) and passes it to
`Gff3ToFFConverter`.

#### 5. `Gff3ToFFConverter` — accept and pass `FastaHeaderProvider`

Extend the constructor to accept an optional `FastaHeaderProvider`:

```java
public Gff3ToFFConverter(ValidationEngine engine, Path gff3Path,
                         CompositeSequenceProvider sequenceProvider,
                         FastaHeaderProvider headerProvider)
```

Store it as a field and pass it to each `GFF3Mapper` instance:

```java
writeEntry(new GFF3Mapper(gff3Reader, headerProvider), annotation, writer);
```

`CompositeSequenceProvider` is passed through unchanged for sequence retrieval.

#### 6. `GFF3Mapper` — apply header fields to `Entry`

Extend the constructor to accept a nullable `FastaHeaderProvider`. In
`mapGFF3ToEntry()`, after creating the `SourceFeature` and before `mapGFF3Features()`, call
a new private method `applyFastaHeader()`:

```java
private void applyFastaHeader(GFF3SequenceRegion sequenceRegion,
                               Entry entry,
                               Sequence sequence,
                               SourceFeature sourceFt) {

    if (headerProvider == null) return;

    String seqId = sequenceRegion.accessionId();
    Optional<FastaHeader> opt = headerProvider.getHeader(seqId);
    if (opt.isEmpty()) {
        LOGGER.debug("No FASTA header found for seqId '{}'; skipping header mapping", seqId);
        return;
    }

    FastaHeader h = opt.get();

    if (h.getDescription() != null) {
        entry.setDescription(new Text(h.getDescription()));
    }

    if (h.getMoleculeType() != null) {
        sequence.setMoleculeType(h.getMoleculeType());
        sourceFt.addQualifier("mol_type", h.getMoleculeType());
    }

    if (h.getTopology() != null) {
        mapTopology(h.getTopology(), sequence);
    }

    if (h.getChromosomeName() != null) {
        sourceFt.addQualifier("chromosome", h.getChromosomeName());
    }

    if (h.getChromosomeType() != null) {
        mapChromosomeType(h.getChromosomeType(), h.getChromosomeName(), sourceFt);
    }

    if (h.getChromosomeLocation() != null) {
        mapChromosomeLocation(h.getChromosomeLocation(), sourceFt);
    }
}
```

`mapTopology()` normalises the string case-insensitively; on unrecognised values it logs a
warning and skips. `mapChromosomeType()` and `mapChromosomeLocation()` apply the qualifier
mapping table once it is finalised (see Open Questions).

### Data Flow Diagram

```
--sequence seqs.fasta          --fasta-header header.json
        │                                    │
        ▼                                    ▼
FileSequenceSource              (parse JSON / read file)
  seqIdToOrdinal: {seq1→0}              │
  seqIdToHeader:  {seq1→…}              │
        │                               │
        ▼                               ▼
FileFastaHeaderSource (NEW)   CliFastaHeaderSource (NEW)
  .getHeader(seqId)             .getHeader(seqId) → same FastaHeader for any seqId
  ← registered first            ← registered last (lowest priority)
        │                               │
        └──────────────┬────────────────┘
                       ▼
            FastaHeaderProvider (NEW)
              .getHeader(seqId)
              (first non-empty wins → FASTA headers take precedence)
                       │
                       ▼
         Gff3ToFFConverter ──→  GFF3Mapper(gff3Reader, headerProvider)
                                       │
                                       ▼
                          mapGFF3ToEntry(annotation)
                                │
                                ├── setPrimaryAccession()  (existing)
                                ├── setSequence()           (existing)
                                ├── applyFastaHeader()      ← NEW
                                │     ├── entry.setDescription()
                                │     ├── sequence.setMoleculeType()
                                │     ├── sequence.setTopology()
                                │     ├── sourceFt /mol_type qualifier
                                │     ├── sourceFt /chromosome qualifier
                                │     ├── sourceFt /organelle qualifier
                                │     └── sourceFt /plasmid (or other) qualifier
                                └── mapGFF3Features()       (existing)
                                           │
                                           ▼
                                    EmblEntryWriter.write()

Note: CompositeSequenceProvider and SequenceSource are unchanged — they manage
      sequence data only and are not involved in header resolution.
```

---

## Implementation Phases

| Phase | Focus | Effort |
|-------|-------|--------|
| Phase 1 | Introduce `FastaHeaderSource` interface, `FileFastaHeaderSource`, `CliFastaHeaderSource`, and `FastaHeaderProvider`; store header map in `FileSequenceSource`; wire `FastaHeaderProvider` through `Gff3ToFFConverter` and `GFF3Mapper`; apply `description`, `molecule_type`, and `topology` to EMBL Entry; add `--fasta-header` CLI option (JSON file path input); add unit and integration tests including header-from-FASTA, `--fasta-header`-only, and mixed-source precedence scenarios | 4 days |
| Phase 2 | Implement `chromosome_name`, `chromosome_type`, and `chromosome_location` mappings to source feature qualifiers once the qualifier mapping table is agreed; add validation warnings for unrecognised topology or type values; extend integration tests with full header coverage | 2 days |

---

## Implementation Notes

- **`Text` class**: The `entry.setDescription()` call requires a
  `uk.ac.ebi.embl.api.entry.Text` wrapping the plain string. Use `new Text(value)` or
  `new Text(value, origin)`.

- **Topology enum**: `Sequence.Topology` has two values: `LINEAR` and `CIRCULAR`. A
  helper utility `TopologyMapper.fromString(String)` should be introduced to centralise
  the case-insensitive parse and warning.

- **Source feature qualifier ordering**: EMBL source feature qualifiers are written in the
  order they are added. Prefer adding `/mol_type` before chromosome-related qualifiers to
  match ENA submission conventions.

- **`molecule_type` normalisation**: The header stores the raw string (e.g. `"dna"`, `"rna"`,
  `"genomic DNA"`). The EMBL flat-file format expects specific vocabulary (e.g. `"genomic DNA"`,
  `"mRNA"`, `"rRNA"`). The spec leaves normalisation to Phase 2; Phase 1 passes the value
  through as-is, relying on the `EmblEntryWriter` to reject or quote invalid values.

- **`--fasta-header` file parsing**: Read the JSON file at the supplied path and deserialise
  its contents into a `FastaHeader` using Jackson's `ObjectMapper` configured with
  `FastaHeader`'s existing `@JsonProperty` / `@JsonAlias` annotations.

- **`--fasta-header` error handling**: If the JSON is malformed or the file path does not
  exist, fail fast with a descriptive error message before the conversion begins.

- **`SequenceSource` interface unchanged**: Header resolution is entirely separate from
  sequence retrieval. `SequenceSource` and `CompositeSequenceProvider` require no
  modifications for this feature.

- **`FastaHeaderProvider` with no sources**: When neither `--sequence` nor `--fasta-header`
  is supplied, `FileConversionCommand` constructs a `FastaHeaderProvider` with an empty
  source list (or passes `null`/an empty provider). `GFF3Mapper.applyFastaHeader()` detects
  this and skips header mapping, preserving existing placeholder behaviour.

- **Plain-sequence fallback**: When `--sequence` references a plain (non-FASTA) file, no
  `FileFastaHeaderSource` is created. If `--fasta-header` is also provided, the
  `CliFastaHeaderSource` provides the fallback header for all seqIds.

- **Multiple FASTA files**: If multiple `FileSequenceSource` instances are registered in
  `CompositeSequenceProvider`, a corresponding `FileFastaHeaderSource` is created for each
  and registered in `FastaHeaderProvider` in the same order. The first source that has a
  header for a given seqId wins.

- **Test resources**: Add new `gff3toff_rules` test pairs (`.gff3` + `.embl`) that exercise
  the full header-to-entry mapping, including: (a) header from FASTA file only, (b) header
  from `--fasta-header` only (no `--sequence`), and (c) mixed mode where the FASTA-embedded
  header is used for known seqIds and the CLI header fills in for unknown seqIds.

- **Existing tests**: All current `GFF3ToFFConverterTest` cases pass no `--sequence`, so
  they exercise the fallback path and must remain green without modification.

- **`GFF3Mapper` constructor**: The current constructor signature is
  `GFF3Mapper(GFF3FileReader gff3FileReader)` (line 54 of `GFF3Mapper.java`). The new
  overload should preserve the single-argument form as a no-op fallback to avoid breaking
  existing internal usages.

---

## Open Questions

1. **`chromosome_type` qualifier mapping table**: What is the complete set of recognised
   `chromosome_type` values (e.g. "Chromosome", "Plasmid", "Linkage Group") and their
   corresponding EMBL source feature qualifiers and qualifier names? This table must be
   finalised before Phase 2 can begin.

2. **`chromosome_location` qualifier mapping**: What organelle location strings are accepted
   (e.g. "Mitochondrion", "Plastid", "Nuclear", "Kinetoplast") and how do they map to
   `/organelle` qualifier values? "Nuclear" should produce no organelle qualifier.

3. **`molecule_type` vocabulary normalisation**: Should the implementation enforce the ENA
   controlled vocabulary for molecule types (e.g. always uppercase "DNA") or pass through
   the raw value from the header? If validation should fail on unrecognised values, should it
   be a warning or an error?

4. **EMBL `dataClass` and `division` fields**: Should `chromosome_type` or any other header
   field also drive `entry.setDataClass()` or `entry.setDivision()`? Or are those always left
   as defaults?

5. **Header-without-sequence**: If the user provides `--sequence` with a FASTA file for
   header metadata only (no sequences are actually translated), is that a supported use case?
   This affects whether `FileSequenceSource` should be usable in a header-only mode.

6. **Non-JSON FASTA headers**: `JsonHeaderParser.validateHeaderLine()` currently throws
   `FastaHeaderParserException` for any header line lacking the `|` separator, so plain FASTA
   files (no JSON payload) will fail to parse. Should non-JSON headers be silently skipped
   (graceful fallback) or remain a hard error?

7. **`--fasta-header` per-seqId mapping**: The current design applies a single CLI-supplied
   `FastaHeader` globally (all seqIds share it as a fallback). Should a future extension
   allow a JSON map of `{ "seqId": { ...header... }, ... }` to supply per-seqId overrides
   from the CLI? If so, how should the input format be distinguished from the single-object
   form?
