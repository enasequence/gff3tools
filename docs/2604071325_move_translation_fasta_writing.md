# Unify translation FASTA writing into GFF3File write phase

**Status**: Draft
**Created**: 2026-04-07T13:25:29Z
**Timestamp**: 2604071325

---

## PART I: Requirements

### Problem Statement

Translation FASTA writing is currently split across two different layers with inconsistent approaches:

1. **FF->GFF3 path** (`GFF3AnnotationFactory.handleTranslation()`): During annotation building, each translation is written immediately to a temp FASTA file on disk. `FFToGff3Converter` creates the temp file before conversion and deletes it after. `GFF3FileFactory` threads the temp file path through to `GFF3File`, which reads and streams it back out during `writeGFF3String()`.

2. **TranslationCommand path** (`TranslationCommand.writeGff3FastaOutput()`): Translations are extracted in a second pass over already-built annotations, written to a separate temp FASTA file, then the annotation attributes are stripped in a third loop, and finally `GFF3File` is constructed with that temp path and writes it back out.

Both paths use a temp file as an intermediary for data that is already in memory. The temp file creates I/O overhead, ties up disk space, requires lifecycle management (create/delete), and is not a natural abstraction boundary. Neither pipeline currently streams annotations — all entries are accumulated in memory — so the temp file delivers no memory benefit.

Meanwhile, `TranslationState` — which already exists in the validation context — records both old and new translations keyed by `TranslationKey`. It is populated by `TranslationFix` during validation and is naturally deduplicated by key. `TranslationComparisonValidation` already reads exclusively from `TranslationState`, never from feature attributes. This is the right data carrier for the write phase.

The correct abstraction is: `TranslationState` is the single source of truth for translations after validation. `TranslationFix.fixAnnotation()` no longer needs to set the `translation` attribute back on features — it records translations in `TranslationState` only. `GFF3File.writeGFF3String()` receives `TranslationState` and writes the `##FASTA` section directly from its entries after all annotations are written. For `attribute` mode, the writer sets translation attributes on features from `TranslationState` during writing. No temp file, no second pass, no attribute dance.

### Requirements

**R1 — GFF3File writes the FASTA section from TranslationState**

`GFF3File` receives an optional `TranslationState` instance (or null). When provided, after writing all annotation records it appends a `##FASTA` section by iterating `TranslationState` entries and writing each `newTranslation` keyed by `TranslationKey`. When null, no FASTA section is appended and features are written as-is. `GFF3File` must not import `TranslationMode` or any CLI type; `TranslationState` (which lives in the `validation.provider` package) is the only coupling point. A minor API addition to `TranslationState` (e.g., `forEach()` or `entries()`) is needed to support iteration for writing.

**R2 — Eliminate the temp FASTA file from both write paths**

The temp FASTA file intermediary must be removed from both the FF->GFF3 path (`FFToGff3Converter.getFastaPath()`, `deleteFastaFile()`) and the TranslationCommand `gff3-fasta` path. `GFF3AnnotationFactory.handleTranslation()` must be removed; `TranslationFix` already captures translations into `TranslationState` during validation, so no separate collection step is needed. `fastaFilePath` must be removed from `GFF3File`, `GFF3File.Builder`, and `GFF3FileFactory`.

**R3 — TranslationState is the single source of truth; TranslationFix no longer sets attributes on features**

`TranslationFix.fixAnnotation()` must stop setting the `translation` attribute back on features after re-translation. It records translations exclusively in `TranslationState`. This simplifies the data flow: features never carry the translation attribute after validation.

`TranslationCommand` passes `TranslationState` from the `ValidationContext` to `GFF3File`. For `gff3-fasta` mode, `GFF3File` writes the `##FASTA` section from `TranslationState`. For `attribute` mode, the writer sets translation attributes on features from `TranslationState` during writing. For `fasta` mode, `writeFastaOutput()` reads from `TranslationState` directly — it no longer needs to scan feature attributes.

### Success Criteria

- FF->GFF3 conversion (`FFToGff3Converter.convert()`) produces identical GFF3+FASTA output without creating any temp file on disk.
- `TranslationCommand` `gff3-fasta` mode produces identical output without creating any temp file.
- `TranslationCommand` `fasta` mode output is unchanged.
- `TranslationCommand` `attribute` mode output is unchanged (translations remain as feature attributes).
- No temp file create/delete lifecycle code remains in `FFToGff3Converter`, `GFF3FileFactory`, or `TranslationCommand`.
- `GFF3File` has no reference to `fastaFilePath`, `TranslationMode`, or any CLI type.
- `TranslationFix.fixAnnotation()` no longer sets translation attributes on features; `TranslationState` is the sole carrier of translation data after validation.
- All existing tests pass; new unit tests cover the `TranslationState`-driven FASTA write path and the null-TranslationState pass-through path.
- Multi-segment CDS features with the same translation key are naturally deduplicated by `TranslationState`'s `HashMap` keying (see `docs/notes/multi_segment_cds_dedup.md` for validation notes).

### Out of Scope

- **GFF3->FF offset map path**: The existing `writeAnnotationFasta` / `gff3Reader.getTranslationOffsetForAnnotation()` path in `GFF3File.writeGFF3String()` and `GFF3File.writeTranslationSection()` is left as-is. A new data provider for translations from file is a follow-up task.
- **Streaming annotations**: Neither pipeline currently streams. The new design does not block future streaming (write GFF3 lines per-annotation, accumulate only the small translation map, write `##FASTA` at end), but implementing streaming is out of scope.
- **TranslationFix fixFeature()**: No changes to `fixFeature()` or `TranslationComparisonValidation`.

### Open Questions

1. **Multi-segment CDS dedup**: When a joined CDS produces N feature rows with the same ID, `TranslationFix` records the translation once per key in `TranslationState`. This should naturally deduplicate, but should be validated against real multi-segment CDS data before merging. See `docs/notes/multi_segment_cds_dedup.md`.

2. **`writeAnnotationFasta` flag coexistence**: The existing boolean `writeAnnotationFasta` on `GFF3File` covers the GFF3->FF offset-map path. The new `TranslationState`-based path replaces `fastaFilePath` but `writeAnnotationFasta` remains for the out-of-scope offset-map path. Consider whether these two mechanisms should eventually be unified under a single abstraction.

3. **TranslationState availability in the FF->GFF3 path**: `TranslationState` is populated by `TranslationFix` during validation. Confirm that the FF->GFF3 path runs `TranslationFix` (or that `TranslationState` is populated by other means) so that translations are available for `GFF3File` to write. If `TranslationFix` is not active in the conversion path, `GFF3AnnotationFactory` may need to populate `TranslationState` directly instead of writing to a temp file.

4. **Attribute mode write path**: With `TranslationFix.fixAnnotation()` no longer setting translation attributes on features, the `attribute` mode needs the writer (or a post-validation step) to set translation attributes from `TranslationState` onto features before or during writing. Decide whether this belongs in `GFF3File.writeGFF3String()` or in `TranslationCommand` before calling write.

---

## PART II: High-Level Implementation Plan

| Phase | Focus | Effort |
|-------|-------|--------|
| Phase 1 | Add `TranslationState` iteration API, simplify `TranslationFix.fixAnnotation()` to stop setting attributes on features, wire `GFF3File.writeGFF3String()` to write `##FASTA` from `TranslationState` entries | 1 day |
| Phase 2 | Remove temp FASTA plumbing from the FF->GFF3 path: delete `handleTranslation()` from `GFF3AnnotationFactory`, remove `getFastaPath()`/`deleteFastaFile()` from `FFToGff3Converter`, remove `fastaFilePath` from `GFF3FileFactory` and `GFF3File`, pass `TranslationState` from `ValidationContext` | 1 day |
| Phase 3 | Migrate `TranslationCommand` to read translations from `TranslationState` for all three modes, removing temp FASTA creation, manual attribute-stripping, and feature attribute scanning | 0.5 days |
| Phase 4 | Unit and integration tests covering all three translation modes, multi-segment CDS deduplication, and the null-TranslationState pass-through path | 1 day |
