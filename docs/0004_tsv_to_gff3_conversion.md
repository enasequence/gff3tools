# TSV Conversion Support

**Spec ID:** 0004  
**Status:** ✅ Complete  
**Created:** January 20, 2026  
**Completed:** January 28, 2026

---

## Problem Statement

Users needed to convert TSV (Tab-Separated Values) files to GFF3 format. TSV is a common input format for ENA submissions using sequencetools' template system. Without this conversion, users had to manually process TSV through multiple tools.

---

## Solution Overview

Leveraged existing sequencetools library infrastructure for TSV parsing and template processing, then reused gff3tools' GFF3 conversion components.

```
TSV File → [sequencetools] → Entry → [gff3tools: GFF3FileFactory] → GFF3 File
```

---

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Template support | All sequencetools templates | Full compatibility with ENA submission system |
| Input formats | Both gzipped and plain TSV | Flexibility for users; gff3tools convention |
| Validation | gff3tools ValidationEngine | Consistent error reporting across all conversions |
| Sequence handling | Optional separate FASTA via `--output-sequence` | GFF3 doesn't embed sequences; keeps output clean |

### Architecture

New `tsvconverter` package wraps sequencetools components:

- **TSVEntryReader** - Wraps `CSVReader` + `TemplateProcessor` to iterate TSV rows as `Entry` objects
- **TSVToGFF3Converter** - Uses `TSVEntryReader` + existing `GFF3FileFactory`

### Template ID Discovery

Template ID extracted from first 10 lines of TSV file, matching patterns:
- `Checklist ERT000028`
- `#template_accession ERT000028`

Templates loaded dynamically from sequencetools resources at runtime.

### Validation Mapping

| sequencetools Severity | gff3tools RuleSeverity |
|------------------------|------------------------|
| `ERROR` | `ERROR` |
| `WARNING` | `WARN` |
| `INFO` | `WARN` (logged only) |

---

## API

### CLI Usage

```bash
# TSV to GFF3 (format inferred from extensions)
java -jar gff3tools.jar conversion input.tsv output.gff3

# Gzipped input
java -jar gff3tools.jar conversion input.tsv.gz output.gff3

# With sequence extraction
java -jar gff3tools.jar conversion input.tsv output.gff3 --output-sequence sequences.fasta
```

### New Exceptions

- `TemplateNotFoundException` - Template ID not found in file or template doesn't exist
- `TSVParseException` - TSV parsing errors with line number context

---

## Alternatives Considered

| Alternative | Why Not Chosen |
|------------|----------------|
| Build custom TSV parser | Duplicates sequencetools work; wouldn't support all templates |
| Require uncompressed input only | Inconsistent with other gff3tools converters |
| Embed sequences in GFF3 | Not standard GFF3 practice; bloats output |

---

## Future Enhancements

- Streaming support for stdin input (requires buffering for template ID extraction)
- Offline mode with cached taxonomy data
- Project ID CLI option for entry metadata
