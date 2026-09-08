- Feature Name: `general_parameters`
- Document Date: 2026-09-08
- Last Updated: 2026-09-08

# Summary

A general-purpose mechanism for overriding the parameter values that individual
validations and fixes check against, per invocation, without a code change or
release. A new CLI flag `--params key:value,key:value` (sibling to `--rules`)
and a symmetric library entry point feed a raw `Map<String, String>` into a
parameter-provider `ContextProvider`. Each rule/fix declares its own tunable
parameters (name, type, optional/mandatory, default); the provider coerces,
defaults, and validates them at `ValidationContext` construction time — before
any file is read — and can render a help listing of everything declared.

# Motivation & Rationale

## Problem

Several validations and fixes hardcode thresholds that are legitimately
submitter- or dataset-dependent. The concrete trigger: ENA tested an Ensembl
GFF3 submission that failed built-in length validations (`INTRON_LENGTH`,
`EXON_LENGTH`, `CDS_LENGTH`). The only lever available was downgrading those
rules to `WARN` via `--rules`, which turns the check *off* rather than tuning it
to the submitter's still-valid shape.

Confirmed hardcoded parameters today:

- `LengthValidation`
  (`src/main/java/uk/ac/ebi/embl/gff3tools/validation/builtin/LengthValidation.java`):
  `INTRON_FEATURE_MIN_LENGTH` (10), `EXON_FEATURE_MIN_LENGTH` (15),
  `COMPLETE_CDS_MIN_AMINO_ACIDS` (25), `COMPLETE_TRNA_MIN_LENGTH` (50),
  `COMPLETE_TRNA_MAX_LENGTH` (150), and the CDS-intron floor of 10 currently
  inlined in `validateCdsIntronLength`.
- The gap-generation fix
  (`src/main/java/uk/ac/ebi/embl/gff3tools/validation/fix/GapGenerationFix.java`):
  `min-gap-length`, `gap_type`, `linkage_evidence`. These already flow through
  `AnalysisContext` / `AnalysisContextProvider` from dedicated CLI flags
  (`--min-gap-length`, `--gap-type`, `--linkage-evidence` in
  `FileConversionCommand`).

## What ENA needs

An "intensity knob," not a severity switch: override the actual parameter values
a rule/fix checks against, per invocation. Decided ad hoc, per submission —
sometimes tied to a known submitter (Ensembl), sometimes not. No persistent
"Ensembl profile" store is in scope now.

## Rationale for the chosen design

- **Build on `ContextProvider`, not a parallel system.** The constraint from
  discovery is explicit: do not add a second configuration mechanism. Parameters
  ride the existing lazy, typed, `isActive()`/`initialize()`/`close()` provider
  lifecycle already used by `TaxonProvider`, `TranslationStateProvider`, and
  `AnalysisContextProvider`.
- **Raw `Map<String, String>` at both entry points.** The CLI converter and a
  programmatic caller pass the *same* untyped map. No type coercion in the CLI
  layer keeps the two entry points symmetric and puts all coercion/validation in
  one place.
- **Rules own their parameter declarations.** Each rule/fix declares its
  descriptors rather than a central schema knowing about every rule. This keeps
  related code close (per the project's organic-growth convention) and lets the
  help listing be generated from the declarations themselves.
- **Fail fast at engine startup.** Missing mandatory value, bad type, or an
  unrecognized key must be a hard startup error before any file is read, so an
  operator never believes they relaxed a rule when a typo meant nothing changed.

# Usage Guidelines

## CLI

New flag on `AbstractCommand`, sibling to `--rules`:

```
--params <key:value,key:value>
```

Keys are namespaced `RULE_NAME.PARAM_NAME` to avoid collisions between rules
that declare similarly-named parameters. Example, tuning the Ensembl case
instead of disabling the checks:

```
gff3tools validate input.gff3 \
  --params CDS_LENGTH.MIN_AMINO_ACIDS:15,INTRON_LENGTH.MIN_LENGTH:4
```

The value half is taken verbatim as a `String` (no CLI-side coercion), mirroring
`RuleConverter`'s `split(",")` then `split(":")` shape. Because values are taken
verbatim, a `RULE.PARAM` key with a `:`-bearing value is split on the first `:`
only (unlike `RuleConverter`, which rejects anything but exactly two halves).

### Help listing

An operator can discover what is tunable. The listing is generated from the
declared descriptors of all registered rules/fixes: for each, the namespaced key
`RULE_NAME.PARAM_NAME`, type, mandatory/optional, default (for optional), and
description. Exact surface (new `--list-params` flag vs. a subcommand vs.
extending existing help) is an open question below; the requirement is only that
the listing exists and is driven by the declarations.

## Library callers

A programmatic caller building a `ValidationEngine` passes the same raw
`Map<String, String>` alongside `additionalProviders`, e.g. a new
`AbstractCommand.initValidationEngine(...)` overload / `ValidationEngineBuilder`
method that accepts the params map. The engine constructs the parameter provider
from that map plus the registered rules' descriptors.

## Extending: declaring a parameter on a new rule

A rule/fix declares each tunable parameter as a descriptor carrying:

- `name` (combined with the rule name into the `RULE_NAME.PARAM_NAME` key)
- `type` (initial set: `STRING`, `LONG`/`INT`; `ENUM` is a candidate — see open
  questions)
- `description` (feeds the help listing)
- `mandatory` vs `optional`
- `default` (for optional parameters only)

At validation time the rule reads its resolved, typed value from the parameter
provider via the context (the same `context.get(...)` /
`context.contains(...)` pattern `LengthValidation` and `GapGenerationFix`
already use), instead of a hardcoded constant.

# System Overview / High-Level Design

```
CLI (--params) ─┐
                ├─► Map<String,String> ─► ParameterProvider (ContextProvider)
library caller ─┘                              │
                                               │ built from raw map
   Rule/Fix descriptors ───────────────────────┤ + declared descriptors
   (name,type,opt/mand,default,desc)            │
                                               ▼
                              coerce + default + validate  ← fail-fast here
                                               │  (missing mandatory,
                                               │   bad type, unknown key)
                                               ▼
                                       ValidationContext
                                               │
                    Rule reads typed value via context.get(...)
```

Main components:

- **`--params` CLI option + converter** on `AbstractCommand` / `Main`. Produces
  a plain `Map<String, String>` (a `CliParamsOption` record mirroring
  `CliRulesOption`, with a converter mirroring `RuleConverter`).
- **Parameter descriptor** declared by each rule/fix. Powers both validation and
  the help listing.
- **`ParameterProvider`** — a `ContextProvider` that holds the resolved, typed
  parameter values, keyed for lookup by the declaring rule. Constructed from the
  raw map + all registered descriptors. This is the single point where coercion,
  defaulting, and validity checks happen, so both CLI and library callers get
  identical fail-fast behavior.
- **Help renderer** driven by the collected descriptors.

Integration points:

- `ValidationEngineBuilder.build()` already assembles the registry, gathers
  providers, and constructs the context. The parameter provider is registered
  here so its construction (and therefore its fail-fast validation) runs at
  engine-build time, before any input is read. Composition with existing
  provider registration order is an open question below.
- `AbstractCommand.initValidationEngine(...)` gains a params-map parameter,
  threaded through to the builder alongside `additionalProviders`.

# Detailed Design & Implementation

## Entry points (symmetry)

- CLI: `@Option(names = "--params", paramLabel = "<key:value,key:value>")`
  producing `Map<String, String>`, following the `--rules` precedent exactly
  (`CliRulesOption` + `RuleConverter` + `AbstractCommand.getRuleOverrides()`).
- Library: the same `Map<String, String>` passed to the engine builder. No typed
  overload per parameter; coercion is the provider's job.

## Descriptor declaration (settled)

A repeatable `@Parameter` annotation on the same method as `@ValidationMethod` /
`@FixMethod`, mirroring how `severity`, `priority`, and `description` are
already expressed declaratively on those annotations:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Parameters.class)
public @interface Parameter {
    String name();
    ParameterType type();          // STRING, LONG (initial set; ENUM later)
    String description() default "";
    boolean mandatory() default false;
    String defaultValue() default ""; // ignored if mandatory=true; coerced by type()
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Parameters {
    Parameter[] value();
}
```

Usage — colocated with the rule it tunes, no separate registration call:

```java
@Parameter(name = "MIN_AMINO_ACIDS", type = ParameterType.LONG,
        description = "Minimum amino acids for a complete CDS", defaultValue = "25")
@ValidationMethod(rule = "CDS_LENGTH", type = ValidationType.ANNOTATION, priority = ValidationPriority.LOW)
public void validateCdsLength(GFF3Annotation gff3Annotation, int line) throws ValidationException {
    long minAminoAcids = context.get(ParameterProvider.class).getLong("CDS_LENGTH.MIN_AMINO_ACIDS");
    ...
}
```

The namespaced key is derived as `rule() + "." + name()` — `rule` already exists
on `@ValidationMethod`/`@FixMethod`, so the parameter annotation never re-states
it and the two cannot drift out of sync. `@Repeatable` covers rules needing more
than one parameter (e.g. `TRNA_LENGTH.MIN_LENGTH` and `TRNA_LENGTH.MAX_LENGTH`
on the same method).

Rejected alternative: programmatic registration (a rule exposing a
`declareParameters()`-style method). More flexible for computed defaults, but
nothing here needs one, and it would add a second code path a rule author has
to remember alongside the existing annotation-driven metadata. `defaultValue`
must be a `String` on the annotation (Java annotations cannot hold arbitrary
typed values) and is coerced by `type()` at provider-build time through the same
coercion path as CLI/library-supplied values — a minor asymmetry accepted to
keep declarations valid Java.

Whichever method carries these annotations, the reflective scan that already
collects `@ValidationMethod`/`@FixMethod` metadata must also collect
`@Parameter`/`@Parameters` from the same method, so the registry sees
descriptors only from *registered* rules/fixes (respecting class/method
enable-toggles) — the parameter provider and the help listing must see exactly
the active set.

## Resolution and fail-fast (in `ParameterProvider` construction)

Given the raw map and the collected descriptors:

1. **Unknown key** → hard startup error. Any `RULE.PARAM` key in the map that no
   registered descriptor declares fails the build. (Note: this interacts with
   disabled rules — a key for a rule that is toggled `OFF` should arguably still
   be reported rather than silently accepted; see open questions.)
2. **Missing mandatory** → hard startup error. A mandatory descriptor with no
   supplied value fails the build.
3. **Bad type** → hard startup error. A value that does not coerce to the
   descriptor's declared type fails the build.
4. **Optional, unsupplied** → the descriptor's default is used.

All four happen at `ValidationContext` construction / engine build, before file
reading. Errors should surface as an `ExitException` subclass mapping to the
`USAGE` exit code (2), consistent with other invalid-CLI-argument failures; the
exact exception class is an implementation detail to align with the existing
exception hierarchy (`docs/0001_error_handling.md`).

## First adopters

- **`LengthValidation`**: replace the `private static final` constants with
  declared parameters read from the provider. Suggested keys (final names TBD):
  `INTRON_LENGTH.MIN_LENGTH`, `EXON_LENGTH.MIN_LENGTH`,
  `CDS_LENGTH.MIN_AMINO_ACIDS`, `TRNA_LENGTH.MIN_LENGTH`,
  `TRNA_LENGTH.MAX_LENGTH`, and the CDS-intron floor
  (`CDS_INTRON_LENGTH.MIN_LENGTH`, currently the inline `10`). Note
  `COMPLETE_CDS_MIN_LENGTH` is derived from `COMPLETE_CDS_MIN_AMINO_ACIDS`; the
  derivation must follow the overridden value, not a frozen constant.
- **Gap-generation fix**: `min-gap-length`, `gap_type`, `linkage_evidence`
  already flow through `AnalysisContext` from dedicated CLI flags. Migrating
  these onto `--params` must preserve the existing validation in
  `AnalysisContext`'s constructor (`GapOptionsValidator`, `minGapSize > 0`) and
  its role as the single point every value passes through — a programmatic
  caller must still be unable to install an invalid `gap_type`. The dedicated
  `--min-gap-length` / `--gap-type` / `--linkage-evidence` flags either remain as
  they are (and this feature does not touch the fix) or are folded into
  `--params` with those constructor checks intact; folding is the riskier change
  and should be a deliberate, separately reviewed step, not a silent side effect.

## Corner cases

- A `RULE.PARAM` value containing `:` (e.g. a linkage-evidence phrase) must not
  be truncated; split on the first `:` only.
- A rule declaring no parameters contributes nothing to the map or the help
  listing.
- Casing: `--rules` upper-cases keys; `--params` key casing must be defined so
  `cds_length.min_amino_acids` and `CDS_LENGTH.MIN_AMINO_ACIDS` resolve
  consistently (recommend upper-casing the rule/param segments to match the
  `--rules` convention).

# Alternatives Considered

- **Keep using `--rules` severity downgrades.** Rejected: turns a check off
  rather than tuning it; the submitter's data is still validated, just against
  the wrong threshold or none.
- **A central parameter schema.** Rejected: couples every rule to one registry
  and fights the project's organic-growth / domain-boundary conventions. Per-rule
  declaration keeps the knowledge where the rule is.
- **Typed per-parameter CLI options (like the existing `--min-gap-length`).**
  Does not scale: every new tunable would need a new flag and would diverge
  between CLI and library callers. `--params` is one flag for all of them and is
  symmetric across entry points. (The existing dedicated gap flags predate this
  and are the migration case above.)
- **Silent no-op on unknown keys.** Rejected outright by the discovery: an
  operator must never believe they relaxed a rule when a typo changed nothing.

# Technical Debt / Future Considerations

- No persistent submitter profiles ("Ensembl profile") — explicitly out of scope;
  every override is per-invocation.
- Initial type set is intentionally small (`String`, integral). `ENUM` and richer
  types can be added later without changing the CLI surface.
- Interaction between `--params` for a disabled rule and the unknown-key error
  needs a decision (reject vs. ignore).
- Whether to migrate the gap fix's dedicated flags onto `--params` or leave them
  is deferred; the constructor-level validation in `AnalysisContext` must survive
  either way.

# Testing Strategy

- **Unit**: parameter provider resolution — defaulting of optionals, coercion of
  each supported type, and each fail-fast path (missing mandatory, bad type,
  unknown key) raising the right exception/exit code.
- **Unit**: `LengthValidation` reads overridden thresholds (e.g. a CDS that
  passes at `MIN_AMINO_ACIDS:15` but fails at the default 25), and the derived
  `COMPLETE_CDS_MIN_LENGTH` tracks the override.
- **Integration (CLI)**: `--params` parsing, including a `:`-bearing value and
  the namespaced key form; a run that previously failed a length rule now passes
  with a relaxed parameter; a typo'd key exits with `USAGE` (2) before reading
  the file.
- **Symmetry**: a library caller passing the same map produces identical
  fail-fast behavior to the CLI.
- **Help listing**: the listing includes a newly declared parameter and omits
  parameters of disabled rules.
- Gap-fix behavior unchanged (or, if migrated, `AnalysisContext` validation still
  rejects invalid `gap_type`/`linkage_evidence` and non-positive `min-gap-length`).

Verify with `./gradlew spotlessCheck test`.

# Related Documentation & Resources

- Discovery: `docs/discovery_general_parameters.md`
- Error handling / exit codes: `docs/0001_error_handling.md`
- Validation rules: `docs/0002_validation_rules.md`
- Validation engine: `docs/0003_validation_engine.md`
- Key code:
  `validation/ContextProvider.java`, `validation/ValidationContext.java`,
  `validation/ValidationEngineBuilder.java`, `cli/Main.java`
  (`CliRulesOption`/`RuleConverter`), `cli/AbstractCommand.java`,
  `validation/builtin/LengthValidation.java`,
  `validation/fix/GapGenerationFix.java`,
  `validation/provider/AnalysisContext.java`,
  `validation/provider/AnalysisContextProvider.java`,
  `validation/provider/TranslationStateProvider.java`
