# Process Command User Guide

This guide provides detailed information about using the `process` command and its sub-commands.

## Table of Contents
- [Overview](#overview)
- [Sub-commands](#sub-commands)
  - [count-regions](#count-regions)
  - [replace-ids](#replace-ids)
- [Common Workflows](#common-workflows)
- [Error Messages](#error-messages)
- [Troubleshooting](#troubleshooting)
- [Performance Considerations](#performance-considerations)

## Overview

The `process` command provides utilities for working with GFF3 files. It includes sub-commands for:
- Counting sequence regions
- Replacing sequence region identifiers

All sub-commands support both file I/O and Unix pipes (stdin/stdout).

## Sub-commands

### count-regions

Counts the number of `##sequence-region` directives in a GFF3 file.

**Use Case:** Determine how many external accessions you need before running `replace-ids`.

**Syntax:**
```bash
java -jar gff3tools-1.0-all.jar process count-regions [input-file]
```

**Arguments:**
- `[input-file]`: Optional. Path to GFF3 file. If omitted, reads from stdin.

**Output:** Single integer to stdout.

**Examples:**

Basic usage:
```bash
$ java -jar gff3tools-1.0-all.jar process count-regions sample.gff3
5
```

From stdin:
```bash
$ cat sample.gff3 | java -jar gff3tools-1.0-all.jar process count-regions
5
```

Store result in variable:
```bash
$ COUNT=$(java -jar gff3tools-1.0-all.jar process count-regions sample.gff3)
$ echo "Need $COUNT accessions"
Need 5 accessions
```

**What It Counts:**
- Only `##sequence-region` directives in the header/annotation section
- Stops counting at `##FASTA` directive (if present)
- Ignores blank lines and comments
- Does not count feature lines

**Performance:**
Uses streaming, so it's memory-efficient even for very large files.

---

### replace-ids

Replaces all sequence region identifiers throughout a GFF3 file with externally provided accessions.

**Use Case:** Synchronize GFF3 sequence regions with external accession identifiers (e.g., from ENA submission).

**Syntax:**
```bash
java -jar gff3tools-1.0-all.jar process replace-ids \
    --accessions ACC1,ACC2,... \
    [input-file] \
    [output-file]
```

**Required Arguments:**
- `--accessions`: Comma-separated list of replacement accessions

**Optional Arguments:**
- `[input-file]`: Input GFF3 file. If omitted, reads from stdin.
- `[output-file]`: Output GFF3 file. If omitted, writes to stdout.

**What Gets Replaced:**
- `##sequence-region` directives: The accession portion
- Feature seqid column (column 1): All references to the original sequence region ID

**What Stays the Same:**
- FASTA section (copied unchanged, including headers)
- All other columns in feature lines
- All attributes and qualifiers
- Feature IDs and Parent relationships

**Mapping Rules:**
Accessions are mapped **sequentially** in the order sequence regions appear in the file:
1. First `##sequence-region` → First accession in list
2. Second `##sequence-region` → Second accession in list
3. And so on...

This is **NOT** alphabetical or any other ordering.

**Version Number Handling:**
- Original IDs may have versions: `BN000065.1`
- Replacement accessions should be versionless: `ACC123`
- Result: All `BN000065.1` references become `ACC123` (version removed)

**Examples:**

Basic file replacement:
```bash
$ java -jar gff3tools-1.0-all.jar process replace-ids \
    --accessions ACC001,ACC002,ACC003 \
    input.gff3 \
    output.gff3
```

Using stdin/stdout:
```bash
$ cat input.gff3 | java -jar gff3tools-1.0-all.jar process replace-ids \
    --accessions ACC001,ACC002,ACC003 \
    > output.gff3
```

Whitespace in accession list (automatically trimmed):
```bash
$ java -jar gff3tools-1.0-all.jar process replace-ids \
    --accessions "ACC001, ACC002, ACC003" \
    input.gff3 \
    output.gff3
```

---

## Common Workflows

### Workflow 1: Count and Replace

```bash
#!/bin/bash

INPUT="sample.gff3"
OUTPUT="updated.gff3"

# Step 1: Count sequence regions
COUNT=$(java -jar gff3tools-1.0-all.jar process count-regions "$INPUT")
echo "Found $COUNT sequence regions"

# Step 2: Check if we have the right number of accessions
# (In real use, you'd fetch these from an external system)
ACCESSIONS="ACC001,ACC002,ACC003"
PROVIDED=$(echo "$ACCESSIONS" | tr ',' '\n' | wc -l)

if [ "$COUNT" -ne "$PROVIDED" ]; then
    echo "ERROR: Need $COUNT accessions, but provided $PROVIDED"
    exit 1
fi

# Step 3: Perform replacement
java -jar gff3tools-1.0-all.jar process replace-ids \
    --accessions "$ACCESSIONS" \
    "$INPUT" \
    "$OUTPUT"

echo "Replacement complete: $OUTPUT"
```

### Workflow 2: Validate Before Replacement

```bash
#!/bin/bash

INPUT="sample.gff3"
OUTPUT="updated.gff3"
ACCESSIONS="ACC001,ACC002,ACC003"

# Validate GFF3 format first
java -jar gff3tools-1.0-all.jar validation "$INPUT"
if [ $? -ne 0 ]; then
    echo "ERROR: Invalid GFF3 file"
    exit 1
fi

# Count and replace
COUNT=$(java -jar gff3tools-1.0-all.jar process count-regions "$INPUT")
PROVIDED=$(echo "$ACCESSIONS" | tr ',' '\n' | wc -l)

if [ "$COUNT" -eq "$PROVIDED" ]; then
    java -jar gff3tools-1.0-all.jar process replace-ids \
        --accessions "$ACCESSIONS" \
        "$INPUT" \
        "$OUTPUT"
    echo "Success!"
else
    echo "ERROR: Count mismatch ($COUNT vs $PROVIDED)"
    exit 1
fi
```

### Workflow 3: Batch Processing

```bash
#!/bin/bash

# Process multiple files
for FILE in *.gff3; do
    echo "Processing $FILE..."
    
    COUNT=$(java -jar gff3tools-1.0-all.jar process count-regions "$FILE")
    echo "  Found $COUNT regions"
    
    # Fetch accessions from external system (example)
    # ACCESSIONS=$(fetch_accessions_from_api "$FILE" "$COUNT")
    
    # For this example, generate dummy accessions
    ACCESSIONS=$(seq -f "ACC%03g" 1 "$COUNT" | paste -sd,)
    
    OUTPUT="${FILE%.gff3}_updated.gff3"
    java -jar gff3tools-1.0-all.jar process replace-ids \
        --accessions "$ACCESSIONS" \
        "$FILE" \
        "$OUTPUT"
    
    echo "  Created $OUTPUT"
done
```

---

## Error Messages

### "Accession count mismatch"

**Full Message:**
```
Expected 3 sequence regions but received 2 accessions
```

**Cause:** The number of accessions provided doesn't match the number of sequence regions in the file.

**Solution:**
1. Run `count-regions` to verify the correct count
2. Provide exactly that many accessions

**Example:**
```bash
# Get correct count
$ java -jar gff3tools-1.0-all.jar process count-regions file.gff3
3

# Provide 3 accessions
$ java -jar gff3tools-1.0-all.jar process replace-ids \
    --accessions ACC1,ACC2,ACC3 \
    file.gff3 output.gff3
```

---

### "Accession is empty or blank"

**Full Message:**
```
Accession at position 2 is empty or blank
```

**Cause:** One or more accessions in the list is empty or contains only whitespace.

**Solution:** Ensure all accessions have non-empty values.

**Common Mistakes:**
```bash
# Wrong: trailing comma creates empty accession
--accessions "ACC1,ACC2,"

# Wrong: double comma creates empty accession
--accessions "ACC1,,ACC2"

# Correct:
--accessions "ACC1,ACC2"
```

---

### "Invalid GFF3 file"

**Full Message:**
```
Invalid GFF3 file: expected ##gff-version directive at line 1
```

**Cause:** The input file is not a valid GFF3 file.

**Solution:**
1. Verify the file starts with `##gff-version 3`
2. Check for corruption or incorrect file format
3. Run the `validation` command for detailed errors:
   ```bash
   java -jar gff3tools-1.0-all.jar validation file.gff3
   ```

---

## Troubleshooting

### My FASTA headers contain sequence region IDs

**Question:** The FASTA section has headers like `>BN000065.1`. Will these be replaced?

**Answer:** No. Per the specification, FASTA headers are **not** modified. This is intentional because:
- FASTA sequences in GFF3 typically represent translations/products
- They reference feature IDs (like `>cds1`), not sequence regions
- Changing FASTA headers without changing sequences would break sequence identity

If you need to update FASTA headers, you'll need to do that separately.

---

### I get "too many" or "too few" accessions error

**Diagnosis:**
```bash
# Check actual count in file
$ java -jar gff3tools-1.0-all.jar process count-regions myfile.gff3
5

# Check what you're providing
$ echo "ACC1,ACC2,ACC3" | tr ',' '\n' | wc -l
3
```

**Solution:** Match the counts. In this example, you need 5 accessions, not 3.

---

### Replacement seems to work but output looks wrong

**Checklist:**
1. ✅ Are you counting the right file (same file you're replacing)?
2. ✅ Are accessions in the right order?
3. ✅ Did you check for hidden whitespace in the accession list?
4. ✅ Did the command exit with code 0?

**Debug:**
```bash
# Verify replacement worked
$ grep "##sequence-region" output.gff3
##sequence-region ACC001 1 5000
##sequence-region ACC002 1 3000

# Check feature lines
$ grep -v "^#" output.gff3 | head -5
ACC001  ENA  gene  100  500  .  +  .  ID=gene1
ACC001  ENA  CDS   100  500  .  +  0  ID=cds1;Parent=gene1
...
```

---

### Can I use the same accession twice?

**Answer:** The tool doesn't validate uniqueness. You can provide duplicate accessions, but this is **not recommended** as it will make multiple sequence regions indistinguishable.

**Example (not recommended):**
```bash
# This works but creates ambiguity
--accessions "ACC001,ACC001,ACC002"
```

---

## Performance Considerations

### Large Files

Both sub-commands use **streaming** to minimize memory usage:
- `count-regions`: Processes line-by-line, doesn't load full file into memory
- `replace-ids`: Two-pass streaming (one to build map, one to replace)

**Tested with:**
- 10,000 sequence regions: < 5 seconds for counting
- 1,000 sequence regions with 5,000 features: < 10 seconds for replacement

### Very Large Accession Lists

If you have hundreds or thousands of sequence regions, consider:
- Using stdin/stdout to avoid creating intermediate files
- Scripting the workflow to validate counts first
- Monitoring disk space (output file will be similar size to input)

### Memory Usage

Expected memory usage:
- `count-regions`: Minimal (< 100MB for any file size)
- `replace-ids`: Proportional to number of sequence regions (not file size)
  - 1,000 regions: ~10MB
  - 10,000 regions: ~50MB

---

## Advanced Topics

### Idempotency

Running `replace-ids` multiple times with the **same** accessions produces the same result:

```bash
# First run
$ java -jar gff3tools-1.0-all.jar process replace-ids \
    --accessions ACC1,ACC2 \
    input.gff3 output1.gff3

# Second run on the output
$ java -jar gff3tools-1.0-all.jar process replace-ids \
    --accessions ACC1,ACC2 \
    output1.gff3 output2.gff3

# output1.gff3 and output2.gff3 will be identical
```

However, using **different** accessions will apply the new mapping:

```bash
$ java -jar gff3tools-1.0-all.jar process replace-ids \
    --accessions NEW1,NEW2 \
    output1.gff3 output3.gff3

# output3.gff3 will have NEW1, NEW2 (not ACC1, ACC2)
```

---

### Character Encoding

GFF3 files use **UTF-8** encoding. The tool preserves this encoding during replacement.

Special characters that are URL-encoded in attributes (e.g., `%3D` for `=`) are preserved as-is.

---

### Exit Codes

See the main README for complete exit code documentation.

**Quick reference for process commands:**
- `0`: Success
- `2`: Usage error (wrong arguments, count mismatch, empty accession)
- `10`: Read error (can't read input file)
- `11`: Write error (can't write output file)
- `12`: File doesn't exist
- `20`: Validation error (invalid GFF3 format)

**Check exit code in shell:**
```bash
$ java -jar gff3tools-1.0-all.jar process replace-ids ...
$ echo $?
0
```
