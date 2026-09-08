# Discovery: General-Purpose Validation/Fix Parameters

## Trigger

ENA's team tested a GFF3 submission from Ensembl and it failed built-in length
validations (`INTRON_LENGTH`, `EXON_LENGTH`, `CDS_LENGTH`). The current workaround
— downgrading those rules to `WARN` via `--rules` — was applied, but it's a blunt
instrument: it turns a check off rather than tuning it to the submitter's actual,
still-valid shape.

## Core need

Several validations and fixes hardcode thresholds/values that are legitimately
submitter- or dataset-dependent. Confirmed examples:

- `LengthValidation` (`CDS_LENGTH`'s minimum amino acid count, and by extension
  `INTRON_LENGTH`/`EXON_LENGTH` minimums) —
  `src/main/java/uk/ac/ebi/embl/gff3tools/validation/builtin/LengthValidation.java`
- The gap-generation fix's `min-gap-length`, `gap_type`, `linkage_evidence`

ENA needs an "intensity knob," not just a severity switch: the ability to override
the actual parameter values a rule/fix checks against, per invocation, without a
code change or release.

## Who and when this happens

- Decided **ad hoc, per submission** — sometimes tied to a known submitter
  (Ensembl), sometimes not. No need for a persistent "Ensembl profile" store now.
- Used both by the **CLI** and by **library callers** (pipelines that build a
  `ValidationEngine` programmatically) — same mechanism, two entry points.

## Ownership of parameters

Each validation/fix should **declare its own parameters** (name, type,
description, optional-vs-mandatory, default for optional ones) rather than there
being one central schema. This declaration should power a help listing so an
operator can discover what's tunable.

## Failure behavior

- A mandatory parameter with no value supplied must **fail fast at engine
  startup**, before any file is read.
- An **unrecognized parameter key** (typo, or a key no registered rule declares)
  must also be a **hard startup error**, not a silent no-op — an operator should
  never believe they've relaxed a rule when a typo meant nothing changed.

## Success in 3 months

An operator can tweak these parameters for a one-off or recurring special-case
submission without needing a code change, so exceptions like the Ensembl one
don't require hand-editing rule severities or waiting on a release.

## Constraints

- Must not break the existing `ContextProvider` / `ValidationContext` mechanism
  (`src/main/java/uk/ac/ebi/embl/gff3tools/validation/ContextProvider.java`,
  `ValidationContext.java`) — build on it as a base, don't add a parallel system.
- CLI syntax should feel like a natural sibling to the existing `--rules
  key:value,key:value` option (`AbstractCommand.java`, `CliRulesOption` /
  `RuleConverter` in `Main.java`).

## CLI interface decisions (from decision-support discussion)

1. **New flag**, sibling to `--rules`: e.g. `--params` (or `--args`), same
   `key:value,key:value` comma-separated convention.
2. **CLI converter produces a plain `Map<String, String>`** — no attempt to
   coerce types at parse time in the CLI layer. This keeps the CLI/library
   entry points symmetric: a programmatic caller building a `ValidationEngine`
   passes the same raw `Map<String, String>` alongside `additionalProviders`.
3. **Coercion, defaulting, and validity live inside a parameter-provider
   `ContextProvider`**, built from the raw map plus each rule's declared
   parameter descriptors (name, type, optional/mandatory, default). This is
   where fail-fast (missing mandatory, bad type, unrecognized key) happens —
   at `ValidationContext` construction time, before any file is read. Same
   place renders the help-style listing of declared parameters.
4. **Key namespacing:** keys are namespaced as `RULE_NAME.PARAM_NAME` (e.g.
   `CDS_LENGTH.MIN_AMINO_ACIDS:30`) to avoid collisions between rules that
   declare similarly-named parameters, reusing the existing flag + converter
   pattern rather than inventing new CLI syntax.

## Existing patterns to build on (implementer lookups, already resolved)

- `ContextProvider<T>` (`validation/ContextProvider.java`): lazy, typed,
  `isActive()` / `initialize()` / `close()` lifecycle, registered by type in
  `ValidationContext`. `TaxonProvider` and `TranslationStateProvider`
  (`validation/provider/`) are existing examples to mirror.
- `--rules` precedent: `CliRulesOption` record + `RuleConverter`
  (`ITypeConverter`) in `cli/Main.java`, consumed via
  `AbstractCommand.getRuleOverrides()` and `initValidationEngine(...)`.
- First adopters to migrate/support: `LengthValidation`
  (`validation/builtin/LengthValidation.java`) and the gap-generation fix.

## Open questions (implementation-design, not user decisions)

- Exact declaration API shape for a rule to register a parameter descriptor
  (annotation vs. programmatic registration).
- How the parameter provider composes with existing provider registration order
  in `ValidationEngineBuilder`.
- Exact type set to support initially (String, long/int, enum?) vs. extensible
  later.
- Where/how the help listing is exposed (new CLI subcommand/flag vs. extending
  existing help output).
