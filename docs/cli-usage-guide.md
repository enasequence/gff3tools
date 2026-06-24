# CLI Usage Guide

This guide covers all gff3tools subcommands with practical examples.

## Prerequisites

Build the shadow JAR first:

```bash
./gradlew shadowJar
```

The runnable JAR is written to `build/libs/gff3tools-<version>-all.jar`. The examples below
use the shell variable `$GFF3TOOLS` for brevity:

```bash
GFF3TOOLS="java -jar build/libs/gff3tools-*-all.jar"
```

Run without arguments to see the top-level help:

```bash
$GFF3TOOLS help
```

Add `help` after a subcommand name to see its options:

```bash
$GFF3TOOLS help conversion
$GFF3TOOLS help validation
$GFF3TOOLS help translate
```

---

## `conversion` — format conversions

Converts between EMBL flat file (`.embl`), GFF3 (`.gff3`), FASTA (`.fasta`), and TSV (`.tsv`).

### EMBL → GFF3

```bash
# Auto-detected from file extensions
$GFF3TOOLS conversion OZ026791.embl OZ026791.gff3

# Explicit format flags (required when the extension is non-standard)
$GFF3TOOLS conversion -f embl -t gff3 annotation.ff annotation.gff3
```

### GFF3 → EMBL

```bash
$GFF3TOOLS conversion OZ026791.gff3 OZ026791.embl
```

### FASTA → GFF3

The input FASTA must have sequence headers (plain sequences without `>` headers are not
supported because the header provides the sequence ID used in the GFF3 `seqid` column).

```bash
# Basic — gap features use the default minimum N-run length (10)
$GFF3TOOLS conversion genome.fasta genome.gff3

# Custom minimum gap length
$GFF3TOOLS conversion --min-gap-length 50 genome.fasta genome.gff3

# Annotate gaps as assembly_gap with a gap_type that does not require linkage evidence
$GFF3TOOLS conversion --gap-type "between scaffolds" genome.fasta genome.gff3

# Gap type that requires linkage evidence (within scaffold, repeat within scaffold, contamination)
$GFF3TOOLS conversion \
  --gap-type "within scaffold" \
  --linkage-evidence "paired-ends" \
  genome.fasta genome.gff3
```

### TSV → GFF3

```bash
# GFF3 output only
$GFF3TOOLS conversion annotation.tsv annotation.gff3

# GFF3 output + nucleotide sequences written to a separate FASTA file
$GFF3TOOLS conversion --output-sequence sequences.fasta annotation.tsv annotation.gff3
```

### Pipes (stdin / stdout)

When no output file is given, gff3tools writes converted data to stdout. Informational
log messages are suppressed to keep stdout clean; warnings and errors still go to stderr.

```bash
# GFF3 → EMBL via pipe
cat OZ026791.gff3 | $GFF3TOOLS conversion -f gff3 -t embl > OZ026791.embl

# EMBL → GFF3 in a pipeline
$GFF3TOOLS conversion -f embl -t gff3 OZ026791.embl | gzip > OZ026791.gff3.gz
```

### Gzip-compressed input

gff3tools detects `.gz` files automatically; no extra flag is needed:

```bash
$GFF3TOOLS conversion OZ026791.embl.gz OZ026791.gff3
```

### Master entry (`--master-entry` / `-m`)

A master entry file provides metadata (organism, topology, molecule type, …) that is
merged into each converted entry. Accepted formats: MasterEntry JSON (`.json`) or EMBL
flat file (`.embl` / `.ff`).

```bash
# JSON master entry
$GFF3TOOLS conversion -m master.json OZ026791.embl OZ026791.gff3

# EMBL flat file master entry
$GFF3TOOLS conversion -m master.embl OZ026791.embl OZ026791.gff3
```

### Sequence sources (`--sequence`)

Sequence data is required for validations that check nucleotide content (e.g. translation
checks). The `--sequence` option is repeatable.

```bash
# Single FASTA file — sequence IDs are taken from the FASTA headers
$GFF3TOOLS conversion --sequence sequences.fasta OZ026791.embl OZ026791.gff3

# Plain sequence file — key maps to GFF3 seqId (no / or \ in the key)
$GFF3TOOLS conversion --sequence "chr1:chr1.seq" OZ026791.embl OZ026791.gff3

# Multiple FASTA files
$GFF3TOOLS conversion \
  --sequence chr1.fasta \
  --sequence chr2.fasta \
  OZ026791.embl OZ026791.gff3

# Override format inference when the extension is non-standard
$GFF3TOOLS conversion --sequence sequences.txt --sequence-format fasta \
  OZ026791.embl OZ026791.gff3
```

---

## `validation` — validate a GFF3 file

Reads a GFF3 file, runs all active validation rules, and reports warnings and errors.
Exits with code `20` if any rule configured as `ERROR` is violated.

```bash
# Validate a file
$GFF3TOOLS validation annotation.gff3

# Validate from stdin
cat annotation.gff3 | $GFF3TOOLS validation

# With sequence data (enables nucleotide-level checks)
$GFF3TOOLS validation --sequence sequences.fasta annotation.gff3

# Stop on the first error instead of collecting all errors
$GFF3TOOLS validation --fail-fast annotation.gff3
```

---

## `translate` — translate CDS features to protein sequences

Reads a GFF3 file and translates CDS features using the provided nucleotide sequences.
Requires at least one `--sequence` source.

### Output modes

| Mode | Flag | Output |
|------|------|--------|
| GFF3 + FASTA (default) | `--translation-mode gff3-fasta` | `<stem>.translated.gff3` containing the original GFF3 plus a `##FASTA` section with protein translations |
| FASTA only | `--translation-mode fasta` | `<stem>.translation.fasta` |
| Attribute only | `--translation-mode attribute` | Translations are added as in-memory attributes; no file is written |

```bash
# Default: GFF3 with embedded FASTA translations
$GFF3TOOLS translate --sequence sequences.fasta annotation.gff3
# Output: annotation.translated.gff3

# Protein FASTA only
$GFF3TOOLS translate \
  --translation-mode fasta \
  --sequence sequences.fasta \
  annotation.gff3
# Output: annotation.translation.fasta

# Explicit output path
$GFF3TOOLS translate \
  --translation-mode fasta \
  --sequence sequences.fasta \
  -o proteins.fasta \
  annotation.gff3
```

---

## `process` — process GFF3 and FASTA files

Validates and processes a GFF3 file together with a FASTA sequence file for a set of
accessions. All three inputs are required.

```bash
$GFF3TOOLS process \
  -accessions ACC001,ACC002,ACC003 \
  -gff3 annotation.gff3 \
  -fasta sequences.fasta \
  -o processed.gff3
```

---

## Common options (all commands)

### `--fail-fast`

Stop processing at the first error instead of collecting all errors before exiting.
Useful when running in pipelines where you want to fail early.

```bash
$GFF3TOOLS conversion --fail-fast OZ026791.embl OZ026791.gff3
$GFF3TOOLS validation --fail-fast annotation.gff3
```

### `--rules` — override validation rule severities

Configure individual rule severities as `RULE_NAME:SEVERITY` pairs (case-insensitive),
separated by commas.

Available severities: `OFF`, `WARN`, `ERROR`

Available rules:

| Rule | Default | Description |
|------|---------|-------------|
| `FLATFILE_NO_SOURCE` | `WARN` | The flat file contains no source feature |
| `FLATFILE_NO_ONTOLOGY_FEATURE` | `WARN` | The flat file feature does not exist in the ontology |
| `GFF3_INVALID_RECORD` | `ERROR` | The record does not conform with GFF3 format |
| `GFF3_INVALID_HEADER` | `ERROR` | Invalid GFF3 header |

```bash
# Treat missing source as an error; silence ontology warnings
$GFF3TOOLS conversion \
  --rules FLATFILE_NO_SOURCE:ERROR,FLATFILE_NO_ONTOLOGY_FEATURE:OFF \
  OZ026791.embl OZ026791.gff3

# Downgrade an error to a warning during development
$GFF3TOOLS validation \
  --rules GFF3_INVALID_RECORD:WARN \
  annotation.gff3
```

---

## Memory

For large files, increase the JVM heap with `-Xmx`:

```bash
java -Xmx4G -jar gff3tools-*-all.jar conversion large_genome.embl large_genome.gff3
```

gff3tools will print a helpful message if it runs out of memory and exit with code `30`.
