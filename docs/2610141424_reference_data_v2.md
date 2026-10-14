- Feature Name: `reference_data_v2`
- Document Date: 2026-10-14
- Last Updated: 2026-10-14

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
reading rules, and the four-sided rollout order (this repo, webin-master-service,
webin-gff3-stages, ena-sequence-persistence).

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
    "referencePosition": "1-1470",          // RP  (optional)
    "referenceComment":  null,              //     (optional)
    "title":             "Complete nucleotide sequence ...",   // RT
    "consortium":        null,              // RG

    // ---- RA, structured (canonical) ----
    "authors": [
      { "surname": "Sugita", "firstName": "C.", "middleName": null, "consortium": null }
    ],

    // ---- RX (was @JsonIgnore; now carried) ----
    "xrefs": [
      { "source": "DOI",    "primaryId": "10.1007/s11120-006-9122-4", "secondaryId": null },
      { "source": "PUBMED", "primaryId": "17211581",                  "secondaryId": null }
    ],

    // ---- type-specific RL tail: exactly one block populated, selected by "type" ----
    "submission":  { "submittedDate": "2012-05-25", "submitterAddress": "Molecular Genetics ..., GERMANY." },
    "journal":     { "journal": "Photosyn. Res.", "volume": "93", "issue": "1-3",
                     "firstPage": "55", "lastPage": "67", "year": 2007 },
    "book":        { "bookTitle": "PS2001 Proceedings ...", "publisher": "CSIRO Publishing ...",
                     "firstPage": "40", "lastPage": "9", "year": 2001, "editors": [] },
    "thesis":      { "institute": "IPK Gatersleben", "year": 2008 },
    "patent":      { "patentOffice": "US", "patentNumber": "1234567", "patentType": "A1",
                     "sequenceNumber": 1, "day": "2001-01-01", "applicants": [] },
    "electronic":  { "text": "https://..." },
    "unpublished": { "journalBlock": "..." },

    // ---- legacy v1 fields, still read, never written by v2 producers ----
    "submitterDetails": null,               // superseded by type=SUBMISSION + "submission"
    "authors-free-text": null               // superseded by structured authors[]
  }
]
```

### `type` values

Exactly the sequencetools publication classes, plus the legacy ERA CV codes they
correspond to:

| `type`        | ERA `PUBTYPE` | sequencetools class   | RL tail emitted by |
|---------------|---------------|-----------------------|--------------------|
| `SUBMISSION`  | 0             | `Submission`          | `EmblSubmissionWriter` (`Submitted (DD-MON-YYYY) to the INSDC. <address>`) |
| `UNPUBLISHED` | 1             | `Unpublished`         | `EmblUnpublishedWriter` |
| `ARTICLE`     | 2             | `Article`             | `EmblArticleWriter` (journal, volume(issue), firstPage-lastPage(year)) |
| `ACCEPTED`    | 7             | `Article` (incomplete)| `EmblArticleWriter` (in press form) |
| `BOOK`        | 3             | `Book`                | `BookWriter` ((in) … (Eds.); bookTitle:pages; publisher (year)) |
| `THESIS`      | 4             | `Thesis`              | `EmblThesisWriter` (Thesis (year), institute) |
| `PATENT`      | 5             | `Patent`              | `EmblPatentWriter` (Patent number "type": date, office) |
| `ELECTRONIC`  | —             | `ElectronicReference` | `ElectronicReferenceWriter` |

Note: `ACCEPTED` exists as an ERA `PUBTYPE`, but it is derivable — the legacy
`EntryPersistenceUtils.getPublicationTypeId` reclassifies an incomplete `ARTICLE`
(missing volume, pages, year, or title) as `ACCEPTED` on write. v2 keeps it as an
explicit value for round-tripping ERA data, but consumers may treat an `ARTICLE`
missing those fields equivalently.

### Field validity matrix

| field | SUBMISSION | UNPUBLISHED | ARTICLE | ACCEPTED | BOOK | THESIS | PATENT | ELECTRONIC |
|---|---|---|---|---|---|---|---|---|
| referenceNumber | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| referencePosition, referenceComment, title, consortium | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| authors, xrefs | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| submission | ✔ | | | | | | | |
| journal | | | ✔ | ✔ | | | | |
| book | | | | | ✔ | | | |
| thesis | | | | | | ✔ | | |
| patent | | | | | | | ✔ | |
| electronic | | | | | | | | ✔ |
| unpublished | | ✔ | | | | | | |

### Dates

All dates are ISO-8601 calendar dates (`YYYY-MM-DD`) or date-times
(`YYYY-MM-DDThh:mm:ss[±hh:mm]`), matching `submitterDetails.submittedDate` today.
EMBL `RL`/`Submitted` rendering (e.g. `25-MAY-2012`) is a writer concern, not schema.

### `xrefs`

One shape, everywhere: `{source, primaryId, secondaryId}` — the master-service /
`webin-gff3-stages` shape, which is field-for-field the sequencetools `XRef`
(`database`, `primaryAccession`, `secondaryAccession`). Allowed `source` values are the
ERA CV: `DOI`, `PUBMED`, `AGRICOLA`; unknown sources are carried but logged by
consumers (EMBL `RX` lines must use known CVs).

`secondaryId` is currently only meaningful for future RX partners; EMBL `RX` writes
`<db>; <primaryId>.` today.

## Backward compatibility

1. **`type` absent → v1 semantics.** `SUBMISSION` when `submitterDetails` (or the legacy
   free-text `authors`/`address`) is present, else `UNPUBLISHED`. Identical to the
   current `GFF3Mapper`/`Gff3MasterJsonReader` behaviour; no consumer change needed to
   keep working.
2. **Unknown `type` → log WARN + fall back to `UNPUBLISHED`.** Never throw; never drop
   the reference silently.
3. **v1 input remains valid input.** Nested `submitterDetails` and free-text
   `authors`/`address` keep their current meaning; producers stop emitting them once v2
   is live. The gff3tools mapper prefers structured `authors[]` and falls back to the
   legacy forms.
4. **`xrefs` is additive.** Absent `xrefs` = current behaviour.

## Consumer mapping (gff3tools)

`GFF3Mapper.mapReference` dispatches on `type`:

| `type` | construction |
|---|---|
| `SUBMISSION` | `createSubmission()`; `day` = `submission.submittedDate`; address = `submission.submitterAddress` (or legacy `submitterDetails` path) |
| `ARTICLE`/`ACCEPTED` | `createArticle()`; volume/issue/firstPage/lastPage/journal/year from `journal` |
| `BOOK` | `createBook()`; bookTitle/publisher/pages/year (+ `editors` as `Person`s) |
| `THESIS` | `createThesis()`; institute/year |
| `PATENT` | `createPatent(...)`; office/number/type/sequenceNumber/day/applicants |
| `ELECTRONIC` | `createElectronicReference()`; `text` |
| `UNPUBLISHED` | `createUnpublished()`; optional `journalBlock` |
| (missing/unknown) | v1 fallback path |

Shared: `title` → `setTitle`, `consortium` → `setConsortium`, `authors[]` →
`createPerson(surname, initials-from-firstName/middleName)` (existing
`toInitials` logic), `xrefs` → `publication.addXRef(new XRef(source, primaryId))` —
which the existing `RXWriter` then emits. `referencePosition` continues to flow to
`reference.setLocations` (existing `RP` handling).

## Rollout order (dependency-ordered)

| # | Repo | Change | Depends on |
|---|---|---|---|
| 0 | (this doc) | agree schema | — |
| 1 | `gff3tools` | widen `ReferenceData`/`CrossReference`, dispatch in `mapReference`, fixtures + tests | 0 |
| 1b | `ena-sequence-persistence` | same in `Gff3MasterJsonReader.mapReference` (duplicated mapper) | 0 |
| 2 | `webin-master-service` | widen model, drop `@JsonIgnore` on xrefs | 0 |
| 3 | `webin-gff3-stages` | ERA SQL for PUBLICATION/JOURNALARTICLE/BOOK/THESIS/PATENT/ACCEPTED; build one ReferenceData per publication with `type`; extend `ReferenceConverter` | 0, 1, 2 |
| 4 | e2e | prod-smoke on accessions with known journal/book/thesis refs; diff vs `ena/browser/api/embl` | 1–3 |

Steps 1/1b/2 are independently shippable (additive, dormant until data arrives).
Step 3 is the activation switch and must not land before 1 and 2 are deployed.

# Open questions

1. **D1 xref shape** — adopt `{source, primaryId, secondaryId}` repo-wide (proposed) and
   refactor gff3tools' `{id, source, url}`?
2. **D2 free-text authors** — keep reading as legacy alias only, or retain as a permanent
   alternative input?
3. **D4 `ACCEPTED`** — explicit type (proposed, for ERA round-trip) or derived-only?
4. **D5 shared model** — single library (e.g. in gff3tools, consumed by the services) vs
   four synced copies. If not now, this change makes the drift worse; budget it.
5. **Who owns the schema** — one repo must be normative. Proposal: gff3tools (it has the
   mapper, the fixtures, and both consumer paths run through it).
6. **`editors` as structured people** — BOOK needs editors (`(in) X. (Eds.)`); confirm
   producers can supply them from ERA (legacy `Book` model has them).

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
