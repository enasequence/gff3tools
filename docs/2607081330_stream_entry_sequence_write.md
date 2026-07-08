- Feature Name: `stream_entry_sequence_write`
- Document Date: 2026-07-08
- Last Updated: 2026-07-08

# Summary

In the GFF3 → EMBL (`gff3toff`) conversion direction, gff3tools currently materialises each
entry's entire nucleotide sequence in memory as a lowercase `byte[]` before writing the EMBL flat
file. For large genome contigs/chromosomes this is a large peak-memory cost that scales linearly
with sequence length.

This spec introduces an internal streaming write path: an `EmblEntryWriter` subclass in the
`gff3toff` package that overrides the sequence-body hook to stream the sequence directly from a
`java.io.Reader` (fetched from the existing `SequenceLookup`) in fixed-size chunks, so the sequence
is never held in memory as a `byte[]`. The change is a **pure internal optimisation**: the emitted
EMBL output must remain **byte-for-byte identical** to the current path for the same input. It only
engages when a `SequenceLookup` is present, can serve the seqId, and the GFF3 `##sequence-region`
covers the whole underlying sequence; otherwise the existing `byte[]` path is used unchanged.

# Motivation & Rationale

## Problem

`GFF3Mapper.applySequenceData` (`src/main/java/uk/ac/ebi/embl/gff3tools/gff3toff/GFF3Mapper.java:325`)
calls `SequenceLookup.getSequenceSlice(...)` which returns the **whole slice as a `String`**
(`GFF3Mapper.java:333`), then allocates a `byte[]` of the same length, lowercases each base, and
stores it on the `Sequence` via `sequence.setSequence(ByteBuffer.wrap(seqBytes))`
(`GFF3Mapper.java:347-348`). The default `EmblEntryWriter.writeSequence` then walks that `byte[]`
(`EmblSequenceWriter`). Peak memory for a single entry therefore includes the full slice `String`
**plus** the full `byte[]` — roughly `~3N` bytes for an `N`-base contig at the peak. For
chromosome-scale sequences (tens–hundreds of Mbp) this dominates the tool's memory footprint and is
the reason the CLI documents an `-Xmx2G` workaround.

## Why streaming, and why now

sequencetools PR #199 (published locally as `2.900.0-streaming`, already pinned in this worktree's
`build.gradle:64`) added write hooks that let a caller emit the `SQ` header + sequence body from a
`Reader` instead of a `byte[]`, while keeping the surrounding `write()` machinery (ID/AC/DE/feature
table/terminator) intact:

- `EmblEntryWriter.writeStreamingSequence(Writer, long totalBases, Map<Character,Long> baseCounts, Reader reader, long crc)`
  (`sequencetools .../flatfile/writer/embl/EmblEntryWriter.java:164`) — writes only the `SQ` header +
  body, streaming the body from `reader` in 8192-char chunks
  (`EmblSequenceStreamWriter.STREAM_CHUNK`).
- `protected void EmblEntryWriter.writeSequence(Writer)` (`EmblEntryWriter.java:144`) — the single
  hook `write()` calls to emit the sequence body (`EmblEntryWriter.java:127`); default keeps the
  `byte[]` path via `EmblSequenceWriter`.
- `protected boolean EmblEntryWriter.isExpandedEntry()` (`EmblEntryWriter.java:158`) — controls
  feature-table ordering (expanded ⇒ FT emitted after the `CO` line, i.e. the layout used for
  inline-sequence entries). Default keys off the presence of the in-memory `byte[]`.
- `Sequence.setLength(long)` (un-deprecated) — `Sequence.getLength()` falls back to this value when
  there is no in-memory `byte[]`/contigs/AGP rows (`Sequence.java:175-203`), so the ID line "`n BP`"
  count stays correct for a streamed entry.

The gff3tools side already has all the plumbing needed to feed those hooks: `SequenceLookup` exposes
`getSequenceSliceReader(...)` (a caller-owned streaming `Reader`) and `getSequenceStats(...)`
(base counts + totals), both already implemented against fastareader through `FileSequenceSource`
and `CompositeSequenceProvider`.

# Usage Guidelines

There is **no new user-facing surface**. The streaming path is selected automatically inside
`gff3toff` conversion and is transparent to CLI users:

- It engages only when a `SequenceLookup` is wired (i.e. one or more `--sequence` sources supplied
  to the `conversion` command; see `FileConversionCommand.java:162` / `SequenceOptions.java`) **and**
  that lookup can serve the entry's seqId **and** the GFF3 `##sequence-region` spans the whole
  underlying sequence.
- When no sequence source is supplied (`sequenceLookup == null`), behaviour is exactly as today:
  no `SQ` block is written.
- When a sub-range `##sequence-region` is encountered (region does not span the whole sequence), the
  existing `byte[]` path is used unchanged.

No new CLI options, exit codes, or config are added. The `2.900.0-streaming` `build.gradle` pin is a
**pre-existing prerequisite** and is explicitly out of scope for this spec (it will be reverted to
`2.+` when PR #199 is released — see `build.gradle:61-64`).

# Requirements

## Functional

- **FR-1** Add an `EmblEntryWriter` subclass in package `uk.ac.ebi.embl.gff3tools.gff3toff` that
  overrides `writeSequence(Writer)` to delegate to
  `writeStreamingSequence(writer, totalBases, baseCounts, reader, 0L)`.
- **FR-2** The subclass must override `isExpandedEntry()` to return `true`, so the feature table is
  emitted in the same position as the current `byte[]` (inline-sequence) path.
- **FR-3** `GFF3Mapper` must, on the streaming path, **not** materialise the `byte[]`. Instead it
  sets `sequence.setLength(totalBases)` (so the ID line "`n BP`" count is correct via the
  `Sequence.getLength()` fallback) and records the data the writer needs at write time: the seqId,
  `totalBases`, and the (lowercased) `a/c/g/t` base counts.
- **FR-4** `Gff3ToFFConverter.writeEntry(...)` (`Gff3ToFFConverter.java:87`) must select the streaming
  writer when the mapper produced a streaming entry, open the `Reader` from
  `SequenceLookup.getSequenceSliceReader(...)` **at write time**, and pass it in; otherwise it uses
  the existing `new EmblEntryWriter(entry)` path (`Gff3ToFFConverter.java:91-93`). `setShowAcStartLine(false)`
  must still be applied in both paths.
- **FR-5** Base-count keys from `SequenceStats.baseCount` must be translated to **lowercase**
  `a/c/g/t` before being passed to `writeStreamingSequence` (see Decision 1).
- **FR-6** The streamed base characters must be **lowercased** before being written, so the sequence
  body matches the current lowercase `byte[]` output (see Decision 5, byte-identity).
- **FR-7** Streaming engages only when the region spans the whole underlying sequence
  (`start == 1 && end == totalBases`); otherwise fall back to the existing `byte[]` path
  (see Decision 2).
- **FR-8** When `sequenceLookup == null` or the lookup cannot serve the seqId, behaviour is exactly
  as today (no `SQ` block; no streaming) (see Decision 4).

## Non-Functional

- **NFR-1 (byte-identity)** For any input, the streaming path must produce EMBL output that is
  byte-for-byte identical to the current `byte[]` path. This is the primary correctness constraint.
- **NFR-2 (bounded memory)** Peak memory attributable to the sequence body on the streaming path must
  be bounded and independent of sequence length — the body is streamed in fixed chunks (the
  sequencetools writer uses an 8192-char buffer; the gff3tools lowercasing wrapper must likewise be
  O(chunk), never O(N)). No full-length `String` or `byte[]` of the sequence may be allocated on the
  streaming path.
- **NFR-3 (reader lifecycle)** The `Reader` from `getSequenceSliceReader` is caller-owned and is
  **not** closed by `writeStreamingSequence`; gff3tools must open it at write time and close it via
  try-with-resources around the write call (see Decision 3).
- **NFR-4 (no scope creep)** No change to the `fftogff3` direction, no change to public CLI
  behaviour, no new exceptions or exit codes.

# Decisions (pinned)

1. **Base-count key case → lowercase.** `SequenceStats.baseCount` keys are **uppercase**
   (verified: `fastareader/.../SequenceStats.java:20-27` — "Letter keys are canonicalized to
   uppercase"). The sequencetools streaming writer reads only lowercase `a/c/g/t` and derives
   `other = totalBases - (a+c+g+t)` (`EmblSequenceStreamWriter.java` `write()`/`countFor`). Therefore
   gff3tools must build a lowercase map (at least keys `a`,`c`,`g`,`t`) from the uppercase stats
   before calling `writeStreamingSequence`.

2. **Whole-sequence only.** `getSequenceStats(seqId)` returns **whole-sequence** stats
   (`FileSequenceSource.getSequenceStats` → `formatReader.getStats(ordinal)`). Assume the GFF3
   `##sequence-region` covers the entire underlying sequence and stream `[1..totalBases]`
   (`SequenceRangeOption.WHOLE_SEQUENCE`). If the region is a genuine sub-range
   (`start != 1 || end != totalBases`), fall back to the existing `byte[]` path (whose per-slice
   base counting is already correct for sub-ranges). Streaming only engages for whole-sequence
   regions.

3. **Reader lifecycle = caller-owned.** `SequenceLookup.getSequenceSliceReader` is documented as
   "Caller must close the returned Reader" (`SequenceLookup.java`), and `writeStreamingSequence`
   does not close it. gff3tools opens it at write time and closes it with try-with-resources around
   the write call.

4. **Null / absent `SequenceLookup` ⇒ current behaviour.** Streaming engages only when a lookup is
   present and can serve the seqId. With no external sequence, the entry carries no sequence body,
   exactly as today (`applySequenceData` early-returns at `GFF3Mapper.java:326-331`).

5. **Case-folding for byte-identity.** fastareader emits **uppercase** bases from both the slice
   `String` (`InternalReader.java:89`, `return sb.toString().toUpperCase()`) and the streaming
   `Reader` (`InternalReader.java:136`, `Character.toUpperCase(...)`). The current `byte[]` path
   lowercases every base (`GFF3Mapper.java:344-346`), so the emitted body is lowercase. To stay
   byte-identical the streaming path must lowercase each streamed character before it reaches the
   writer, via a thin lowercasing `Reader` wrapper (see Detailed Design). This wrapper is O(chunk),
   satisfying NFR-2.

6. **CRC = 0.** The current `byte[]` path constructs `EmblSequenceWriter` with the default `crc = 0`
   (`EmblEntryWriter.writeSequence` default → 2-arg `EmblSequenceWriter(entry, sequence)`;
   `EmblSequenceWriter.java:27-31`), so **no** `CRC32;` field is emitted in the `SQ` line
   (`EmblSequenceWriter.java:92-93`). The streaming path must pass `crc = 0L` so the `SQ` header
   matches byte-for-byte (`EmblSequenceStreamWriter.writeHeader` only emits `CRC32;` when
   `crc != 0`).

# Open Questions

- **OQ-1** Do any production `##sequence-region` directives legitimately describe a sub-range of the
  underlying FASTA (rather than the whole sequence)? If sub-ranges never occur in practice, the
  whole-sequence-only restriction (Decision 2 / FR-7) covers 100% of cases; if they do occur, they
  transparently fall back to the `byte[]` path (still correct, just not memory-optimised). No action
  needed unless sub-range streaming later becomes a requirement.
- **OQ-2** Are the `other`-base semantics fully equivalent between paths for sequences containing
  IUPAC ambiguity codes / gaps? Both paths count only `a/c/g/t` explicitly and lump everything else
  (including `n`) into `other`; the equivalence test (see Testing) must include such a base to prove
  it. Expected to be equivalent; flagged for explicit test coverage rather than assumption.

# System Overview / High-Level Design

```
Gff3ToFFConverter.convert(reader, writer)          [Gff3ToFFConverter.java:47]
  └─ per annotation → writeEntry(mapper, annotation, writer)   [:87]
        ├─ entry = mapper.mapGFF3ToEntry(annotation)           [GFF3Mapper.java:102]
        │     └─ applySequenceData(region, sequence)           [GFF3Mapper.java:325]
        │           ├─ lookup null / seqId unserved → skip (no seq)      [Decision 4]
        │           ├─ whole-sequence region → STREAM:                    [Decision 2]
        │           │     set sequence.setLength(totalBases); NO byte[];
        │           │     stash StreamingSequenceContext(seqId,total,counts)
        │           └─ sub-range region → BYTE[] (current behaviour)      [Decision 2]
        └─ choose writer:
              ├─ streaming context present →
              │     StreamingEmblEntryWriter(entry, ctx, lookup)
              │       ├─ overrides writeSequence():                        [FR-1]
              │       │     try (Reader r = lookup.getSequenceSliceReader(seqId,1,total,WHOLE_SEQUENCE);
              │       │           Reader low = new LowerCaseReader(r)) {    [FR-6 / Decision 5]
              │       │        writeStreamingSequence(w, total, lcCounts, low, 0L); [Decision 6]
              │       │     }
              │       └─ overrides isExpandedEntry() → true                [FR-2]
              └─ else → new EmblEntryWriter(entry)   (current path, unchanged) [Gff3ToFFConverter.java:91]
```

Two new types live in `uk.ac.ebi.embl.gff3tools.gff3toff`:

- **`StreamingEmblEntryWriter`** — extends `EmblEntryWriter`; overrides `writeSequence` and
  `isExpandedEntry`. Holds the seqId + `totalBases` + lowercased base-count map + the
  `SequenceLookup`, opens/closes the `Reader` inside `writeSequence`.
- **`LowerCaseReader`** — a small `java.io.FilterReader` (or equivalent) that lowercases each
  character as it streams (ASCII `A–Z → a–z`), keeping memory O(chunk).

`GFF3Mapper` gains a small per-instance holder (a `StreamingSequenceContext` record, nullable) that
carries the data forward from `applySequenceData` to `writeEntry`. `GFF3Mapper` instances are
created fresh per annotation inside the read loop (`Gff3ToFFConverter.java:52-53`), so per-entry
state on the mapper is safe.

# Detailed Design & Implementation

## `StreamingSequenceContext` (holder)

A package-private record capturing what the writer needs at write time:

```java
record StreamingSequenceContext(String seqId, long totalBases, Map<Character, Long> baseCounts) {}
```

`baseCounts` contains lowercase `a/c/g/t` (values default to 0 when absent). Kept immutable and
small (four entries).

## `GFF3Mapper` changes

- Add a nullable field `StreamingSequenceContext streamingContext` (reset to `null` at the top of
  `mapGFF3ToEntry`, alongside the existing `parentFeatures.clear()` etc. at `GFF3Mapper.java:104-107`)
  and a package-private getter used by the converter.
- Rework `applySequenceData` (`GFF3Mapper.java:325`) so validation lives inside the function
  (per CLAUDE.md "input validation inside functions"):
  1. `if (sequenceLookup == null || sequenceRegion == null)` → keep current skip/log and leave
     `streamingContext == null` (`GFF3Mapper.java:326-331`).
  2. `if (!sequenceLookup can serve seqId)` → skip as today (defensive; today `getSequenceSlice`
     would throw). Use the seqId `sequenceRegion.accessionId()`.
  3. Fetch `SequenceStats stats = sequenceLookup.getSequenceStats(seqId)`; `long totalBases =
     stats.totalBases()`.
  4. **Whole-sequence check (FR-7):** `if (sequenceRegion.start() == 1 && sequenceRegion.end() ==
     totalBases && totalBases > 0)` →
     - build `Map<Character,Long> lc` with `a/c/g/t` from `stats.baseCount()` via
       `Character.toLowerCase` on the keys;
     - `sequence.setLength(totalBases)` (do **not** call `sequence.setSequence(...)`);
     - `this.streamingContext = new StreamingSequenceContext(seqId, totalBases, lc)`;
     - return (no `byte[]`).
  5. **Else (sub-range or degenerate):** fall through to the **existing** `byte[]` materialisation
     (`GFF3Mapper.java:333-352`) unchanged; leave `streamingContext == null`.

  Note: the seqId used for the reader/stats is `sequenceRegion.accessionId()` (the GFF3 seqId, the
  key `SequenceLookup`/`FileSequenceSource` map to an ordinal — `FileSequenceSource.resolveOrdinal`),
  **not** `sequenceRegion.accession()` (which includes the version suffix).

## `StreamingEmblEntryWriter`

```java
final class StreamingEmblEntryWriter extends EmblEntryWriter {
    private final SequenceLookup lookup;
    private final StreamingSequenceContext ctx;

    StreamingEmblEntryWriter(Entry entry, SequenceLookup lookup, StreamingSequenceContext ctx) {
        super(entry);
        this.lookup = lookup;
        this.ctx = ctx;
    }

    @Override
    protected void writeSequence(Writer writer) throws IOException {
        try (Reader raw = lookup.getSequenceSliceReader(
                        ctx.seqId(), 1L, ctx.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE);
                Reader lower = new LowerCaseReader(raw)) {
            writeStreamingSequence(writer, ctx.totalBases(), ctx.baseCounts(), lower, 0L);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(
                    "Failed to stream sequence for '" + ctx.seqId() + "': " + e.getMessage(), e);
        }
    }

    @Override
    protected boolean isExpandedEntry() {
        return true;
    }
}
```

Notes:

- `getSequenceSliceReader` declares `throws Exception`, so the `catch (Exception)` funnels it into an
  `IOException`, which `writeSequence`'s signature already allows and which `Gff3ToFFConverter`
  already maps to `WriteException` (`Gff3ToFFConverter.java:94-95`). This preserves the existing exit
  code contract (WRITE_ERROR) — no new exception types (NFR-4).
- `crc = 0L` (Decision 6). `isExpandedEntry()` returns `true` (FR-2 / Decision — matches the current
  `byte[]` layout where `isExpandedEntry()` is true because the `byte[]` is present).

## `LowerCaseReader`

```java
final class LowerCaseReader extends FilterReader {
    LowerCaseReader(Reader in) { super(in); }

    @Override
    public int read() throws IOException {
        int c = super.read();
        return c == -1 ? -1 : Character.toLowerCase((char) c);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int n = super.read(cbuf, off, len);
        for (int i = 0; i < n; i++) {
            cbuf[off + i] = Character.toLowerCase(cbuf[off + i]);
        }
        return n;
    }
}
```

Operates only on the caller-supplied buffer window; memory is O(chunk) (NFR-2). Lowercasing matches
the per-byte `Character.toLowerCase` used by the current path (`GFF3Mapper.java:345`).

## `Gff3ToFFConverter.writeEntry` changes

`writeEntry` (`Gff3ToFFConverter.java:87-101`) already holds the `mapper` and `annotation`, and the
converter holds `sequenceLookup`. After `mapper.mapGFF3ToEntry(annotation)`:

```java
Entry entry = mapper.mapGFF3ToEntry(annotation);
StreamingSequenceContext ctx = mapper.getStreamingContext();
EmblEntryWriter entryWriter = (ctx != null)
        ? new StreamingEmblEntryWriter(entry, sequenceLookup, ctx)
        : new EmblEntryWriter(entry);
entryWriter.setShowAcStartLine(false);
entryWriter.write(writer);
```

The existing `catch (IOException) → WriteException` and `catch (ValidationException) →
validationEngine.handleSyntacticError` blocks are unchanged (`Gff3ToFFConverter.java:94-99`).

## Byte-identity argument (why NFR-1 holds)

For a whole-sequence region the two paths differ only in *how* the identical bytes are produced:

| Aspect | `byte[]` path | Streaming path | Same? |
|---|---|---|---|
| `SQ` total (`n BP`) | `sequence.getLength()` = byte length | `totalBases` = `stats.totalBases()` | yes (region spans whole seq) |
| a/c/g/t counts | counted from lowercase bytes (`EmblSequenceWriter`) | `stats.baseCount` lowercased | yes (same underlying bases) |
| `other` count | non-acgt bytes | `total-(a+c+g+t)` | yes |
| CRC field | none (`crc=0`) | none (`crc=0`) | yes (Decision 6) |
| body case | lowercased in-memory | lowercased via `LowerCaseReader` | yes (Decision 5) |
| body line wrapping | `EmblSequenceWriter` 60/6×10 layout | `EmblSequenceStreamWriter` 60/6×10 layout | yes (same format) |
| feature-table order | expanded (byte[] present) | `isExpandedEntry()=true` | yes (FR-2) |
| ID line "`n BP`" | from `getLength()` (byte length) | from `getLength()` fallback to `setLength` | yes (`Sequence.java:175-203`) |

The equivalence test (Testing Strategy) turns this argument into an executable guarantee.

# Alternatives Considered

- **Lowercase inside sequencetools' streaming writer.** Rejected: sequencetools is a shared library;
  its writer intentionally writes reader chars verbatim (only protein is uppercased). Case-folding
  is a gff3tools policy (it is what the current gff3tools `byte[]` path does), so it belongs on the
  gff3tools side. A `LowerCaseReader` keeps sequencetools generic and keeps byte-identity a
  gff3tools-owned concern.
- **Infer "streaming" from `byte[] == null && length > 0` in the converter** (no explicit context
  holder). Rejected as too implicit: metadata paths set `entry.setIdLineSequenceLength(...)` /
  `setAnnotationOnlyCON(...)` (`GFF3Mapper.java` WGS block) without a sequence `byte[]`, and relying
  on subtle state coupling is fragile. An explicit `StreamingSequenceContext` keeps the decision in
  one place (`applySequenceData`) and makes the converter's branch obvious.
- **Stream sub-ranges too** (compute per-range base counts by scanning the reader once). Rejected for
  this iteration: it needs a second pass or a counting wrapper and complicates byte-identity; the
  whole-sequence case is the memory-dominant one. Sub-ranges fall back to the correct `byte[]` path
  (Decision 2 / OQ-1).
- **Reuse `getSequenceSlice` (String) then wrap in `StringReader`.** Rejected: it reintroduces the
  full-length `String` allocation, defeating NFR-2.

# Technical Debt / Future Considerations

- The `2.900.0-streaming` `build.gradle` pin (`build.gradle:61-64`) is temporary; revert to `2.+`
  once sequencetools PR #199 is released. Out of scope here.
- Sub-range streaming (OQ-1) remains a possible future optimisation.
- If gff3tools ever needs the EMBL `CRC32;` field, `crc` would have to be computed while streaming
  (e.g. a checksumming wrapper around the reader); currently intentionally `0` to match today's
  output.

# Testing Strategy

Follows repo conventions: JUnit 5, package-private test classes, static assertion imports,
auto-discovered `.gff3`+`.embl` pairs where applicable, TDD (write failing tests first). Tests must
be added because **there are currently zero `gff3toff_rules` fixtures containing an `SQ` block**, so
sequence output is entirely uncovered today.

- **T-1 Equivalence / byte-identity (NFR-1, primary).** A new unit test (e.g.
  `Gff3ToFFStreamingSequenceTest` in `src/test/java/.../gff3toff/`) that, for at least one fixture
  carrying a real whole-sequence region backed by a small FASTA:
  1. runs the conversion with a `SequenceLookup` (via `FileSequenceSource`) and asserts the
     streaming path is taken (byte identity implies it, but also assert no full `byte[]` by checking
     `entry.getSequence().getSequenceByte() == null` on the mapped entry / streaming context present);
  2. runs the same conversion forced through the `byte[]` path and asserts the two output strings are
     **byte-for-byte identical**. Include at least one non-`acgt` base (e.g. `n`) and mixed-case
     input FASTA to exercise Decisions 1, 5 and OQ-2.
- **T-2 New conversion fixture pair.** Add a `.gff3`+`.embl` pair under
  `src/test/resources/gff3toff_rules/` (or a dedicated resource dir if a `--sequence` source is
  required that the existing pair-runner can't supply) whose `.embl` includes a real `SQ` block, so
  the emitted `SQ` header + body are asserted against a golden file. If the shared pair-runner
  `GFF3ToFFConverterTest` cannot pass a `--sequence` source, drive this via a dedicated CLI/unit
  test that supplies `--sequence`.
- **T-3 Fallback paths.** Assert that (a) with `sequenceLookup == null` no `SQ` block is emitted
  (current behaviour, FR-8), and (b) a genuine sub-range `##sequence-region` uses the `byte[]` path
  and still emits correct output (FR-7 / Decision 2).
- **T-4 Reader lifecycle (NFR-3).** With a mock/fake `SequenceLookup` returning a `Reader` whose
  `close()` is observable, assert the reader is closed exactly once after the write (try-with-resources).
- **T-5 Verification commands.** `./gradlew spotlessApply` then `./gradlew spotlessCheck test`
  (add `-Pgitlab_private_token=dummy-token` for local runs) — zero spotless violations, all tests
  green.

# Deployment & Operations

No deployment change. Same shadow JAR, same CLI. Operationally the benefit is lower peak memory for
`gff3toff` conversions of large contigs/chromosomes when a `--sequence` source is supplied; the
documented `-Xmx` workaround becomes unnecessary for the sequence-body portion of the footprint.

# Related Documentation & Resources

- sequencetools PR #199 (streaming Entry write API) — `EmblEntryWriter` / `EmblSequenceStreamWriter`.
- `docs/0001_error_handling.md` — exception hierarchy / exit codes (WRITE_ERROR path reused).
- Code anchors: `Gff3ToFFConverter.java:47,87,91-99`; `GFF3Mapper.java:102,325-352`;
  `SequenceLookup.java` (`getSequenceSliceReader`, `getSequenceStats`);
  `FileSequenceSource.java` (`getSequenceSliceReader`, `getSequenceStats`, `resolveOrdinal`);
  `CompositeSequenceProvider.java`; fastareader `SequenceStats.java:20-27`, `InternalReader.java:89,136`;
  sequencetools `EmblEntryWriter.java:127,144,158,164`, `EmblSequenceStreamWriter.java`,
  `EmblSequenceWriter.java:27-31,92-93`, `Sequence.java:175-215`.

---

# Delivery Plan (phased)

## Phase 1 — Streaming write path (writer + mapper + converter wiring)

Introduce `StreamingSequenceContext`, `LowerCaseReader`, and `StreamingEmblEntryWriter` in
`gff3toff`; rework `GFF3Mapper.applySequenceData` to choose stream-vs-`byte[]` and stash the context
(FR-1..FR-3, FR-5..FR-8, Decisions 1–6); wire `Gff3ToFFConverter.writeEntry` to pick the writer and
own the reader lifecycle (FR-4, NFR-3). Includes the byte-identity equivalence test (T-1), fallback
tests (T-3), and reader-lifecycle test (T-4) — TDD: write T-1 first against a small FASTA fixture.

**Scope boundaries:** no `fftogff3` changes; no CLI/option changes; no new exceptions/exit codes;
sub-ranges keep the existing `byte[]` path (no new sub-range streaming).

**Acceptance:** streaming path produces byte-identical EMBL to the `byte[]` path for the T-1 fixture;
`entry.getSequence().getSequenceByte()` is `null` on the streaming path; reader closed exactly once;
`./gradlew spotlessCheck test` green.

## Phase 2 — Golden `SQ` fixture coverage

Add the `.gff3`+`.embl` fixture pair (T-2) carrying a real `SQ` block (including a non-`acgt` base and
mixed-case input) so the emitted `SQ` header + body are pinned against a golden file, wired through
whichever runner can supply a `--sequence` source.

**Scope boundaries:** test resources + minimal test harness only; no production-code changes beyond
Phase 1.

**Acceptance:** new fixture(s) run and pass; `./gradlew spotlessCheck test` green.

## Phases (JSON)

```json
{
  "phases": [
    {
      "phase": 1,
      "focus": "Streaming write path",
      "effort": "M",
      "difficulty": "hard",
      "id": 1,
      "name": "Streaming write path (writer + mapper + converter wiring)",
      "description": "Add StreamingSequenceContext, LowerCaseReader, and StreamingEmblEntryWriter in the gff3toff package. Rework GFF3Mapper.applySequenceData to select streaming vs byte[] (whole-sequence only), set sequence.setLength(totalBases) without materialising the byte[], build lowercase a/c/g/t base counts, and stash a StreamingSequenceContext. Wire Gff3ToFFConverter.writeEntry to pick StreamingEmblEntryWriter when a context is present, opening/closing the Reader via try-with-resources with crc=0 and isExpandedEntry()=true. TDD: write the byte-identity equivalence test first.",
      "requirements": ["FR-1","FR-2","FR-3","FR-4","FR-5","FR-6","FR-7","FR-8","NFR-1","NFR-2","NFR-3","NFR-4"],
      "decisions": [1,2,3,4,5,6],
      "changes": [
        "src/main/java/uk/ac/ebi/embl/gff3tools/gff3toff/StreamingSequenceContext.java (new)",
        "src/main/java/uk/ac/ebi/embl/gff3tools/gff3toff/LowerCaseReader.java (new)",
        "src/main/java/uk/ac/ebi/embl/gff3tools/gff3toff/StreamingEmblEntryWriter.java (new)",
        "src/main/java/uk/ac/ebi/embl/gff3tools/gff3toff/GFF3Mapper.java (applySequenceData rework + streamingContext field/getter, reset in mapGFF3ToEntry)",
        "src/main/java/uk/ac/ebi/embl/gff3tools/gff3toff/Gff3ToFFConverter.java (writeEntry writer selection)",
        "src/test/java/uk/ac/ebi/embl/gff3tools/gff3toff/Gff3ToFFStreamingSequenceTest.java (new: T-1 equivalence, T-3 fallbacks, T-4 reader lifecycle)"
      ],
      "scopeBoundaries": [
        "No changes to the fftogff3 direction",
        "No CLI/option/exit-code/exception changes",
        "Sub-range ##sequence-region keeps the existing byte[] path (no sub-range streaming)",
        "Do not modify the 2.900.0-streaming build.gradle pin"
      ],
      "acceptance": [
        "Streaming path output is byte-for-byte identical to the byte[] path for a whole-sequence FASTA-backed fixture (including a non-acgt base and mixed-case input)",
        "On the streaming path entry.getSequence().getSequenceByte() is null and sequence.getLength() equals totalBases",
        "The Reader from getSequenceSliceReader is closed exactly once after the write",
        "With sequenceLookup==null no SQ block is emitted; a sub-range region uses the byte[] path",
        "./gradlew spotlessCheck test passes with zero spotless violations"
      ],
      "dependsOn": []
    },
    {
      "phase": 2,
      "focus": "Golden SQ fixture coverage",
      "effort": "S",
      "difficulty": "standard",
      "id": 2,
      "name": "Golden SQ fixture coverage",
      "description": "Add a .gff3+.embl fixture pair carrying a real SQ block (including a non-acgt base and mixed-case input FASTA) plus a --sequence-backed harness so the emitted SQ header and body are asserted against a golden EMBL file. Closes the current gap where zero gff3toff_rules fixtures contain an SQ block.",
      "requirements": ["NFR-1"],
      "decisions": [1,2,5,6],
      "changes": [
        "src/test/resources/gff3toff_rules/<new_pair>.gff3 (new)",
        "src/test/resources/gff3toff_rules/<new_pair>.embl (new golden)",
        "src/test/resources/<new fasta source>.fasta (new, if required by the harness)",
        "src/test/java/uk/ac/ebi/embl/gff3tools/gff3toff/ (test harness able to supply a --sequence source, if the shared pair-runner cannot)"
      ],
      "scopeBoundaries": [
        "Test resources and minimal test harness only",
        "No production-code changes beyond Phase 1"
      ],
      "acceptance": [
        "New fixture(s) are discovered/run and pass, asserting the SQ header and body against the golden file",
        "./gradlew spotlessCheck test passes"
      ],
      "dependsOn": [1]
    }
  ]
}
```
