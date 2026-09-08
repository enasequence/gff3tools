- Feature Name: `general_parameters`
- Document Date: 2026-09-08
- Last Updated: 2026-09-08

# Summary

A general-purpose mechanism for overriding the parameter values that individual
validations and fixes check against, per invocation, without a code change or
release. A new CLI flag `--params key:value,key:value` (sibling to `--rules`)
and a symmetric library entry point feed a raw `Map<String, String>` into a
parameter-provider `ContextProvider`. Each rule/fix declares its own tunable
parameters (name, type, optional/mandatory, default); a caller-side step coerces,
defaults, and validates them before the engine is built at all — before any
file is read — and can render a help listing of everything declared.

# Motivation & Rationale

## Problem

Several validations and fixes hardcode thresholds that are legitimately
submitter- or dataset-dependent. The concrete trigger: ENA tested an Ensembl
GFF3 submission that failed built-in length validations (`INTRON_LENGTH`,
`EXON_LENGTH`, `CDS_LENGTH`). (`EXON_LENGTH` already defaults to `WARN` today—
`LengthValidation.java:173` — so the concrete workaround applied was downgrading
`INTRON_LENGTH`/`CDS_LENGTH` from `ERROR` to `WARN` via `--rules`.) Either way,
downgrading severity turns the check *off* rather than tuning it to the
submitter's still-valid shape.

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
- **Fail fast, before the engine is built.** Missing mandatory value, bad type,
  or an unrecognized key must be a hard startup error before any file is read,
  so an operator never believes they relaxed a rule when a typo meant nothing
  changed.

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
gff3tools validation input.gff3 \
  --params CDS_LENGTH.MIN_AMINO_ACIDS:15,INTRON_LENGTH.MIN_LENGTH:4
```

The value half is taken verbatim as a `String` (no CLI-side coercion), mirroring
`RuleConverter`'s `split(",")` then `split(":")` shape. Because values are taken
verbatim, a `RULE.PARAM` key with a `:`-bearing value is split on the first `:`
only (unlike `RuleConverter`, which rejects anything but exactly two halves).

### Help listing

An operator can discover what is tunable. The listing is generated from the
same descriptor collection used to build `ParameterProvider` (see System
Overview): for each descriptor, the namespaced key `RULE_NAME.PARAM_NAME`,
type, mandatory/optional, default (for optional), and description, omitting
any descriptor whose owning rule/fix is effectively `OFF` per the same
three-mechanism check described there. Because that check needs the caller's
`--rules`/fix-override state, the listing reflects whatever overrides are
passed alongside the listing request (e.g. `--rules X:OFF --list-params`); with
no `--rules` supplied, it reflects the default `ValidationConfig` alone. Exact
surface (new `--list-params` flag vs. a subcommand vs. extending existing help)
remains an implementation choice; the requirement is only that the listing
exists, is driven by the declarations, and applies the same OFF-filtering rule
used for fail-fast validation.

## Library callers

A programmatic caller builds `ParameterProvider` itself — from the raw
`Map<String, String>` plus a static, standalone descriptor scan (see below) —
**before** building the engine, then passes it through the existing
`additionalProviders` vararg on `AbstractCommand.initValidationEngine(...)` /
`ValidationEngineBuilder.withProvider(...)`. No new overload is needed: this
mirrors exactly how `CompositeSequenceProvider` is built and populated by the
caller, then handed in the same way. Fail-fast validation therefore happens in
caller-side code, before `ValidationEngineBuilder.build()` / `ValidationRegistry`
are touched at all — genuinely before any file is read, and outside the
ordering constraints of the registry's own provider/descriptor construction
sequence (see System Overview).

## Extending: declaring a parameter on a new rule

A rule/fix declares each tunable parameter as a descriptor carrying:

- `name` (combined with the rule name into the `RULE_NAME.PARAM_NAME` key)
- `type` (initial set: `STRING`, `LONG`; `ENUM` and richer types can be added
  later, see Technical Debt / Future Considerations)
- `description` (feeds the help listing)
- `mandatory` vs `optional`
- `default` (for optional parameters only)

At validation time the rule reads its resolved, typed value from the parameter
provider via the context (the same `context.get(...)` /
`context.contains(...)` pattern `LengthValidation` and `GapGenerationFix`
already use), instead of a hardcoded constant.

# System Overview / High-Level Design

The central design constraint driving this section: `ValidationRegistry`
instantiates and `initialize()`s all `ContextProvider`s *before* it builds
validator/fix descriptors (`ValidationRegistry.java:98-156`) — a `ParameterProvider`
built "from all registered descriptors" cannot be constructed inside that
sequence without the descriptors it needs already existing. The resolution:
descriptor collection for `@Parameter` is **not** part of `ValidationRegistry`'s
instance-scoped, config-aware descriptor build. It reuses the *existing*
`ValidationRegistry.ScanHolder.validationList` (exposed via a small public
static accessor — no second classpath scan) to read `@Parameter`/`@Parameters`
off the same classes, purely by annotation inspection, no instantiation.

Two corrections to the prior draft, found in review:

- **`CompositeSequenceProvider` is *not* an example of "never classpath
  auto-scanned."** It has an implicit public no-arg constructor and doesn't
  override `isActive()`, so `ValidationRegistry.instantiateProviders()` *does*
  auto-instantiate an empty one on every build; an explicitly-passed instance
  merely overwrites it under the same type key (explicit wins,
  `ValidationRegistry.java:111-137`). `ParameterProvider` will be auto-scanned
  the same way, and that's fine — see the dual-mode design below, which uses
  this rather than fighting it.
- **Defaults need a path that exists even when nobody passes `--params`.**
  `ValidationContext.get` throws for an unregistered type, and this feature
  deletes the hardcoded constants `LengthValidation` used to fall back to, so
  "the provider just isn't added" cannot be the answer for the common,
  no-`--params` case.

**Settled: `ParameterProvider` is dual-mode.**

1. A **public no-arg constructor** builds a `ParameterProvider` seeded *only*
   with each descriptor's declared default (no caller map at all). This is the
   instance `ValidationRegistry.instantiateProviders()` auto-scans and
   registers, on any build that leaves classpath-provider scanning enabled and
   doesn't exclude it. It reproduces today's hardcoded-constant behavior
   exactly, sourced from the same annotations instead of a `private static
   final`. **Known gap:** an engine built via
   `disableAutodetectContextProviders()` or `excludeProvider(ResolvedParameters.class)`
   gets neither instance unless the caller explicitly adds one, so a rule
   whose parameters were migrated onto `@Parameter` (deleting its hardcoded
   constant) has no value to read in that configuration — the same hazard
   that already exists today for `LengthValidation`'s other
   `context.get(...)`-based lookups (e.g. `OntologyClient`), not a new one
   introduced by this feature, but worth a test case covering it explicitly.
2. **Settled: the caller-side step always runs, not only when `--params` is
   supplied.** `AbstractCommand`/the library helper always builds the
   **explicit** `ParameterProvider` — from the caller's raw map when
   `--params` was given, or from an **empty** map otherwise — and always
   registers it via `additionalProviders`/`withProvider(...)`, the same way a
   caller populates and passes `CompositeSequenceProvider` today. Running the
   full Resolution-and-fail-fast checker against an empty map still correctly
   catches a missing mandatory parameter (check 3 fires exactly as it would
   for any other value), so there is no gap for documented entry points: a
   rule declaring `mandatory = true` fails the build whether or not `--params`
   was passed, as long as the caller goes through `AbstractCommand`/the
   library helper. Explicit registration overwrites the auto-scanned instance
   under the same type key, so the caller's overrides (or, with an empty map,
   nothing but declared defaults) win; anything not overridden keeps its
   declared default because the explicit instance is always built from the
   *same* full descriptor set, not just the supplied keys.

With this, the auto-scanned no-arg instance (item 1) is no longer on the path
for any caller going through `AbstractCommand` or the library helper — it is
explicitly overwritten every time, including the empty-map case. **Its only
remaining role is a defensive fallback for code that constructs
`ValidationRegistry`/`ValidationEngineBuilder` directly, bypassing that helper**
(as several existing tests do, and as `disableAutodetectContextProviders()`/
`excludeProvider(ResolvedParameters.class)` allow). For that narrower case, the
same mandatory-parameter gap described in item 1 still applies — out of scope
for the first adopters here, none of which declare a mandatory parameter, and
worth closing before any future rule does.

All five fail-fast checks in *Resolution and fail-fast* below run every time
the caller-side helper executes — with a possibly-empty map — strictly before
`ValidationEngineBuilder.build()` is called, genuinely before any file is read.
The claim that non-`STRING` optional parameters must have a coercible default
(see Descriptor declaration) still holds regardless.

**OFF-rule detection needs the real effective severity, not just the CLI map.**
Effective state is a merge of the annotation's own default severity, the
`default-rule-severities.properties` file, and the caller's `--rules`
override — currently computed by `ValidationConfig.getSeverity(rule,
defaultAction)` (already public) fed by `ValidationEngineBuilder.getValidationConfig()`
(currently **private**). Settled: expose that loader (e.g.
`ValidationConfig.loadDefault()`), so the caller-side step can compute the same
effective severity `ValidationEngineBuilder` will apply, instead of only seeing
its own `--rules` map. The equivalent applies to class-level
`@Gff3Validation`/`@Gff3Fix` enablement via the already-public
`ValidationConfig.isValidatorEnabled(...)`. Fix-enable overrides that a command
assembles internally (e.g. `ValidationCommand` hardcoding `GAP_GENERATION:false`)
are already caller-side data at the point the command builds its `fixOverrides`
map — the `ParameterProvider`-construction step must run after that map is
assembled, using the same map, not a separate one.

```
CLI (--params, --rules) ─┐
                         ├─► raw Map<String,String> + RuleSeverity/fix overrides
library caller ──────────┘                    │
                                               │
     ParameterDescriptors (reuses             │  (annotation-only read of
     ScanHolder.validationList)  ─────────────┤   @Parameter/@Parameters via the
     (name,type,opt/mand,default,desc,         │   existing one-time classpath
      owning rule/fix)                         │   scan — no instantiation)
                                               │
     ValidationConfig.loadDefault()  ─────────┤  (exposed loader: annotation
     + isValidatorEnabled/getSeverity          │   default + properties file,
                                               │   merged with the caller's
                                               │   --rules/fix overrides to get
                                               │   real effective OFF state)
                                               ▼
        always: coerce + default + validate (map is empty if no --params)
                                               │  ← fail-fast HERE, in
                                               │  caller-side code, before
                                               │  ValidationEngineBuilder.build()
                                               │  is ever called (missing
                                               │  mandatory, bad type, unknown
                                               │  key, key for an OFF rule/fix —
                                               │  catches missing-mandatory even
                                               │  with an empty map)
                                               ▼
            explicit ParameterProvider (ContextProvider<ResolvedParameters>)
                                               │
              always passed as an additionalProvider (overwrites the
              auto-scanned, defaults-only instance under the same type key,
              which is otherwise only reached by callers that bypass
              AbstractCommand/the library helper entirely)
                                               │
                                               ▼
                                       ValidationContext
                                               │
              Rule reads typed value via context.get(ResolvedParameters.class)
```

Main components:

- **`--params` CLI option + converter** on `AbstractCommand` / `Main`. Produces
  a plain `Map<String, String>` (a `CliParamsOption` record mirroring
  `CliRulesOption`, with a converter mirroring `RuleConverter`).
- **Parameter descriptor** declared by each rule/fix via `@Parameter`. Powers
  both validation and the help listing.
- **A public static accessor on `ValidationRegistry`** exposing
  `ScanHolder.validationList` (or an equivalent read-only view), so descriptor
  collection reuses the existing one-time classpath scan instead of running a
  second one.
- **`ParameterProvider`** — dual-mode `ContextProvider<ResolvedParameters>`
  (see Detailed Design for the value type): a no-arg, defaults-only
  auto-scanned instance (a fallback for direct `ValidationRegistry`/
  `ValidationEngineBuilder` use only), and an explicit, caller-built instance
  that `AbstractCommand`/the library helper *always* constructs — from the
  raw map when `--params` is supplied, or an empty map otherwise — and always
  registers, overwriting the auto-scanned instance. The explicit instance's
  construction is the single point where coercion, defaulting, and validity
  checks happen, and it runs unconditionally.
- **An exposed `ValidationConfig` loader** (settled: make
  `ValidationEngineBuilder.getValidationConfig()`'s loading logic public, e.g.
  `ValidationConfig.loadDefault()`), so the caller-side step can compute real
  effective severity/enablement, not just its own override map.
- **Help renderer** driven by the same descriptor collection.

Integration points:

- The explicit `ParameterProvider` is **always** constructed by the caller
  (CLI: inside `AbstractCommand` before calling `initValidationEngine`, after
  the `ruleOverrides`/`fixOverrides` maps are assembled; library: by the
  pipeline before building the engine), using an empty map when `--params`
  wasn't supplied, and passed through the existing `additionalProviders`
  vararg — identical to how a caller populates and passes
  `CompositeSequenceProvider` today.
- No changes to `ValidationEngineBuilder.build()` or `ValidationRegistry`'s
  provider/descriptor ordering are required. Two small additions to existing
  classes are required: the `ScanHolder.validationList` accessor and the
  `ValidationConfig` loader becoming public.

# Detailed Design & Implementation

## Entry points (symmetry)

- CLI: `@Option(names = "--params", paramLabel = "<key:value,key:value>")`
  producing `Map<String, String>`, following the `--rules` precedent exactly
  (`CliRulesOption` + `RuleConverter` + `AbstractCommand.getRuleOverrides()`).
- Library: the caller builds the explicit `ParameterProvider` itself from the
  same raw `Map<String, String>` (see System Overview) and passes it via
  `additionalProviders`/`withProvider(...)`; no map is passed to the engine
  builder directly, and no typed overload per parameter exists — coercion is
  the provider's job.

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
    long minAminoAcids = context.get(ResolvedParameters.class).getLong("CDS_LENGTH.MIN_AMINO_ACIDS");
    ...
}
```

The namespaced key is derived as `rule() + "." + name()` — `rule` already exists
on `@ValidationMethod`/`@FixMethod`, so the parameter annotation never re-states
it and the two cannot drift out of sync. `@Repeatable` covers rules needing more
than one parameter (e.g. `TRNA_LENGTH.MIN_LENGTH` and `TRNA_LENGTH.MAX_LENGTH`
on the same method). Note `@ValidationMethod` has no `enabled()` member (only
`@FixMethod` does) — method-level disablement for validations is expressed via
a `RuleSeverity.OFF` entry in the rule-severity overrides, not an annotation
attribute; see below for how that interacts with parameter validity.

Rejected alternative: programmatic registration (a rule exposing a
`declareParameters()`-style method). More flexible for computed defaults, but
nothing here needs one, and it would add a second code path a rule author has
to remember alongside the existing annotation-driven metadata. `defaultValue`
must be a `String` on the annotation (Java annotations cannot hold arbitrary
typed values) and is coerced by `type()` at provider-build time through the same
coercion path as CLI/library-supplied values — a minor asymmetry accepted to
keep declarations valid Java. A non-`STRING` parameter with `mandatory = false`
must supply a `defaultValue` that coerces to its declared `type` — an optional
numeric parameter left at the annotation default (`""`) is a build-time defect
in the rule itself (verifiable by a unit test over the static scan, not a
runtime concern), not something `ParameterProvider` needs to handle.

Descriptor collection reuses `ValidationRegistry.ScanHolder.validationList`
(exposed via a small public accessor) to read `@Parameter`/`@Parameters` off
the same classes already discovered for `@ValidationMethod`/`@FixMethod` — no
second classpath scan. It is deliberately decoupled from `ValidationRegistry`'s
instance-scoped, config-filtered descriptor build (`buildDescriptors`) — that
decoupling is what makes caller-side, pre-engine-build construction of the
explicit `ParameterProvider` possible (see System Overview).

Because it is decoupled, it does not know on its own which rules are
effectively `OFF`. Determining that requires all three of the codebase's
distinct disablement mechanisms, each checked separately:

1. **Class-level**: `@Gff3Validation`/`@Gff3Fix` enablement, via the
   already-public `ValidationConfig.isValidatorEnabled(...)`, fed the effective
   `ValidationConfig` (see below). Filters at registration.
2. **Method-level severity**: `RuleSeverity.OFF`, via
   `ValidationConfig.getSeverity(rule, defaultAction)` — the effective value
   merges the annotation's own default severity, `default-rule-severities.properties`,
   and the caller's `--rules` override. Filters at execution, but for this
   feature's purposes a descriptor whose rule resolves to `OFF` is treated as
   inactive.
3. **Fix-enable**: via the already-public `ValidationConfig.getFix(rule,
   defaultEnabled)` (the fix-enable analogue of `getSeverity`, same call
   shape as `ValidationEngine.java:116`) — the effective value merges
   `@FixMethod`'s own default, `fix.*` entries in
   `default-rule-severities.properties` (yes, fix-enable has its own
   properties-file layer too, exactly like severity), and the caller's
   fix-override map (which some commands partly assemble internally, e.g.
   `ValidationCommand` hardcoding `GAP_GENERATION:false`).

The caller-side step needs the effective `ValidationConfig` to check (1) and
(2) correctly — not just its own `--rules` map — which is why
`ValidationEngineBuilder`'s config loader must be exposed (see System
Overview's `ValidationConfig.loadDefault()`). For (3), the step must run after
the command's `fixOverrides` map is fully assembled and use that exact map.

For a **library caller**, the same ordering constraint applies to all three
mechanisms, not just (3): `overrideClassRules(...)` and `overrideMethodRules(...)`
are builder calls a pipeline could legally make *after* constructing the
explicit `ParameterProvider`, at which point the caller-side check would be
stale. The CLI is unaffected (there is no `--rules`-equivalent path to
`overrideClassRules` today), but a library caller must construct the explicit
`ParameterProvider` only after all three override inputs (class rules, method
rules, fix overrides) are in their final form, immediately before
`ValidationEngineBuilder.build()`.

## Resolution and fail-fast (settled: caller-side, before engine build)

Given the raw map, the static scan's descriptors, and the OFF/enabled state of
each descriptor's owning rule/fix:

1. **Unknown key** → hard startup error. Any `RULE.PARAM` key in the map that no
   descriptor declares fails the build.
2. **Key for a toggled-`OFF` rule/fix** → hard startup error, same as an unknown
   key. (Settled: a parameter for a method-level `OFF` rule is invalid to
   supply, not silently accepted or silently ignored.)
3. **Missing mandatory** → hard startup error. A mandatory descriptor with no
   supplied value fails the build (unless its owning rule is `OFF`, in which case
   the parameter is simply not required — an `OFF` rule needs no value for a
   parameter it will never read).
4. **Bad type** → hard startup error. A value that does not coerce to the
   descriptor's declared type fails the build.
5. **Optional, unsupplied** → the descriptor's default is used.

All five apply to the **explicit** `ParameterProvider`, which `AbstractCommand`/
the library helper *always* builds — from the caller's map when `--params` is
supplied, or from an empty map otherwise. Running the checker against an empty
map still exercises check 3 correctly (a mandatory descriptor with nothing
supplied still fails the build), which is what closes the mandatory-parameter
gap for both documented entry points; only code that bypasses
`AbstractCommand`/the library helper and talks to `ValidationRegistry`/
`ValidationEngineBuilder` directly relies on the auto-scanned, defaults-only
instance instead, which does not run this checker (see System Overview).
Construction happens in caller-side code (`AbstractCommand`/`Main` for the CLI;
the pipeline's own setup for a library caller) — strictly before
`ValidationEngineBuilder.build()` is called, so before `ValidationRegistry`
does anything and before any file is read.

`ExitException` is a checked exception (`extends Exception`,
`exception/ExitException.java`). `AbstractCommand implements Runnable`, so
`run()` cannot declare `throws`; the existing, working pattern (already used by
`ValidationCommand.run()`) is to construct the explicit `ParameterProvider`
inside `run()`'s existing try block, wrap a thrown `ExitException` subclass
(e.g. a new `CLIException`-mapped-to-`USAGE` case, or reuse `CLIException`
directly) in an unchecked `RuntimeException(message, cause)`, and let
`ExecutionExceptionHandler.findExitException` recover the exit code by walking
the cause chain — exactly the mechanism `ValidationCommand.java` already uses
for other invalid-argument failures. No `throws`-signature changes are needed
anywhere in the engine, but the checked exception does not "flow freely" — it
is wrapped and unwrapped by this existing mechanism, not thrown bare.

`ResolvedParameters` is the context value type `ParameterProvider` produces
(`ParameterProvider implements ContextProvider<ResolvedParameters>`,
`type()` returns `ResolvedParameters.class`). It exposes typed accessors keyed
by the same namespaced `RULE.PARAM` string used everywhere else in this spec
(e.g. `getLong("CDS_LENGTH.MIN_AMINO_ACIDS")`), resolving either the caller's
supplied value or the descriptor's default.

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
  Note also: these three values are consumed off a single shared
  `AnalysisContext`, read by `GapGenerationFix` and (for `analysisType`) by
  `SequenceLengthValidation` — the strict single-owner `RULE.PARAM` namespacing
  this spec otherwise assumes doesn't have a natural owning rule for a
  cross-cutting value like this. If/when gap-fix migration is undertaken, the
  key naming for these three (e.g. a non-rule-prefixed reserved namespace, or
  attributing them to one nominal owning rule/fix by convention) is a decision
  for that follow-on work, not resolved by this spec.

## Corner cases

- A `RULE.PARAM` value containing `:` (e.g. a linkage-evidence phrase) must not
  be truncated; split on the first `:` only.
- A rule declaring no parameters contributes nothing to the map or the help
  listing.
- Casing (settled): `--params` **keys** are upper-cased, matching `--rules`
  (`cds_length.min_amino_acids` and `CDS_LENGTH.MIN_AMINO_ACIDS` resolve
  identically). This applies to keys only — **values** are passed through
  verbatim, unmodified by this feature's parsing. This matters concretely for
  the gap-fix migration case: `GapOptionsValidator.normaliseGapType` /
  `normaliseLinkageEvidence` apply their own, different casing rules to values
  downstream, and `--params` must not pre-empt that by uppercasing values.
- A value containing `,` is not supported (mirrors the same pre-existing
  limitation in `--rules`/`RuleConverter`'s `split(",")`); no escaping is
  provided by this feature. `RuleConverter` also trims each whole `key:value`
  entry but not the individual key/value halves — `--params` follows the same
  behavior for consistency, so a value with leading/trailing whitespace is taken
  verbatim.
- `mandatory = true` with a non-empty `defaultValue` is simply ignored (the
  value is never mandatory-and-defaulted at once); a supplied but empty value
  (`KEY:`) for a mandatory `STRING` parameter is treated as "missing" and fails
  the same as an absent key, not accepted as a valid empty string.
- If the gap-fix parameters (`min-gap-length`/`gap_type`/`linkage_evidence`) are
  ever migrated onto `--params` (see First adopters), their hyphen/underscore
  names must be translated to a `PARAM_NAME` form consistent with the
  upper-cased, dot-namespaced key convention (e.g. `MIN_GAP_LENGTH`) as part of
  that follow-on decision — not resolved by this spec.

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
- Initial type set is intentionally small (`String`, integral `LONG`). `ENUM` and
  richer types can be added later without changing the CLI surface.
- Whether to migrate the gap fix's dedicated flags onto `--params` or leave them
  is deferred; the constructor-level validation in `AnalysisContext` must survive
  either way, and the shared-value namespacing question (see First adopters)
  must be resolved as part of that follow-on decision, not assumed.
- Rule-description text and validation-failure messages that hardcode the same
  values now being parameterized (e.g. `LengthValidation`'s "at least 25 amino
  acids" description, and `INVALID_CDS_INTRON_LENGTH_MESSAGE`'s "at least 10 nt")
  will read incorrectly once a value is overridden. Out of scope for this spec's
  first pass; worth a follow-up to make these messages read the resolved value
  rather than a literal.

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
- **Empty-map fail-fast**: a CLI/library run through `AbstractCommand`/the
  library helper with no `--params` at all resolves `LengthValidation`'s
  parameters to their declared defaults via the always-built, empty-map
  explicit instance (not the auto-scanned one). If a rule declares a
  `mandatory` parameter, this case must fail the build the same way a
  missing-mandatory `--params` value would, proving the mandatory-parameter gap
  is closed for both documented entry points.
- **Bypass-the-helper fallback**: a plain `new ValidationEngineBuilder().build()`
  (matching existing tests that don't go through `AbstractCommand`) resolves
  `LengthValidation`'s parameters to their declared defaults via the
  auto-scanned instance, with no crash — this is the one path that does not run
  the fail-fast checker, and it must not be used as a stand-in for the
  empty-map case above.
- **`--params` for a rule/fix that is effectively `OFF`** exits `USAGE` (2)
  before reading the file, same as an unknown key — one case per disablement
  mechanism: class-level (`@Gff3Validation`/`@Gff3Fix` disabled), method-level
  severity `OFF` (via `--rules`, and via `default-rule-severities.properties`
  alone with no `--rules` override), and fix-enable (via a command's internal
  `fixOverrides`, e.g. `GAP_GENERATION`, **and** via `fix.*` properties-file
  entries alone with no override — fix-enable has the same properties layer
  as severity).
- **Help listing**: the listing includes a newly declared parameter and omits
  parameters of rules/fixes that are effectively `OFF` under the same
  three-mechanism check.
- **Descriptor collection**: unit test that a non-`STRING` optional parameter
  with no `defaultValue` (or a non-coercible one) is caught as a build-time
  defect in the rule's own annotation, independent of any `--params` map being
  supplied at all.
- **Exit-code path**: a thrown parameter-validation failure is wrapped and
  recovered by `ExecutionExceptionHandler.findExitException` the same way
  `ValidationCommand`'s existing invalid-argument failures are, ending in exit
  code `USAGE` (2).
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
  `validation/provider/TranslationStateProvider.java`,
  `validation/provider/CompositeSequenceProvider.java` (the caller-populated
  provider pattern the explicit `ParameterProvider` follows — note it, like
  `ParameterProvider`, is also auto-scanned empty and then overwritten, not
  exempt from auto-scan), `validation/ValidationConfig.java`,
  `validation/ValidationEngine.java` (`getSeverity` call site)
