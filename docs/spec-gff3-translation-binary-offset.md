# Spec: GFF3 translation reading via fastareader binary-search offset

- Feature: `gff3_translation_binary_offset`
- Date: 2026-07-08
- Status: Ready for implementation

## Problem Statement

`GFF3TranslationReader`
(`src/main/java/uk/ac/ebi/embl/gff3tools/gff3/reader/GFF3TranslationReader.java`)
locates the embedded `##FASTA` section of a GFF3 file and reads back embedded protein
translations using two hand-rolled mechanisms that are both slow and hard to maintain:

1. `readTranslationOffset()` scans from EOF **backwards** through a manual 1 MB
   `ByteBuffer`, reversing a `StringBuilder` per line to reconstruct text read backwards,
   in order to find the `##FASTA` marker and record a `Map<String, OffsetRange>` of raw
   byte start/end per translation.
2. `readTranslation(OffsetRange)` reads each sequence back with
   `raf.seek(pos); raf.readByte()` **one byte at a time** — O(N) individual syscalls for
   a length-N translation. This per-byte extraction is the single biggest real
   performance problem.

A shipped capability — `uk.ac.ebi.ena:fastareader:1.3.0` (already declared at
`build.gradle:77`, resolved via `mavenLocal()` first) — provides a `FastaReader` that can
open at an arbitrary decompressed-stream byte offset (designed exactly for
"annotations, then embedded `##FASTA`" files) and serve slices with buffered 4 MB
positional reads. It removes the need for both the backward scan and the per-byte
extraction, leaving only one gff3tools-specific concern the library does not know about:
finding a safe byte offset **at or before** the first FASTA-side line.

`FastaReader` has no concept of GFF3 syntax (`##FASTA`, tab-delimited feature lines, `#`
directives), so locating the FASTA boundary is entirely gff3tools's responsibility. The
GFF3 file structure — `annotation*` then optionally exactly one `##FASTA` then `fasta*`
to EOF, with `##FASTA` never recurring — makes "is byte P at-or-after the FASTA
boundary" a **monotonic predicate**, which is what makes a binary search over byte
offsets sound.

The goal: replace the backward-scan + per-byte reader with (a) a binary search over raw
file byte offsets that lands at/before the first FASTA-side line using only
O(log filesize) small forward line-probes, and (b) a single `FastaReader` opened at that
offset that indexes in one forward pass and extracts via `getSequenceSlice`. The public
map keyed by translation id (header text minus the leading `>`) is preserved; the two
consumers keep working with mechanical type changes only.

## Functional Requirements

### Boundary location (binary search)

- **FR-1** A component (advisory name `FastaSectionLocator`) MUST expose a static
  operation that, given the GFF3 `Path`, returns the byte offset of the first FASTA-side
  line, or an empty result when the file has no FASTA-side line anywhere (advisory
  signature `OptionalLong locate(Path gff3Path)`; exact name/return shape at
  implementer's discretion).

- **FR-2** The locator MUST classify a probed line using this table:

  | Line content                                       | Classification |
  |----------------------------------------------------|----------------|
  | Equals `##FASTA` (trimmed)                          | FASTA-side (boundary marker) |
  | Starts with `>`                                     | FASTA-side (header) |
  | Contains a TAB, or starts with `#` (not `##FASTA`)  | annotation-side |
  | Only protein-alphabet chars / `*` (no TAB)          | FASTA-side (wrapped sequence line) |
  | Empty / blank                                       | ambiguous → skip to next line |
  | EOF (no line at/after probe)                        | annotation-side (predicate false) |

- **FR-3** The binary search MUST operate over search space `[0, fileSize)` with the
  invariant: everything strictly below `lo` is annotation-side; `hi` is an upper bound
  known FASTA-side (or `fileSize`). Each probe MUST:
  1. Compute `mid = (lo + hi) >>> 1`.
  2. Find the start of the next full line at/after `mid` by reading a small buffer
     forward from `mid` to the next `\n`/`\r`; the byte after it is the line start
     (special-case `mid == 0` as line start 0). This is an O(line-length) forward read,
     never a full-section scan.
  3. Read that one line and classify per FR-2, skipping blank lines forward.
  4. On FASTA-side → set `hi = lineStart`; on annotation-side (including EOF) → set
     `lo = lineStartOfNextLine` (advance past the classified line).
  5. Terminate when the bracket collapses; return `hi` if a FASTA-side line was ever
     seen, else empty.

- **FR-4** The returned offset MUST be guaranteed to be at or before the first `'>'`
  header in the file. (The first FASTA-side line in file order is always `##FASTA` if
  present, otherwise the first `>` header; a wrapped sequence-continuation line can never
  be first because its header precedes it and is itself FASTA-side and earlier. The
  search therefore always converges onto `##FASTA` or the first `>`.)

- **FR-5** The "no `##FASTA` anywhere" case MUST be a first-class outcome of the locator
  (empty result), not a separate pre/post scanning fallback. Files ending in a bare
  `>header` block with no `##FASTA` MUST still resolve (the monotone predicate converges
  on the first `>`). A pre-probe of the final non-blank line MAY be used to give the fast
  "no FASTA-side line anywhere → empty" answer.

### FastaReader integration

- **FR-6** `GFF3TranslationReader` MUST keep its constructor
  `GFF3TranslationReader(ValidationEngine, Path)` (invoked at `GFF3FileReader.java:64`).

- **FR-7** `readTranslationOffset()` MUST:
  1. Call the locator. If empty → return an empty `TreeMap` and open **no** `FastaReader`
     (handles empty-file and no-FASTA cases with zero further work).
  2. Otherwise open exactly one
     `FastaReader(gff3Path.toFile(), SequenceAlphabet.defaultProteinAlphabet(), offset)`,
     cached in a field for reuse by `readTranslation`.
  3. Build a `TreeMap<String, Long>`: for each `id` in `fastaReader.getOrderedIds()`,
     key = `fastaReader.getHeaderline(id)` with the single leading `>` stripped (mirroring
     `GFF3FileReader.TRANSLATION_ID_PATTERN`), value = the `FastaReader` sequential entry
     id. `TreeMap` preserves the alphabetical external ordering asserted today.

- **FR-8** `readTranslation(Long id)` MUST:
  - Obtain `SequenceIndex idx = fastaReader.getSequenceIndex(id); long n = idx.totalBases();`
  - if `n == 0` → return `""` (the only way an "empty" translation can arise now).
  - else return `fastaReader.getSequenceSlice(id, 1, n).toUpperCase()` (uppercase
    preserves current output; the slice already has newlines stripped).

### Map value type change (OffsetRange → Long)

- **FR-9** The translation map value type MUST change from `OffsetRange` to `Long` (the
  `FastaReader` sequential entry id), and `OffsetRange.java` MUST be deleted.

- **FR-10** `GFF3FileReader` MUST be updated: field `Map<String, OffsetRange>` →
  `Map<String, Long>`; `getTranslationOffsetForAnnotation(GFF3Annotation)` and
  `getTranslationOffsetMap()` return `Map<String, Long>`; `getTranslation(OffsetRange)` →
  `getTranslation(Long id)` delegating to `translationReader.readTranslation(id)`.

- **FR-11** `GFF3File` MUST be updated: `writeFastaFromOffsets(Writer, Map<String, Long>)`;
  the `annOffserMap` type; `getTranslation(entry.getValue())` unchanged in shape.

- **FR-12** `GFF3Mapper` MUST be updated: `mapGFF3Feature` / `mapTranslation` signatures
  take `Map<String, Long>`; `getTranslation(translationMap.get(translationKey))` unchanged
  in shape.

### Resource lifecycle

- **FR-13** `GFF3TranslationReader` MUST implement `AutoCloseable`; `close()` MUST close
  the cached `FastaReader` if non-null (no-op otherwise). Rationale: `getSequenceSlice`
  requires its underlying reader to stay open (`ensureFileReaderOpen()`), so the
  `FastaReader` must live from index-build through the last extraction.

- **FR-14** `GFF3FileReader.close()` (currently closing only `bufferedReader`) MUST also
  close `translationReader`. `GFF3FileReader` is already `AutoCloseable`, so callers using
  try-with-resources need no change.

### Error handling (eager validation)

- **FR-15** `FastaReader` validates every byte against `defaultProteinAlphabet()` eagerly
  during construction; an illegal byte throws `FastaFileException` (wrapping
  `SequenceReadingException` with a message naming the illegal character, its byte value,
  and its absolute file position). The construction call also declares `IOException`.

- **FR-16** The new implementation MUST wrap both `FastaFileException` and `IOException`
  from `FastaReader` construction (and any `Exception` from `getSequenceSlice`) into
  `ReadException` (`ExitException`, exit code `READ_ERROR=10`), with a message naming the
  GFF3 file and preserving the underlying `FastaFileException` message as the cause. Use
  `ReadException.wrapAsIOException(...)` for the constructor path that needs an
  `IOException` cause. Illegal-character failures MUST NOT be distinguished from I/O
  failures by message sniffing.

### Test behavior changes

- **FR-17** All tests constructing `new OffsetRange(...)` and reading `r.start`/`r.end`
  MUST be rewritten against the new API. Tests that hand-crafted a byte range and called
  `readTranslation(range)` — `testReadTranslationExtractsCorrectSequence`,
  `testNewlinesAreRemoved`, `testOffsetsPointInsideFile`, `testEmptyRangeReturnsEmptyString`,
  `testInvalidSequenceTriggersValidationError` — MUST be replaced with tests that build the
  map via `readTranslationOffset()` and extract via `readTranslation(id)` (extraction is now
  keyed by `FastaReader` entry id, not a caller-supplied raw range).

- **FR-18** `testReadTranslationOffset_ReturnsCorrectKeys` MUST be kept: `TreeMap`
  alphabetical order is preserved (`BN000065.1|CDS_RHX` before `BN000066.1|CDS_RHD`).
  Assert the reassembled, newline-stripped, uppercased sequences (`ATGCATGCATGATAT`,
  `TTTTGGGGAT`) via the new API.

- **FR-19** `testInvalidSequenceTriggersValidationError` (`>IDBAD\nATGC1234\n`) MUST change
  from "verify `handleSyntacticError` called, no throw" to "`readTranslationOffset()`
  throws `ReadException`" (digit `1` is illegal for the protein alphabet; failure is now
  eager at map build, not lazy per read).

- **FR-20** `testInvalidTranslationSequenceGff3`
  (`id\tsource\tattribute\nid\tsource\n>test_1\nATGCATGCATAT`) MUST be rewritten to assert
  the translation is read **successfully with no throw**. The old backward-scan threw
  `RuntimeException("Invalid GFF3 translation sequence: ...")` on the malformed annotation
  line above the header; the new locator classifies that line as annotation-side, starts
  the `FastaReader` at `>test_1`, which contains only legal characters. The
  "clear informative error" intent is covered instead by FR-19 and a new dedicated
  illegal-character test.

- **FR-21** `testEmptyRangeReturnsEmptyString` MUST be replaced (the inverted-range case
  cannot arise without caller-supplied ranges) with a test that a zero-base translation
  entry (`totalBases()==0`) yields `""` from `readTranslation(id)`.

- **FR-22** `testEmptyFileReturnsEmptyMap`, `testNoSequenceGff3`,
  `testReadingHandlesSingleEntry`, `testReadingStopsAtFasta`, `testNewlinesAreRemoved` MUST
  preserve their semantics (empty map / no throw / newline stripping / never keying
  `##FASTA` or annotation lines) asserted via the new API.

- **FR-23** A new `FastaSectionLocatorTest` MUST be added covering: empty file; no `##FASTA`
  and no `>` (→ empty); bare `>header` block with no `##FASTA` (→ offset of first `>`);
  `##FASTA` at byte 0; `##FASTA` at EOF with no trailing records; huge annotation section +
  tiny FASTA section and the reverse (assert probe count / no full scan via a counting
  `SeekableByteChannel` or byte-read counter); probe landing exactly on a line boundary vs.
  mid-line; `\n` vs `\r\n` line endings. It MUST assert the returned offset is `<=` the
  first `'>'` in the file.

## Non-Functional Requirements

- **NFR-1 (performance — logarithmic probes)** Locating the FASTA boundary MUST take
  ≈ `log2(fileSize)` probes worst-case (~40 for a 1 TB file). No full linear scan of either
  the annotation section or the FASTA section is permitted at any stage.

- **NFR-2 (performance — per-probe bounded read)** Each probe MUST read at most one line
  (an O(line-length) forward read), never a whole section.

- **NFR-3 (performance — buffered extraction)** Sequence extraction MUST use the library's
  buffered positional `getSequenceSlice` path; per-byte `readByte()` extraction MUST NOT be
  reintroduced.

- **NFR-4 (correctness — offset upper bound)** The located offset MUST be `<=` the first
  `'>'` header byte offset. (An offset landing past a header silently drops that record; an
  offset on `##FASTA` or any preceding annotation line is tolerated and skipped forward by
  `FastaReader`.)

- **NFR-5 (correctness — ordering preserved)** The externally observed alphabetical
  ordering of translation ids MUST be preserved (via `TreeMap`).

## Observable Success Criteria

- **SC-1** `./gradlew spotlessCheck test -Pgitlab_private_token=dummy-token` passes with
  zero spotless violations and all tests green.
- **SC-2** `GFF3TranslationReaderTest` is updated (not left red) and passes against the new
  API, including the rewritten error-handling cases (FR-19, FR-20).
- **SC-3** `FastaSectionLocatorTest` exists and passes, including the assertion that the
  located offset is `<=` the first `'>'` and the probe-count / no-full-scan assertions.
- **SC-4** Existing `GFF3File` / `GFF3Mapper` conversion (integration) tests pass unchanged,
  proving the `Map<String, Long>` swap and lifecycle wiring are correct end-to-end.
- **SC-5** `OffsetRange.java` no longer exists; the project compiles with `Map<String, Long>`
  at all three call sites.
- **SC-6** Malformed translation content in the FASTA section produces a `ReadException`
  (exit code `READ_ERROR=10`) whose cause message names the illegal character and its
  absolute file position.

## Scope and Boundaries

In scope:

- Rewriting `GFF3TranslationReader` and adding `FastaSectionLocator` in `gff3/reader/`.
- The `OffsetRange` → `Long` map value type swap across `GFF3FileReader`, `GFF3File`,
  `GFF3Mapper`, and deletion of `OffsetRange`.
- Resource-lifecycle wiring in `GFF3FileReader.close()`.
- The test changes enumerated in FR-17 … FR-23.

Explicitly out of scope:

- **Modifying the `fastareader` repo/dependency itself.** The `fastareader` repo at
  `/root/code/ebi/fastareader` is read-only reference and MUST NOT be modified; the
  dependency stays at `uk.ac.ebi.ena:fastareader:1.3.0`.
- **Any CLI or user-facing surface change.** No command, flag, or output-format change.
- Re-introducing a soft/WARN validation pathway for translation-sequence characters (see
  Open Questions).

## Advisory Solution Approach

Two components in `gff3/reader/`:

1. **`FastaSectionLocator`** (new, small, independently testable) — the binary search of
   FR-1 … FR-5 over raw byte offsets. Kept separate from `GFF3TranslationReader` because the
   boundary logic has independent test value: every boundary edge case can be asserted
   against raw byte offsets without constructing a `FastaReader` or supplying valid protein
   data. Matches the repo's existing pattern of small focused classes (`TranslationKey`,
   `OffsetRange`).

2. **`GFF3TranslationReader`** (rewritten, same constructor) — calls the locator; if a
   boundary exists, opens one cached `FastaReader` at that offset, builds the
   `TreeMap<String, Long>` from `getOrderedIds()`/`getHeaderline()`, and serves each
   sequence via `getSequenceSlice(id, 1, totalBases)`. Implements `AutoCloseable`; wraps
   library failures in `ReadException`.

Design decision (Decision 1, chosen: **Option B — delete `OffsetRange`**): route both
indexing and extraction through the single `fastareader` path. The rejected Option A (keep
`Map<String, OffsetRange>`, derive `OffsetRange(idx.firstBaseByte, idx.lastBaseByte)` and do
our own buffered range-read) leaves the biggest performance problem on the table by
re-implementing, against our own file channel, exactly the buffered slice reader the library
already provides — more code and more indirection. Option B's cost — swapping a generic type
parameter across three call sites — is mechanical and compile-checked, and deleting
`OffsetRange` removes a type rather than adding one.

Design decision (error routing, chosen: uniform `ReadException`): routing illegal-character
failures to `ValidationException` (exit `VALIDATION_ERROR=20`) would better mirror the old
`handleSyntacticError` path but requires brittle inspection of the `FastaFileException`
message/cause to tell illegal-character failures from I/O failures. Rejected in favor of a
uniform `ReadException` with a stable exit code.

## Codebase Map

Files this work touches (anchors reused from the prior verified exploration):

- `src/main/java/uk/ac/ebi/embl/gff3tools/gff3/reader/GFF3TranslationReader.java`
  — **rewritten**. Constructor `GFF3TranslationReader(ValidationEngine, Path)` kept;
  `readTranslationOffset()` returns `Map<String, Long>`; `readTranslation(Long)` via
  `getSequenceSlice`; implements `AutoCloseable`; wraps failures in `ReadException`.
- `src/main/java/uk/ac/ebi/embl/gff3tools/gff3/reader/FastaSectionLocator.java`
  — **new**. Static binary-search locator (`OptionalLong locate(Path)` advisory).
- `src/main/java/uk/ac/ebi/embl/gff3tools/gff3/reader/OffsetRange.java` — **deleted**.
- `src/main/java/uk/ac/ebi/embl/gff3tools/gff3/reader/GFF3FileReader.java`
  — `:64` constructs `GFF3TranslationReader`; `:320`, `:326`, `:333` are the
  `getTranslationOffsetForAnnotation` / `getTranslationOffsetMap` / `getTranslation` call
  sites; field type `Map<String, OffsetRange>` → `Map<String, Long>`; `getTranslation`
  signature `OffsetRange` → `Long`; `close()` must also close `translationReader`.
  `TRANSLATION_ID_PATTERN` is the reference for stripping the leading `>`.
- `src/main/java/uk/ac/ebi/embl/gff3tools/gff3/GFF3File.java`
  — `writeFastaFromOffsets(Writer, Map<String, Long>)`; `annOffserMap` type;
  `getTranslation(entry.getValue())` unchanged in shape.
- `src/main/java/uk/ac/ebi/embl/gff3tools/gff3toff/GFF3Mapper.java`
  — `mapGFF3Feature` / `mapTranslation` signatures take `Map<String, Long>`;
  `getTranslation(translationMap.get(translationKey))` unchanged in shape.
- `src/main/java/uk/ac/ebi/embl/gff3tools/gff3toff/TranslationKey.java`
  — the translation-key type used by `GFF3Mapper` (unchanged; referenced by consumers).
- `src/test/java/uk/ac/ebi/embl/gff3tools/gff3/reader/GFF3TranslationReaderTest.java`
  — **updated** per FR-17 … FR-22.
- `src/test/java/uk/ac/ebi/embl/gff3tools/gff3/reader/FastaSectionLocatorTest.java`
  — **new** per FR-23.
- `build.gradle:77` — `uk.ac.ebi.ena:fastareader:1.3.0` dependency (already present; no
  change required).
- `docs/0001_error_handling.md` — exit-code / `ExitException` hierarchy reference
  (`ReadException` = `READ_ERROR=10`).

fastareader 1.3.0 API surface used:

- `FastaReader(File, SequenceAlphabet, long offset)` constructor (declares `IOException`,
  throws `FastaFileException` eagerly on illegal bytes).
- `getOrderedIds()`, `getHeaderline(id)`, `getSequenceIndex(id)`,
  `getSequenceSlice(id, 1, totalBases)`.
- `SequenceIndex.totalBases()` / `firstBaseByte()` / `lastBaseByte()`.
- `SequenceAlphabet.defaultProteinAlphabet()`.

## Open Questions and Risks

- **`validationEngine` becomes unused for sequence-character validation.** The constructor
  parameter is kept for signature stability (`GFF3FileReader.java:64`) and possible future
  line-level validation, but no longer gates sequence characters. If dead-parameter checks
  object, a follow-up could re-introduce a soft/WARN pathway — but that conflicts with
  `FastaReader`'s eager hard-fail model and is out of scope here.
- **Loss of configurable severity for translation-sequence validation.** Previously an
  invalid character routed through `ValidationEngine` and could be downgraded to WARN and
  tolerated; now it is always a hard failure at map-build time. This is a genuine,
  documented behavior tightening and a residual risk if any downstream config relied on
  WARN-ing malformed translations.
- **Alphabet permissiveness delta.** `defaultProteinAlphabet()` is stricter than the old
  `isValidSequence` in one direction (rejects `J`, digits, tabs, any non-alphabet byte) and
  more permissive in another (accepts lowercase and ambiguity codes X/B/Z/U/O). The old
  check merely asked "A–Z or `*`". No extra post-extraction check is added: the protein
  alphabet is the correct domain constraint for translations. Accepted as a documented
  tightening.

## Phased Delivery Plan

### Phase 1 — `FastaSectionLocator` + tests

- **Goal:** implement the pure offset-location binary search with full edge-case coverage,
  with no dependency on `FastaReader`.
- **Scope (create):** `FastaSectionLocator.java` (FR-1 … FR-5),
  `FastaSectionLocatorTest.java` (FR-23).
- **Out of bounds:** touching `GFF3TranslationReader`, `OffsetRange`, or any consumer.
- **Entry conditions:** none (independent).
- **Exit criteria:** `FastaSectionLocatorTest` passes; offset `<=` first `'>'` asserted;
  probe-count / no-full-scan assertion in place (NFR-1, NFR-2, NFR-4).
- **Parallelism:** independent of Phase 2's `GFF3TranslationReader` internals but Phase 2
  depends on this class existing.
- **Effort:** medium. **Difficulty:** hard (binary-search correctness over raw bytes,
  line-boundary/mid-line and `\n` vs `\r\n` handling, monotonic-predicate edge cases).
- **Blockers:** none.

### Phase 2 — `GFF3TranslationReader` rewrite

- **Goal:** rewrite the reader to open one `FastaReader` at the located offset, build the
  `Map<String, Long>`, extract via `getSequenceSlice`, implement `AutoCloseable`, and wrap
  failures in `ReadException`.
- **Scope (modify):** `GFF3TranslationReader.java` (FR-6 … FR-8, FR-13, FR-15, FR-16).
- **Out of bounds:** consumer signature changes (Phase 3), test rewrites (Phase 4).
- **Entry conditions:** Phase 1 complete (`FastaSectionLocator` available).
- **Exit criteria:** class compiles; `readTranslationOffset()` returns `Map<String, Long>`;
  `readTranslation(Long)` uses buffered slice; `close()` closes the cached `FastaReader`;
  library failures surface as `ReadException` (READ_ERROR=10).
- **Parallelism:** must follow Phase 1; precedes Phase 3.
- **Effort:** medium. **Difficulty:** medium (library wiring, lifecycle, exception wrapping).
- **Blockers:** Phase 1.

### Phase 3 — consumer type-swap + delete `OffsetRange`

- **Goal:** swap the map value type to `Long` across all consumers, wire lifecycle, delete
  `OffsetRange`.
- **Scope (modify):** `GFF3FileReader.java` (FR-10, FR-14 — including `close()` and the
  `:320`/`:326`/`:333` call sites), `GFF3File.java` (FR-11), `GFF3Mapper.java` (FR-12).
  **(delete):** `OffsetRange.java` (FR-9).
- **Out of bounds:** test rewrites (Phase 4); any behavior change beyond the type swap and
  `close()` wiring.
- **Entry conditions:** Phase 2 complete (new reader API in place).
- **Exit criteria:** main source compiles with `Map<String, Long>` everywhere;
  `OffsetRange.java` gone; `GFF3FileReader.close()` closes `translationReader`.
- **Parallelism:** must follow Phase 2; precedes Phase 4.
- **Effort:** small. **Difficulty:** easy (mechanical, compile-checked).
- **Blockers:** Phase 2.

### Phase 4 — test file rewrite

- **Goal:** update `GFF3TranslationReaderTest` to the new API and behavior.
- **Scope (modify):** `GFF3TranslationReaderTest.java` (FR-17 … FR-22), including the new
  dedicated illegal-character `ReadException` test.
- **Out of bounds:** production code changes (done in Phases 2–3); `FastaSectionLocatorTest`
  (done in Phase 1).
- **Entry conditions:** Phase 3 complete (consumers compile).
- **Exit criteria:** `GFF3TranslationReaderTest` compiles and passes against the new API;
  FR-19 asserts `ReadException`, FR-20 asserts successful read with no throw, FR-21 asserts
  zero-base → `""`.
- **Parallelism:** must follow Phase 3.
- **Effort:** medium. **Difficulty:** medium (behavior semantics must be re-derived per FR).
- **Blockers:** Phase 3.

### Phase 5 — spotless + full test run

- **Goal:** format and verify the whole change end-to-end.
- **Scope (run):** `./gradlew spotlessApply` then
  `./gradlew spotlessCheck test -Pgitlab_private_token=dummy-token`.
- **Out of bounds:** any further code change beyond formatting fixes and test-failure
  remediation surfaced by this run.
- **Entry conditions:** Phases 1–4 complete.
- **Exit criteria:** zero spotless violations; all tests pass (SC-1 … SC-6), including the
  unchanged `GFF3File`/`GFF3Mapper` integration tests (SC-4).
- **Parallelism:** final, sequential.
- **Effort:** small. **Difficulty:** easy.
- **Blockers:** Phases 1–4.

## Phases (JSON)

```json
[
  { "phase": 1, "focus": "Add FastaSectionLocator (binary-search offset locator) + FastaSectionLocatorTest; pure raw-byte offset logic, no FastaReader.", "effort": "medium", "difficulty": "hard" },
  { "phase": 2, "focus": "Rewrite GFF3TranslationReader: open one FastaReader at located offset, build Map<String,Long>, getSequenceSlice extraction, AutoCloseable, wrap failures in ReadException.", "effort": "medium", "difficulty": "medium" },
  { "phase": 3, "focus": "Swap map value type OffsetRange->Long across GFF3FileReader/GFF3File/GFF3Mapper; wire GFF3FileReader.close() to close translationReader; delete OffsetRange.", "effort": "small", "difficulty": "easy" },
  { "phase": 4, "focus": "Rewrite GFF3TranslationReaderTest per behavior changes (ReadException on illegal char, no-throw for annotation-side malformed line, zero-base empty string, new-API extraction).", "effort": "medium", "difficulty": "medium" },
  { "phase": 5, "focus": "Run spotlessApply then spotlessCheck test with gitlab_private_token=dummy-token; fix formatting and any surfaced failures.", "effort": "small", "difficulty": "easy" }
]
```
