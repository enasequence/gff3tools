- Feature Name: `annotation_scoped_fasta_writing`
- Document Date: 2026-09-07
- Last Updated: 2026-09-07

# Summary

`GFF3File.writeGFF3String()` currently has two mutually exclusive FASTA-writing modes selected
by a boolean, and **both are broken for the multi-annotation case**: one emits structurally
invalid GFF3, the other silently emits no translations at all. This document records the
defects with reproduction evidence, and proposes replacing the boolean with a single write path
that always writes one trailing `##FASTA` section scoped to the annotations the file actually
contains.

The trigger is the webin-gff3-stages archiving work, which needs to archive a *subset* of a
submission's annotations as a standalone GFF3 object. That requirement is not exotic — it is
simply "a GFF3 file whose FASTA section matches its feature section", which is what the writer
should have been doing all along.

# Motivation & Rationale

## Requirement from the pipeline

webin-gff3-stages is moving from one archived GFF3 object per analysis to one object per
*annotation group* (see `webin-gff3-stages/docs/design/per-annotation-gff3-archiving.md`):

| Archived object | Content |
|---|---|
| split part (a chromosome, or a TSV annotation) | 1 annotation + its translations |
| bundle (non-chromosome remainder) | N annotations + their translations |

Both are the same shape — *a GFF3 file containing a subset of the submission's annotations,
plus exactly that subset's translations, in one trailing `##FASTA` section*. Only the size of
the subset differs. Nothing about that needs a new mode or a new format; it needs the writer to
scope the FASTA section to its own annotations.

## Defect 1 — multi-annotation output with `writeAnnotationFasta = true` is invalid GFF3

`GFF3File.writeGFF3String()` writes the header **outside** the annotation loop but, when
`writeAnnotationFasta` is set, writes a `##FASTA` section **inside** it. With more than one
annotation in a single `GFF3File`, the result is one header followed by feature blocks
interleaved with `##FASTA` sections.

Per the GFF3 specification `##FASTA` terminates the feature section — everything after it is
sequence data to EOF. Features appearing after a `##FASTA` are therefore not features.

This is exactly how webin-gff3-stages calls it, in `ValidationResultArchiver`:

```java
GFF3File gff3File = GFF3FileFactory.fromAnnotationAndReader(
        fixedAnnotations, gff3FileReader, true, Optional.empty());
validatedFileStore.writeValidatedGff3FileWithTranslation(gff3File);
```

**Observed** (2 annotations in, via a scratch probe — reproduction below):

```
##gff-version 3.1.26
##species http://example.org?name=Homo sapiens
##sequence-region BN000065.1 1 315242
BN000065.1	.	CDS	1	315242	.	+	.	ID=CDS_A;gene=RHD;

##FASTA
>BN000065.1|CDS_A
MSSKYPRSVRRCLPLWALTLE

##sequence-region BN000066.1 1 315242      <-- features after ##FASTA
BN000066.1	.	CDS	1	315242	.	+	.	ID=CDS_B;gene=RHD;

##FASTA                                    <-- second ##FASTA
>BN000066.1|CDS_B
AALILLFYFFTHYDASLE
```

`##FASTA` count = 2, `##gff-version` count = 1.

**gff3tools cannot read its own output.** `GFF3FileReader.readAnnotation()` breaks on the first
`##FASTA` (`GFF3FileReader.java:81`); the following call matches `TRANSLATION_ID_PATTERN` and
returns null. Reading the file above back yields **1 annotation of 2** — silent data loss, no
error raised.

Note the mode itself is not wrong — it was designed for *one `GFF3File` per annotation*, and
`GFF3FileReaderTest.testReadTranslationAndWriteTranslationOnEachAnnotation` exercises it that
way, constructing a fresh `GFF3File` per annotation and passing the header each time so each
block is a standalone document. The defect is that nothing prevents, or warns about, the
multi-annotation misuse — and the pipeline fell into it.

## Defect 2 — an empty-but-present `TranslationState` suppresses all FASTA output

`writeTranslationSection()` selects a source by null-check, not by content:

```java
if (translationState != null)            writeFastaFromTranslationState(writer);
else if (fastaFilePath != null)          writeFastaFromExistingFile(writer);
else if (gff3Reader != null && ...)      writeFastaFromOffsets(writer, ...);
```

`writeFastaFromTranslationState()` then returns early when the state holds nothing resolved.
Because `TranslationState` is placed in the validation context unconditionally, it is
**present but empty** on any path where no re-translation occurred — and the two fallbacks are
never reached.

**Observed**, same input, `writeAnnotationFasta = false`:

```
### TranslationState present in context = true
### TranslationState resolved entries   = 0
### reader translation offset map       = [BN000065.1|CDS_A, BN000066.1|CDS_B]

=> ##FASTA count in output = 0        (translations dropped)
=> annotations on read-back = 2       (structure correct)
```

The translations were available in the reader's offset map and were silently discarded.

## The two defects are causally linked

This is the important part. Neither mode is currently correct for a multi-annotation file:

| mode | structure | translations |
|---|---|---|
| `writeAnnotationFasta = true` | **broken** (multiple `##FASTA`) | correct (from offsets) |
| `writeAnnotationFasta = false` | correct (one trailing section) | **dropped** when `TranslationState` is empty |

Defect 2 makes `false` look like it loses data, which is a plausible reason for the pipeline to
have been switched to `true` — trading a silent data loss for a structural one. Fixing Defect 2
removes the only reason to use the interleaving mode.

## Defect 3 — accession prefix matching (confirmed)

`GFF3FileReader.getTranslationOffsetForAnnotation()` selects an annotation's translations with:

```java
.filter(e -> e.getKey().startsWith(annotation.getAccession()))
```

Keys are `accession|featureId` (`TranslationKey.of()`), so an accession that is a string prefix
of another also claims the other's translations.

**Confirmed** by `GFF3FileFastaSectionTest.doesNotMatchAccessionsByPrefixWhenSelectingOffsets`,
which calls the method directly and fails today:

```
AB123.10's translation was claimed by AB123.1
  expected: <[AB123.1|CDS_SHORT]>
  but was:  <[AB123.1|CDS_SHORT, AB123.10|CDS_LONG]>
```

The realistic trigger is not two versions of one accession but **mixed versioning of seqIds** in
a single GFF3 — an annotation whose accession is recorded unversioned (`AB123456`) claims every
translation key of `AB123456.1`. The fix is free: match `accession + "|"`, or split the key on
the first `|` and compare exactly. It becomes load-bearing once the FASTA section is scoped per
file (§ Detailed Design), which is why it is in scope here rather than deferred.

# Usage Guidelines

After this change there is one way to write a GFF3 file, and callers choose content, not
format:

```java
// A file containing every annotation
GFF3File all = GFF3FileFactory.fromAnnotationAndReader(annotations, reader, Optional.empty());

// A file containing one annotation — same call, smaller list
GFF3File one = GFF3FileFactory.fromAnnotationAndReader(List.of(annotation), reader, Optional.empty());
```

Both produce a valid GFF3 document: header, species, features, then a single `##FASTA` holding
the translations of the annotations in that file and no others. To emit several standalone
documents, write several files — do not concatenate them into one.

# System Overview / High-Level Design

Three changes to the write path, no new concepts:

```
GFF3File.writeGFF3String()
  ├─ header, species                                  (unchanged)
  ├─ for each annotation: features                    (unchanged)
  └─ ONE trailing ##FASTA section                     (was: conditional / per-annotation)
         └─ TranslationSource.resolve(annotations)    (NEW: content-based selection, scoped)
```

1. **Always one trailing `##FASTA`.** Remove the `writeAnnotationFasta` branch from
   `writeGFF3String()`; the FASTA section is written once, after all annotations.
2. **Select the translation source by content, not nullness.** First source that actually
   yields entries wins: `TranslationState` → fallback FASTA file → reader offset map.
3. **Scope the section to the file's annotations.** Whichever source is used, emit only
   translations whose key belongs to an annotation present in this `GFF3File`.

Point 3 is the new capability, and it is what makes a subset file correct. Points 1 and 2 are
defect fixes.

# Detailed Design & Implementation

## `GFF3File.writeGFF3String()`

```java
@Override
public void writeGFF3String(Writer writer) throws WriteException {
    try {
        if (header != null) header.writeGFF3String(writer);
        if (species != null) species.writeGFF3String(writer);
        for (GFF3Annotation ann : annotations) {
            ann.writeGFF3String(writer);
        }
        writeTranslationSection(writer);      // once, scoped to `annotations`
    } catch (IOException e) {
        throw new WriteException(e);
    }
}
```

## Source selection

```java
private void writeTranslationSection(Writer writer) throws IOException {
    Set<String> accessions = annotations.stream()
            .map(GFF3Annotation::getAccession)
            .collect(Collectors.toSet());

    if (writeFastaFromTranslationState(writer, accessions)) return;   // true if it wrote
    if (writeFastaFromExistingFile(writer)) return;
    writeFastaFromOffsets(writer, scopedOffsets(accessions));
}
```

Each writer returns whether it emitted anything, so an empty source falls through instead of
terminating the chain. The `##FASTA` header line moves to a single place that is only written
once at least one translation is known to follow — no empty `##FASTA` directives.

## Scoping predicate

Both `TranslationState` and the reader offset map key on `accession|featureId`
(`TranslationKey.of()`), so one predicate serves both:

```java
static boolean belongsTo(String translationKey, Set<String> accessions) {
    int bar = translationKey.indexOf('|');
    return bar > 0 && accessions.contains(translationKey.substring(0, bar));
}
```

This also retires Defect 3: exact accession comparison rather than `startsWith`.
`getTranslationOffsetForAnnotation()` should either adopt the same predicate or be removed if
scoping moves entirely into `GFF3File`.

## Fallback FASTA file

`fastaFilePath` is copied verbatim and cannot be scoped — it is an opaque file. It stays a
whole-file fallback, and callers must not combine it with a subset `GFF3File`. Worth an
explicit note in the javadoc, or a guard that rejects the combination.

## What is removed

* `GFF3File.writeAnnotationFasta` field and its builder method
* the `appendTranslationFasta` parameter of `GFF3FileFactory.fromAnnotationAndReader()`
* `GFF3FileReaderTest.testReadTranslationAndWriteTranslationOnEachAnnotation` and its
  `testReadWithHeaderAndFastaOnEachAnnotation` helper

Nothing in gff3tools' CLI uses the mode; the only production caller is webin-gff3-stages, which
passes `true` and wants the corrected behaviour. Removing the parameter is a source-breaking
change for that one call site, which is the point — it forces the fix rather than leaving the
broken combination reachable.

## Reproduction of the observed behaviour

The evidence above was first obtained from a scratch probe and is now committed as
`GFF3FileFastaSectionTest`. The probe:

1. writes the two-annotation input below to disk and reads it with `GFF3FileReader`;
2. builds one `GFF3File` from *all* annotations via `fromAnnotationAndReader(..., true/false, empty)`;
3. writes it to a `StringWriter`, counts `##FASTA` / `##gff-version` occurrences;
4. writes the result back to disk and re-reads it, counting recovered annotations.

```
##gff-version 3
##species http://example.org?name=Homo sapiens
##sequence-region BN000065.1 1 315242
BN000065.1	.	CDS	1	315242	.	+	.	ID=CDS_A;gene=RHD;

##sequence-region BN000066.1 1 315242
BN000066.1	.	CDS	1	315242	.	+	.	ID=CDS_B;gene=RHD;

##FASTA
>BN000065.1|CDS_A
MSSKYPRSVRRCLPLWALTLE

>BN000066.1|CDS_B
AALILLFYFFTHYDASLE
```

Run with `./gradlew test --offline` (dependency resolution needs a real
`gitlab_private_token`; an already-populated Gradle cache works offline).

# Alternatives Considered

## A custom container format — concatenated documents with separators

Emit one blob per submission containing each annotation as a standalone document, separated by
a repeated `##gff-version` header. This is close to what the `true` mode's *tested* usage
produces, so it is nearly free to implement.

Rejected:

* it does not solve the pipeline's problem, it relocates it. The archiving work splits a
  submission into separate archived objects; boundaries are then expressed by object identity.
  A container would reintroduce, inside the blob, precisely the structure being pulled out.
* `##gff-version` mid-file is not valid GFF3 either, so consumers gain a bespoke parser
  requirement; gff3tools' own reader would still stop at the first `##FASTA` and would need
  matching reader work to round-trip.
* the two shapes actually needed — one annotation, or N annotations — are both expressible as
  ordinary valid GFF3. Choosing a bespoke format over a conformant one needs a reason stronger
  than convenience, and there is none here.

Worth revisiting only if a consumer ever needs to extract a single annotation from a bundle
without parsing it — which no current consumer does.

## Keep `writeAnnotationFasta`, document it as multi-document mode

Make the mode write a header per annotation, so its output is genuinely a concatenation of
standalone documents, and leave callers to choose. Rejected: it keeps a footgun (the pipeline
already fell into it) to serve a use case nobody has, and the per-file scoping of §Detailed
Design covers the same ground safely — write N files instead of one file with N documents.

## Fix only the source-selection chain, leave the boolean

Minimal fix for Defect 2 alone. Rejected: it leaves Defect 1 reachable and leaves the pipeline
on the interleaving path, which the archiving work cannot consume.

# Technical Debt / Future Considerations

* **Streaming.** Writing one trailing `##FASTA` requires the annotation set up front. The
  offset-based source reads each translation lazily by offset, so memory stays at one
  translation at a time, but a truly streaming writer would need a two-pass or
  temp-file strategy. Out of scope; the design doc `2604071325` already flags streaming as
  future work.
* **`fastaFilePath` cannot be scoped**, as noted above. If a subset file ever needs an opaque
  fallback FASTA, that source needs an index.
* **`getTranslationOffsetForAnnotation()`** becomes redundant if scoping moves into `GFF3File`.
  Removing it would drop the last `startsWith` accession match in the codebase.
* **No validation of writer output.** Nothing currently asserts that written GFF3 is
  re-readable. A round-trip property test (write N annotations → read back N) would have
  caught Defect 1 at the commit that introduced it; see Testing Strategy.

# Testing Strategy

Implemented in `src/test/java/uk/ac/ebi/embl/gff3tools/gff3/GFF3FileFastaSectionTest.java`,
written first and currently **7 of 8 failing** — one per defect plus the new capability. The
class is organised by the three properties a written document must satisfy (valid GFF3;
round-trips; FASTA matches its own annotations) rather than by defect, so it keeps its shape
after the fix lands.

Current status against the unmodified code:

| Test | Status | Observed |
|---|---|---|
| `writesAtMostOneFastaSectionForMultipleAnnotations` | FAIL | expected 1 `##FASTA`, was 2 |
| `writesNoFeatureLinesAfterTheFastaSection` | FAIL | feature lines found after `##FASTA` |
| `roundTripsEveryAnnotationItWrote` | FAIL | expected 2 annotations, was 1 |
| `fallsBackToReaderOffsetsWhenTranslationStateIsEmpty` | FAIL | expected 1 `##FASTA`, was 0 |
| `scopesFastaSectionToTheAnnotationsInTheFile` | FAIL | blocked behind Defect 2 — no FASTA to scope |
| `doesNotMatchAccessionsByPrefixWhenSelectingOffsets` | FAIL | `AB123.1` claimed `AB123.10`'s translation |
| `doesNotMatchAccessionsByPrefixWhenScoping` | FAIL | blocked behind Defect 2 |
| `writesNoFastaDirectiveWhenThereAreNoTranslations` | **PASS** | guard against over-correcting |

The two "blocked behind Defect 2" rows fail on their first assertion rather than on the
behaviour they name; they become meaningful tests of scoping once the fallback chain is fixed.
`doesNotMatchAccessionsByPrefixWhenSelectingOffsets` exists precisely so Defect 3 is proven
independently of the writer.

The rest of the suite is unaffected: 1321 tests, 7 failures, all of them the new ones.

What each covers:

1. **Round-trip invariant (Defect 1).** For N in {1, 2, 5}: build a `GFF3File` from N
   annotations, write, re-read with `GFF3FileReader`, assert N annotations recovered and
   exactly one `##FASTA` in the output. This is the regression guard that was missing.
2. **Empty `TranslationState` falls through (Defect 2).** Context contains a `TranslationState`
   with zero resolved entries, reader offset map non-empty → assert translations are written
   from the offset map. Then the inverse: non-empty `TranslationState` → assert it wins over
   the offset map.
3. **No source at all.** Empty state, no fallback file, empty offset map → assert **no**
   `##FASTA` directive is emitted (rather than an empty one).
4. **Per-file scoping (new capability).** From a 3-annotation input, build a `GFF3File`
   containing only annotation 2; assert its `##FASTA` holds annotation 2's translations and
   neither of the others'. This is the test the archiving work depends on.
5. **Accession prefix (Defect 3).** Two accessions where one is a string prefix of the other
   (`AB123.1`, `AB123.10`), each with translations → assert no cross-contamination.
6. **Existing tests.** `testReadTranslationAndWriteTranslationInEnd` should continue to pass
   unchanged — it already asserts the target shape. The two per-annotation-FASTA tests are
   removed with the mode; `GFF3FileFastaSectionTest`'s first three tests, which currently pass
   `true`, drop that argument at the same time and keep asserting the same properties.

Run: `./gradlew test` (add `-Pgitlab_private_token=<token>`; `--offline` works against a
populated cache).

# Deployment & Operations

Library change; released as a new gff3tools version and picked up by webin-gff3-stages via its
dependency bump. The removal of the `appendTranslationFasta` parameter is source-breaking for
that one call site, which must be updated in the same coordination.

**Data already written is affected.** Any `validated.gff3` produced by the current pipeline for
a multi-annotation submission is malformed and re-reads as a single annotation. Whether existing
archived objects need regenerating is a webin-gff3-stages decision, but it should be made
knowingly — see the archiving design doc's rollout section.

# Related Documentation & Resources

* `docs/2604071325_move_translation_fasta_writing.md` — established `TranslationState` as the
  translation source of truth and moved the FASTA section to the end. This document restores
  that intent for the multi-annotation and subset cases.
* `docs/2603171142_allow_loading_sequence_translations.md`
* `webin-gff3-stages/docs/design/per-annotation-gff3-archiving.md` — the consumer requirement;
  its §3.4 splitter assumes a single trailing `##FASTA` and is blocked on this change.
* Key code: `gff3/GFF3File.java`, `fftogff3/GFF3FileFactory.java`,
  `gff3/reader/GFF3FileReader.java:320`, `validation/provider/TranslationState.java`,
  `gff3/writer/TranslationWriter.java`
