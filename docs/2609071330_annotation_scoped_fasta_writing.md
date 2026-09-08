- Feature Name: `annotation_scoped_fasta_writing`
- Document Date: 2026-09-07
- Last Updated: 2026-09-08
- Status: **Implemented** (branch `archiving-gff3-fix`)

# Summary

The GFF3 writer could not produce a valid multi-annotation document carrying translations. Asked
to append them it emitted a `##FASTA` section after *every* annotation — and because `##FASTA`
terminates the feature section, the result parsed as a single annotation, in gff3tools' own
reader. Asked not to, it wrote none, because the alternative path selected its translation source
by presence rather than content and always landed on an empty one.

Both are fixed, along with a prefix-matching defect that decided which translations belonged to
which annotation. `GFF3File` now writes one `##FASTA` section after the last annotation, holding
the translations of the annotations that file contains and no others — which is what lets one
submission be written as several documents, the requirement that started this work.

Four defects, all closed. The suite is green at 1332 tests.

# Motivation & Rationale

## What the pipeline needs

webin-gff3-stages is moving from one archived GFF3 object per analysis to one per *annotation
group* (`webin-gff3-stages/docs/design/per-annotation-gff3-archiving.md`):

| Archived object | Content |
|---|---|
| split part (a chromosome, or a TSV annotation) | 1 annotation + its translations |
| bundle (non-chromosome remainder) | N annotations + their translations |

Both are the same shape — a document holding a subset of a submission's annotations plus exactly
that subset's translations — differing only in the size of the subset. No new format and no new
mode: the writer needed to scope its FASTA section to its own annotations and put it where a
reader will find it.

## Why this was not caught earlier

**Neither write path had a test with more than one annotation carrying translations.** The
FF→GFF3 fixtures `phase_feature.embl` (one entry, four CDS) and `reduced/contig-reduced.embl`
(one entry) are both single-annotation, and the reader-based path's per-annotation-FASTA test
builds a separate `GFF3File` per annotation. A single-annotation document is well formed under
either behaviour, so the interleaving was invisible to the entire suite.

# System Overview / High-Level Design

Two factory methods build a `GFF3File`, and the difference between them mattered:

```
GFF3FileFactory.from(EmblEntryReader, MasterMetadata)      FF → GFF3
    translations: TranslationState  (captured/generated during validation)

GFF3FileFactory.fromAnnotationAndReader(annotations, reader, append, fallback)
    translations: TranslationState, then a fallback FASTA file,
                  then the SUBMITTED file's ##FASTA via reader offsets
```

Both now route through one source chain. The writer emits:

```
##gff-version …        header, when set
##species …            when set
…features…             every annotation, in order
##FASTA                once, only when a source yields translations
>accession|featureId   scoped to this file's annotations
```

# Detailed Design & Implementation

## 3.1 Defect 1 — interleaved `##FASTA` sections (FIXED)

`writeGFF3String()` wrote the header outside the annotation loop but, with
`writeAnnotationFasta` set, a `##FASTA` section *inside* it. For more than one annotation that
produced one header followed by feature blocks interleaved with FASTA sections — invalid GFF3,
since `##FASTA` terminates the feature section and everything after it is sequence to EOF.

**Measured before the fix**, two annotations in: `##FASTA` count 2, `##gff-version` count 1, and
reading the document back recovered **1 annotation of 2** — silent data loss, no diagnostic.
`GFF3FileReader.readAnnotation()` breaks on the first `##FASTA` and the following call matches
`TRANSLATION_ID_PATTERN` and returns null.

**Fix.** The FASTA emission moved out of the loop:

```java
for (GFF3Annotation ann : annotations) {
    ann.writeGFF3String(writer);
}

// ##FASTA terminates the feature section, so it is written once, after every
// annotation — never interleaved between them.
if (writeAnnotationFasta) {
    writeTranslationSection(writer);
}
```

The mode itself was not wrong; it was built for *one `GFF3File` per annotation*, which
`GFF3FileReaderTest.testReadTranslation()` exercises correctly and which still passes byte for
byte. Nothing prevented or warned about the multi-annotation misuse, and the pipeline fell into
it.

## 3.2 Defect 2 — source selection by presence, not content (FIXED)

`writeTranslationSection()` chose a source by null-check. `TranslationStateProvider` is
classpath-scanned and auto-instantiated, so `translationState` is **always** non-null when the
object came from either factory: branch one always won, returned early when the state held
nothing, and the two fallbacks were unreachable.

The design doc that introduced this (`2604071325`) stated the contract explicitly — *"When
non-null, it writes `##FASTA`; when null, no FASTA section"* — and at the time presence was a
real signal. It stopped being one when `TranslationState` became unconditionally provided.

**Fix.** Selection by content: each writer reports whether it emitted anything, so an empty
source falls through instead of ending the chain.

```java
private void writeTranslationSection(Writer writer) throws IOException {
    Set<String> accessions =
            annotations.stream().map(GFF3Annotation::getAccession).collect(Collectors.toSet());

    if (writeFastaFromTranslationState(writer, accessions)) return;
    if (writeFastaFromExistingFile(writer)) return;
    writeFastaFromOffsets(writer, translationOffsetsForAnnotations());
}
```

A side effect worth recording: `existingTranslationFilePathFallback` **fires for the first time
since it was written**. `Gff3FileWritingTest`'s "falls back to the supplied FASTA file when
TranslationState yields nothing" now passes. Its only prior test invoked
`writeFastaFromExistingFile` by reflection, bypassing the entry point — which is how a
permanently dead branch keeps a green tick.

## 3.3 Scoping to the file's annotations (NEW CAPABILITY)

Neither source was previously filtered: `writeFastaFromTranslationState` wrote every resolved
entry in the state, and the offsets path took the reader's whole map. Fine for a
whole-submission document, wrong for a subset — a single chromosome's archived object would have
carried the entire submission's translations.

Both sources are now scoped to `annotations`, which is what makes a subset document correct and
is the capability the archiving work needs. `fastaFilePath` is the exception: it is copied
verbatim and cannot be filtered, so it must not be combined with a subset — now stated in its
javadoc.

## 3.4 The source question — was the pipeline reading the wrong translations? (RESOLVED)

`TranslationFix` runs whenever `SequenceLookup` resolves (`TranslationFix.java:81-85`) and
records generated translations into `TranslationState`. webin-gff3-stages registers a
`CompositeSequenceProvider` on **every** path, so translations there are always ours, and
`CdsTranslationPresenceValidation` (`CDS_TRANSLATION_PRESENCE`, ERROR) is always active — it
fails the submission if any non-exempt CDS lacks a generated translation, by exact key.

Before this work the pipeline passed `appendTranslationFasta = true`, which read **reader
offsets** — translations parsed back out of the *submitted* file's `##FASTA`. A submitted GFF3
should not carry one, and would be ignored if it did, so the offset map was empty and
**`validated.gff3` carried no translations at all**, immediately after validation certified a
complete set existed.

With `true` now routed into the content-based chain, the pipeline gets `TranslationState` — the
source `CDS_TRANSLATION_PRESENCE` validated. **The call site did not change.** The bug was never
in what the pipeline asked for.

### 3.4.1 A submitted translation with no sequence source registered

Measured, with a submitted `##FASTA` and no `SequenceSource` added:

```
SequenceLookup registered=true | resolves to null | TranslationState entries=0
reader offsets=2               | parsing warnings=0
```

`CompositeSequenceProvider` is auto-discovered like `TranslationStateProvider`, so the
`SequenceLookup` *type* is always present — but `isActive()` is `!sources.isEmpty()` and `get()`
returns null with no sources. `TranslationFix` and `CdsTranslationPresenceValidation` both check
**presence and non-nullness**, so both correctly no-op; `TRANSLATION_COMPARISON` finds an empty
state and skips. The submitted translations are never captured, compared or validated — while
the reader parses them into offsets regardless, and the chain's last resort will echo them.

That two-step check (`contains(...)` **and** `get(...) != null`) is the house idiom for
auto-registered providers. `writeTranslationSection()` was the one place checking presence
alone — and for `TranslationState` even the two-step check is insufficient, since the state is
non-null but empty. Hence the content check of §3.2.

## 3.5 Defect 3 — accession matching by string prefix (FIXED)

`getTranslationOffsetForAnnotation()` selected translations with
`key.startsWith(annotation.getAccession())` over keys of the form `accession|featureId`, so an
accession that is a string prefix of another claimed its translations:

```
expected: <[AB123.1|CDS_SHORT]>
but was:  <[AB123.10|CDS_LONG, AB123.1|CDS_SHORT]>
```

Severity rose once §3.3 landed: this predicate became the only thing deciding what a subset
document contains, so a per-annotation archived object could carry a protein belonging to a
different annotation, with nothing downstream to flag it.

**Fix, in `TranslationKey`** — the class that defines the format now owns its inverse, so the
two call sites cannot drift apart again (which was the shape of the defect: two places deciding
the same thing, one exact, one not):

```java
public static String accessionOf(String key) {
    int separator = key.indexOf('|');
    return separator > 0 ? key.substring(0, separator) : null;
}

public static boolean belongsTo(String key, String accession) {
    return accession != null && accession.equals(accessionOf(key));
}
```

Safe to split on the first separator: accessions never contain `|`, and feature IDs are
URL-encoded by `TranslationKey.of` (`|` → `%7C`).

**Deliberate consequence.** Where an annotation's accession is recorded unversioned against
versioned keys, an over-match becomes a **no-match**: a wrong translation becomes a missing one.
Chosen knowingly — a missing translation is a visible failure, a misattributed one is not — and
recorded in the method's javadoc so it is not "fixed" back later.

## 3.6 Where the offsets source came from, and what it is for

Everything on the offsets path arrived in `ee18ea06`, *"Ena 6701 moving translation fasta to
end"* (#67, Nov 2025): `GFF3TranslationReader`, `OffsetRange`, `TranslationWriter`,
`getTranslationOffsetMap()`, `getTranslationOffsetForAnnotation()`, and the
`writeAnnotationFasta` flag.

**Its purpose is GFF3 → EMBL flat file.** `GFF3Mapper.mapTranslation()` reads translations out of
a submitted GFF3's `##FASTA` and emits them as `/translation=` qualifiers, lazily by offset so
proteins are never all held in memory. Sound design for that job, still current, and it looks
translations up by **exact key**, so that direction was never exposed to §3.5.

**The writer's use of that source was a leftover iteration.** The commit message records the
sequence: *"This commit saves FASTA in each annotation"*, then *"Update to code to add
translation to end of file"*. Both shipped — the second as the default, the first preserved
behind the flag. `getTranslationOffsetForAnnotation()` existed solely to serve the first.

Five months later `2604071325` made `TranslationState` the source of truth and explicitly left
the *"GFF3->FF offset map path"* out of scope — correctly, since that path is `GFF3Mapper`'s.
But the **writer's** offset path is not the GFF3→FF path, and was never revisited. That is how
the pipeline came to archive from a source the library had already stopped treating as
authoritative. It survives as the chain's last resort, where a caller with no `TranslationState`
(a read-modify-write of an existing GFF3) still needs it.

## 3.7 Defect 4 — the factory's documented contract (FIXED)

Different in kind — the others are behaviour, this is documentation — but it is the one that
propagated the bug into the pipeline. `appendTranslationFasta` was described as *"whether to
append annotation FASTA output"* while the code selected *where*, so `false` read as "no
translations" and anyone wanting them reached for `true`. And
`existingTranslationFilePathFallback` claimed a trigger that could never occur.

Every public member of `GFF3File` and `GFF3FileFactory` now carries javadoc, including two
undocumented behaviours of `from()` worth knowing: it always stamps `##gff-version 3.1.26`
regardless of the source, and it takes `##species` from the **first** entry only, silently
discarding every later organism.

# 4. The `writeAnnotationFasta` flag — kept

Removal was proposed and rejected. The reasoning is worth recording, since the case for deletion
looked strong and was wrong in a specific way.

The case for removing: after §3.2 the chain writes nothing when no source yields anything, so
"no translations" is already expressible as "supply no source"; every in-repo caller passes
`true`; and a probe making the call unconditional broke only three tests, all of them the flag's
own.

Why that fails: **`fromAnnotationAndReader` unconditionally pulls `TranslationState` out of the
reader's validation context.** A caller on that path cannot un-supply the source, so the flag is
the *only* way to ask that factory for a features-only document. "Supply no source" works for
builder users, not factory users. And "every caller passes `true`" describes this repository, not
what a public library should offer — gff3tools is consumed externally, and removing a public
parameter breaks consumers we cannot see.

The footgun that motivated removal is separately gone: the danger was that the flag meant *where*
while the javadoc said *whether*. It now means *whether*, and the javadoc agrees.

**Default left at `false`.** Noted as a sharp edge rather than a defect: a builder user who
supplies a `TranslationState` and forgets the flag silently gets nothing, which is exactly what
broke six tests during this work — including two production classes. `@Builder.Default` set to
`true` would make it opt-out and match every existing caller's expectation while preserving
suppression. Deferred deliberately.

# Alternatives Considered

## A custom container format — concatenated documents with separators

One blob per submission holding each annotation as a standalone document, separated by a repeated
`##gff-version` header. Close to what the per-annotation mode's *tested* usage produced, so nearly
free to build.

Rejected: it relocates the problem. The archiving work splits a submission into separate archived
objects, so boundaries are expressed by object identity; a container would reintroduce inside the
blob exactly the structure being pulled out. It is also not valid GFF3, so consumers gain a
bespoke parser requirement and gff3tools' own reader would need matching work to round-trip.
Both shapes actually needed are ordinary valid GFF3.

## Keep `writeAnnotationFasta` as a multi-document mode

Make the flag write a header per annotation so its output is a genuine concatenation of standalone
documents. Rejected: it preserves a footgun the pipeline already fell into, to serve a use case
nobody has.

## Removing the flag

See §4.

# Technical Debt / Future Considerations

* **`fastaFilePath` cannot be scoped** — an opaque file, copied verbatim. Documented as
  incompatible with a subset document; if one ever needs it, that source needs an index.
* **No reverse-mapping validation.** `CDS_TRANSLATION_PRESENCE` checks the forward direction
  (every CDS has a translation) by exact key. Nothing checks that every translation traces back
  to exactly one annotation present in the file. `DUPLICATE_SEQ_ID` guarantees accessions are
  unique but says nothing about one being a prefix of another, so it offered no protection
  against §3.5.
* **`from()` discards organisms after the first** — now documented, still silent.
* **`./gradlew javadoc` fails on `main`**, and did before this work: javadoc cannot see the
  Lombok-generated `ValidationRegistryBuilder` (`ValidationRegistry.java:182`). The task cannot
  currently gate documentation quality.
* **Streaming.** One trailing `##FASTA` needs the annotation set up front. Offsets are read
  lazily so memory stays at one translation at a time, but a genuinely streaming writer would
  need two passes or a temp file. `2604071325` already lists streaming as future work.

# Testing Strategy

`src/test/java/uk/ac/ebi/embl/gff3tools/gff3/Gff3FileWritingTest.java` — 19 tests in four nests,
organised by the properties a written document must satisfy rather than by defect, so the class
kept its shape as the defects closed. All green.

| Nest | Covers |
|---|---|
| `appendTranslationFasta = false — appends no FASTA output` | the suppression contract §4 preserves |
| `appendTranslationFasta = true — appends the annotations' FASTA output` | one section, nothing after it, round-trip, scoping, fallback, empty-source |
| `converting an EMBL flat file` | `from()` end to end, incl. two characterisation tests |
| `selecting an annotation's translations` | §3.5, at reader and writer level |

Notes for anyone reading it:

* `writes one trailing FASTA section holding every entry's translations` (flat-file nest) is the
  reference case: `from()` already produced, for multiple annotations, the shape the other path
  was fixed to produce. The golden fixture `phase_feature.gff3` is that shape too.
* Content assertions in the `true` nest run through a `trailingFastaSectionOf()` helper that
  first asserts the document is well formed — one `##FASTA`, every feature line before it — and
  returns the section body. A translation only counts when appended where a reader will look.
* Two flat-file tests are **characterisation, not requirements** (the version stamp, species from
  the first entry) and are labelled as such so they are not later read as intended contract.
* `GFF3FileTest`'s translation fixtures were built with `.annotations(List.of())` — no
  annotations at all — and passed only because nothing consulted them. They now carry an
  annotation matching their translation key, so they describe a document that could exist.

Run: `./gradlew test` (add `-Pgitlab_private_token=<token>`; `--offline` works against a populated
cache). **1332 tests, 0 failures.**

# Deployment & Operations

Library change, released as a new gff3tools version and picked up by webin-gff3-stages via a
dependency bump. **No signature changed**, so no call site in the pipeline needs editing —
`ValidationResultArchiver` keeps passing `true` and now gets translations from
`TranslationState`.

**Data already written is affected, in two ways.** Any `validated.gff3` produced for a
multi-annotation submission is malformed and re-reads as a single annotation; and per §3.4 it
carries no translations at all. Whether existing archived objects are regenerated is a
webin-gff3-stages decision, but the symptom is *missing* translations rather than wrong ones —
no archived object holds a submitter's protein — so regeneration is about restoring, not
correcting.

# Open Questions

**Q1 — Should an ignored submitted `##FASTA` be reported?** A submitted translation section is
today silently either echoed or dropped, with zero warnings (§3.4.1). If the policy is that such
a section is ignored, saying so once — a warning, or a rule — costs little and turns a silent
policy into a visible one. New rule, own severity decision.

**Q2 — Is a reverse-mapping validation wanted?** Every translation resolves to exactly one
annotation in the file. It would catch mis-attribution as data rather than preventing it in one
code path, and would cover the unversioned-accession case §3.5 turns into a silent no-match. New
rule, out of scope here.

**Q3 — Should `writeAnnotationFasta` default to `true`?** See §4. Deferred, not declined.

**Q4 — Does the pipeline regenerate affected `validated.gff3` files?** Owned by
webin-gff3-stages; the answer decides whether the archiving work starts from a clean slate.

# Related Documentation & Resources

* `docs/2604071325_move_translation_fasta_writing.md` — established `TranslationState` as the
  translation source of truth and moved the FASTA section to the end. This document restores that
  intent for the multi-annotation and subset cases.
* `docs/2603171142_allow_loading_sequence_translations.md`
* `webin-gff3-stages/docs/design/per-annotation-gff3-archiving.md` — the consumer requirement.
* Key code: `gff3/GFF3File.java`, `gff3/TranslationKey.java`, `fftogff3/GFF3FileFactory.java`,
  `gff3/reader/GFF3FileReader.java`, `validation/provider/TranslationState.java`,
  `validation/fix/TranslationFix.java`, `validation/builtin/CdsTranslationPresenceValidation.java`,
  `gff3toff/GFF3Mapper.java`
