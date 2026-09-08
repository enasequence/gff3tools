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

### `--fixes` — toggle individual auto-fixes

Configure individual auto-fixes as `FIX_NAME:ON` or `FIX_NAME:OFF` pairs (case-insensitive),
separated by commas. Unlike `--rules`, which only affects how a violation is reported, a fix
actually mutates the feature or annotation being converted or validated.

Some subcommands force a specific fix off or on regardless of `--fixes`, because that
subcommand's own output would make the fix meaningless or wrong — for example `GAP_GENERATION`
is always off for `validation` (which discards its output) and for `conversion` in every
direction except FASTA → GFF3 (the only direction that should synthesize new features). Those
structural overrides always take precedence over `--fixes`.

`FIX_NAME:OFF` only ever disables that one fix method, never a whole class or an unrelated
validation rule that happens to share the same name (e.g. `ATTRIBUTES_VALUE` and
`CHROMOSOME_NAME` are also validation rule names). Class-level re-enabling via `FIX_NAME:ON` is
only needed for, and only applies to, the two fixes that are disabled by default:
`PROTEIN_ID_REMOVE` and `TRANSFORM_EXCLUSIVE_ATTRIBUTE_TO_NOTE`. Every other fix is already
enabled by default, so toggling it on or off only ever affects that specific fix method.

Available fixes:

| Fix | Default | Description |
|------|---------|-------------|
| `ATTRIBUTES_VALUE` | `ON` | Change the attribute value of mod_base. Refer: Modified base abbreviations |
| `CDS_RNA_LOCUS` | `ON` | Transfers gene, gene_synonym, and locus_tag attributes from gene features to their corresponding CDS, rRNA, and tRNA child features based on location overlap |
| `CHROMOSOME_NAME` | `ON` | Normalises the chromosome_name of the FASTA header registered for the annotation's accession |
| `EC_NUMBER` | `ON` | Remove invalid EC_NUMBER values |
| `FASTA_HEADER_VALUE_NORMALISATION` | `ON` | Folds FASTA header values to ASCII7 and normalises controlled vocabulary fields to their canonical form |
| `GAP_ESTIMATED_LENGTH` | `ON` | Set estimated_length for a gap feature |
| `GAP_GENERATION` | `ON` | Add gap features for runs of N bases that no existing gap feature covers (forced off outside FASTA → GFF3 conversion) |
| `GENE_ASSOCIATED_FEATURE_REMOVAL` | `ON` | Removes gene features entry if locations are identical with gene associated features (CDS, rRNA, tRNA) |
| `LOCUS_TAG_ADD_TO_FEATURES_SHARING_THE_GENE` | `ON` | Adds locus tag attribute to the features with the gene attribute, considering first-seen pair as the correct one |
| `LOCUS_TAG_TO_UPPERCASE` | `ON` | Update the locus_tag value to upper case |
| `PRODUCT_WITH_EC_NUMBER` | `ON` | Derive EC_NUMBER from PRODUCT and clean PRODUCT value |
| `PROTEIN_ID_REMOVE` | `OFF` | Removes the protein ID from feature |
| `PUSHING_GENE_SYNONYM_ATTRIBUTE_TO_PARENT_FEATURES_ONLY` | `ON` | Pushes gene_synonym attribute to only persist at a parent level |
| `REMOVE_ATTRIBUTES` | `ON` | Remove attributes citation & compare from old_sequence feature |
| `REMOVE_ATTRIBUTES_DUPLICATE_VALUE` | `ON` | Remove the duplicate values in the old_locus_tag and locus_tag |
| `REMOVE_PSEUDOGENE_QUOTE` | `ON` | Remove single quotes from Pseudogene value |
| `REMOVE_TRANSLATION_ATTRIBUTE` | `ON` | Capture existing translation attribute into TranslationState and remove it from the feature |
| `RENAME_ATTRIBUTES` | `ON` | Moves 'label' into 'Note' and renames 'mobile_element' to 'mobile_element_type' |
| `TRANSFORM_EXCLUSIVE_ATTRIBUTE_TO_NOTE` | `OFF` | Moves the value of one of the mutually exclusive feature attributes to the note attribute |
| `TRANSLATION` | `ON` | Translate CDS features and set the translation attribute |

```bash
# Strip protein_id from every feature during conversion (off by default)
$GFF3TOOLS conversion \
  --fixes PROTEIN_ID_REMOVE:ON \
  OZ026791.embl OZ026791.gff3

# Disable locus_tag upper-casing while iterating on a submission
$GFF3TOOLS conversion \
  --fixes LOCUS_TAG_TO_UPPERCASE:OFF \
  OZ026791.embl OZ026791.gff3
```

---

## Memory

For large files, increase the JVM heap with `-Xmx`:

```bash
java -Xmx4G -jar gff3tools-*-all.jar conversion large_genome.embl large_genome.gff3
```

gff3tools will print a helpful message if it runs out of memory and exit with code `30`.
