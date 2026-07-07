- Feature Name: `effective_metadata_resolution`
- Document Date: 2026-07-07
- Last Updated: 2026-07-07

# Summary

During GFF3-to-EMBL conversion, sequence-level metadata (description, molecule type,
topology, chromosome name/type/location, taxonomy, etc.) can arrive from two independent
sources in the *same* run: a master-entry file (`--master-entry`, exposed via
`MasterMetadataProvider`) and FASTA headers (`--fasta-header` or FASTA-embedded, exposed via
`FastaHeaderProvider`). This design introduces a single **effective-metadata resolution** step
that merges both sources — with a well-defined precedence — into one resolved object per
accession, validates and normalises *that* object once, and feeds it to `GFF3Mapper`. It
replaces the current arrangement where the precedence is implicit in `GFF3Mapper`'s method
ordering and where the master-entry path is not validated at all.

# Motivation & Rationale

## Problems with the current design

1. **Implicit, duplicated precedence.** `GFF3Mapper.mapGFF3ToEntry` calls
   `applyMasterMetadata(...)` then `applyFastaHeader(...)`. Master wins purely because it runs
   first, and the header "fills the gaps" via scattered guards such as
   `if (sourceFt.getQualifiers("organelle").isEmpty())` and `if (!hasChromosomeQualifier(...))`.
   The precedence rule is not stated in one place and is easy to break.

2. **The master-entry path has no validation or normalisation.** The FASTA-header path is
   covered by `FastaHeaderFormatValidation` and `FastaHeaderNormalisationFix`; `MasterMetadata`
   (deserialised straight from MasterEntry JSON in `MasterEntryJsonMetadataSource`) is mapped
   verbatim, so its chromosome fields are neither validated nor canonicalised.

3. **Silent cross-source fall-through.** Because the mapper warn-skips an unrecognised master
   `chromosome_location` (leaving no `/organelle` qualifier), the header path — which only fires
   when that qualifier slot is still empty — can silently supply its own value, overriding what
   the master file actually specified. Validating each source independently would not catch
   this, because neither source in isolation is wrong.

## Rationale for the chosen approach

Resolving both sources into one *effective* view before validation means validation sees
exactly what the mapper will emit, the precedence rule lives in exactly one place, and both
sources are covered by the same rules with no duplication. It aligns with the project direction
of this tool owning validation rather than deferring to downstream EMBL validation.

## Team decision on `chromosome_location` (2026-07)

After discussion with the team, **`chromosome_location` is a mandatory attribute** when a
chromosome is described (i.e. whenever `chromosome_name` and `chromosome_type` are present),
rather than optional-with-an-implied-nuclear-default. To keep the nuclear/cytoplasmic default
expressible, **`Nuclear` is supported as an allowed value** of the controlled vocabulary. This
supersedes the interim change on branch `TTENA-258-fastaheader-fixes-and-moved-validations`
(commit `cdd6822`) that had made `chromosome_location` optional and stripped a submitted
`"nuclear"` to absent; that approach is reverted in favour of the mandatory + explicit-`Nuclear`
model described here.

# Usage Guidelines

- Supply metadata via `--master-entry` and/or `--fasta-header` (and/or FASTA-embedded headers)
  exactly as today; no CLI change.
- **Precedence:** for any field, the master-entry value wins; the FASTA header fills a field
  only when the master value is absent/blank. This is unchanged in spirit from today, but is now
  enforced in one place (`MetadataResolver`).
- **Chromosome fields (per team decision):**
  - Allowed combinations: *none* (unplaced contig), *chromosome_name only* (unlocalised), or
    *chromosome_name + chromosome_type + chromosome_location* (chromosome).
  - `chromosome_location` is mandatory for the chromosome combination; use `Nuclear` for a
    chromosome residing in the nucleus (or cytoplasm for prokaryotes). Organelle chromosomes use
    the INSDC `/organelle` values (`Mitochondrion`, `Chloroplast`, …).
- **Extending vocabularies:** add values to `ControlledVocabularyUtils.ChromosomeLocation`
  (canonical casing as the enum value). `Nuclear` is a gff3tools extension of the INSDC
  `/organelle` list; it maps to *no* `/organelle` qualifier in EMBL output.

# System Overview / High-Level Design

New/changed concepts:

- **`ResolvedMetadata`** — the effective, merged metadata for a single accession. Either a new
  lean type or a reuse of `MasterMetadata` (already documented as "the union of all metadata
  fields needed by both conversion directions").
- **`MetadataResolver`** — merges a `MasterMetadata` (from `MasterMetadataProvider`) and a
  `FastaHeader` (from `FastaHeaderProvider`) into a `ResolvedMetadata`, encoding the
  master-wins / header-fills-gaps precedence in one place.
- **`ChromosomeFields`** — a small value type `(name, type, location)` carrying the shared
  chromosome combination + vocabulary rules, decoupled from any provider so both the
  FASTA-header path and the merged path use identical logic.
- **`MetadataNormalisationFix` / `MetadataFormatValidation`** — engine-registered fix and
  validation that operate on the *resolved* view (superseding, or delegating from, the
  FASTA-header-specific `FastaHeaderNormalisationFix` / `FastaHeaderFormatValidation`).

Data flow:

```
                 ┌─ MasterMetadataProvider.getMetadata(acc) ─┐
MetadataResolver ┤                                           ├─► ResolvedMetadata (precedence explicit)
                 └─ FastaHeaderProvider.getHeader(acc)       ─┘
                                    │
             MetadataNormalisationFix   (ChromosomeFields.normalised(): ascii-fold, canonicalise vocab)
                                    │
             MetadataFormatValidation   (ChromosomeFields.validate(): combination + vocabulary + mandatory location)
                                    │
             GFF3Mapper.applyMetadata(resolved)   (single mapping path, no implicit precedence)
```

Integration: `GFF3Mapper.mapGFF3ToEntry` replaces the `applyMasterMetadata` + `applyFastaHeader`
pair with a single `applyMetadata(resolved)`.

# Detailed Design & Implementation

## `ChromosomeFields` (shared rules)

```java
public record ChromosomeFields(String name, String type, String location) {

    /** ascii-fold + canonicalise vocabulary values to their controlled form. */
    public ChromosomeFields normalised() { ... }

    /**
     * Combination + vocabulary rules. Per the 2026-07 team decision, chromosome_location is
     * mandatory whenever a chromosome is described. Valid combinations:
     *   - none
     *   - name only                    (unlocalised)
     *   - name + type + location       (chromosome)
     * Equivalently: type OR location present => all three must be present.
     */
    public List<String> validate() { ... }
}
```

`ControlledVocabularyUtils.ChromosomeLocation` keeps the 18 INSDC `/organelle` values **plus**
`NUCLEAR("Nuclear")`, documented as a gff3tools extension that maps to no `/organelle`
qualifier.

## `MetadataResolver`

```java
public static Optional<ResolvedMetadata> resolve(
        String accession, MasterMetadataProvider master, FastaHeaderProvider header) {
    var m = master == null ? Optional.<MasterMetadata>empty() : master.getMetadata(accession);
    var h = header == null ? Optional.<FastaHeader>empty()    : header.getHeader(accession);
    if (m.isEmpty() && h.isEmpty()) return Optional.empty();

    // master wins; header fills gaps — the ONE place this rule lives
    return Optional.of(ResolvedMetadata.builder()
        .description(firstNonBlank(m.map(MasterMetadata::getDescription),  h.map(FastaHeader::getDescription)))
        .moleculeType(firstNonBlank(m.map(MasterMetadata::getMoleculeType), h.map(FastaHeader::getMoleculeType)))
        .topology(firstNonBlank(m.map(MasterMetadata::getTopology),         h.map(FastaHeader::getTopology)))
        .chromosome(new ChromosomeFields(
            firstNonBlank(m.map(MasterMetadata::getChromosomeName),     h.map(FastaHeader::getChromosomeName)),
            firstNonBlank(m.map(MasterMetadata::getChromosomeType),     h.map(FastaHeader::getChromosomeType)),
            firstNonBlank(m.map(MasterMetadata::getChromosomeLocation), h.map(FastaHeader::getChromosomeLocation))))
        // ...taxonomy, WGS, dates, cross-references, etc.
        .build());
}
```

## `GFF3Mapper`

`applyMasterMetadata(...)` + `applyFastaHeader(...)` collapse into:

```java
private void applyMetadata(GFF3SequenceRegion region, Entry entry, Sequence seq, SourceFeature src) {
    if (region == null) return;
    MetadataResolver.resolve(region.accessionId(), metadataProvider, headerProvider)
        .ifPresent(rm -> {
            mapChromosome(rm.chromosome(), src);   // fields already validated + normalised
            mapTopology(rm.topology(), seq);
            // ...description, mol_type, taxonomy, WGS, dates...
        });
}
```

`mapChromosomeLocation` keeps mapping `Nuclear` (case-insensitively) to no `/organelle`
qualifier; all other values resolve via the controlled vocabulary, and an unrecognised value is
rejected rather than passed through.

## Corner cases

- **Both sources present, same field:** master value used; header ignored for that field.
- **Master present but invalid `chromosome_location`:** now caught by `MetadataFormatValidation`
  on the resolved view (a `ValidationException`), instead of being silently warn-skipped and
  back-filled by the header.
- **Neither source:** resolver returns empty; mapping is a no-op, as today.
- **`MasterMetadataProvider` internal ordering** ("first source wins, no field merging" between
  master sources) is unchanged; the new merge is only master-vs-header.

# Alternatives Considered

- **Option 1 — validate/normalise each source independently against shared rules.** Cheaper and
  closes the "no validation on master" gap, but does not address the silent cross-source
  fall-through, because neither source is individually wrong. Rejected as insufficient given both
  sources routinely coexist.
- **`chromosome_location` optional with implied nuclear default** (interim commit `cdd6822`).
  Faithful to the ENA "optional fourth column" wording, but leaves the nuclear case implicit and
  under-specified. Superseded by the team decision to make the attribute mandatory with explicit
  `Nuclear`.
- **Full unification of the two providers into one metadata model.** Larger refactor of the
  provider chain; deferred — the resolver captures the merge without forcing the providers to
  merge upstream.

# Technical Debt / Future Considerations

- Staged migration recommended: (1) introduce `ChromosomeFields` + `MetadataResolver` as pure,
  well-tested units with no behaviour change; (2) switch `GFF3Mapper` to `applyMetadata`;
  (3) move validation/normalisation onto the resolved view and retire the FASTA-header-specific
  classes (or make them thin delegates).
- `EmblEntryMetadataSource` (FF→GFF3 direction) does not currently populate chromosome fields;
  out of scope here.
- Consider whether `MetadataFormatValidation` should aggregate errors across all metadata fields
  (topology, mol_type, chromosome) in one pass for better reporting.

# Testing Strategy

- **Unit:** `ChromosomeFields.validate()`/`.normalised()` (combination matrix incl. mandatory
  location and `Nuclear`); `MetadataResolver` precedence (master-only, header-only, both,
  gap-fill, neither).
- **Integration:** `GFF3HeaderIntegrationTest`-style cases exercising `--master-entry` alone,
  `--fasta-header` alone, and both together for the same accession, asserting the resolved
  precedence and the emitted EMBL `/organelle` (or its absence for `Nuclear`).
- **Regression:** existing `GFF3MapperTest`, `FastaHeaderFormatValidationTest`,
  `ControlledVocabularyUtilsTest`, `FastaHeaderNormalisationFixTest` updated to the mandatory +
  `Nuclear` model.

# Deployment & Operations

No deployment or operational change; internal refactor of the conversion pipeline. Behaviour
change is limited to metadata validation/precedence during GFF3-to-EMBL conversion.

# Related Documentation & Resources

- `docs/spec-fasta-header-to-embl-entry-mapping.md`
- `docs/0003_validation_engine.md`, `docs/0006_validation_priority.md`,
  `docs/0005_validation_context.md`
- ENA assembly submission docs (Chromosome List File):
  <https://ena-docs.readthedocs.io/en/latest/submit/fileprep/assembly.html>
- Code: `gff3toff/GFF3Mapper.java`, `metadata/`, `sequence/fasta/header/`,
  `validation/builtin/FastaHeaderFormatValidation.java`, `validation/fix/FastaHeaderNormalisationFix.java`
- PR: <https://github.com/enasequence/gff3tools/pull/141>
