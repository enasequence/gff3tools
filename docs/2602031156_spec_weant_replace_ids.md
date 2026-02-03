# Technical Specification: GFF3 Sequence Region ID Replacement

- **Status**: Draft
- **Created**: 2026-02-03
- **Last Updated**: 2026-02-03

---

## I. Requirements

### Problem Statement

**Business Context:**
GFF3 files generated or processed by gff3tools often need to be synchronized with external data sources that provide specific accession identifiers. When submitting genomic data to repositories like ENA, or when integrating data from multiple sources, sequence regions must be identified by specific, stable accessions rather than the original identifiers in the source files.

**Current State:**
Currently, gff3tools can convert between EMBL flat files and GFF3 format, and can validate GFF3 files, but it lacks the ability to replace sequence region identifiers with externally provided accessions. Users must manually edit GFF3 files or write custom scripts to perform this ID replacement, which is error-prone and time-consuming, especially for files with multiple sequence regions.

**Key Issues:**
1. No automated way to replace sequence region IDs in GFF3 files
2. Manual replacement is error-prone and doesn't maintain referential integrity
3. Users cannot easily determine how many accessions are needed before attempting replacement
4. The replacement process must be memory-efficient for large files
5. No validation that the number of provided accessions matches the number of sequence regions

### Requirements

**R1: Count Sequence Regions**
The tool must provide a command to count the number of sequence regions in a GFF3 file without loading the entire file into memory. This count represents the number of external accessions required for ID replacement.

**R2: Replace Sequence Region IDs**
The tool must replace sequence region identifiers throughout a GFF3 file with externally provided accessions while maintaining referential integrity across all features.

**R3: Sequential Mapping**
Accessions must be mapped to sequence regions in the order they appear in the file: the first provided accession replaces the first `##sequence-region` directive found, the second accession replaces the second sequence region, and so on.

**R4: Update All References**
When a sequence region ID is replaced, the replacement must occur in:
- The `##sequence-region` directive itself
- The first column (seqid) of all GFF3 feature lines that reference that sequence region
- Any other directives or locations that reference the sequence region ID

**R5: Preserve FASTA Section**
The FASTA section (after `##FASTA` directive) must be copied to output unchanged.

**Assumption**: FASTA headers in gff3tools-generated files reference feature IDs (translations, proteins, CDSs) rather than sequence region IDs. If FASTA headers do contain sequence region IDs, they will NOT be replaced.

**Rationale**: FASTA sequences represent translations/products, not the genomic sequence regions themselves. Replacing IDs in FASTA headers could break sequence identity without corresponding sequence changes.

**R6: Version Number Handling**
The tool must support sequence regions both with and without version numbers (e.g., `BN000065.1` or `BN000065`). External accessions will be provided without version numbers. When replacing, the tool must use the provided accession as-is, regardless of whether the original sequence region had a version number.

**Example**:
- Original sequence region: `BN000065.1`
- Provided accession: `ACC123`
- Result: All references to `BN000065.1` become `ACC123`
- The `.1` version suffix is removed, not preserved

**R7: Strict Count Validation**
The tool must validate that the number of provided accessions exactly matches the number of sequence regions in the file. If counts don't match, the tool must exit with code 2 (USAGE) and display an error message showing expected vs. provided counts. No output file should be generated.

**R8: Stdin/Stdout Support**
Both sub-commands must support reading from stdin and writing to stdout to enable Unix pipes and scripting workflows, following the same patterns as the existing `conversion` command.

**R9: Non-Empty Accession Validation**
The tool must validate that all provided accessions are non-empty strings. Additional format validation (e.g., alphanumeric patterns, length constraints) is explicitly out of scope for the initial implementation.

**R9b: Accession Input Format**
Accessions are provided via the `--accessions` flag as a comma-delimited string.
- Whitespace before/after commas is trimmed
- Accessions themselves must not contain commas
- Individual accessions cannot be empty or consist only of whitespace
- For files with many sequence regions, consider adding `--accessions-file` option (marked as future enhancement, out of scope for this spec)

**R10: Logging Behavior**
The tool must follow existing logging conventions:
- Log individual replacements and a summary when outputting to files
- Suppress informational logs when outputting to stdout (only show warnings and errors to stderr)
- Set log level to ERROR when writing to stdout to prevent log contamination

**R11: Input Validation**
Both sub-commands must validate that the input is a valid GFF3 file. If the file is malformed or not GFF3 format, exit with error code 20 (VALIDATION_ERROR) with a clear error message indicating the problem line/location.

**R12: Idempotency**
If replace-ids is run multiple times with the same accessions on the same file, the result should be the same. However, running it twice with different accessions will use the second set, not attempt to map back to originals.

**R13: Error Recovery**
If replacement fails mid-stream (e.g., I/O error), the output file may be partially written. Users should verify success via exit code before using output. The tool makes no guarantees about partial output state on failure.

### Success Criteria

- [ ] `java -jar gff3tools.jar process count-regions file.gff3` outputs a single integer representing the number of sequence regions
- [ ] `cat file.gff3 | java -jar gff3tools.jar process count-regions` reads from stdin and outputs the count
- [ ] `java -jar gff3tools.jar process replace-ids --accessions ACC1,ACC2 file.gff3 output.gff3` successfully replaces two sequence region IDs
- [ ] All features referencing the old sequence region ID now reference the new accession in column 1
- [ ] `##sequence-region` directives are updated with new accessions
- [ ] `cat file.gff3 | java -jar gff3tools.jar process replace-ids --accessions ACC1,ACC2 > output.gff3` works via stdin/stdout
- [ ] Tool exits with error code 2 (USAGE) when fewer accessions than sequence regions
- [ ] Tool exits with error code 2 (USAGE) when more accessions than sequence regions
- [ ] Error message clearly states: expected count vs. provided count
- [ ] No output file is created when validation fails (R7)
- [ ] Tool exits with error code 20 (VALIDATION_ERROR) when input file is not valid GFF3
- [ ] FASTA section remains unchanged after ID replacement
- [ ] Sequence regions with versions (e.g., `BN000065.1`) are replaced with versionless accessions (e.g., `ACC1`)
- [ ] Empty or blank accessions are rejected with a clear error message
- [ ] Whitespace around commas in accession list is properly trimmed
- [ ] When outputting to stdout, only warnings and errors appear on stderr
- [ ] When outputting to file, each replacement is logged along with a summary

### Example Transformation

**Input GFF3:**
```
##gff-version 3
##sequence-region BN000065.1 1 5000
##sequence-region BN000066.2 1 3000
BN000065.1  ENA  gene  100  500  .  +  .  ID=gene1
BN000065.1  ENA  CDS   100  500  .  +  0  ID=cds1;Parent=gene1
BN000066.2  ENA  gene  200  600  .  -  .  ID=gene2
##FASTA
>cds1
ATGCATGC
```

**After**: `replace-ids --accessions ACC123,ACC456`
```
##gff-version 3
##sequence-region ACC123 1 5000
##sequence-region ACC456 1 3000
ACC123  ENA  gene  100  500  .  +  .  ID=gene1
ACC123  ENA  CDS   100  500  .  +  0  ID=cds1;Parent=gene1
ACC456  ENA  gene  200  600  .  -  .  ID=gene2
##FASTA
>cds1
ATGCATGC
```

### Out of Scope

The following are explicitly **NOT** included in this specification:

- Updating FASTA section headers (they reference feature IDs, not sequence regions)
- Advanced accession format validation (e.g., regex patterns, length constraints, repository-specific formats)
- Key-value mapping format (e.g., `old_id:new_id` pairs) - only sequential mapping is supported
- In-place file modification (dropped to reduce complexity)
- Batch processing of multiple files in a single command
- Validation of accession uniqueness or duplicate checking
- Support for partial replacements (replacing only some sequence regions)
- Preserving version numbers from original IDs
- Support for file formats other than GFF3
- Dry-run or preview mode for the replacement operation
- File input for accessions (`--accessions-file` option) - future enhancement

### Open Questions

None. All decisions have been made during discovery.

---

## II. High-Level Implementation Plan

This feature will be implemented by extending the existing `FileProcessCommand` with two new sub-commands. The implementation is divided into three phases, organized by capability.

| Phase | Focus | Effort | Details |
|-------|-------|--------|---------|
| Phase 1 | Count Regions Sub-command | 2 days | [phase1_count_regions.md](./2602031156_replace_ids/phase1_count_regions.md) |
| Phase 2 | Replace IDs Sub-command | 3 days | [phase2_replace_ids.md](./2602031156_replace_ids/phase2_replace_ids.md) |
| Phase 3 | Integration & Testing | 2 days | [phase3_integration.md](./2602031156_replace_ids/phase3_integration.md) |

**Total Estimated Effort**: 7 days

### High-Level Guidance

**Architecture Overview:**
This feature leverages the existing GFF3 parsing and writing infrastructure. The implementation will:
- Add two new sub-commands under `FileProcessCommand`: `count-regions` and `replace-ids`
- Use the existing `GFF3FileReader` pattern that already parses `##sequence-region` directives
- Implement streaming processing to minimize memory footprint (critical for large files)
- Follow the `FileConversionCommand` pattern for stdin/stdout handling

**Key Components Involved:**
- **CLI Layer**: `FileProcessCommand` and new sub-command classes
- **Reader Layer**: `GFF3FileReader` (existing) for parsing sequence regions
- **Writer Layer**: `GFF3FileWriter` (existing) for output generation
- **Validation Layer**: Integration with `ValidationEngine` for error handling

**Existing Patterns to Follow:**
1. **Sub-command Structure**: Reference `Main.java` which shows how `FileConversionCommand`, `FileProcessCommand`, and `ValidationCommand` are registered as sub-commands
2. **Stdin/Stdout Handling**: Reference `FileConversionCommand.run()` method which uses:
   - `getPipe()` helper method for BufferedReader/BufferedWriter
   - Empty `outputFilePath` to detect stdout mode
   - LoggerContext to set ERROR level when writing to stdout
3. **Sequence Region Parsing**: Reference `GFF3FileReader` which already:
   - Uses `SEQUENCE_REGION_DIRECTIVE` pattern for parsing
   - Stores regions in `accessionSequenceRegionMap` (TreeMap)
   - **Note**: TreeMap provides sorted ordering by key (accession), but sequential mapping requires *insertion order* (order in file). Verify that GFF3FileReader preserves appearance order, or use LinkedHashMap instead to maintain insertion order.
4. **Error Handling**: Reference `ExitException` hierarchy and `CLIExitCode` enum from existing commands
5. **Validation Integration**: Reference `ValidationCommand` and `FileConversionCommand` for how to initialize `ValidationEngine` with rule overrides

**Memory Constraints:**
- The count operation must use streaming to avoid loading entire file into memory
- The replace operation should process annotations one at a time rather than loading all into memory
- Store only the accession replacement map (sequence region count will typically be small)

**Referential Integrity:**
The replacement operation must maintain consistency between:
- `##sequence-region` directives (header)
- Feature seqid column (column 1 in GFF3 records)
- Any internal data structures that key by accession

**Logging Strategy:**
Follow existing patterns from `FileConversionCommand`:
- Detect stdout mode by checking if `outputFilePath` is empty
- When stdout mode: set LoggerContext to ERROR level to prevent INFO/WARN logs from contaminating output
- When file mode: use standard INFO level logging for each replacement and summary

**Testing Strategy:**
- Unit tests for sub-commands with various input scenarios
- Integration tests with sample GFF3 files containing multiple sequence regions
- Edge case tests: mismatched counts, empty accessions, stdin/stdout combinations
- Reference existing test files in `src/test/resources/` for GFF3 structure examples

**No Breaking Changes:**
This feature adds new sub-commands to `FileProcessCommand` but does not modify existing commands or change any existing APIs. The current `process` command is a stub/TODO, so these sub-commands are the first implementation.
