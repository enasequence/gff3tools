- Feature Name: Allow loading sequence translations from CLI argument
- Status: In Progress
- Document Date: 2026-03-17
- Last Updated: 2026-03-17

# Summary

The `translate` CLI subcommand accepts `--sequence` pointing to a nucleotide sequence file (FASTA or plain), translates CDS features into proteins via the validation/fix pipeline, and writes the results according to a configurable `--translation-mode`. The sequence format is inferred from file extension (`.fasta` → FASTA, `.seq` → plain) or set explicitly via `--sequence-format`. A `CompositeSequenceProvider` resolves sequences through an ordered chain of sources — local file first, then plugin-supplied providers (e.g., remote fetching) — so the command remains agnostic to where sequences originate.

# Motivation & Rationale

gff3tools can translate nucleotide sequences into proteins (`Translator`) and read FASTA files (`SequenceReaderFactory`), but had no CLI entry point wiring these together. The `translate` command closes this gap and establishes the foundation for downstream features such as translation comparison against `##FASTA`-embedded proteins.

Sequences may come from different sources: a local FASTA file supplied by the user, or a remote service that downloads sequences on demand. The `CompositeSequenceProvider` handles this transparently via a chain of responsibility — sources are tried in order until one resolves the requested sequence ID. This keeps the core module lean while allowing EBI-internal plugins to add remote fetching without modifying the main codebase.

# Usage

```bash
# Default: format inferred from extension, translations in ##FASTA section
gff3tools translate --sequence /path/to/nucleotides.fasta input.gff3

# Explicit format (when extension is ambiguous or missing)
gff3tools translate --sequence /path/to/seqdata --sequence-format fasta input.gff3

# Plain sequence input (without key — matches any GFF3 seqId)
gff3tools translate --sequence /path/to/sequence.seq input.gff3

# Multiple plain sequences with keys (key = GFF3 seqId)
gff3tools translate \
  --sequence chr1:/path/to/chr1.seq \
  --sequence chr2:/path/to/chr2.seq \
  input.gff3

# Mix FASTA and keyed plain sequences
gff3tools translate \
  --sequence /path/to/multi.fasta \
  --sequence chrX:/path/to/extra.seq \
  input.gff3

# Override the default output path
gff3tools translate --sequence seq.fasta -o custom.gff3 input.gff3

# Write translations to a separate FASTA file (default: input.translation.fasta)
gff3tools translate --sequence seq.fasta --translation-mode fasta input.gff3

# Translations as inline feature attributes (no output file)
gff3tools translate --sequence seq.fasta --translation-mode attribute input.gff3

# Without --sequence (requires a plugin that provides sequences)
gff3tools translate --translation-mode fasta input.gff3
```

Inherits `--fail-fast` and `--rules` from `AbstractCommand`.

## CLI Options

| Option | Description | Default |
|--------|-------------|---------|
| `--sequence <[key:]path>` | Sequence source (repeatable). Use `path` for FASTA files (IDs from headers) or `key:path` for plain sequences where `key` is the GFF3 seqId. Without a key, a plain sequence matches any seqId. | None (plugin must supply sequences) |
| `--sequence-format <format>` | Sequence file format: `fasta` or `plain`. Applies to all `--sequence` entries. | Inferred from file extension |
| `--translation-mode <mode>` | Output mode: `gff3-fasta`, `fasta`, or `attribute` | `gff3-fasta` |
| `-o <path>` | Output file path for `fasta` and `gff3-fasta` modes | Derived from input file name |

**Sequence format resolution** (follows the same pattern as `-f` in the `conversion` command):
1. If `--sequence-format` is provided, use it
2. Otherwise, infer from file extension: `.fasta`, `.fa`, `.fna` → `fasta`; `.seq` → `plain`
3. If neither is available, fail with a message suggesting `--sequence-format`

**Default output paths when `-o` is not specified:**
- `gff3-fasta` mode: `<input>.translated.gff3`
- `fasta` mode: `<input>.translation.fasta`
- `attribute` mode: `-o` is ignored

# Architecture

```
 CLI Layer                    Provider Layer                         Fix Layer
+-----------------------+    +----------------------------------+   +------------------+
| TranslationCommand    |    | CompositeSequenceProvider        |   | TranslationFix   |
| (Picocli @Command)    |--->| (ContextProvider<SequenceReader>)|<--| (@Gff3Fix)       |
| --sequence opt  |    | Chain of Responsibility:         |   | @InjectContext   |
| --translation-mode    |    |  1. FileSequenceProvider (local) |   | reads sequences  |
| -o output path        |    |  2. (plugin sources)             |   | calls Translator |
| extends AbstractCommand|   +----------------------------------+   +------------------+
+-----------------------+
```

| Component | Role |
|-----------|------|
| **TranslationCommand** | Picocli `@Command` that parses one or more `--sequence` specs (`[key:]path`), opens a `SequenceReader` for each via `SequenceReaderFactory`, and registers them as local sources in the `CompositeSequenceProvider`. For keyed plain sequences, the key becomes the accession ID used for ID matching. Handles output mode and file writing. |
| **CompositeSequenceProvider** | `ContextProvider<SequenceReader>` keyed on `SequenceReader.class`. Wraps an ordered list of `SequenceSource` instances. Resolves sequence IDs by trying each source in order until one succeeds. |
| **FileSequenceProvider** | A `SequenceSource` backed by a local sequence file (FASTA or plain). Wraps a `SequenceReader` opened from `--sequence`. For plain sequences with a key, `hasSequence()` matches only that key; without a key it matches any ID (backward compatible single-sequence behavior). |
| **SequenceSource** | Interface for sequence sources. Each source can report whether it has a given sequence ID and provide a `SequenceReader` for it. Plugin JARs implement this to add remote sources. |
| **TranslationFix** | `@Gff3Fix` with `@FixMethod(priority = LOW)`. For each CDS feature: resolves the nucleotide sequence via `CompositeSequenceProvider`, runs `Translator.translate()`, sets the `translation` attribute. Gracefully skips when no sequence source is available. |
| **ContextProvider.initialize()** | Lifecycle hook called after all providers are registered. Remote plugins use this to register themselves as additional sources in the `CompositeSequenceProvider`. |

# Key Design Decisions

**Chain of Responsibility for sequence resolution.** Sequence sources are tried in order (local file → plugin sources). This avoids coupling between providers — each source is independent and doesn't know about others. The composite assembles them and handles fallback.

**Plugin sources register during `initialize()`.** Remote plugins discover the `CompositeSequenceProvider` via the `ValidationContext` during the `initialize()` lifecycle hook and add themselves as additional sources. No modification to the core module is needed.

**TranslationFix runs at LOW priority.** Structural fixes (locus tag, pseudogene, attribute corrections) must be applied before translation because they affect translation behavior (e.g., pseudo features skip translation).

**TranslationFix skips gracefully when no sequence source is available.** Since it's auto-discovered via classpath scanning, it runs in all pipelines (validation, translate). It catches missing/null providers and returns silently, only performing work when a source is available.

**Translation runs through the fix pipeline, not directly.** Running through the validation/fix engine ensures structural fixes are applied first and allows translation validation rules to be added incrementally without changing the command.

**Three output modes for flexibility.** `attribute` keeps translations inline (useful for programmatic access). `fasta` writes a separate file (matches the FF→GFF3 conversion pattern via `TranslationWriter`). `gff3-fasta` embeds in the `##FASTA` section (standard GFF3 format for associated sequences).

# Alternatives Considered

**Observer/Subject pattern between FileSequenceProvider and RemoteSequenceProvider.** Rejected because Observer is for broadcasting events, not for "can anyone handle this?" requests. Chain of Responsibility is a better fit for ordered fallback resolution.

**Embedding SequenceReader in a configuration context.** Rejected because coupling an `AutoCloseable` resource with lifecycle management into a simple configuration holder would complicate usage.

**Calling Translator directly without the fix pipeline.** Rejected because structural fixes affect translation behavior and the pipeline enables incremental addition of translation validation rules.

**Adding --sequence to the existing validation command.** Rejected to maintain separation of concerns. Validation focuses on correctness checks; translation is a data transformation.

# Future Considerations

- **RemoteSequenceProvider plugin**: Implements `SequenceSource`, packaged as a separate JAR. During `initialize()`, registers itself with the `CompositeSequenceProvider`. Downloads sequences on demand when the local file doesn't have a requested ID.
- **Translation comparison**: A future validation rule should compare proteins produced by `TranslationFix` against translations in the GFF3 `##FASTA` section.

# Implementation Plan

| Phase | Focus | Effort |
|-------|-------|--------|
| Phase 1 | `SequenceSource` interface and `CompositeSequenceProvider` | 1 day |
| Phase 2 | Refactor `FileSequenceProvider` as a `SequenceSource`, update `TranslationFix` | 1 day |
| Phase 3 | `--translation-mode` and `-o` output options in `TranslationCommand` | 1 day |
| Phase 4 | Tests and edge case coverage | 1 day |

# Related Documentation

- `docs/0003_validation_engine.md` — Validation engine architecture and fix/validation execution order
- `docs/0005_validation_context.md` — Context provider registration and injection
- `docs/0006_validation_priority.md` — Priority tiers for fixes and validations
