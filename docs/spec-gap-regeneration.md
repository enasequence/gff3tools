- Feature Name: `gap_regeneration_fix`
- Document Date: 2026-07-07
- Last Updated: 2026-07-07

# Summary

Add a GFF3 **gap regeneration** capability that discards whatever `gap`/`assembly_gap`
features (SO:0000730) are present in a GFF3 document and rebuilds them purely from the
runs of `N` bases in an accompanying FASTA sequence, leaving every other feature (and
every other fix) untouched, then writes a corrected GFF3 file.

Concretely this delivers:

1. A new annotation-level fix `REGENERATE_GAPS` (`validation/fix/GapRegenerationFix.java`),
   enabled by default and runtime-gated on sequence availability: it runs automatically in
   any command that has a `SequenceLookup` (i.e. a FASTA/sequence was supplied) and is a
   no-op otherwise.
2. A renamed CLI command `process` → `fix` (`cli/FileProcessCommand.java` →
   `cli/FileFixCommand.java`), which runs the full fix pipeline (including
   `REGENERATE_GAPS`) over a GFF3 + FASTA pair and writes the fixed GFF3.
3. A refactor of the existing FASTA→GFF3 gap-synthesis path used by `conversion`
   (`fftogff3/FastaToGff3Converter.java`) to **delegate to the same
   `GapRegenerationFix`** instead of maintaining a second copy of the gap-scanning logic.
4. A shared, extended CLI-level gap-option validator (`utils/GapOptionsValidator.java`)
   used by both `fix` and `conversion` for fail-fast checking of `--gap-type` /
   `--linkage-evidence`.
5. A stable `(start, end)` feature sort on `GFF3Annotation`.

`validation` remains report-only (it writes no output), but — like every other fix — the
regeneration runs in-memory there too whenever a sequence is supplied. This is intentional
and harmless: the submission-critical gap *validations* (`GAP_BASES_PERCENTAGE`, N-run
checks) read the **sequence**, not the gap features, so they remain fully meaningful and
complementary to the fix.

# Motivation & Rationale

Submitters send GFF3 + FASTA pairs where the GFF3's gap features drift out of sync with
the actual N-runs in the FASTA (stale coordinates, missing gaps, wrong
`estimated_length`). The authoritative signal for a gap is the sequence itself, so the
only robust correction is to throw the GFF3's gap features away and rebuild them from the
FASTA. We already synthesise gaps from a FASTA during `conversion` (FASTA→GFF3), so the
same logic should back both flows rather than being duplicated.

Design rationale for the key choices (most were pre-decided in the discovery brief and are
restated here as constraints; the "Decisions made in this spec" section below records the
choices left open):

- **`fix` command name.** Mirrors the `validation` (report-only) / `fix` (mutate + write)
  pairing and the existing `@Gff3Fix` / `FixMethod` vocabulary already in the codebase.
- **Ontology-based gap identification.** Resolve `feature.getName()` through
  `OntologyClient.findTermByNameOrSynonym(...)` and compare to `OntologyTerm.GAP.ID`
  (`"SO:0000730"`) — the exact pattern in `GapEstimatedLengthFix` and
  `AssemblyGapValidation`. This correctly treats both `gap` and `assembly_gap` (and any
  synonym) as the same SO term instead of brittle string matching.
- **Fix delegation for `conversion`.** A single implementation of "scan N-runs → build gap
  features" removes a whole class of drift between the two flows.
- **Enabled by default, sequence-gated at runtime.** The sequence is the source of truth
  for gaps, so whenever a `SequenceLookup` is available the fix regenerates; when no
  sequence is supplied it skips entirely. This uniform rule applies across `validation`,
  both `conversion` directions, and `fix`, and needs no per-command enable/override.
  Commands that expose `--min-gap-length` / `--gap-type` / `--linkage-evidence` register a
  CLI-parameterised instance that overrides the default one (see §6); every other command
  uses the default-parameter instance (`min-gap-length=10`, no `gap_type`).

# Usage Guidelines

## The `fix` command

```
gff3tools fix -gff3 <input.gff3> --sequence <seqs.fasta> -o <output.gff3> \
    [--min-gap-length <n>] [--gap-type <type>] [--linkage-evidence <evidence>] \
    [--sequence-format fasta|plain] [--fasta-header <headers.json>] \
    [--rules <k:v,...>] [--fail-fast]
```

- `-gff3` (**required**): GFF3 input file.
- `-o` (**required**): fixed GFF3 output file (atomic write).
- `--sequence` / `--sequence-format` / `--fasta-header`: the FASTA source, via the shared
  `SequenceOptions` mixin (repeatable `--sequence`, `key:path` or bare path). FASTA is
  required for `fix` because gap regeneration needs the sequence.
- `--min-gap-length` / `-mgl` (default `10`), `--gap-type` / `-gt`, `--linkage-evidence`
  / `-le`: gap-generation parameters, identical in meaning to `conversion`.
- `--rules`, `--fail-fast`: inherited from `AbstractCommand`.

All other registered fixes (`LOCUS_TAG_TO_UPPERCASE`, `RENAME_ATTRIBUTES`,
`REMOVE_ATTRIBUTES`, `TRANSLATION`, `GAP_ESTIMATED_LENGTH`, …) run as normal; only gap
features are regenerated.

## The `conversion` command (FASTA→GFF3)

Unchanged CLI surface. `--min-gap-length` / `--gap-type` / `--linkage-evidence` behave
exactly as today; internally the gap features are now produced by `GapRegenerationFix`.

## Other commands that supply a sequence (`validation`, `conversion` GFF3→EMBL)

Because the fix is enabled by default and sequence-gated, any command that is given a
sequence provider also regenerates gaps in-memory with default parameters
(`min-gap-length=10`, no `gap_type`). For `validation` the regenerated features are never
written (it produces no output) and the sequence-based gap validations are unaffected. For
`conversion` GFF3→EMBL, supplying `--sequence` means the EMBL output's gaps are rebuilt
from the sequence; omitting `--sequence` leaves the GFF3's gaps untouched.

## Extending

The gap-generation logic lives in one place (`GapRegenerationFix`). Adjust the N-run
selection or attribute construction there and both `fix` and `conversion` inherit the
change. Valid `gap_type` values are the single source of truth in
`AssemblyGapValidation` (see Detailed Design).

# System Overview / High-Level Design

```
                       ┌────────────────────────────────────────────┐
                       │              GapRegenerationFix              │
                       │  @Gff3Fix(name=REGENERATE_GAPS)              │
                       │  @FixMethod(type=ANNOTATION, enabled=true)   │
                       │  reads SequenceLookup + OntologyClient       │
                       │  from @InjectContext ValidationContext       │
                       └───────────────▲───────────────▲─────────────┘
                                       │ validate(annotation,line)     │
        ┌──────────────────────────────┴──────┐   ┌────┴───────────────────────┐
        │  fix command (FileFixCommand)        │   │ conversion (FASTA→GFF3)     │
        │  GFF3FileReader.read(annotation ->…) │   │ FastaToGff3Converter.convert│
        │  → per-annotation validate() hook    │   │ builds empty per-seq        │
        │  removes+regenerates gaps            │   │ annotations, validate() adds│
        │  writes fixed GFF3                    │   │ gaps                        │
        └──────────────────────────────────────┘   └─────────────────────────────┘
                    both first call:  GapOptionsValidator.validate(gapType, linkageEvidence)
                    both build engine: .withFix(new GapRegenerationFix(mgl, gt, le))
                    (fix enabled by default; no overrideMethodFixs needed. validation /
                     gff3→embl use the default classpath instance, sequence-gated.)
```

Core insight already verified against the code: `GFF3FileReader.readAnnotation()` calls
`validationEngine.validate(annotation, lineCount)` at every accession boundary / `###`
directive / EOF, **after** all features for that annotation have been accumulated into
`GFF3Annotation.getFeatures()`. That is the correct and only hook for an ANNOTATION-level
fix to remove and re-add features before the annotation is handed to the caller. The
`conversion` path reaches the same fix by building an empty annotation per sequence (with
`##sequence-region` set) and calling `validate(annotation, -1)`.

# Detailed Design & Implementation

## 1. `GFF3Annotation.sortFeatures()`

`gff3/GFF3Annotation.java` currently has no sort method. Add:

```java
/** Stable sort of features by ascending (start, end); preserves relative order of ties. */
public void sortFeatures() {
    features.sort(Comparator.comparingLong(GFF3Feature::getStart)
            .thenComparingLong(GFF3Feature::getEnd));
}
```

`List.sort` is guaranteed stable, satisfying constraint 5 (equal-position features keep
their relative order). Called by `GapRegenerationFix` after splicing in new gaps.

## 2. `GapRegenerationFix` (`validation/fix/GapRegenerationFix.java`)

```java
@Slf4j
@Gff3Fix(name = "REGENERATE_GAPS", description = "Discard existing gap features and rebuild them from FASTA N-runs")
public class GapRegenerationFix implements Fix {

    @InjectContext
    private ValidationContext context;

    private final int minGapLength;   // >= 1 (defensive floor)
    private final String gapType;         // null when not supplied / blank
    private final String linkageEvidence; // null when not supplied / blank
    private int gapCounter = 0;           // document-wide, spans annotations within one run

    /** No-arg constructor required for classpath discovery; uses default parameters. */
    public GapRegenerationFix() { this(FastaToGff3Converter.DEFAULT_MIN_GAP_LENGTH, null, null); }

    public GapRegenerationFix(int minGapLength, String gapType, String linkageEvidence) { ... }

    @FixMethod(rule = "REGENERATE_GAPS", type = ANNOTATION,
               enabled = true, priority = ValidationPriority.HIGH)
    public void regenerateGaps(GFF3Annotation annotation, int line) throws ValidationException { ... }
}
```

Key points, each grounded in existing code:

- **Enabled by default; runtime-gated on `SequenceLookup`.** Both the class-level
  `@Gff3Fix` and the method-level `@FixMethod(enabled = true)` are enabled, so the fix is
  discovered, registered, and active in every command. The *only* gate on whether it does
  anything is control-flow step 1 below: no `SequenceLookup` in the context → immediate
  no-op. No `overrideMethodFixs` is used anywhere. (`ValidationEngine.executeFixes` calls
  `validationConfig.getFix(rule, methodAnnotation.enabled())`, which returns `true` here
  absent any override.)
- **No-arg constructor required.** Classpath discovery instantiates the class via its
  no-arg constructor (`ValidationRegistry.buildDescriptors` → `getDeclaredConstructor()`),
  so a no-arg constructor defaulting to (`DEFAULT_MIN_GAP_LENGTH`, `null`, `null`) is
  mandatory; commands with CLI gap options register a parameterised instance that overrides
  it by name.
- **Implements the `Fix` marker interface** — required by `ValidationEngineBuilder.withFix(Fix)`.
- **`@InjectContext ValidationContext context`** is injected even for explicit
  `withFix(...)` instances (verified in
  `ValidationRegistry.buildDescriptorsFromInstances` → `injectContext`).
- **Priority `HIGH`.** Gap regeneration is structural and independent of CDS translation
  (`TRANSLATION` runs at `LOW`); running it early keeps the annotation's feature list
  clean before other annotation-level work and mirrors `TranslationFix`'s multi-priority
  convention. Gaps do not interact with the other fixes, so exact ordering is not
  load-bearing, but `HIGH` documents intent.
- **Constructor state.** `minGapLength` is floored to `Math.max(1, minGapLength)`;
  blank `gapType`/`linkageEvidence` normalise to `null` — identical to the current
  `FastaToGff3Converter` constructor so behaviour is preserved. (The linkage-without-type
  contradiction is now caught earlier by `GapOptionsValidator`; a defensive
  `IllegalArgumentException` guard may remain to keep the class safe when constructed
  directly in unit tests.)

### Control flow of `regenerateGaps(annotation, line)`

1. If `context` has no `SequenceLookup` (`!context.contains(SequenceLookup.class)` or it
   resolves to `null`) → return without touching the annotation. Defensive per the repo's
   "validate inside the function" convention; matches `TranslationFix`.
2. Resolve `seqId = annotation.getAccession()`.
3. If `seqId` is not in `lookup.knownSeqIds()` → throw
   `ValidationException("REGENERATE_GAPS", line, ...)` **before** mutating, so a FASTA that
   does not cover the accession is surfaced through the normal engine error path
   (collected or fail-fast) rather than silently dropping the annotation's gaps.
4. Remove existing gap features: iterate a copy of `annotation.getFeatures()`, resolve each
   via `OntologyClient.findTermByNameOrSynonym(feature.getName())`, and
   `annotation.removeFeature(f)` when the SO id equals `OntologyTerm.GAP.ID`. (Copy-iterate
   to avoid concurrent modification; `removeFeature` already exists.)
5. `List<GapRegion> gaps = lookup.getGapRegions(seqId, SequenceRangeOption.WHOLE_SEQUENCE)`
   (constraint 12; same call shape as `GapFeatureBasesValidation`). Wrap the checked
   `Exception` into a `ValidationException`.
6. For each `GapRegion` with `lengthBases() >= minGapLength`, build a gap feature via the
   private `buildGapFeature(...)` helper and `annotation.addFeature(...)`, incrementing the
   instance-level `gapCounter`.
7. `annotation.sortFeatures()`.

**Document-unique IDs across annotations.** `GFF3FileReader` invokes the *same*
`GapRegenerationFix` instance for every annotation in the run (one instance per engine —
either the CLI-parameterised one registered via `withFix`, or the default classpath
instance), so the instance field `gapCounter` produces
globally unique IDs (`gap`, `gap_1`, `gap_2`, …) across all annotations in the output
file — matching the current `FastaToGff3Converter` document-wide counter and GFF3's
requirement that IDs be unique within a file. This is explicitly why the counter is
instance state, not a local variable.

### `buildGapFeature(...)` — private method, not a separate class

Only one caller remains after the `FastaToGff3Converter` refactor, so a standalone factory
class would be premature abstraction (per this repo's convention). The body is lifted
verbatim from `FastaToGff3Converter` (current lines ~136–158) to preserve output exactly:

```java
String id = gapCounter == 0 ? "gap" : "gap_" + gapCounter;
GFF3Feature f = new GFF3Feature(Optional.of(id), Optional.empty(), seqId, Optional.empty(),
        ".", "gap", gap.startBase, gap.endBase, ".", "+", ".");
f.addAttribute(GFF3Attributes.ATTRIBUTE_ID, id);
f.addAttribute(GFF3Attributes.ESTIMATED_LENGTH, String.valueOf(gap.lengthBases()));
if (gapType != null)         f.addAttribute(GFF3Attributes.GAP_TYPE, gapType);
if (linkageEvidence != null) f.addAttribute(GFF3Attributes.LINKAGE_EVIDENCE, linkageEvidence);
```

`estimated_length` is set here directly. Note `GAP_ESTIMATED_LENGTH` (a FEATURE-level fix)
will **not** re-fire on these programmatically-added features (constraint 11), which is
fine because we set it ourselves — exactly as the converter does today.

## 3. `GapOptionsValidator` (`utils/GapOptionsValidator.java`)

**Location decision: `utils/`.** It is a pure, stateless helper shared by two `cli`
commands and referencing validation vocabulary; `utils/` is where cross-cutting helpers
(`OntologyClient`, conversion helpers) already live, and it keeps the shared rule out of
either command. It elevates the existing private `FileConversionCommand.validateGapOptions()`

- `GAP_TYPES_REQUIRING_LINKAGE` and extends it with full `gap_type` vocabulary validation.

```java
public final class GapOptionsValidator {
    private GapOptionsValidator() {}
    public static void validate(String gapType, String linkageEvidence) throws CLIException { ... }
}
```

Logic (superset of the current `validateGapOptions`):

1. `--linkage-evidence` without `--gap-type` → `CLIException`.
2. If `--gap-type` present, validate it against the **full** vocabulary. The single source
   of truth is `AssemblyGapValidation`'s `GAP_TYPE` map keys (`within scaffold`,
   `between scaffolds`, `between scaffold`, `centromere`, `short arm`, `heterochromatin`,
   `telomere`, `repeat within scaffold`, `unknown`, `repeat between scaffolds`,
   `contamination`). Expose them as a public accessor
   (`AssemblyGapValidation.validGapTypes()` returning `GAP_TYPE.keySet()`) so the vocabulary
   is defined once. Unknown value → `CLIException`.
3. Linkage-evidence relationship (unchanged): required for `within scaffold` /
   `repeat within scaffold` / `contamination`; not allowed for any other type.

**Why CLI-level fail-fast is the resolution for constraint 11.** For generated gaps, the
FEATURE-level `AssemblyGapValidation` vocabulary check does not re-fire (features are added
inside an ANNOTATION-level fix; `validate(annotation, …)` does not cascade into FEATURE
validators — verified in `ValidationEngine`). Validating the option values once, up front,
before the engine is built covers every generated gap in the run with a clear usage
message. No engine self-reference/callback plumbing is added to `ValidationContext`
(explicitly rejected in the brief).

`FileConversionCommand.validateGapOptions()` is deleted and its call site replaced with
`GapOptionsValidator.validate(gapType, linkageEvidence)`; `GAP_TYPES_REQUIRING_LINKAGE` moves
into the validator.

## 4. `FastaToGff3Converter` refactor (behavior-preserving)

- Constructor simplifies to `(ValidationEngine engine, FileSequenceSource source)`: the
  `minGapLength` / `gapType` / `linkageEvidence` now live in the `GapRegenerationFix`
  instance the caller registers on the engine.
- `convert(...)` keeps the sequence-enumeration loop (ordinal → seqId, `getStats().totalBases()`
  for length, build `GFF3Annotation` with `##sequence-region`) but **drops** the inner
  gap-scanning loop and the per-feature `validate(feature, -1)` call. It now just calls
  `validationEngine.validate(annotation, -1)` per annotation; the registered
  `GapRegenerationFix` populates the gaps.
- `writeGFF3String` + `throwIfErrorsCollected()` at the end are unchanged.
- Output is byte-for-byte identical (same IDs, same attributes, same ordering, same
  min-length filtering) — this is a refactor, not a behaviour change.

## 5. `FileFixCommand` (`cli/FileFixCommand.java`, was `FileProcessCommand.java`)

- `@CommandLine.Command(name = "fix", …)`.
- Drop `-accessions` entirely (constraint 2).
- Keep `-gff3` (required) and `-o` (required) explicit options. **Decision:** unlike
  `validation` (a stdin-pipeable reporter), `fix` mutates and writes, so explicit input and
  output flags are clearer, match the old `process` command, and avoid ambiguity with the
  positional `inputFilePath`. FASTA comes from the `SequenceOptions` mixin (`--sequence`),
  which is required for this command.
- `run()` flow (mirrors `ValidationCommand` for reading + `FileConversionCommand` for the
  atomic write and engine wiring):
  1. `GapOptionsValidator.validate(gapType, linkageEvidence)` (fail fast).
  2. `validateFile(gff3InputFile, "gff3")`, `validateOutputFile(outputFilePath)` (retain
     the existing helpers; drop `validateAccessions`).
  3. `sources = buildFastaSourceList(sequenceOptions.sequenceSpecs, sequenceOptions.sequenceFormat)`;
     fail with `CLIException` if empty (FASTA required).
  4. `compositeProvider = Gff3ProviderFactory.buildCompositeProvider(sources)`
     (+ `buildHeaderProvider` if a `--fasta-header` is supplied, matching `conversion`).
  5. Build the engine with the CLI-parameterised gap fix registered via `withFix` (see §6);
     it is enabled by default, so no override is needed.
  6. Write to a temp file, read the input GFF3 with `GFF3FileReader`, and for each
     annotation returned by `read(annotation -> …)` write it via
     `GFF3File.builder().header(header).annotations(List.of(annotation)).build().writeGFF3String(writer)`
     (streaming, one annotation at a time, the `TranslationCommand`/converter pattern).
     The annotation has already passed through `validate()` — and thus `GapRegenerationFix`
     — inside `readAnnotation()`.
  7. On success, atomically `Files.move` the temp file to `-o` (the exact
     `FileConversionCommand` pattern, including the `AtomicMoveNotSupportedException`
     fallback); delete the temp file on any failure.
  8. `validationEngine.throwIfErrorsCollected()` before completing.

## 6. Engine wiring — `AbstractCommand.initValidationEngine(...)` overload

**Decision: add one overload** rather than each command hand-rolling a
`ValidationEngineBuilder`, to keep both commands DRY and consistent with the existing
helper:

```java
protected ValidationEngine initValidationEngine(
        Map<String, RuleSeverity> ruleOverrides,
        List<Fix> extraFixes,
        ContextProvider<?>... additionalProviders) {
    ValidationEngineBuilder builder = new ValidationEngineBuilder()
            .overrideMethodRules(ruleOverrides)
            .failFast(failFast);
    for (Fix fix : extraFixes) builder.withFix(fix);
    for (ContextProvider<?> p : additionalProviders) builder.withProvider(p);
    return builder.build();
}
```

The existing 2-arg/vararg overload delegates with `List.of()` so all current callers are
unaffected. `fix` and the `conversion` FASTA→GFF3 branch call the new overload with
`extraFixes = List.of(new GapRegenerationFix(mgl, gt, le))` — no `fixOverrides`, since the
fix is enabled by default. This is verified safe: explicit `withFix` instances override
classpath-discovered ones by `@Gff3Fix.name()` on collision
(`ValidationRegistry.mergeDescriptors`) and receive context injection, so registering our
single instance will not throw `DuplicateValidationRuleException`.

`FileConversionCommand` passes the parameterised instance only for the FASTA→GFF3 branch
(it knows `fromFileType`/`toFileType` before building the engine); all other conversion
branches pass `List.of()` and simply rely on the default classpath instance, which is
sequence-gated (active for GFF3→EMBL only when `--sequence` is supplied, a no-op
otherwise).

## 7. `Main.java`

Replace `FileProcessCommand.class` with `FileFixCommand.class` in the `subcommands` list.

## Corner cases

- **No FASTA / empty `--sequence`** in `fix`: fail fast with `CLIException`.
- **Accession in GFF3 not covered by FASTA**: `ValidationException` from the fix (step 3),
  surfaced via collected-errors or fail-fast — no partial mutation.
- **Sequence with no N-runs**: existing gaps removed, none added; annotation still written
  (empty gap set), sequence-region preserved.
- **`plain` (headerless) sequence**: `conversion` already rejects this; `fix` inherits the
  same seqId-resolution constraints via `annotation.getAccession()` matching
  `lookup.knownSeqIds()`.
- **Idempotency**: running `fix` twice yields identical output (regeneration is a pure
  function of the FASTA + options).

# Alternatives Considered

- **Engine self-reference in `ValidationContext`** so the fix could re-trigger FEATURE-level
  `AssemblyGapValidation` on generated gaps. Rejected (per brief) as unnecessary complexity;
  the CLI-level `GapOptionsValidator` fail-fast covers the only mutable inputs (gap_type /
  linkage_evidence values).
- **Standalone gap-feature factory class.** Rejected: single caller after the converter
  refactor → premature abstraction.
- **Literal name matching (`"gap"`/`"assembly_gap"`)** instead of ontology resolution.
  Rejected: brittle and inconsistent with the rest of the codebase.
- **Keeping two gap-synthesis implementations** (converter + fix). Rejected: the whole point
  is to unify and prevent drift.
- **`GapOptionsValidator` in `cli/`.** Reasonable (both callers are in `cli`) but `utils/`
  better isolates the shared rule and avoids either command owning shared vocabulary logic.

# Technical Debt / Future Considerations

- FEATURE-level `AssemblyGapValidation` still does not run over programmatically-added
  features; this is compensated at the CLI layer for the only variable inputs. If future
  fixes need full FEATURE re-validation of generated features, a deliberate re-dispatch
  mechanism in the engine would be the place to add it.
- `--sequence-format` applies globally to all `--sequence` entries (existing mixin
  limitation); unchanged here.
- `gapCounter` being instance state assumes one fix instance per run; documented and true
  for both call sites, but worth a comment on the field.

# Testing Strategy

TDD, each phase ending green on `./gradlew test`; run `./gradlew spotlessApply` before test
runs where files were added/edited.

- **`GFF3AnnotationTest`**: add a `sortFeatures()` test (stable, ascending `(start,end)`).
- **`GapRegenerationFixTest`** (unit, `validation/fix/`): mock `OntologyClient` and
  `SequenceLookup`, inject via a `ValidationContext` populated with both (extend
  `TestUtils.injectContext` if a two-provider variant is needed — current variants cover a
  single `OntologyClient` or a full `ValidationContext`). Cover: removes existing gaps;
  regenerates from N-runs; `minGapLength` filtering; `estimated_length` set; optional
  `gap_type`/`linkage_evidence`; document-unique IDs across two annotations; unknown seqId
  throws before mutation; no `SequenceLookup` → no-op; features sorted.
- **`GapOptionsValidatorTest`** (unit, `utils/`): linkage-without-type; unknown gap_type;
  required-linkage types; not-allowed-linkage types; valid combinations.
- **`FastaToGff3ConverterTest`**: update the test harness to register + enable
  `GapRegenerationFix` on the engine and use the simplified converter constructor. **Expected
  GFF3 output must be unchanged** — this is the behaviour-preservation checkpoint. Existing
  `fftogff3_rules/gap_*` and `fasta_to_gff3/*` fixtures/expectations must NOT change.
- **`FileFixCommandIntegrationTest`** (`cli/`): end-to-end `fix` run over a GFF3 + FASTA
  pair asserting the output GFF3 has regenerated gaps and untouched non-gap features; error
  cases (missing FASTA, bad gap_type, accession not in FASTA); atomic-write behaviour (no
  output file on failure). Fixtures in a dedicated `src/test/resources/gap_regeneration/`
  directory (plain JUnit fixtures, not the auto-discovered `*_rules` pair convention, since
  `fix` is not a pure format conversion).

# Phased Delivery Plan

Each phase is independently buildable and ends with `./gradlew test` (add
`-Pgitlab_private_token=dummy-token` locally) and `./gradlew spotlessApply`.

**Phase 1 — Scaffolding & sort.**

- Add `GFF3Annotation.sortFeatures()` + test.
- Expose `AssemblyGapValidation.validGapTypes()` (public accessor over `GAP_TYPE` keys).
- Gate: `./gradlew test` green.

**Phase 2 — `GapOptionsValidator`.**

- Create `utils/GapOptionsValidator.java` (elevated + extended logic) + `GapOptionsValidatorTest`.
- Repoint `FileConversionCommand` to it; delete `validateGapOptions` + `GAP_TYPES_REQUIRING_LINKAGE`.
- Gate: `./gradlew test` green (conversion behaviour unchanged).

**Phase 3 — `GapRegenerationFix` (enabled by default, sequence-gated).**

- Create `validation/fix/GapRegenerationFix.java` with `@FixMethod(enabled=true)`, a no-arg
  constructor (defaults) + the parameterised constructor, and private `buildGapFeature`.
- Add `GapRegenerationFixTest`.
- Add/confirm a test that `validation` **with a sequence** runs the fix in-memory (no-op on
  output, sequence-based gap validations unaffected) and `validation` **without a sequence**
  is a pure no-op.
- Gate: `./gradlew test` green.

**Phase 4 — `initValidationEngine` overload.**

- Add the `(ruleOverrides, extraFixes, providers…)` overload; existing overload
  delegates with `List.of()`.
- Gate: `./gradlew test` green (all existing callers unaffected).

**Phase 5 — `conversion` refactor (behaviour-preserving).**

- Simplify `FastaToGff3Converter` to delegate to `GapRegenerationFix`.
- Update `FileConversionCommand` FASTA→GFF3 branch to register the parameterised fix via the
  new overload (enabled by default) and construct the simplified converter.
- Update `FastaToGff3ConverterTest` harness; **assert unchanged output** for all gap fixtures.
- Gate: `./gradlew test` green; diff the `fasta_to_gff3` / `fftogff3_rules/gap_*` expected
  outputs to confirm no change.

**Phase 6 — `fix` command.**

- Rename `FileProcessCommand` → `FileFixCommand`, command name `fix`, drop `-accessions`, add
  gap options + `SequenceOptions` mixin, implement `run()` (read loop + atomic write + engine
  with the parameterised fix registered via `withFix`).
- Update `Main.java` subcommands.
- Add `FileFixCommandIntegrationTest` + `src/test/resources/gap_regeneration/` fixtures.
- Gate: `./gradlew spotlessApply && ./gradlew test` green.

**Phase 7 — Docs & polish.**

- Update `docs/cli-usage-guide.md` (rename `process`→`fix`, document gap options); mark this
  spec `Last Updated`.
- Gate: `./gradlew spotlessCheck test` green.

# Decisions Made in This Spec (previously open)

| Open question | Decision |
|---|---|
| `GapOptionsValidator` location | `utils/` (shared, stateless, single source of gap-type vocab via `AssemblyGapValidation.validGapTypes()`). |
| Gap-feature construction | Private `buildGapFeature` method in `GapRegenerationFix` (single caller → no factory class). |
| Fix enablement | Enabled by default (class + method `enabled=true`); the only runtime gate is `SequenceLookup` presence (no-op when absent). No `overrideMethodFixs` anywhere. Runs uniformly across `validation`, both `conversion` directions, and `fix`. |
| `@FixMethod` priority | `HIGH`. |
| Constructors | No-arg (defaults, for classpath discovery) + `(minGapLength, gapType, linkageEvidence)` (CLI-parameterised, registered via `withFix`, overrides the default by name). |
| `regenerateGaps` control flow | no-op without `SequenceLookup`; throw on unknown seqId before mutating; remove-then-regenerate; instance `gapCounter` for doc-unique IDs; sort at end. |
| `-gff3` input flag | Keep explicit + required (paired with required `-o`); FASTA via `--sequence`. |
| `initValidationEngine` | Add one overload accepting `extraFixes` (+ providers); existing overload delegates with `List.of()`. No `fixOverrides` param needed (fix enabled by default). |
| Test resources | Dedicated `src/test/resources/gap_regeneration/` with plain JUnit fixtures (not `*_rules` auto-discovery). |
| `fftogff3_rules` fixtures | Unchanged; converter refactor is behaviour-preserving (Phase 5 verification step). |

# Related Documentation & Resources

- `docs/0003_validation_engine.md`, `docs/0006_validation_priority.md` — engine + priority model.
- `docs/0002_validation_rules.md`, `docs/0005_validation_context.md` — rule config + context injection.
- Code anchors: `validation/fix/{GapEstimatedLengthFix,TranslationFix}.java`,
  `validation/builtin/AssemblyGapValidation.java`, `validation/{ValidationEngine,ValidationRegistry,ValidationEngineBuilder,ValidationConfig}.java`,
  `fftogff3/FastaToGff3Converter.java`, `cli/{FileConversionCommand,ValidationCommand,AbstractCommand,SequenceOptions,Main}.java`,
  `gff3/{GFF3Annotation,GFF3Feature,GFF3File}.java`, `gff3/reader/GFF3FileReader.java`,
  `sequence/SequenceLookup.java`, `validation/provider/{FileSequenceSource,CompositeSequenceProvider}.java`,
  `utils/{OntologyClient,OntologyTerm}.java`.

# Phases (JSON)

```json
{
  "phases": [
    { "phase": 1, "focus": "GFF3Annotation sortFeatures and validGapTypes accessor", "effort": "S", "difficulty": "standard" },
    { "phase": 2, "focus": "GapOptionsValidator shared CLI gap option validation", "effort": "S", "difficulty": "standard" },
    { "phase": 3, "focus": "GapRegenerationFix enabled by default sequence gated", "effort": "M", "difficulty": "hard" },
    { "phase": 4, "focus": "initValidationEngine overload accepting extra fixes", "effort": "S", "difficulty": "standard" },
    { "phase": 5, "focus": "FastaToGff3Converter refactor to delegate to the fix", "effort": "M", "difficulty": "hard" },
    { "phase": 6, "focus": "fix CLI command renamed from process", "effort": "M", "difficulty": "standard" },
    { "phase": 7, "focus": "docs and cli usage guide updates", "effort": "S", "difficulty": "standard" }
  ]
}
```
