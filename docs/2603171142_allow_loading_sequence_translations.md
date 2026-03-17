- Feature Name: Allow loading sequence translations from CLI argument
- Status: Implemented
- Document Date: 2026-03-17
- Completed: 2026-03-17

# Summary

Introduced a `translate` CLI subcommand that accepts `--sequence-fasta` pointing to a nucleotide FASTA file. The command reads the FASTA into a `SequenceReader`, makes it available to the validation/fix pipeline through `FileSequenceProvider`, and runs `TranslationFix` to generate protein translations from CDS features. A provider plugin architecture allows a future `RemoteSequenceProvider` (separate JAR) to supply sequences when no local file is given.

# Motivation & Rationale

gff3tools could translate nucleotide sequences into proteins (`Translator`) and read FASTA files (`SequenceReaderFactory`), but had no CLI entry point wiring these together. The `translate` command closes this gap and establishes the foundation for downstream features such as translation comparison against `##FASTA`-embedded proteins.

The provider plugin architecture separates the sequence source behind `ContextProvider`, keeping the core module lean while allowing EBI-internal plugins to add remote fetching without modifying the main codebase.

# Usage

```bash
# With a local FASTA file
gff3tools translate --sequence-fasta /path/to/nucleotides.fasta input.gff3

# Without --sequence-fasta (requires a plugin that provides sequences)
gff3tools translate input.gff3
```

Inherits `--fail-fast` and `--rules` from `AbstractCommand`.

# Architecture

```
 CLI Layer                    Provider Layer                Fix Layer
+-----------------------+    +-------------------------+   +------------------+
| TranslationCommand    |    | FileSequenceProvider    |   | TranslationFix   |
| (Picocli @Command)    |--->| (@ContextProvider)      |<--| (@Gff3Fix)       |
| --sequence-fasta opt  |    | holds SequenceReader    |   | @InjectContext   |
| extends AbstractCommand|   +-------------------------+   | reads sequences  |
+-----------------------+            ^                     | calls Translator |
                                     |                     +------------------+
                              +------+------+
                              | (future)    |
                              | RemoteSeq.  |
                              | Provider    |
                              +-------------+
```

| Component | Role |
|-----------|------|
| **TranslationCommand** | Picocli `@Command` that opens a `SequenceReader` via `SequenceReaderFactory.readFasta()`, sets it on `FileSequenceProvider`, and runs the GFF3 pipeline |
| **FileSequenceProvider** | `ContextProvider<SequenceReader>` keyed on `SequenceReader.class`. Returns `null` when auto-discovered without a reader set; returns the opened instance when the CLI sets it |
| **TranslationFix** | `@Gff3Fix` with `@FixMethod(priority = LOW)`. For each CDS feature: extracts nucleotide slice, runs `Translator.translate()`, sets `translation` attribute. Gracefully skips when no sequence source is available |
| **ContextProvider.initialize()** | New `default void initialize()` lifecycle hook called after all providers are registered but before fixes/validators run. Extension point for future `RemoteSequenceProvider` |

# Key Design Decisions

**TranslationFix runs at LOW priority.** Structural fixes (locus tag, pseudogene, attribute corrections) must be applied before translation because they affect translation behavior (e.g., pseudo features skip translation).

**TranslationFix skips gracefully when no SequenceReader is available.** Since it's auto-discovered via classpath scanning, it runs in all pipelines (validation, translate). It catches missing/null providers and returns silently, only performing work when a reader is explicitly provided.

**Translation runs through the fix pipeline, not directly.** Running through the validation/fix engine ensures structural fixes are applied first and allows translation validation rules to be added incrementally without changing the command.

# Alternatives Considered

**Embedding SequenceReader in a configuration context.** Rejected because coupling an `AutoCloseable` resource with lifecycle management into a simple configuration holder would complicate usage. A dedicated provider also enables the plugin architecture.

**Calling Translator directly without the fix pipeline.** Rejected because structural fixes affect translation behavior and the pipeline enables incremental addition of translation validation rules.

**Adding --sequence-fasta to the existing validation command.** Rejected to maintain separation of concerns. Validation focuses on correctness checks; translation is a data transformation.

# Future Considerations

- **RemoteSequenceProvider plugin**: The `initialize()` lifecycle hook is designed for this — a separate JAR that auto-discovers and downloads sequences when no local file is given
- **Output format**: Translated proteins are currently stored as GFF3 attributes; a future enhancement could write to a separate FASTA file via `TranslationWriter`

# Related Documentation

- `docs/0003_validation_engine.md` — Validation engine architecture and fix/validation execution order
- `docs/0005_validation_context.md` — Context provider registration and injection
- `docs/0006_validation_priority.md` — Priority tiers for fixes and validations
