# Unify translation FASTA writing into GFF3File write phase

**Status**: Implemented
**Created**: 2026-04-07T13:25:29Z
**Timestamp**: 2604071325

---

## Problem Statement

Translation FASTA writing was split across two layers with inconsistent approaches: the FF->GFF3 path wrote translations to a temp FASTA file during annotation building, while TranslationCommand used a separate temp file with a multi-pass strip-and-rebuild dance. Both used temp files as intermediaries for data already in memory.

## Solution Overview

`TranslationState` (already in the validation context) is the single source of truth for translations after validation. `GFF3File.writeGFF3String()` writes the `##FASTA` section directly from `TranslationState` entries. No temp files, no attribute scanning during write.

## Key Design Decisions

- **TranslationState as data carrier**: Rather than scanning feature attributes during write or using a BiConsumer on the factory, translations flow through `TranslationState` which is already populated by `TranslationFix`. This works for ALL write paths (FF->GFF3, TranslationCommand, any future path).

- **TranslationFix no longer sets attributes on features**: `fixAnnotation()` records translations exclusively in `TranslationState`. Features never carry the translation attribute after validation. `TranslationComparisonValidation` already read from `TranslationState`, so nothing else depended on the attribute.

- **Boolean decoupling via null check**: `GFF3File` receives an optional `TranslationState` (or null). When non-null, it writes `##FASTA`; when null, no FASTA section. No `TranslationMode` import — CLI concerns stay in CLI.

- **Old translation fallback**: `writeFastaFromTranslationState()` prefers `newTranslation` but falls back to `oldTranslation`. This handles the FF->GFF3 path where no re-translation occurs (no sequence source available).

- **LinkedHashMap for deterministic order**: `TranslationState` uses `LinkedHashMap` to ensure FASTA output order matches insertion order.

## Out of Scope

- **GFF3->FF offset map path**: Left as-is. A new data provider for translations from file is a follow-up task.
- **Streaming annotations**: The design enables future streaming but implementing it was not in scope.

## Open Questions (resolved during implementation)

- **Multi-segment CDS dedup**: `TranslationState`'s map keying naturally deduplicates. Noted in `docs/notes/multi_segment_cds_dedup.md` for expert validation.
- **TranslationState in FF->GFF3 path**: `TranslationFix.fixFeature()` captures the flat file's translation qualifier into `TranslationState` during validation, so translations are available for `GFF3File` to write even without a sequence source for re-translation.
