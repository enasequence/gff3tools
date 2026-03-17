- Feature Name: Allow loading sequence translations from CLI argument
- Document Date: 2026-03-17
- Last Updated: 2026-03-17

# Summary

Introduce a `translate` CLI subcommand that accepts an optional `--sequence-fasta` argument pointing to a nucleotide FASTA file. The command reads the FASTA into a `SequenceReader`, makes it available to the validation/fix pipeline through a new `FileSequenceProvider`, and runs a `TranslationFix` that generates protein translations from CDS features. A provider plugin architecture allows a future `RemoteSequenceProvider` (packaged as a separate JAR) to supply sequences when no local file is given.

# Motivation & Rationale

Today, gff3tools can translate nucleotide sequences into proteins (via `Translator`) and can read FASTA files (via `SequenceReaderFactory`), but there is no CLI entry point that wires these capabilities together. Users who want to generate translations must do so programmatically. Adding a `translate` command closes this gap and establishes the foundation for downstream features such as translation comparison against `##FASTA`-embedded proteins.

The provider plugin architecture is motivated by the operational reality that sequences may come from different sources: a local FASTA file supplied by the user, or a remote service that downloads sequences on demand. By separating the sequence source behind a `ContextProvider`, the translate command remains agnostic to where sequences originate. This keeps the core module lean while allowing EBI-internal plugins to add remote fetching without modifying the main codebase.

# Usage Guidelines

**Basic usage with a local FASTA file:**

```bash
gff3tools translate --sequence-fasta /path/to/nucleotides.fasta input.gff3
```

**Without `--sequence-fasta` (requires a plugin that provides sequences):**

```bash
gff3tools translate input.gff3
```

If no `--sequence-fasta` is provided and no plugin supplies a `SequenceReader`, the command fails with a clear error message indicating that a sequence source is required.

The `--fail-fast` and `--rules` flags from `AbstractCommand` are inherited and work identically to the `validation` command.

# System Overview / High-Level Design

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

The design introduces three new classes and modifies two existing ones:

1. **`TranslationCommand`** -- A new Picocli `@Command` subclass of `AbstractCommand`. Parses `--sequence-fasta`, opens a `SequenceReader` via `SequenceReaderFactory.readFasta()`, configures a `FileSequenceProvider` with it, and runs the validation/fix pipeline on the GFF3 input.

2. **`FileSequenceProvider`** -- A new `ContextProvider<SequenceReader>` that holds a `SequenceReader` instance with getter/setter. It is always on the classpath. When auto-discovered with no reader set, it returns null (signaling no local file). When the CLI sets the reader, it returns the opened instance.

3. **`TranslationFix`** -- A new `@Gff3Fix` class with a `@FixMethod` that, for each CDS feature, extracts the nucleotide subsequence from the `SequenceReader`, runs `Translator.translate()`, and writes the resulting protein as a `translation` attribute on the feature.

4. **`ContextProvider` interface** -- Gains an optional `default void initialize()` lifecycle method, called after all providers are instantiated and registered but before any fixes/validators run. This lets a `RemoteSequenceProvider` check whether `FileSequenceProvider` already has a reader and, if not, download the sequence and set it.

5. **`Main.java`** -- Registers `TranslationCommand` as a new subcommand.

# Detailed Design & Implementation

## TranslationCommand

Extends `AbstractCommand`. Declares:

```java
@CommandLine.Option(names = "--sequence-fasta",
    description = "Path to a nucleotide FASTA file for translation")
public Path sequenceFastaPath;
```

In `run()`:
- Resolves the process directory (same logic as `ValidationCommand`).
- If `sequenceFastaPath` is provided, opens a `SequenceReader` via `SequenceReaderFactory.readFasta(sequenceFastaPath.toFile())`.
- Creates a `FileSequenceProvider`, sets the reader on it.
- Builds a `ValidationEngine` via `ValidationEngineBuilder`, passing the provider via `withProvider()`. This overrides any auto-discovered `FileSequenceProvider`.
- Reads the GFF3 file using `GFF3FileReader`, which triggers fixes (including `TranslationFix`) and validations through the engine.
- The `SequenceReader` is closed in a try-with-resources that wraps the entire pipeline execution.

## FileSequenceProvider

```java
public class FileSequenceProvider implements ContextProvider<SequenceReader> {
    private SequenceReader sequenceReader;
    // getter, setter

    @Override
    public SequenceReader get(ValidationContext context) {
        return sequenceReader;
    }

    @Override
    public Class<SequenceReader> type() {
        return SequenceReader.class;
    }
}
```

This provider uses `SequenceReader.class` as its registration key in the `ValidationContext`, distinct from the existing `TranslationContext.class` key used by `TranslationProvider`. The two providers coexist: `TranslationProvider` supplies configuration (process directory, output paths), while `FileSequenceProvider` supplies sequence data.

## TranslationFix

Annotated with `@Gff3Fix`. Contains a `@FixMethod(type = FEATURE)` that:

1. Checks that the feature type is `CDS`.
2. Retrieves the `SequenceReader` from the `ValidationContext`.
3. Extracts the nucleotide slice using `SequenceReader.getSequenceSlice()` with the feature's seqid, start, and end coordinates.
4. Creates a `Translator` for the feature, calls `translate()`.
5. If the translation succeeds, sets the `translation` attribute on the feature.

The fix runs at `LOW` priority, ensuring all structural fixes (locus tag, pseudogene, attribute corrections) have been applied before translation occurs.

## Provider initialize() Lifecycle

A new `default void initialize()` method on `ContextProvider`:

```java
public interface ContextProvider<T> {
    T get(ValidationContext context);
    Class<T> type();
    default void initialize() {}
}
```

`ValidationRegistry` calls `initialize()` on each provider instance after all providers are registered in the context but before `buildDescriptors()` processes fixes/validators. This is the extension point for `RemoteSequenceProvider`: during `initialize()`, it can look up `FileSequenceProvider` via the context, check if a reader is already set, and if not, download the sequence FASTA and set the reader.

## Interaction with Existing TranslationContext

The existing `TranslationContext` and `TranslationProvider` remain unchanged. `TranslationContext` continues to hold the `processDir` and `sequenceFastaPath` (the output/temp FASTA path for downloaded sequences). `FileSequenceProvider` is a separate provider keyed on `SequenceReader.class`. Fixes that need both configuration and sequence data retrieve each independently from the context.

# Alternatives Considered

**Embedding SequenceReader directly in TranslationContext.** This was rejected because `TranslationContext` is a simple configuration holder (paths), and coupling it to a resource with lifecycle management (`AutoCloseable`) would complicate its usage. Keeping sequence data in a dedicated provider also enables the plugin architecture where different providers can supply the reader.

**Making the translate command call Translator directly without the fix pipeline.** This was rejected because running through the validation/fix engine ensures that structural fixes (e.g., pseudogene, locus tag) are applied before translation, which affects translation behavior (e.g., pseudo features skip translation). It also means translation validation rules can be added incrementally without changing the command.

**Adding --sequence-fasta to the existing validation command.** This was rejected to maintain separation of concerns. The validation command focuses on correctness checks; translation is a data transformation. A dedicated command makes the CLI surface clearer and avoids overloading the validation command with unrelated options.

# Technical Debt / Future Considerations

- **Translation comparison**: A future validation rule should compare the protein produced by `TranslationFix` against any translation found in the GFF3 `##FASTA` section (read by `GFF3TranslationReader`). This belongs in the `validation` command, not `translate`.
- **RemoteSequenceProvider plugin**: The `initialize()` lifecycle hook is designed for this. Implementation will be in a separate JAR that, when present on the classpath, auto-discovers and registers itself. It should download sequences to the `sequenceFastaPath` in `TranslationContext` and open a `SequenceReader` on that file.
- **Output format**: Currently the translated protein is stored as a GFF3 attribute. A future enhancement could write translations to a separate FASTA file via `TranslationWriter`.
- **TranslationProvider constructor**: `TranslationProvider` currently requires a `TranslationContext` argument, which prevents auto-discovery via no-arg constructor instantiation. If `TranslationProvider` needs to be auto-discovered in the future, it will need a no-arg constructor or a factory approach.

# Testing Strategy

- **Unit tests for TranslationFix**: Mock `SequenceReader` and verify that CDS features get correct `translation` attributes. Cover forward strand, complement strand, partial features, and pseudo features.
- **Unit tests for FileSequenceProvider**: Verify getter/setter behavior and that `type()` returns `SequenceReader.class`.
- **Integration test for TranslationCommand**: Use a small GFF3 + FASTA test fixture. Run the command end-to-end and verify that CDS features in the output contain correct translations.
- **Edge cases**: Missing `--sequence-fasta` with no plugin (expect clear error). FASTA with sequence IDs that do not match GFF3 seqids (expect error per feature). CDS features on sequences not present in the FASTA.
- **Provider lifecycle test**: Verify that `initialize()` is called after registration and before fix execution.

# Deployment & Operations

No changes to deployment or operations. The `translate` subcommand is added to the existing `gff3tools` CLI JAR. No new dependencies are introduced; `SequenceReaderFactory` and `Translator` are already in the codebase.

The `--sequence-fasta` argument accepts any file path readable by `SequenceReaderFactory.readFasta()`, which supports standard multi-entry FASTA with JSON headers. Users must ensure the FASTA file is indexed or indexable by the underlying reader implementation.

# Implementation Plan

| Phase | Focus | Effort |
|-------|-------|--------|
| Phase 1 | FileSequenceProvider and ContextProvider.initialize() lifecycle | 1 day |
| Phase 2 | TranslationCommand with --sequence-fasta wiring | 1 day |
| Phase 3 | TranslationFix: CDS feature translation via the fix pipeline | 2 days |
| Phase 4 | Integration tests and edge case coverage | 1 day |

# Related Documentation & Resources

- `docs/0003_validation_engine.md` -- Validation engine architecture and fix/validation execution order
- `docs/0005_validation_context.md` -- Context provider registration and injection
- `docs/0006_validation_priority.md` -- Priority tiers for fixes and validations
