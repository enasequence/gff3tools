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
  | Equals `##FASTA`                                    | FASTA-side (boundary marker) |
  | Starts with `>`                                     | FASTA-side (header) |
  | Starts with `#` (not `##FASTA`)                     | ambiguous → skip to next line |
  | Empty / blank                                       | ambiguous → skip to next line |
  | Contains a TAB                                      | annotation-side |
  | Any other line                                      | FASTA-side (sequence line, valid or not) |
  | EOF (no line at/after probe)                        | annotation-side (predicate false) |

  Only a TAB marks a line as definitely annotation-side: FASTA lines never contain one.
  A `#` line is ambiguous because comments and directives are legal in the annotation
  section, but a stray one can also sit inside the FASTA section, and one line alone cannot
  tell the two apart. Skipping it, as with a blank line, means the boundary never moves past
  it, so a `#` line inside the FASTA section is handed to `FastaReader`, which rejects it per
  FR-15. A `##FASTA` line with trailing whitespace is skipped the same way; the boundary
  then lands on the first `>` header, which is still correct.

  A line with no TAB and no leading `#` MUST be classified FASTA-side even when it holds
  characters outside the protein alphabet (digits, `-`, `.`, trailing whitespace). Calling
  it annotation-side would move the boundary past it, so the records before it would be
  silently dropped and the bad characters never validated. As FASTA-side it is handed to
  `FastaReader`, which rejects it per FR-15.

- **FR-3** The binary search MUST operate over search space `[0, fileSize)` with the
  invariant: everything strictly below `lo` is annotation-side; `hi` is an upper bound
  known FASTA-side (or `fileSize`). Each probe MUST:
  1. Compute `mid = (lo + hi) >>> 1`.
  2. Find the start of the next full line at/after `mid` by reading a small buffer
     forward from `mid` to the next `\n`/`\r`; the byte after it is the line start
     (special-case `mid == 0` as line start 0). This is an O(line-length) forward read,
     never a full-section scan.
  3. Read that one line and classify per FR-2, skipping ambiguous lines (blank or `#`)
     forward, without passing `hi`.
  4. On FASTA-side → set `hi = lineStart`; on annotation-side (including EOF) → set
     `lo = lineStartOfNextLine` (advance past the classified line). If only ambiguous lines
     remain up to `hi` or EOF → set `hi` to the probe's line start and keep searching below.
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

### Compressed input

- **FR-24** Before calling the locator, `readTranslationOffset()` MUST check whether the file
  starts with the gzip magic bytes (`GzipUtils.isGzipped`, which matches both plain gzip and
  bgzip). If it does, it MUST log one WARN that any `##FASTA` translations are omitted and the
  file should be decompressed to keep them, then return an empty `TreeMap` and open **no**
  `FastaReader`. The check sits in the reader rather than in a command so every caller is
  covered; `ValidationCommand` already decompresses to a temp file and never reaches it.

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

- **FR-25** Tests MUST cover FR-24: in `GFF3TranslationReaderTest`, a gzipped file returns an
  empty map with the WARN logged, and a multi-block BGZF file large enough that the old
  behaviour returned a partial map returns an empty one; in `FileConversionCommandTest`,
  GFF3 → EMBL conversion of gzipped input exits 0 both without a `##FASTA` section and with
  one, the latter alongside a plain copy of the same file that does produce `/translation`.

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
- **SC-7** GFF3 → EMBL conversion of a gzip- or bgzip-compressed GFF3 file exits 0 with no
  `/translation` qualifiers and the FR-24 warning, whether or not the file has a `##FASTA`
  section.

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
- **Any CLI or user-facing surface change.** No command, flag, or output-format change. The
  only user-visible addition is the FR-24 warning, documented in `docs/cli-usage-guide.md`.
- **Reading translations from compressed input.** Compressed files are skipped (FR-24), not
  decompressed; see Open Questions.
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
- `src/main/java/uk/ac/ebi/embl/gff3tools/utils/GzipUtils.java` — `isGzipped` magic-byte
  check used by FR-24 (unchanged).
- `src/test/java/uk/ac/ebi/embl/gff3tools/cli/FileConversionCommandTest.java` — compressed
  GFF3 → EMBL conversion tests per FR-25.
- `docs/cli-usage-guide.md` — "Gzip-compressed input" section notes that conversion omits
  translations from compressed GFF3.

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
- **`#` lines are skipped, not classified (resolved).** An earlier version classified `#`
  lines as annotation-side. A `#` line inside the FASTA section then moved the boundary past
  it whenever a probe landed on it, silently dropping every record before it (6 of 500
  positions in a 500-record sweep). Treating `#` lines as ambiguous (FR-2) closes this: the
  same sweep now fails loudly at every position, with `FastaReader` rejecting the `#`
  character, matching the old backward scan's hard failure. Cost: a probe that lands in a
  run of `#` lines (e.g. many `##sequence-region` directives) reads forward to the end of
  the run, bounded by `hi`; once a TAB line is found, `lo` moves past the whole run.
- **Annotation lines without TABs can mislead the locator (harmless).** A malformed
  annotation line that uses spaces instead of TABs is classified FASTA-side, so if a probe
  lands on it the boundary is placed too early (6 of 500 positions in a sweep). The result
  is unaffected: `FastaReader` starts there and still returns every record (500 of 500
  positions).
- **A TAB inside the FASTA section silently drops records (open).** FR-2 assumes FASTA lines
  never contain a TAB, but a sequence line can carry a stray one (e.g. `MKVLAAGIVG\t`). If a
  probe lands on it, the line is classified annotation-side, `lo` moves past it, and the
  boundary lands on a later `>` header. `FastaReader` then never sees the bad line or any
  record before it. Observed in a 500-record sweep, moving the TAB through each record:

  | Result | Positions |
  |---|---|
  | Boundary placed after `##FASTA` | 3 of 500 |
  | TAB in record 0 | no error, 499 of 500 translations read |
  | TAB in record 13 | no error, 486 of 500 translations read |

  The two misplacements are not symmetric: a boundary too early is harmless (`FastaReader`
  skips to the first `>`, as in the entry above), a boundary too late loses data. So the
  locator should call a line annotation-side only when it is sure.

  Proposed fix, in `classify()` only: a line is annotation-side when it has exactly 9
  TAB-separated columns and columns 4 and 5 (start, end) are integers; every other line that
  is not `>`, `##FASTA` or ambiguous stays FASTA-side. `MKVLAAGIVG\t` then splits into 2
  columns, stays FASTA-side, and `FastaReader` rejects the TAB per FR-15, the same loud
  failure as a `#` line. The search loop does not change.

  Optional safety net: after the search, check the boundary sits on `##FASTA` (or on a `>`
  header with no FASTA-side line before it) and fall back to a forward scan when it does not.
  Well-formed files never take that path; it also covers a full 9-column feature line pasted
  into the FASTA section, which the column check alone would still misread.

  Tests: the same TAB sweep in `FastaSectionLocatorTest` (boundary never after `##FASTA`)
  and in `GFF3TranslationReaderTest` (`ReadException` at every position, never a short map).
- **Compressed input is skipped, not read (resolved, FR-24).** `FileConversionCommand`
  decompresses gzip input as a stream for the annotation reader but passes the original
  `.gz` path to `Gff3ToFFConverter`, so the locator searched compressed bytes. On `main` the
  old backward scan found nothing there and returned no translations without a warning.
  Before FR-24, this branch did worse:

  | Input (GFF3 → EMBL conversion) | `main` | Before FR-24 | With FR-24 |
  |---|---|---|---|
  | gzip, no `##FASTA` | exit 0 | exit 10, "Plain gzip is not supported" | exit 0, warning |
  | gzip, with `##FASTA` | exit 0, 0 translations, no warning | exit 10 | exit 0, 0 translations, warning |
  | bgzip, with `##FASTA` (500 proteins) | exit 0, 0 translations, no warning | exit 0, 351 of 500 read, no warning | exit 0, 0 read, warning |

  `FastaReader` opens BGZF, but the locator's offset is a position in the compressed bytes
  while `FastaReader` reads it as a position in the decompressed text; when it lands past the
  real FASTA start the first records are dropped (0 of 50 `/translation` qualifiers in the
  converted EMBL for the file above). Two fixes were weighed:

  | | Skip with a warning (chosen) | Decompress to a temp file first |
  |---|---|---|
  | Change | ~5 lines in `GFF3TranslationReader` + docs | ~10–15 lines in conversion, copying `ValidationCommand` |
  | Translations from compressed input | omitted, with a warning | kept |
  | Consistency with `validation` | differs: validation keeps them | same |
  | Cost on large files | none | a full decompressed copy written to disk |

  Skipping was chosen for this change because it is smaller and matches what `main` already
  did, but no longer silently. Follow-up: decompress to a temp file when FASTA reading moves
  into validation, or sooner if ENA sends compressed GFF3 to conversion.
- **Duplicate FASTA headers: the last one silently wins (resolved).** FR-7 step 3 put each
  record into the `TreeMap` in file order, so when two records shared a header, the later
  one overwrote the earlier one with no error. On `main`, the backward scan put records in
  reverse order, so the *first* record won, also silently, so this change flipped which
  copy was used. Observed: a `##FASTA` section with `>SEQID1|cds1` twice (`MFIRSTAAA`,
  then `MSECONDAA`), converted to EMBL, exited 0 and wrote `/translation="MSECONDAA"`.

  | Input | `main` | Before the fix | Now |
  |---|---|---|---|
  | two records with the same header | first wins, exit 0 | last wins, exit 0 | `ReadException`, exit 10 |

  Fix: while building the map, `readTranslationOffset()` throws a `ReadException`
  (`READ_ERROR=10`) when a header is already present, naming the file and the repeated
  header, instead of overwriting it. Headers match on the full text after `>`.

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

### Phase 6 — skip compressed input

- **Goal:** stop the locator from searching compressed bytes, which failed plain gzip input
  and silently dropped records from bgzip input.
- **Scope (modify):** `GFF3TranslationReader.java` (FR-24), `GFF3TranslationReaderTest.java`
  and `FileConversionCommandTest.java` (FR-25), `docs/cli-usage-guide.md`.
- **Out of bounds:** decompressing compressed input to read its translations (see Open
  Questions).
- **Entry conditions:** Phases 1–5 complete.
- **Exit criteria:** SC-7 holds; the FR-25 tests fail with the FR-24 check disabled and pass
  with it; full suite green.
- **Parallelism:** final, sequential.
- **Effort:** small. **Difficulty:** easy.
- **Blockers:** Phases 1–5.

## Phases (JSON)

```json
[
  { "phase": 1, "focus": "Add FastaSectionLocator (binary-search offset locator) + FastaSectionLocatorTest; pure raw-byte offset logic, no FastaReader.", "effort": "medium", "difficulty": "hard" },
  { "phase": 2, "focus": "Rewrite GFF3TranslationReader: open one FastaReader at located offset, build Map<String,Long>, getSequenceSlice extraction, AutoCloseable, wrap failures in ReadException.", "effort": "medium", "difficulty": "medium" },
  { "phase": 3, "focus": "Swap map value type OffsetRange->Long across GFF3FileReader/GFF3File/GFF3Mapper; wire GFF3FileReader.close() to close translationReader; delete OffsetRange.", "effort": "small", "difficulty": "easy" },
  { "phase": 4, "focus": "Rewrite GFF3TranslationReaderTest per behavior changes (ReadException on illegal char, no-throw for annotation-side malformed line, zero-base empty string, new-API extraction).", "effort": "medium", "difficulty": "medium" },
  { "phase": 5, "focus": "Run spotlessApply then spotlessCheck test with gitlab_private_token=dummy-token; fix formatting and any surfaced failures.", "effort": "small", "difficulty": "easy" },
  { "phase": 6, "focus": "Skip gzip/bgzip input in GFF3TranslationReader with a WARN (no FastaReader opened); tests for gzip, BGZF and compressed GFF3->EMBL conversion; cli-usage-guide note.", "effort": "small", "difficulty": "easy" }
]
```
