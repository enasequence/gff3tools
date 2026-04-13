- Feature Name: Allow loading sequence translations from CLI argument
- Status: Implemented
- Document Date: 2026-03-17
- Last Updated: 2026-03-31

# Summary

The `translate` CLI subcommand accepts `--sequence` pointing to a nucleotide sequence file (FASTA or plain), translates CDS features into proteins via the validation/fix pipeline, and writes the results according to a configurable `--translation-mode`. A `CompositeSequenceProvider` resolves sequences through an ordered chain of sources — local file first, then plugin-supplied providers — so the command remains agnostic to where sequences originate.

# Motivation & Rationale

gff3tools can translate nucleotide sequences into proteins (`Translator`) and read FASTA files, but had no CLI entry point wiring these together. The `translate` command closes this gap and establishes the foundation for downstream features such as translation comparison against `##FASTA`-embedded proteins.

Sequences may come from different sources: a local FASTA file supplied by the user, or a remote service that downloads sequences on demand. The `CompositeSequenceProvider` handles this transparently via a chain of responsibility — sources are tried in order until one resolves the requested sequence ID. This keeps the core module lean while allowing EBI-internal plugins to add remote fetching without modifying the main codebase.

# Usage

```bash
# Default: format inferred from extension, translations in ##FASTA section
gff3tools translate --sequence /path/to/nucleotides.fasta input.gff3

# Multiple plain sequences with keys (key = GFF3 seqId)
gff3tools translate \
  --sequence chr1:/path/to/chr1.seq \
  --sequence chr2:/path/to/chr2.seq \
  input.gff3

# Write translations to a separate FASTA file
gff3tools translate --sequence seq.fasta --translation-mode fasta input.gff3

# Translations as inline feature attributes (no output file)
gff3tools translate --sequence seq.fasta --translation-mode attribute input.gff3
```

# Architecture

```
 CLI Layer                    Provider Layer                         Fix Layer
+-----------------------+    +----------------------------------+   +------------------+
| TranslationCommand    |    | CompositeSequenceProvider        |   | TranslationFix   |
| (Picocli @Command)    |--->| (ContextProvider<SequenceLookup>)|<--| (@Gff3Fix)       |
| --sequence opt        |    | Chain of Responsibility:         |   | @InjectContext   |
| --translation-mode    |    |  1. FileSequenceSource (local)   |   | reads sequences  |
| extends AbstractCommand|   |  2. (plugin sources)             |   | calls Translator |
+-----------------------+    +----------------------------------+   +------------------+
```

| Component | Role |
|-----------|------|
| **TranslationCommand** | Picocli `@Command` that parses `--sequence` specs, registers sources in the `CompositeSequenceProvider`, and handles output mode and file writing. |
| **CompositeSequenceProvider** | `ContextProvider<SequenceLookup>` that wraps an ordered list of `SequenceSource` instances. Resolves sequence IDs by trying each source in order. |
| **FileSequenceSource** | A `SequenceSource` backed by a local file (FASTA or plain). Opens the reader lazily on first access. Thread-safe initialization via `synchronized`. |
| **SequenceSource** | Interface for sequence sources. Plugin JARs implement this to add remote sources. |
| **TranslationFix** | `@Gff3Fix` at `ANNOTATION` level, `LOW` priority. Groups CDS by ID (join segments), concatenates slices, translates once, propagates join attributes. |
| **TranslationKey** | Shared utility for consistent `accession|urlEncode(featureId)` key format across translation writer and validation state. |
| **TranslationState** | In-memory state shared between `TranslationFix` (records old/new translations) and `TranslationComparisonValidation` (compares them). |

# Key Design Decisions

**Chain of Responsibility for sequence resolution.** Sources are tried in order (local file → plugin sources). Each source is independent; the composite handles fallback.

**Plugin sources register during `initialize()`.** Remote plugins discover the `CompositeSequenceProvider` via `ValidationContext` during the `initialize()` lifecycle hook. No core module changes needed.

**TranslationFix operates at ANNOTATION level, LOW priority.** Sees all CDS features at once for join grouping. Runs after structural fixes that affect translation behavior (pseudo, locus tag).

**Translation runs through the fix pipeline, not directly.** Ensures structural fixes are applied first and allows translation validation rules to be added incrementally.

**Lazy reader opening and AutoCloseable engine.** `FileSequenceSource` opens lazily; `ValidationEngine` propagates `close()` through the provider chain, eliminating manual lifecycle management.

**Three output modes.** `attribute` (inline), `fasta` (separate file), `gff3-fasta` (embedded `##FASTA` section).

**Shared translation key format.** `TranslationKey.of(accession, featureId)` provides a single source of truth used by the FASTA writer, translation state, and comparison validation.

# Alternatives Considered

**Observer/Subject pattern** for sequence resolution — rejected; Chain of Responsibility better fits ordered fallback.

**Calling Translator directly** without the fix pipeline — rejected; structural fixes affect translation behavior.

**Adding --sequence to the validation command** — rejected; translation is a data transformation, not a correctness check.

# Future Considerations

- **RemoteSequenceProvider plugin**: Implements `SequenceSource`, registers with `CompositeSequenceProvider` during `initialize()`, downloads sequences on demand.

# Related Documentation

- `docs/0003_validation_engine.md` — Validation engine architecture and fix/validation execution order
- `docs/0005_validation_context.md` — Context provider registration and injection
- `docs/0006_validation_priority.md` — Priority tiers for fixes and validations
