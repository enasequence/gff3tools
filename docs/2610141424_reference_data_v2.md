- Feature Name: `reference_data_v2`
- Document Date: 2026-10-14
- Last Updated: 2026-10-19

# Summary

The master-entry JSON used by GFF3-to-EMBL conversion cannot express journal articles,
books, theses, patents, or electronic references — only submitter-style references. As a
result `GFF3Mapper.mapReference` degrades every non-submission reference to
`Submission`/`Unpublished`, silently losing journal citations, book/thesis framing, and
`RX DOI`/`RX PUBMED` cross-references that appear in real ENA records (e.g.
[AP008231.1](https://www.ebi.ac.uk/ena/browser/api/embl/AP008231.1),
[HE820743](https://www.ebi.ac.uk/ena/browser/api/embl/HE820743)).

This document specifies **ReferenceData v2**: a flat, discriminated-union JSON schema for
the `references` array of master-entry JSON, projected from the EMBL reference block
(RN/RP/RX/RG/RA/RT/RL). It defines the field set, per-type validity, backward-compatible
reading rules, and the rollout order (this repo, webin-master-service, webin-gff3-stages).

# Motivation & Rationale

## Problems with the current design

1. **The JSON cannot carry the data.** `ReferenceData` models only
   `referenceNumber/Position/Comment`, `title`, `consortium`/`group`, and nested
   `submitterDetails`. There is no journal, volume, issue, pages, institute, publisher,
   patent office/number, or `RX` cross-reference field. The producer
   (`webin-gff3-stages` → `EraMasterDataResolver`) correspondingly emits at most one
   submission reference. Real master JSON captures show `"references":[]`.

2. **`RX` xrefs are structurally dropped.** The master-service and gff3-stages models
   carry `List<CrossReference> crossReferences` but annotate it
   `@JsonIgnore`, so DOI/PUBMED/AGRICOLA can never reach the JSON. gff3tools'
   `CrossReference` (`{id, source, url}`) is a different shape again and unused by the
   mapper.

3. **Four drifted copies of the same model.** `webin-master-service`,
   `webin-gff3-service`, `webin-gff3-stages`, and `gff3tools` each hold a hand-synced
   `ReferenceData` (and `CrossReference`) — three distinct `CrossReference` shapes
   (`{source,primaryId,secondaryId}` / `{source,pId}` / `{id,source,url}`), two
   disagreeing `authors` conventions (free-text string vs nested structured list).

4. **The capability exists one layer down, unused.** sequencetools' `ReferenceFactory`
   already creates `Article`/`Book`/`Thesis`/`Patent`/`ElectronicReference`, and the
   legacy flatfile reader (`ena-sequence-persistence`,
   `PublicationRowAggregateRetriever`) already reads all of them from ERA with a
   `PUBTYPE` CV (`0`=submission, `1`=unpublished, `2`=article, `3`=book, `4`=thesis,
   `5`=patent, `7`=accepted) and `RX` DB CVs (`DOI`, `PUBMED`, `AGRICOLA`). Only the JSON
   hop loses the information.

## Why a flat discriminated union

- The EMBL reference block is itself flat: shared lines (RN/RP/RX/RG/RA/RT) + one
  type-specific RL tail. The JSON mirrors that, so mapping in both directions is
  field-for-field.
- It is **backward compatible by construction**: everything v1 carries keeps its name and
  meaning; `type` is additive; consumers that never see `type` behave exactly as today.
- A nested per-type schema (one object per type, no discriminator) would force every
  consumer to infer the type from which object is present — ambiguous for types that
  share fields, and hostile to schema evolution.

# Design

## Schema (informative JSON; formal JSON Schema to follow once agreed)

```jsonc
"references": [
  {
    // ---- shared fields (all types) ----
    "referenceNumber":   2,                 // RN
    "type":              "ARTICLE",         // NEW: discriminator; REQUIRED in v2 output
    "referencePosition": "1-1470",          // RP  (optional; rendered for v2 records only)
    "referenceComment":  null,              //     (optional)
    "title":             "Complete nucleotide sequence ...",   // RT
    "consortium":        null,              // RG

    // ---- RA, structured (canonical) ----
    "authors": [
      { "surname": "Sugita", "firstName": "C.", "middleName": null, "consortium": null }
    ],                                      // authors[].consortium is ignored by gff3tools:
                                            // lift it to the reference-level "consortium"

    // ---- RX (was @JsonIgnore; now carried) ----
    "xrefs": [
      { "source": "DOI",    "primaryId": "10.1007/s11120-006-9122-4", "secondaryId": null },
      { "source": "PUBMED", "primaryId": "17211581",                  "secondaryId": null }
    ],

    // ---- type-specific RL tail: the block whose key is lower(type) ----
    "submission":  { "submittedDate": "2012-05-25", "submitterAddress": "Molecular Genetics ..., GERMANY." },
    "article":     { "journal": "Photosyn. Res.", "volume": "93", "issue": "1-3",
                     "firstPage": "55", "lastPage": "67", "year": 2007,
                     "accepted": false },   // true = in press (ERA PUBTYPE 7)
    "book":        { "bookTitle": "PS2001 Proceedings ...", "publisher": "CSIRO Publishing ...",
                     "firstPage": "40", "lastPage": "9", "year": 2001, "editors": [] },
    "thesis":      { "institute": "IPK Gatersleben", "year": 2008 },
    "patent":      { "patentOffice": "US", "patentNumber": "1234567", "patentType": "A1",
                     "sequenceNumber": 1, "day": "2001-01-01", "applicants": [] },
    "electronic":  { "text": "https://..." },
    "unpublished": { "journalBlock": "..." },

    // ---- legacy v1 fields: still read; producers MAY keep emitting them alongside the
    //      v2 fields during the rollout ----
    "submitterDetails": null,               // superseded by type=SUBMISSION + "submission";
                                            // fills gaps in the submission block (see below)
    "authors-free-text": null               // informational only, never rendered; superseded
                                            // by structured authors[]
  }
]
```

### `type` values

The block key is always the lowercase `type` (`ARTICLE` -> `article`, `BOOK` -> `book`, ...). Exactly the sequencetools publication classes, plus the legacy ERA CV codes they
correspond to:

| `type`        | ERA `PUBTYPE` | sequencetools class   | RL tail emitted by |
|---------------|---------------|-----------------------|--------------------|
| `SUBMISSION`  | 0             | `Submission`          | `EmblSubmissionWriter` (`Submitted (DD-MON-YYYY) to the INSDC. <address>`) |
| `UNPUBLISHED` | 1             | `Unpublished`         | `EmblUnpublishedWriter` (bare `Unpublished.`) |
| `ARTICLE`     | 2 (7 when `article.accepted` is true) | `Article` | `EmblArticleWriter` (journal, volume(issue), firstPage-lastPage(year)) |
| `BOOK`        | 3             | `Book`                | `BookWriter` ((in) … (Eds.); bookTitle:pages; publisher (year)) |
| `THESIS`      | 4             | `Thesis`              | `EmblThesisWriter` (Thesis (year), institute) |
| `PATENT`      | 5             | `Patent`              | `EmblPatentWriter` (Patent number "type": date, office) |
| `ELECTRONIC`  | —             | `ElectronicReference` | `ElectronicReferenceWriter` |

### `article.accepted` (in-press articles)

ERA stores in-press articles as a distinct `PUBTYPE` 7 (own `ACCEPTED` table), while
sequencetools has no such class: `EntryPersistenceUtils.getPublicationTypeId`
reclassifies an `Article` missing volume/pages/year/title as accepted on write. v2
carries the stored fact as `article.accepted: boolean` (default false) instead of a
separate `type`:

- it is a flag on `ARTICLE`, so it cannot be combined with another type (it lives
  inside the `article` block);
- the EMBL writer does not render accepted articles differently (`EmblArticleWriter`
  only prints the fields that exist), so consumers may parse and ignore it; it exists
  so ERA round-trips are lossless;
- producers set it from `PUBTYPE = 7`, never by inferring from missing fields.

Measured on ENAPRO (2026-10-16): 79,629 `PUBLICATION` rows have `PUBTYPE = 7` (about
15% of journal-type references); no `PUBTYPE = 2` article lacks volume or pages; at most
55 type-7 rows have complete volume and pages. Stored type and the derived rule agree
on all but a handful of rows, which is why the flag is a faithful carry, not a guess.

### Field validity matrix

| field | SUBMISSION | UNPUBLISHED | ARTICLE | BOOK | THESIS | PATENT | ELECTRONIC |
|---|---|---|---|---|---|---|---|
| referenceNumber | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| referencePosition, referenceComment, title, consortium | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| authors, xrefs | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| submission | ✔ | | | | | | |
| article (incl. `accepted`) | | | ✔ | | | | |
| book | | | | ✔ | | | |
| thesis | | | | | ✔ | | |
| patent | | | | | | ✔ | |
| electronic | | | | | | | ✔ |
| unpublished | | ✔ | | | | | |

A record is valid when the block named `lower(type)` is present (an `UNPUBLISHED`
record may omit its block) and carries the fields its RL line needs (see "Required fields"
below). Any other type block present is ignored.

### Dates

All dates are ISO-8601 calendar dates (`YYYY-MM-DD`) or date-times
(`YYYY-MM-DDThh:mm:ss[±hh:mm]`), matching `submitterDetails.submittedDate` today.
EMBL `RL`/`Submitted` rendering (e.g. `25-MAY-2012`) is a writer concern, not schema.

### Typed values

Fields inside the type blocks are typed (`year` and `sequenceNumber` are integers). A value
of the wrong JSON type (e.g. `"year": "n/a"`) fails the master JSON parse instead of
degrading the one reference; producers are typed, so this is a known limitation. Only
`authors` is read leniently (see Backward compatibility).

### `xrefs`

One shape, everywhere: `{source, primaryId, secondaryId}` — the master-service /
`webin-gff3-stages` shape, which is field-for-field the sequencetools `XRef`
(`database`, `primaryAccession`, `secondaryAccession`). Allowed `source` values are the
ERA CV: `DOI`, `PUBMED`, `AGRICOLA`; unknown sources are carried but logged by
consumers (EMBL `RX` lines should use known CVs). gff3tools trims and upper-cases `source`,
skips xrefs with a blank `source` or `primaryId`, and renders unknown sources with a WARN.

`secondaryId` is currently only meaningful for future RX partners; EMBL `RX` writes
`<db>; <primaryId>.` today.

## Backward compatibility

1. **`type` absent → v1 semantics.** `SUBMISSION` when `submitterDetails` carries a valid
   `submittedDate`, else `UNPUBLISHED`; `referencePosition` is not rendered (RP on a v1
   record comes only from the WGS contig length); RA comes from `submitterDetails.authors`
   only, any top-level `authors` is ignored. Byte-identical to the pre-v2 gff3tools output
   (locked by `ReferenceV1GoldenTest`), with one intended exception: broker accounts
   (non-blank `brokerName`) now get the submitter address `broker, address, country`
   instead of `center, laboratory, address, country`, matching the legacy pipeline
   (sequencetools `ReferenceUtils.getAddressFromSubmissionAccount`). No consumer change
   needed to keep working.
2. **Unknown `type` → log WARN + fall back to `UNPUBLISHED`.** Never throw; never drop
   the reference silently. It is otherwise read as a typed record: RA from `authors[]`
   only (no `submitterDetails` fallback), RX and RP rendered.
3. **v1 input remains valid input.** Nested `submitterDetails` keeps its current meaning;
   producers may keep emitting it alongside the v2 fields during the rollout. A free-text
   `authors` string (and free-text `address`, `authors-free-text`) is tolerated and
   ignored, never rejected. Records without `type` take RA from `submitterDetails.authors`;
   typed records take it from structured `authors[]` only, never falling back (an empty or
   absent `authors[]` renders no RA persons). Non-object elements inside an `authors`
   array (e.g. plain name strings) are skipped with a WARN instead of failing the master
   JSON.
4. **`xrefs` is additive.** Absent `xrefs` = current behaviour.

## Consumer mapping (gff3tools)

`ReferenceMapper.map` (called by `GFF3Mapper.mapReference`) dispatches on `type`:

| `type` | construction |
|---|---|
| `SUBMISSION` | `createSubmission()`; merged field by field: `day` = `submission.submittedDate`, else `submitterDetails.submittedDate`; address = non-blank `submission.submitterAddress`, else the `submitterDetails.submissionAccount` address (broker rule below) |
| `ARTICLE` | `createArticle()`; journal/volume/issue/firstPage/lastPage/year from `article`; `accepted` is read but not rendered |
| `BOOK` | `createBook()`; bookTitle/publisher/pages/year (+ `editors` as `Person`s) |
| `THESIS` | `createThesis()`; institute/year |
| `PATENT` | `createPatent()`; office/number/type/sequenceNumber/day/applicants |
| `ELECTRONIC` | `createElectronicReference()`; `text` |
| `UNPUBLISHED` | `createUnpublished()`; optional `journalBlock` is carried but not rendered (`EmblUnpublishedWriter` prints a bare `Unpublished.`) |
| (missing) | v1 path (see Backward compatibility 1) |
| (unknown) | `createUnpublished()` + WARN; RA from `authors[]` only (no `submitterDetails` fallback); RX and RP rendered |

Shared: `title` → `setTitle`, `consortium` → `setConsortium`, `authors[]` (typed records) →
`createPerson(surname, initials-from-firstName/middleName)` (existing
`toInitials` logic), `xrefs` → `publication.addXRef(new XRef(source, primaryId, secondaryId))` —
which the existing `RXWriter` then emits. `authors[].consortium` is ignored: producers must
lift a consortium to the reference-level `consortium` (RG). `referencePosition` ("1-100" or
"1-100,200-300") → `reference.setLocations` is **new for v2 records only**; v1 records
never rendered it and still do not. On WGS contigs the contig range `1-<contigLength>`
overrides it for both v1 and v2. An unparseable value omits the RP line with a WARN.

### Required fields (fallback to `UNPUBLISHED`)

A typed record whose RL line would not parse is mapped as `Unpublished` with a WARN
instead of emitting a malformed line:

| `type` | required |
|---|---|
| `SUBMISSION` | a valid date in `submission` or `submitterDetails` |
| `ARTICLE` | `journal`, `year` (volume/issue/pages optional; an in-press article renders e.g. `Photosyn. Res.:(2007).`) |
| `BOOK` | at least one editor with a name, `bookTitle`, `publisher`, `year` (pages render as `0-0` when absent) |
| `THESIS` | `institute`, `year` |
| `PATENT` | `patentOffice` (2 letters, stored upper-case), `patentNumber` and `patentType` (trimmed; letters and digits only, as `EmblPatentMatcher` reads them), `sequenceNumber`, valid `day`; blank applicants are dropped |
| `ELECTRONIC` | non-blank `text` |

A `year` outside 1000..9999 counts as missing.

### Submitter reference (producer rule)

Producers (webin-gff3-stages) build the `SUBMISSION` reference the way the legacy
pipeline does (sequencetools `ReferenceUtils`):

- **Authors**: the manifest `AUTHORS` when present — for submission account
  `Webin-55551` the whole value becomes the consortium (RG) instead of RA; otherwise the
  submission account contacts, where a contact with a consortium contributes RG (and no
  RA person).
- **Address**: the manifest `ADDRESS` when present, else the submission account address:
  `broker, address, country` for broker accounts (non-blank `brokerName`), otherwise
  `center, laboratory, address, country`; blank parts are skipped. gff3tools applies the
  same account rule when it falls back to `submitterDetails.submissionAccount`.

## Rollout order (dependency-ordered)

| # | Repo | Change | Depends on |
|---|---|---|---|
| 0 | (this doc) | agree schema | — |
| 1 | `gff3tools` [#188](https://github.com/enasequence/gff3tools/pull/188) | widen `ReferenceData`, `ReferenceMapper` dispatch, fixtures + tests (v1 output unchanged except broker submitter addresses, see Backward compatibility 1) | 0 |
| 2 | `webin-master-service` #7 | widen model, carry xrefs; ENAPRO reference enrichment is on by default once the ENAPRO secret exists | 1 |
| 3 | (operations) | drain the stages pipeline (no in-flight jobs on the old producer) | 2 |
| 4 | `webin-gff3-stages` [#39](https://github.com/enasequence/webin-gff3-stages/pull/39) | emit `type: SUBMISSION` + `submission` block + structured `authors[]` (manifest AUTHORS parsed; initials split from surname), keeping v1 fields alongside | 1, 2, 3 |

The ENAPRO secret that switches master-service enrichment on is created only after every
consumer of master.json, including UniParc, runs a gff3tools build with #188; until then
master-service serves v1-shaped references only.

# Open questions

1. **D1 xref shape** — resolved: reference `xrefs` use `{source, primaryId, secondaryId}`
   (`ReferenceData.Xref` in gff3tools). `CrossReference {id, source, url}` is a different
   concept (entry-level DR lines) and is left unchanged.
2. **D2 free-text authors** — resolved: structured `authors[]` is canonical for typed
   records; records without `type` read the v1 `submitterDetails.authors` instead (there is
   no fallback on typed records); free-text author strings are tolerated and ignored (not
   rendered, not rejected).
3. **D4 `ACCEPTED`** — resolved: `ARTICLE` plus `article.accepted`, set from `PUBTYPE = 7` (see above).
4. **D5 shared model** — single library (e.g. in gff3tools, consumed by the services) vs
   four synced copies. If not now, this change makes the drift worse; budget it.
5. **Who owns the schema** — one repo must be normative. Proposal: gff3tools (it has the
   mapper, the fixtures, and both consumer paths run through it).
6. **`editors` as structured people** — BOOK needs editors (`(in) X. (Eds.)`); confirm
   producers can supply them from ERA (legacy `Book` model has them).
7. **Producer source and timing** — resolved by measurement (ENAPRO week samples, 2022-03 / 2024-03 / 2026-03): every Webin assembly carries only the PUBTYPE-0 submission reference at load and never gains a journal reference afterwards (0 article/book/thesis/patent references on 1,794 and 46,152-assembly samples, and on the chromosome entries of the 2022/2024 samples). No pipeline backfill is justified. Non-submission references, where they exist, are enriched in the **webin-master-service read path** (#7: read PUBLICATION/JOURNALARTICLE/ACCEPTED from ENAPRO `DATALIB` on GET, cached), switched on by the ENAPRO secret (see Rollout order) — not a pipeline change. For context: ~20% of 2024-03 assemblies have a `PUBMED` link in their ERA project XML, but all point to a shared study citation (one PMID across 18 projects), not a per-entry paper.

# References

- EMBL reference model & writers: `sequencetools` `api/entry/reference/*`,
  `flatfile/writer/embl/EmblPublicationWriter`, `RXWriter`, `RLWriter` dispatch.
- ERA CV values: `ena-sequence-persistence` `EntryPersistenceUtils`
  (`CV_VALUE_PUBLICATION_*`, `CV_VALUE_PUB_XREF_*`).
- Legacy ERA read path: `ena-sequence-persistence`
  `PublicationRowAggregateRetriever` (SQL + type dispatch, all types).
- Current JSON models: `gff3tools` `metadata/ReferenceData|CrossReference|AuthorData|SubmitterDetails`;
  `webin-master-service` `model/ReferenceData|CrossReference`;
  `webin-gff3-stages` `model/header/ReferenceData`; `webin-gff3-service` `model/ReferenceData`.
- Producer: `webin-gff3-stages` `EraMasterDataResolver`, `ReferenceConverter`,
  `MasterMetaDataToMasterEntryConverter`, `ERARepository`.
- Real-record examples: ENA `HE820743` (thesis), `AP008231.1` (article w/ DOI+PUBMED, book).
