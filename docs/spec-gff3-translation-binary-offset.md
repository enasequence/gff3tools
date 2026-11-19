# Spec: GFF3 translation reading via fastareader binary-search offset

- Feature: `gff3_translation_binary_offset`
- Date: 2026-07-08
- Status: Implemented

## Problem Statement

A GFF3 file can end with a `##FASTA` section holding protein translations, one record per
CDS (`>accession|id`, then the sequence). `GFF3TranslationReader` reads them back for
GFF3 → EMBL conversion and for GFF3 output.

On `main` it did this with two hand-rolled mechanisms that were slow and hard to maintain:

1. `readTranslationOffset()` scanned from the end of the file **backwards** through a 1 MB
   buffer, reversing each line to rebuild it, to find `##FASTA` and record a raw byte
   start/end (`OffsetRange`) per translation.
2. `readTranslation(OffsetRange)` read each sequence back with `seek` + `readByte()`, **one
   byte at a time**: one system call per residue. This was the biggest performance problem.

`uk.ac.ebi.ena:fastareader:1.3.0` provides a `FastaReader` that can open a file at a byte
offset (built for "annotations, then `##FASTA`" files), index the records in one forward
pass, and serve sequences with buffered reads. It replaces both mechanisms. What it cannot
do is find that offset: it knows nothing about GFF3 syntax, so locating the FASTA section is
gff3tools's job.

## How it works

Two classes in `gff3/reader/`:

- **`FastaSectionLocator`** finds the byte offset where the FASTA section starts, with a
  binary search over the file's bytes. Each probe reads one line. A GFF3 file is
  annotation lines, then optionally `##FASTA`, then FASTA lines to the end, so "is this
  line in the FASTA section?" is false and then true, never back to false. That makes the
  binary search sound.
- **`GFF3TranslationReader`** asks the locator for the offset, opens one `FastaReader`
  there, maps each header to `FastaReader`'s entry id, and reads each translation with
  `getSequenceSlice`.

**The rule behind every classification choice:** the located offset may be too early, but
never too late.

| Offset lands… | Effect |
|---|---|
| Too early (in the annotation section) | Harmless. `FastaReader` skips everything before the first `>` header. |
| Too late (past a `>` header) | Every record before it is dropped, with no error. |

So the locator only calls a line "annotation" when it is certain. Anything it is unsure of
counts as FASTA, where `FastaReader` either skips it (before the first header) or rejects it
(inside a record), loudly.

## Functional Requirements

### Locating the FASTA section

- **FR-1** `FastaSectionLocator.locate(Path)` MUST return the byte offset of the first
  FASTA-side line, or an empty `OptionalLong` when the file has none.

- **FR-2** Each probed line MUST be classified with this table:

  | Line | Classification |
  |---|---|
  | Equals `##FASTA` | FASTA-side (boundary marker) |
  | Starts with `>` | FASTA-side (header) |
  | Starts with `#` (not `##FASTA`) | ambiguous: skip to the next line |
  | Empty or blank | ambiguous: skip to the next line |
  | Feature line: ≥ 9 TAB-separated columns, columns 4 and 5 (start, end) integers | annotation-side |
  | Any other line | FASTA-side (sequence line, valid or not) |
  | EOF (no line at or after the probe) | annotation-side |

  - **Feature lines only.** A feature line is what `GFF3FileReader.readFeature` reads as
    one. A TAB alone is not enough: a broken sequence line can carry a stray one. A
    malformed feature line counts as FASTA-side, which only places the offset too early.
  - **`#` lines are ambiguous.** Comments and directives (`##gff-version`,
    `##sequence-region`, `###`) are legal in the annotation section, but a stray `#` line
    can also sit inside the FASTA section, and one line alone cannot tell which. Skipping
    it keeps the offset from moving past it, so a `#` line inside the FASTA section reaches
    `FastaReader`, which rejects it (FR-10). A `##FASTA` with trailing whitespace is
    skipped the same way; the offset then lands on the first `>`, which is still correct.
  - **Bad sequence lines stay FASTA-side.** A line that is not a feature line and not a
    `#` line MUST count as FASTA-side even with characters outside the protein alphabet
    (digits, `-`, `.`, a TAB, trailing whitespace), so `FastaReader` rejects it rather
    than the records before it being dropped.

- **FR-3** The search runs over `[0, fileSize)`. Everything below `lo` is annotation-side;
  `hi` is a known FASTA-side line start (or `fileSize`). Each probe:
  1. takes `mid = (lo + hi) >>> 1`;
  2. reads forward from `mid` to the next `\n`/`\r` to find the next line start (`mid == 0`
     is line start 0); `\r\n` counts as one line ending;
  3. classifies that line per FR-2, skipping ambiguous lines forward without passing `hi`;
  4. on FASTA-side sets `hi` to the line start; on annotation-side sets `lo` past the line;
     if only ambiguous lines remain up to `hi` or EOF, sets `hi` to the probe's line start
     and keeps searching below.

  It ends when the bracket collapses and returns the earliest FASTA-side line seen, never a
  blank line, or empty if none was seen.

- **FR-4** The returned offset MUST be at or before the first `>` header. The first
  FASTA-side line in a well-formed file is `##FASTA`, or the first `>` when there is no
  directive; a sequence line can never be first, because its header comes before it.

### Reading translations

- **FR-5** `GFF3TranslationReader(ValidationEngine, Path)` keeps its constructor.
  `readTranslationOffset()` MUST:
  1. return an empty map, opening no `FastaReader`, when the path is not a real file
     (stdin), when the file is compressed (FR-8), or when the locator finds nothing;
  2. otherwise open one
     `FastaReader(file, SequenceAlphabet.defaultProteinAlphabet(), offset)` and keep it
     open for `readTranslation`;
  3. return a `TreeMap<String, Long>` from translation id (the header with its leading `>`
     stripped) to `FastaReader`'s entry id. `TreeMap` keeps the ids in alphabetical order.

- **FR-6** `readTranslation(Long id)` MUST return `getSequenceSlice(id, 1, totalBases)`
  uppercased, newlines already stripped, or `""` for a record with no residues.

- **FR-7** The map value is `FastaReader`'s entry id (`Long`). `OffsetRange` is deleted;
  `GFF3FileReader`, `GFF3File.writeTranslationSection` and `GFF3Mapper` take
  `Map<String, Long>`.

- **FR-8** Compressed input: before locating, `readTranslationOffset()` MUST check for the
  gzip magic bytes (`GzipUtils.isGzipped`, which matches gzip and bgzip). If present it
  MUST log one WARN that `##FASTA` translations are omitted and the file should be
  decompressed, and return an empty map. The check sits in the reader so every caller is
  covered; `ValidationCommand` decompresses to a temp file first and never reaches it.

- **FR-9** Lifecycle: `GFF3TranslationReader` implements `AutoCloseable` and closes its
  `FastaReader`, which must stay open from indexing to the last `getSequenceSlice`.
  `GFF3FileReader.close()` closes the translation reader too, so callers using
  try-with-resources need no change.

### Errors

- **FR-10** `FastaReader` checks every byte against `defaultProteinAlphabet()` while
  indexing and throws `FastaFileException` on an illegal one, naming the character, its
  byte value and its file position. The alphabet allows `ACDEFGHIKLMNPQRSTVWYXBZUO*` in
  either case; `\n` and `\r` are line endings. Anything else, including a TAB, a digit or
  `#`, is illegal.

- **FR-11** `FastaFileException` and `IOException` from `FastaReader`, and any failure from
  `getSequenceSlice`, MUST be wrapped in `ReadException` (exit `READ_ERROR=10`). The message
  names the GFF3 file and the innermost cause's message, without Java class names; the full
  chain stays as the cause. Illegal characters are not told apart from I/O failures by
  message sniffing.

- **FR-12** A header that appears twice MUST throw `ReadException` naming the file and the
  header. Headers match on the full text after `>`.

| Situation | Result |
|---|---|
| Illegal character in `##FASTA` (TAB, digit, `#` line, …) | `ReadException`, exit 10 |
| Same header twice | `ReadException`, exit 10 |
| No FASTA section | empty map, no `FastaReader` opened |
| Compressed input to conversion | translations omitted, WARN, exit 0 |
| Any of the read failures above while writing GFF3 (`validation --output`) | wrapped in `WriteException`, exit 11 (see Known limitations) |

## Non-Functional Requirements

- **NFR-1** Locating the section MUST take about `log2(fileSize)` probes (~40 for 1 TB),
  each reading one line, with no full scan of either section.
- **NFR-2** Sequences MUST be read with the buffered `getSequenceSlice`; per-byte reads
  MUST NOT come back.
- **NFR-3** Translation ids MUST stay in alphabetical order.

## Tests

- **`FastaSectionLocatorTest`** checks the offset directly:
  - edge cases: empty file, no `##FASTA` and no `>`, `>` records with no `##FASTA`,
    `##FASTA` at byte 0 and at EOF, `\n`, `\r\n` and mixed line endings, probes landing
    mid-line and exactly on a line start, blank lines around and inside the section;
  - the offset is never past the first `>`;
  - probe counts on a huge annotation section and on a huge FASTA section (no full scan);
  - sweeps of 2,000 random layouts with blank lines, `#` lines and tabbed sequence lines
    in the FASTA section: the offset is always `##FASTA`.
- **`GFF3TranslationReaderTest`** checks what callers see:
  - ids, order, newline stripping, single records, empty and FASTA-less files,
    zero-residue records;
  - malformed annotation lines before the header still read;
  - illegal characters throw `ReadException` naming the character and position;
  - fixed layouts where the search probes the bad line (a digit, a `#` line, a TAB): each
    throws instead of dropping records;
  - gzip and multi-block bgzip input return an empty map with the warning;
  - duplicate headers throw.
- **`FileConversionCommandTest`**: GFF3 → EMBL conversion of gzipped input exits 0 with and
  without a `##FASTA` section, and omits translations that a plain copy keeps.

## Out of scope

- Changing `fastareader`: version 1.3.0 is used as published.
- Command, flag or output-format changes. The only user-visible addition is the FR-8
  warning, documented in `docs/cli-usage-guide.md`.
- Reading translations from compressed input (see Known limitations).
- A WARN-level path for bad translation characters (see Known limitations).

## Design decisions

### Translations keyed by `FastaReader` entry id, `OffsetRange` deleted

Keeping `Map<String, OffsetRange>` and doing our own buffered range reads would have
re-implemented the slice reader `fastareader` already has. Swapping the map's value type
was a mechanical, compile-checked change in three places, and removes a type.

### One `ReadException` for every read failure

Sending illegal characters to `ValidationException` (exit 20) would match the old
`handleSyntacticError` path, but telling them apart from I/O failures needs brittle
inspection of `fastareader`'s exception messages. A single `ReadException` gives a stable
exit code.

### Which lines count as annotation

Two rules were tried before the feature-line rule (FR-2). Each let some bad FASTA line be
taken for annotation, placing the offset too late and dropping records with no error:

| Rule | Failure |
|---|---|
| `#` lines are annotation | A `#` line inside `##FASTA` dropped the records before it (6 of 500 positions in a sweep). Fixed by treating `#` lines as ambiguous. |
| Any line with a TAB is annotation | A sequence line with a stray TAB did the same (3 of 500 positions; e.g. 486 of 500 translations read, no error). |

Rules for the TAB case, with each bad line moved through all 500 records ("late" = offset
after `##FASTA`):

| Rule | `MKVLAAGIVG\t` | `MKV1AAGIVG` | `MKV1AAGIVG\t` | `MKV.AAG\tIVG` |
|---|---|---|---|---|
| Contains a TAB | late | ok | late | late |
| Contains a TAB and a non-letter | ok | ok | late | late |
| Feature line (chosen) | ok | ok | ok | ok |

The feature-line rule asks what an annotation line must have rather than what it usually
contains, which a broken sequence line can share. With it, every bad line in the sweep
throws `ReadException` and a clean file reads all 500 translations.

The cost of skipping `#` lines: a probe that lands in a run of them (e.g. many
`##sequence-region` lines) reads forward to the end of the run, bounded by `hi`.

### Compressed input is skipped, not read

`FileConversionCommand` streams gzip input to the annotation reader but passes the `.gz`
path on, so the locator would search compressed bytes. On bgzip, `FastaReader` opens the
file but reads that offset as a position in the decompressed text, so it can land past the
real section start and drop records with no error (351 of 500 read in one test).

| | Skip with a warning (chosen) | Decompress to a temp file |
|---|---|---|
| Change | ~5 lines in `GFF3TranslationReader` + docs | ~10–15 lines in conversion, as in `ValidationCommand` |
| Translations from compressed input | omitted, with a warning | kept |
| Matches `validation` | no: validation keeps them | yes |
| Cost on large files | none | a full decompressed copy on disk |

Skipping matches `main`, which also returned no translations for compressed input, but now
says so.

### Duplicate headers throw

On `main` the backward scan kept the first of two records with the same header; a forward
`TreeMap` fill would have kept the last. Either silently picks a translation for the
submitter, so a repeated header is an error (FR-12).

## Known limitations and open questions

- **Compressed input loses its translations in conversion.** Follow-up: decompress to a
  temp file, as `ValidationCommand` does, when FASTA reading moves into validation, or
  sooner if ENA sends compressed GFF3 to conversion.
- **No check that the offset is right.** A full feature line pasted into the FASTA section
  would still count as annotation. Possible safety net: after the search, check the offset
  sits on `##FASTA` (or on a `>` with no FASTA-side line before it) and fall back to a
  forward scan if not. Well-formed files would never take that path.
- **Read failures while writing GFF3 exit 11.** In `validation --output` the FASTA section
  is first read while `GFF3File` writes, so a bad character is wrapped in `WriteException`
  (exit 11, not 10) after the features are already written.
- **Bad translation characters are always fatal.** On `main` they went through
  `ValidationEngine` and could be lowered to a warning. The `validationEngine` constructor
  argument is now unused.
- **`J` is rejected.** `main` accepted `A`–`Z` and `*` in either case. The protein
  alphabet accepts the same set except `J`, which `TranslationFix` produces for codons that
  translate to I or L, so a GFF3 file translated by gff3tools can fail to read back.
- **Public API change.** `OffsetRange` and `GFF3TranslationReader.isValidSequence` are
  removed, and `getTranslationOffsetMap` / `getTranslation` change type and now throw
  `ReadException`. The version moves 5.7.0 → 5.7.1.
