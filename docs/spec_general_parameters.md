- Feature Name: `general_parameters`
- Document Date: 2026-09-08
- Status: Implemented (see [Implementation](#implementation))

# Summary

A general-purpose mechanism for overriding the parameter values that individual
validations and fixes check against, per invocation, without a code change or
release. A CLI flag `--params key:value,key:value` (sibling to `--rules`) and a
symmetric library entry point feed a raw `Map<String, String>` into a
`ParameterProvider` `ContextProvider`. Each rule/fix declares its own tunable
parameters (name, type, optional/mandatory, default); a caller-side step
coerces, defaults, and validates them before the engine is built — before any
file is read — and can render a help listing of everything declared.

# Motivation & Rationale

## Problem

Several validations and fixes hardcode thresholds that are legitimately
submitter- or dataset-dependent. The concrete trigger: ENA tested an Ensembl
GFF3 submission that failed built-in length validations (`INTRON_LENGTH`,
`EXON_LENGTH`, `CDS_LENGTH`). The only workaround was downgrading severity via
`--rules`, which turns the check *off* rather than tuning it to the submitter's
still-valid shape.

ENA needs an "intensity knob," not a severity switch: override the actual
parameter values a rule/fix checks against, per invocation, decided ad hoc per
submission. No persistent "Ensembl profile" store is in scope.

## Settled design decisions

- **Build on `ContextProvider`, not a parallel system.** Parameters ride the
  existing lazy, typed provider lifecycle already used by `TaxonProvider`,
  `TranslationStateProvider`, and `AnalysisContextProvider`. Discovery was
  explicit: do not add a second configuration mechanism.
- **Raw `Map<String, String>` at both entry points.** CLI converter and
  programmatic caller pass the *same* untyped map. All coercion/validation lives
  in one place (the provider), keeping the two entry points symmetric.
- **Rules own their parameter declarations.** Each rule/fix declares its
  descriptors via a `@Parameter` annotation colocated with `@ValidationMethod`/
  `@FixMethod`, rather than a central schema. Keeps related code close (organic-
  growth convention) and lets the help listing be generated from the
  declarations.
- **Fail fast, before the engine is built.** Missing mandatory value, bad type,
  unrecognized key, or a key for an effectively-`OFF` rule is a hard startup
  error before any file is read, so an operator never believes they relaxed a
  rule when a typo meant nothing changed.
- **Dual-mode `ParameterProvider`.** A no-arg, defaults-only instance is
  auto-scanned/registered by `ValidationRegistry` (reproducing today's
  hardcoded-constant behavior, sourced from the annotation defaults) as a
  fallback for callers that build `ValidationRegistry`/`ValidationEngineBuilder`
  directly. `AbstractCommand`/the library helper *always* builds an explicit
  instance (from the caller's map, or an empty map when `--params` is absent)
  and registers it via `additionalProviders`, overwriting the auto-scanned one
  under the same type key. The explicit instance is the single point where
  coercion, defaulting, and the fail-fast checks run — always, even with an
  empty map, which is what closes the missing-mandatory gap for both documented
  entry points.
- **OFF-detection uses real effective state.** Whether a rule/fix is `OFF` is a
  merge of the annotation default, `default-rule-severities.properties`, and the
  caller's `--rules`/fix overrides across all three disablement mechanisms
  (class-level `@Gff3Validation`/`@Gff3Fix` enablement, method-level
  `RuleSeverity.OFF`, and fix-enable). `ValidationConfig.loadDefault()` exposes
  the loader so the caller-side step computes the same effective state the
  engine will apply. For library callers, the explicit provider must be built
  only after all three override inputs are final, immediately before
  `ValidationEngineBuilder.build()`.

# Usage

## CLI

```
--params <key:value,key:value>
```

Keys are namespaced `RULE_NAME.PARAM_NAME` and upper-cased (matching `--rules`).
Values are taken verbatim (no CLI-side coercion, no case folding) and split on
the first `:` only, so a value may itself contain `:`. Example, tuning the
Ensembl case instead of disabling the checks:

```
gff3tools validation input.gff3 \
  --params CDS_LENGTH.MIN_AMINO_ACIDS:15,INTRON_LENGTH.MIN_LENGTH:4
```

A value containing `,` is not supported (same limitation as `--rules`). A typo'd
or unknown key, a key for an effectively-`OFF` rule, a missing mandatory value,
or a non-coercible value all exit `USAGE` (2) before the file is read.

## Help listing

An operator can discover what is tunable. The listing is generated from the same
descriptor collection used to build `ParameterProvider`, omitting any descriptor
whose owning rule/fix is effectively `OFF` under the same three-mechanism check,
reflecting whatever `--rules`/fix overrides are passed alongside the listing
request.

## Library callers

A programmatic caller builds the explicit `ParameterProvider` itself from the
raw map plus a static descriptor scan, then passes it through the existing
`additionalProviders` vararg on `AbstractCommand.initValidationEngine(...)` /
`ValidationEngineBuilder.withProvider(...)` — mirroring how
`CompositeSequenceProvider` is built and handed in. No new overload; coercion is
the provider's job.

## Declaring a parameter on a new rule

Add a repeatable `@Parameter` annotation to the same method as
`@ValidationMethod`/`@FixMethod`:

```java
@Parameter(name = "MIN_AMINO_ACIDS", type = ParameterType.LONG,
        description = "Minimum amino acids for a complete CDS", defaultValue = "25")
@ValidationMethod(rule = "CDS_LENGTH", type = ValidationType.ANNOTATION, priority = ValidationPriority.LOW)
public void validateCdsLength(GFF3Annotation gff3Annotation, int line) throws ValidationException {
    long minAminoAcids = context.get(ResolvedParameters.class).getLong("CDS_LENGTH.MIN_AMINO_ACIDS");
    ...
}
```

The namespaced key is derived as `rule() + "." + name()`, so the two cannot
drift. `defaultValue` is a `String` (annotations cannot hold typed values) and
is coerced by `type()` through the same path as supplied values. A non-`STRING`
optional parameter must supply a `defaultValue` that coerces to its type; a
missing or non-coercible one is a build-time defect in the rule, caught by a
unit test over the static scan. Initial type set is `STRING` and `LONG`; `ENUM`
and richer types can be added later without changing the CLI surface.

# Implementation

The mechanism was delivered in four phases on branch `general-arguments` (base
`178e10946b5233ff3deb43fb2ed610d17e4bddfc`). Refer to these commits for the
detailed "how":

- **Phase 1 — core annotation and descriptor infrastructure** `e6559f42`:
  `@Parameter`/`@Parameters`, `ParameterType`, `ParameterDescriptor`,
  `ParameterDescriptors` static scan (reusing
  `ValidationRegistry.ScanHolder.validationList` via a public accessor — no
  second classpath scan), `ResolvedParameters`, and the dual-mode
  `ParameterProvider` (auto-scanned no-arg instance + explicit raw-map instance
  running the five fail-fast checks).
- **Phase 2 — effective-config exposure for OFF-detection** `6e501b84`:
  `ValidationConfig.loadDefault()` plus reachable `getSeverity`/
  `isValidatorEnabled`/`getFix`, wired into the explicit provider's construction
  so OFF-detection uses the real three-mechanism effective state.
- **Phase 3 — CLI wiring and help listing** `5c2cd882`: `--params` option
  (`CliParamsOption` + a converter mirroring `RuleConverter`) on
  `AbstractCommand`/`Main`; `AbstractCommand` always builds and registers the
  explicit provider after `ruleOverrides`/`fixOverrides` are assembled;
  fail-fast surfaced as `USAGE` (2) via the existing
  `ExecutionExceptionHandler.findExitException` path; help-listing renderer.
- **Phase 4 — first adopter: migrate `LengthValidation`** `58a0d5e2`: replaced
  the `private static final` threshold constants with `@Parameter`-declared,
  provider-read values (`INTRON_LENGTH.MIN_LENGTH`, `EXON_LENGTH.MIN_LENGTH`,
  `CDS_LENGTH.MIN_AMINO_ACIDS`, `TRNA_LENGTH.MIN_LENGTH`, `TRNA_LENGTH.MAX_LENGTH`,
  `CDS_INTRON_LENGTH.MIN_LENGTH`); `COMPLETE_CDS_MIN_LENGTH` now derives from the
  resolved `COMPLETE_CDS_MIN_AMINO_ACIDS`, not a frozen constant.

## Fail-fast checks (reference)

The explicit provider's construction applies, against the (possibly empty) map:

1. Unknown key → hard startup error.
2. Key for an effectively-`OFF` rule/fix → hard startup error, same as unknown.
3. Missing mandatory value → hard startup error (unless the owning rule is `OFF`).
4. Non-coercible value for the declared type → hard startup error.
5. Optional, unsupplied → the descriptor's default is used.

An empty value (`KEY:`) for a mandatory `STRING` parameter is treated as missing.
`mandatory = true` with a `defaultValue` ignores the default.

# Out of scope / future considerations

- **Gap-fix migration.** `min-gap-length`/`gap_type`/`linkage_evidence` still
  flow through `AnalysisContext` from dedicated CLI flags. Migrating them onto
  `--params` is deferred: the `AnalysisContext` constructor validation
  (`GapOptionsValidator`, `minGapSize > 0`) must survive, and these
  cross-cutting values (shared by `GapGenerationFix` and `SequenceLengthValidation`)
  have no natural single owning rule, so their `RULE.PARAM` namespacing and
  hyphen/underscore→`PARAM_NAME` translation must be resolved as part of that
  follow-on work. Values must not be pre-uppercased by `--params` parsing, since
  `GapOptionsValidator` applies its own casing downstream.
- **No persistent submitter profiles** ("Ensembl profile") — every override is
  per-invocation.
- **Stale message text.** Rule descriptions and failure messages that hardcode
  the values now parameterized (e.g. "at least 25 amino acids", the "at least
  10 nt" `INVALID_CDS_INTRON_LENGTH_MESSAGE`) will read incorrectly once
  overridden. A follow-up should make these read the resolved value.
- **Bypass-the-helper mandatory gap.** Code that constructs
  `ValidationRegistry`/`ValidationEngineBuilder` directly (or uses
  `disableAutodetectContextProviders()`/`excludeProvider(...)`) gets only the
  auto-scanned defaults-only instance, which does not run the fail-fast checker.
  No current adopter declares a mandatory parameter; close this before one does.

# Alternatives Considered

- **Keep using `--rules` severity downgrades.** Rejected: turns a check off
  rather than tuning it.
- **A central parameter schema.** Rejected: couples every rule to one registry
  and fights the organic-growth/domain-boundary conventions.
- **Typed per-parameter CLI options (like `--min-gap-length`).** Rejected: does
  not scale and diverges between CLI and library callers. `--params` is one flag
  for all, symmetric across entry points.
- **Silent no-op on unknown keys.** Rejected outright by discovery: an operator
  must never believe they relaxed a rule when a typo changed nothing.
- **Programmatic descriptor registration** (a `declareParameters()` method).
  Rejected: nothing needs computed defaults, and it adds a second code path
  alongside the annotation-driven metadata.

# Related Documentation & Resources

- Discovery: `docs/discovery_general_parameters.md`
- Error handling / exit codes: `docs/0001_error_handling.md`
- Validation rules: `docs/0002_validation_rules.md`
- Validation engine: `docs/0003_validation_engine.md`
- Key code: `validation/Parameter.java`, `validation/ParameterProvider.java`,
  `validation/ResolvedParameters.java`, `validation/ParameterDescriptors.java`,
  `validation/ValidationConfig.java`, `validation/ValidationRegistry.java`,
  `validation/builtin/LengthValidation.java`, `cli/AbstractCommand.java`,
  `cli/Main.java`.
