- Feature Name: `annotation_scoped_fasta_writing`
- Document Date: 2026-09-07
- Last Updated: 2026-09-08

# Summary

The GFF3 writer could not produce a valid multi-annotation document with translations. Asked to
append them, it emitted a `##FASTA` section after *every* annotation — and because `##FASTA`
terminates the feature section, the result was a file that gff3tools' own reader parses as a
single annotation. **That part is now fixed** (§4.1): the section is written once, after every
annotation, scoped to the annotations the file contains.

Two defects remain open, and investigating them turned up a third and more consequential
question that this document exists to settle: **when a file carries translations, where should
they come from?** The writer prefers translations read back out of the *submitted* file over the
ones gff3tools itself generated — which, for the webin-gff3-stages pipeline, is very likely the
wrong source (§3.3). Tracing that path's history (§3.5) suggests it is not a deliberate
alternative at all but a superseded iteration that was never removed, which would make one of
the two open defects a deletion rather than a fix.

# Motivation & Rationale

## What the pipeline needs

webin-gff3-stages is moving from one archived GFF3 object per analysis to one per *annotation
group* (`webin-gff3-stages/docs/design/per-annotation-gff3-archiving.md`):

| Archived object | Content |
|---|---|
| split part (a chromosome, or a TSV annotation) | 1 annotation + its translations |
| bundle (non-chromosome remainder) | N annotations + their translations |

Both are the same shape — a GFF3 document holding a subset of a submission's annotations plus
exactly that subset's translations — differing only in the size of the subset. It needs no new
format and no new mode; it needs the writer to scope the FASTA section to its own annotations
and to put it where a reader will find it.

## Why this was not caught earlier

**Neither write path had a test with more than one annotation carrying translations.** The
FF→GFF3 fixtures `phase_feature.embl` (one entry, four CDS) and `reduced/contig-reduced.embl`
(one entry) are both single-annotation, and the reader-based path's only per-annotation-FASTA
test builds a separate `GFF3File` per annotation. A single-annotation file is well formed under
either behaviour, so the interleaving was invisible to the whole suite.

# System Overview / High-Level Design

Two factory methods build a `GFF3File`, and they behave differently in ways that matter here:

```
GFF3FileFactory.from(EmblEntryReader, MasterMetadata)      FF → GFF3
    translations: TranslationState        (captured/generated during validation)
    writeAnnotationFasta: never set → false
    → writeTranslationSection()

GFF3FileFactory.fromAnnotationAndReader(annotations, reader, append, fallback)
    translations: reader offsets          (parsed out of the SUBMITTED file's ##FASTA)
    writeAnnotationFasta: caller's choice
    → append ? offsets : writeTranslationSection()
```

The consequence, and the crux of §3.3: **the same field means different things depending on
which factory built the object.** `from()` writes a FASTA section with the flag `false`;
`fromAnnotationAndReader(..., false, ...)` writes none. Both are current, tested behaviour.

# Detailed Design & Implementation

## 3.1 Defect 1 — interleaved `##FASTA` sections (FIXED, §4.1)

`writeGFF3String()` wrote the header outside the annotation loop but, with
`writeAnnotationFasta` set, a `##FASTA` section *inside* it. For more than one annotation that
produced one header followed by feature blocks interleaved with FASTA sections.

Per the GFF3 specification `##FASTA` terminates the feature section: everything after it is
sequence data to EOF. Features written after it are not features.

**Measured, two annotations in, before the fix:** `##FASTA` count 2, `##gff-version` count 1,
and reading the document back recovered **1 annotation of 2** — silent data loss, no diagnostic.
`GFF3FileReader.readAnnotation()` breaks on the first `##FASTA` (`GFF3FileReader.java:81`) and
the following call matches `TRANSLATION_ID_PATTERN` and returns null.

The mode itself was not wrong; it was built for *one `GFF3File` per annotation*, which
`GFF3FileReaderTest.testReadTranslation()` exercises correctly. Nothing prevented or warned
about the multi-annotation misuse, and the pipeline fell into it.

## 3.2 Defect 2 — source selection by presence, not content (OPEN)

`writeTranslationSection()` chooses a source by null-check:

```java
if (translationState != null)        writeFastaFromTranslationState(writer);
else if (fastaFilePath != null)      writeFastaFromExistingFile(writer);
else if (gff3Reader != null && ...)  writeFastaFromOffsets(writer, gff3Reader.getTranslationOffsetMap());
```

`TranslationStateProvider` is classpath-scanned and auto-instantiated, so `translationState` is
**always** non-null when the object came from either factory. Branch one always wins;
`writeFastaFromTranslationState()` returns early when nothing is resolved, and no fallback runs.

The design doc that introduced this (`2604071325`) stated the contract explicitly — *"When
non-null, it writes `##FASTA`; when null, no FASTA section"* — and at the time presence was a
real signal. It stopped being one when `TranslationState` became unconditionally provided.

Consequences: the documented `existingTranslationFilePathFallback` can never fire, and
`writeFastaFromExistingFile()` is unreachable through the factories. Its only test invokes it
**by reflection** (`GFF3FileTest.java:49`), bypassing the entry point — which is how a
permanently dead branch keeps a green tick. No call site in either repository has ever passed a
non-empty fallback.

## 3.3 The source question — is the pipeline reading the wrong translations? (OPEN, most important)

This did not start as a defect report; it emerged from asking where translations come from.

`TranslationFix` runs whenever `SequenceLookup` is in the validation context
(`TranslationFix.java:81`) and records generated translations into `TranslationState`. The
webin-gff3-stages pipeline registers a `CompositeSequenceProvider` for `SequenceLookup`
(`FileSequenceSource` / `ECASequenceSource`, `SequenceContextDTOFactory.java:33`), so in
pipeline conditions **translations are generated by us and live in `TranslationState`**.

That also activates `CdsTranslationPresenceValidation` (`CDS_TRANSLATION_PRESENCE`, ERROR),
whose gate is exactly `SequenceLookup` + `TranslationState`. It fails the submission if any
non-exempt CDS lacks a generated translation, looking up by **exact key**. So the pipeline has
a validated guarantee that `TranslationState` is complete and correctly keyed.

But the pipeline passes `appendTranslationFasta = true`, and that path reads **reader offsets**
— translations parsed back out of the *submitted* file's `##FASTA`. Two failure modes follow:

* submitted GFF3 has no `##FASTA` — expected for FASTA+GFF3 submissions, where the translations
  are ours — the offset map is empty and **nothing is archived**, immediately after validation
  certified a complete set exists;
* submitted GFF3 does have one — the archive receives the **submitter's** translations rather
  than the ones `TranslationFix` generated or corrected.

If this holds, the completeness guarantee `CDS_TRANSLATION_PRESENCE` provides never reaches the
archived file, because the file is written from a different source than the one validated.

**Resolved (D1).** Confirmed by the pipeline authors: a submitted GFF3 **should not** carry a
`##FASTA`, and if one arrived it would be ignored — the translations are ours to generate. So
the live symptom is the first: the offset map is empty and **nothing is archived**, immediately
after validation certified a complete set exists. The second form is not a data-corruption risk
in production, but it is exactly what the writer does in the degenerate case below.

### 3.3.1 What happens to a submitted translation when no sequence source is registered

Measured, with a submitted `##FASTA` and no `SequenceSource` added:

```
SequenceLookup registered=true | resolves to null | TranslationState entries=0
reader offsets=2               | parsing warnings=0

append=true  -> submitted protein echoed verbatim, ##FASTA count=1
append=false -> submitted protein dropped,         ##FASTA count=0
```

`CompositeSequenceProvider` is auto-discovered like `TranslationStateProvider`, so the
`SequenceLookup` *type* is always present — but `isActive()` is `!sources.isEmpty()` and `get()`
returns `null` with no sources. `TranslationFix` and `CdsTranslationPresenceValidation` both
check **presence and non-nullness**, so both correctly no-op; `TRANSLATION_COMPARISON` finds an
empty state and skips. The submitted translations are therefore never captured, never compared
and never validated — while the reader parses them into offsets regardless.

The result is that submitted translations are either **passed through unverified** (`true`) or
**silently discarded** (`false`), decided by a flag, with no diagnostic in either direction. Given
that submitted translations are to be ignored on principle, the `true` behaviour is precisely the
wrong one — and it is the one the pipeline selects.

Worth noting the contrast this exposes: the two-step check (`contains(...)` **and**
`get(...) != null`) is the established idiom for auto-registered providers, applied correctly in
`TranslationFix.java:81-85` and `CdsTranslationPresenceValidation.java:86-88`.
`GFF3File.writeTranslationSection()` is the one place that checks presence alone — and for
`TranslationState` even the two-step check would not be enough, since the state is non-null but
empty. It needs a content check (§3.2).

## 3.4 Defect 3 — accession matching by string prefix (OPEN)

`GFF3FileReader.getTranslationOffsetForAnnotation()` selects an annotation's translations with
`key.startsWith(annotation.getAccession())` over keys of the form `accession|featureId`, so an
accession that is a string prefix of another claims the other's translations. Confirmed
directly:

```
AB123.10's translation was claimed by AB123.1
  expected: <[AB123.1|CDS_SHORT]>
  but was:  <[AB123.1|CDS_SHORT, AB123.10|CDS_LONG]>
```

The fix is `startsWith(accession + "|")`, or splitting on the first `|` and comparing exactly —
safe either way, since accessions cannot contain `|` and feature IDs are URL-encoded (`|` →
`%7C`).

**Scope is narrower than it first appears.** This predicate is only reached on the offsets path,
i.e. for *submitter-authored* FASTA headers. Translations we generate are keyed by
`TranslationKey.of(feature.accession(), featureId)` — the same string `getAccession()` returns —
so exact matching is provably safe for them, and the mixed-versioning hazard below does not
arise. If §3.3 resolves toward `TranslationState`, this defect stops being on the pipeline's
path at all, though it remains a correctness bug for submissions that carry their own
translations.

Severity rose once scoping became real: before the §4.1 fix this predicate fed one
per-annotation section; now it is the only thing deciding what a subset file contains. A
per-annotation archived object could carry a protein belonging to a different annotation, with
nothing downstream to flag it.

## 3.5 Where the offsets source came from, and what it is for

Everything on the offsets path arrived in one commit — `ee18ea06`, *"Ena 6701 moving translation
fasta to end"* (#67, Nov 2025): `GFF3TranslationReader`, `OffsetRange`, `TranslationWriter`,
`getTranslationOffsetMap()`, `getTranslationOffsetForAnnotation()`, and the
`writeAnnotationFasta` flag.

**Its real purpose is GFF3 → EMBL flat file.** `GFF3Mapper.mapTranslation()`
(`GFF3Mapper.java:237`) reads translations out of a submitted GFF3's `##FASTA` and emits them as
`/translation=` qualifiers. Offsets rather than strings so proteins are read lazily, one at a
time, instead of being held in memory — a sound design for that job, and still current. Note it
looks translations up by **exact key**, `TranslationKey.of(accession, featureId)`, so the
GFF3→FF direction is immune to §3.4.

**The writer's use of that source looks like a leftover iteration.** The commit's own message
records the sequence: *"This commit saves FASTA in each annotation"*, then *"Update to code to
add translation to end of file"*. Both implementations shipped — the second as the default, the
first preserved behind `writeAnnotationFasta`. `getTranslationOffsetForAnnotation()`, the only
place in the codebase that matches accessions by prefix, exists solely to serve that first
iteration. The PR title says the intent plainly.

Five months later `2604071325` made `TranslationState` the translation source of truth and
explicitly left the *"GFF3->FF offset map path"* out of scope — correctly, since that path is
`GFF3Mapper`'s. But the **writer's** offset path is not the GFF3→FF path, and it was never
revisited. That is how the pipeline ended up archiving from a source the library had already
stopped treating as authoritative.

This reframes §3.4: if the writer sources from `TranslationState` (D2), then
`getTranslationOffsetForAnnotation()` has no remaining caller, and the prefix defect is
**deleted rather than fixed**.

## 3.6 Defect 4 — the factory's documented contract (OPEN)

Different in kind from the others — these are behaviour, this is contract — but it is the one
that propagated the bug into the pipeline. Of `fromAnnotationAndReader`'s four documented
parameters:

| Param | Doc says | Reality |
|---|---|---|
| `annotations` | "already constructed GFF3 annotations" | accurate |
| `gff3FileReader` | "providing species, validation context, and warnings" | incomplete — omits its most consequential role, the translation data source |
| `appendTranslationFasta` | "whether to append annotation FASTA output" | it selects *where*, not *whether*; `false` still wrote a section on the FF path |
| `existingTranslationFilePathFallback` | "defaulted to if the `TranslationState` is not available" | unreachable (§3.2) |

"Whether to append" makes `false` read as "no translations", so anyone wanting translations in
the file reaches for `true`. That is the most plausible route by which the pipeline ended up on
the interleaving path.

Two structural notes: `from` is an instance method using the `engine` field while
`fromAnnotationAndReader` is static and digs the engine out of the reader; and the latter reads
`gff3FileReader.gff3Species` as a public field rather than via `getSpecies()`, coupling the
factory to read order. `fromAnnotationAndReader` has **no production caller inside gff3tools** —
only tests, plus webin-gff3-stages. The contract that is wrong is the one only an external
repository depends on.

# 4. What has been implemented

## 4.1 Structural fix (done, on `archiving-gff3-fix`)

19 lines across two files.

`GFF3File.writeGFF3String()` — FASTA emission hoisted out of the annotation loop:

```java
for (GFF3Annotation ann : annotations) {
    ann.writeGFF3String(writer);
}

// ##FASTA terminates the feature section, so it is written once, after every
// annotation — never interleaved between them.
if (writeAnnotationFasta) {
    writeFastaFromOffsets(writer, translationOffsetsForAnnotations());
} else {
    writeTranslationSection(writer);
}
```

with a helper that gathers, in annotation order, what the loop used to write piecemeal:

```java
private Map<String, OffsetRange> translationOffsetsForAnnotations() {
    Map<String, OffsetRange> offsets = new LinkedHashMap<>();
    for (GFF3Annotation ann : annotations) {
        offsets.putAll(gff3Reader.getTranslationOffsetForAnnotation(ann));
    }
    return offsets;
}
```

`writeFastaFromOffsets` is unchanged and still returns early on an empty map, so no empty
`##FASTA` directive is written.

**Determinism, fixed in the same change.** `getTranslationOffsetForAnnotation` collected via
`Collectors.toMap(...)` into a `HashMap`, discarding the sorted order of the `TreeMap` that
`readTranslationOffset()` builds. Harmless while each annotation had its own section; merging
would have made hash order the order of the whole file. It now collects into a `LinkedHashMap`.
This matters beyond tidiness: the archiving design relies on **byte-stable output** for its
MD5 skip-if-unchanged behaviour, and a reordered FASTA section would not merely miss the skip —
it would trip `Gff3Archive`'s MD5-mismatch guard and fail the stage.

**Not changed, deliberately:** no null guard on `gff3Reader` — a translation-carrying file built
without a reader stays a loud construction error rather than a silent empty section.

**Verification.** `GFF3FileReaderTest.testReadTranslation()`, the existing test of the
per-annotation mode, still passes untouched: it builds one `GFF3File` per annotation, so the
merged map holds a single entry and output is byte-identical. No golden fixture moved — every
existing fixture is single-annotation, so the change from N trailing blank lines to one affected
nothing.

# Alternatives Considered

## A custom container format — concatenated documents with separators

One blob per submission holding each annotation as a standalone document, separated by a
repeated `##gff-version` header. Close to what the per-annotation mode's *tested* usage
produces, so nearly free to build.

Rejected: it relocates the problem rather than solving it. The archiving work splits a
submission into separate archived objects, so boundaries are expressed by object identity; a
container would reintroduce inside the blob exactly the structure being pulled out. It is also
not valid GFF3 — `##gff-version` mid-file — so consumers gain a bespoke parser requirement, and
gff3tools' own reader would still stop at the first `##FASTA` and need matching reader work.
Both shapes actually needed are ordinary valid GFF3.

Worth revisiting only if a consumer needs to extract one annotation from a bundle without
parsing it. None does.

## Splitting the file downstream instead of writing subsets

The pipeline could keep writing one document and split it by line-routing in its ARCHIVE stage
(its design doc §3.4 assumed exactly this). Still the plan for the *accessioned* file, because
accession rewriting happens stages later and the object model is long gone by then. But it is
not a substitute for the writer being correct — a splitter cannot recover annotations from a
document that already reads as one.

## Keep `writeAnnotationFasta`, document it as multi-document mode

Make the flag write a header per annotation so its output is a genuine concatenation of
standalone documents. Rejected: it preserves a footgun the pipeline already fell into, to serve
a use case nobody has.

# Technical Debt / Future Considerations

* **`writeFastaFromTranslationState()` is unscoped** — it writes every resolved entry in the
  state. The scoping added in §4.1 covers the offsets path only. Any move toward
  `TranslationState` (§3.3) must carry the same scoping across, or a subset file — a single
  chromosome's archived object — would receive the entire submission's translations. That is the
  §3.4 corruption case inverted: not one neighbour leaking in, but everything.
* **No reverse-mapping validation.** `CDS_TRANSLATION_PRESENCE` checks the forward direction
  (every CDS has a translation) by exact key, so it is immune to §3.4. Nothing checks the
  reverse — that every translation traces back to exactly one annotation present in the file.
  `DUPLICATE_SEQ_ID` guarantees accessions are unique but says nothing about one being a prefix
  of another, so it offers no protection here either.
* **`fastaFilePath` cannot be scoped** — it is an opaque file. If a subset document ever needs
  it, that source needs an index.
* **Undocumented behaviour of `from()`**, pinned by characterisation tests rather than treated as
  requirements: it always stamps `##gff-version 3.1.26` regardless of the source, and it takes
  `##species` from the first entry only. The latter means a mixed-organism flat file silently
  loses every organism after the first, with no diagnostic.
* **Streaming.** One trailing `##FASTA` needs the annotation set up front. Offsets are read
  lazily so memory stays at one translation at a time, but a genuinely streaming writer would
  need two passes or a temp file. `2604071325` already lists streaming as future work.

# Testing Strategy

`src/test/java/uk/ac/ebi/embl/gff3tools/gff3/Gff3FileWritingTest.java` — 19 tests in four
nests, organised by the properties a written document must satisfy rather than by defect, so it
keeps its shape as defects close. Current state: **3 failing**, both remaining defects.

| Nest | Status |
|---|---|
| `appendTranslationFasta = false — appends no FASTA output` | 5/5 pass |
| `appendTranslationFasta = true — appends the annotations' FASTA output` | 6/7 pass; `falls back to the supplied FASTA file…` fails (§3.2) |
| `converting an EMBL flat file` | 5/5 pass |
| `selecting an annotation's translations` | 0/2 pass (§3.4) |

Notes on reading it:

* The `false` nest passes, but it exercises `fromAnnotationAndReader` only. It does **not**
  establish what the flag means globally — the flat-file nest shows a document written with the
  same flag `false` that *does* carry a FASTA section. Taken together the two nests contradict
  any single reading of the flag, which is §3.3's premise.
* `writes one trailing FASTA section holding every entry's translations` (flat-file nest) is the
  reference: `from()` already produces, for multiple annotations, the shape the other path was
  fixed to produce. The golden fixture `phase_feature.gff3` is that shape too.
* Content assertions in the `true` nest run against a `trailingFastaSectionOf()` helper that
  first asserts the document is well formed — one `##FASTA`, every feature line before it — and
  returns the section body. A translation only counts when it is appended where a reader will
  look for it.
* Two flat-file tests are **characterisation, not requirements** (header stamp, species from
  first entry) and are labelled as such so they are not later read as intended contract.

Run: `./gradlew test` (add `-Pgitlab_private_token=<token>`; `--offline` works against a
populated cache). Full suite: 1332 tests, 3 failures, all of them the known-open ones.

# Deployment & Operations

Library change, released as a new gff3tools version and picked up by webin-gff3-stages via a
dependency bump. Any change to `fromAnnotationAndReader`'s signature is source-breaking for its
one production call site, `ValidationResultArchiver` — which is a feature, not a cost: it forces
the call to be revisited rather than leaving a broken combination reachable.

**Data already written is affected.** Any `validated.gff3` produced for a multi-annotation
submission is malformed and re-reads as a single annotation. Whether existing archived objects
need regenerating is a webin-gff3-stages decision, but it should be made knowingly, and §3.3 may
widen it — if translations were written from the wrong source, regeneration is about content and
not only structure.

# Open Questions & Doubts

**D1 — Does §3.3 actually fire, and in which form? — RESOLVED.** A submitted GFF3 should not
carry a `##FASTA`, and would be ignored if it did: translations are generated, not accepted. The
production symptom is therefore **no translations archived**, not wrong ones — which means no
already-archived object holds a submitter's protein, and any regeneration is about restoring
missing translations rather than correcting bad ones. §3.3.1 records what the writer does in the
degenerate case where one is submitted and no sequence source is registered.

**D1a — Should an ignored submitted `##FASTA` be reported?** Today a submitted translation
section is silently either echoed or dropped, with zero warnings (§3.3.1). If the policy is that
such a section is ignored, saying so once — a warning, or a rule — costs little and turns a
silent policy into a visible one. New rule, own severity decision; raised here rather than
assumed.

**D2 — What should `writeAnnotationFasta` mean?** Today it means different things by
construction route (§System Overview). Three coherent options:

1. *"Does this file carry translations?"* — `from()` builds with `true`; `true` runs one
   content-based chain (state → file → scoped offsets, first that yields entries); `false` writes
   nothing on both routes. Unifies the routes and makes the fallback reachable. Cost:
   `testReadTranslationAndWriteTranslationInEnd` builds via the builder with the flag unset and
   relies on the offsets branch, so it would need `.writeAnnotationFasta(true)`.
2. Content-based chain only, flag semantics untouched. Minimal, but leaves the fallback
   reachable solely on the path documented to write nothing — incoherent.
3. Remove the flag; the writer always emits whatever translations it can scope. Simplest
   contract, but removes the ability to write a features-only document, which the `false` nest
   shows is current tested behaviour someone may rely on.

Leaning toward (1), and §3.5 strengthens it considerably: the writer's offsets path is a
superseded iteration from the same commit that introduced the trailing-section behaviour, not a
deliberate second source. Under (1) the writer sources from `TranslationState` — the source
`2604071325` designated and `CDS_TRANSLATION_PRESENCE` validates — while the offsets source
stays where it belongs, serving `GFF3Mapper` for GFF3→FF conversion. It is the only option under
which §3.3's fix is a natural consequence rather than a special case.

**D3 — Delete `existingTranslationFilePathFallback`, or resurrect it?** Never passed non-empty
anywhere, its documented trigger is unreachable, and an opaque whole-file FASTA cannot be scoped
to a subset — so honouring it would be actively wrong for the archiving case. Deleting it makes
the failing test a won't-fix rather than a fix, which needs a decision rather than a commit.

**D4 — Which failure is preferable for mixed versioning?** Tightening §3.4's predicate converts
an over-match into a no-match where an annotation's accession is recorded unversioned against
versioned translation keys: a *wrong* translation becomes a *missing* one. Missing is the safer
failure — visible as absent records rather than silently wrong protein — but it is still wrong.
Worth pairing the fix with a diagnostic when an annotation with CDS features resolves to zero
translations. That is a behaviour change, not a bug fix, so it is a separate decision.

**D5 — Is a reverse-mapping validation wanted?** It would catch mis-attribution as data rather
than preventing it in one code path, and would cover D4. New rule, own severity decision, out of
scope for the current work.

**D6 — Are there pipeline paths without a `SequenceLookup`? — RESOLVED: no.** Confirmed by the
pipeline authors: every path registers one. So `TranslationFix` runs and
`CDS_TRANSLATION_PRESENCE` is active on every pipeline submission, `TranslationState` is
populated and validated throughout, and D2's answer can be uniform rather than conditional.

# Related Documentation & Resources

* `docs/2604071325_move_translation_fasta_writing.md` — established `TranslationState` as the
  translation source of truth and moved the FASTA section to the end. This document restores that
  intent for the multi-annotation and subset cases, and questions in §3.3 whether the reader-based
  path ever honoured it.
* `docs/2603171142_allow_loading_sequence_translations.md`
* `webin-gff3-stages/docs/design/per-annotation-gff3-archiving.md` — the consumer requirement.
* Key code: `gff3/GFF3File.java`, `fftogff3/GFF3FileFactory.java`,
  `gff3/reader/GFF3FileReader.java:320`, `validation/provider/TranslationState.java`,
  `validation/fix/TranslationFix.java`, `validation/builtin/CdsTranslationPresenceValidation.java`,
  `validation/builtin/DuplicateSeqIdValidation.java`, `gff3/writer/TranslationWriter.java`
