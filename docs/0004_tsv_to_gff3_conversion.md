# TSV Conversion Support Design Proposal

**Author:** Agent  
**Date:** January 20, 2026  
**Status:** IMPLEMENTED (Phase 1-3 complete)

---

## 1. Overview

This document proposes adding support for converting TSV (Tab-Separated Values) files to both GFF3 and EMBL formats in gff3tools. The approach leverages existing TSV-to-Entry conversion logic from the sequencetools library, then uses gff3tools' converters to produce the final output.

### 1.1 Supported Conversions

| From | To | Description |
|------|-----|-------------|
| TSV | GFF3 | Primary use case - template-based TSV to GFF3 |
| TSV | EMBL | Secondary use case - template-based TSV to EMBL flat file |

### 1.2 Conversion Flow

```
                                    ┌──► [gff3tools: GFF3FileFactory] ──► GFF3 File
                                    │
TSV File → [sequencetools] → Entry ─┤
                                    │
                                    └──► [sequencetools: EmblEntryWriter] ──► EMBL File
```

### 1.3 Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Template support | All sequencetools templates | Full compatibility with ENA submission system |
| Output formats | GFF3 and EMBL | EMBL output is essentially free since Entry is intermediate |
| Input formats | Both gzipped and plain TSV | Flexibility for users; gff3tools convention |
| Validation | gff3tools ValidationEngine | Consistent error reporting across all conversions |

---

## 2. Background: TSV Handling in sequencetools

### 2.1 TSV File Types in sequencetools

The sequencetools library handles several TSV-based formats:

| Format | Purpose | Key Classes |
|--------|---------|-------------|
| **Template-based TSV** | Submitter data for sequence entries (main format) | `CSVReader`, `TemplateProcessor`, `TemplateEntryProcessor` |
| **PolySample TSV** | Sample frequency data (3 cols: Sequence_id, Sample_id, Frequency) | `TSVReader` |
| **SequenceTax TSV** | Taxonomy mapping (2-3 cols: Sequence_id, Tax_id, [Scientific_name]) | `TSVReader` |

**The primary TSV format for sequence data is the Template-based TSV**, which is what we should support.

### 2.2 Template-based TSV Format

Template-based TSV files:
- Are tab-delimited, gzip-compressed (`.tsv.gz`)
- Start with a checklist ID line (e.g., `Checklist ERT000002` or `#template_accession ERT000002`)
- Have a header row matching template token names
- Each subsequent row represents one sequence entry

**Example TSV content:**
```
Checklist ERT000002
Organism name	Sedimentation coefficient	Strain name	Sequence
Escherichia coli	16S	K-12	ATGCATGCATGC...
Bacillus subtilis	16S	168	GCTAGCTAGCTA...
```

### 2.3 Key sequencetools Classes

| Class | Location | Purpose |
|-------|----------|---------|
| `TSVFileValidationCheck` | `validation/check/file/` | Entry point for TSV processing; orchestrates template loading and processing |
| `CSVReader` | `template/` | Parses TSV rows into `TemplateVariables` (despite name, handles TSV) |
| `TemplateProcessor` | `template/` | Coordinates template processing; loads XML template |
| `TemplateEntryProcessor` | `template/` | Converts `TemplateVariables` → `Entry` object using template |
| `TemplateLoader` | `template/` | Loads XML template definitions |
| `TemplateInfo` | `template/` | Template metadata (tokens, sections, template string) |
| `EmblEntryWriter` | `flatfile/writer/embl/` | Writes `Entry` to EMBL format |

### 2.4 TSV → EMBL Conversion Flow in sequencetools

```java
// 1. Get template ID from TSV file
String templateId = getTemplateIdFromTsvFile(submissionFile.getFile());

// 2. Load template XML
TemplateInfo templateInfo = templateLoader.loadTemplateFromFile(templateFile);

// 3. Create processors
TemplateProcessor templateProcessor = new TemplateProcessor(templateInfo, options);
CSVReader csvReader = new CSVReader(inputStream, templateInfo.getTokens(), 0);

// 4. Process each row
CSVLine csvLine;
while ((csvLine = csvReader.readTemplateSpreadsheetLine()) != null) {
    // Process row into Entry
    TemplateProcessorResultSet result = templateProcessor.process(
        csvLine.getEntryTokenMap(), options);
    Entry entry = result.getEntry();
    
    // Write Entry as EMBL
    new EmblEntryWriter(entry).write(writer);
}
```

---

## 3. Proposed Implementation for gff3tools

### 3.1 Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          gff3tools                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────┐     ┌───────────────┐     ┌─────────────────┐ │
│  │ TSVToGFF3       │ ──► │  Entry        │ ──► │ FFToGff3        │ │
│  │ Converter       │     │  (in-memory)  │     │ Converter       │ │
│  └────────┬────────┘     └───────────────┘     └─────────────────┘ │
│           │                                                         │
│           │ uses                                                    │
│           ▼                                                         │
│  ┌─────────────────────────────────────────────────────┐           │
│  │              sequencetools library                   │           │
│  │  ┌──────────────┐  ┌───────────────┐  ┌──────────┐  │           │
│  │  │ CSVReader    │  │TemplateProc.  │  │ Template │  │           │
│  │  │              │  │               │  │ Info     │  │           │
│  │  └──────────────┘  └───────────────┘  └──────────┘  │           │
│  └─────────────────────────────────────────────────────┘           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 New/Modified Files

| File | Type | Description |
|------|------|-------------|
| `ConversionFileFormat.java` | Modify | Add `tsv` enum value |
| `tsvconverter/TSVEntryReader.java` | New | Adapter that reads TSV and produces `Entry` objects; handles both gzipped and plain input |
| `tsvconverter/TSVToGFF3Converter.java` | New | TSV→GFF3 converter using `TSVEntryReader` + `GFF3FileFactory` |
| `tsvconverter/TSVToFFConverter.java` | New | TSV→EMBL converter using `TSVEntryReader` + `EmblEntryWriter` |
| `FileConversionCommand.java` | Modify | Add TSV→GFF3 and TSV→EMBL conversion cases |
| `exception/TemplateNotFoundException.java` | New | Exception when template ID not found |
| `exception/TSVParseException.java` | New | Exception for TSV parsing errors |

### 3.3 Core Implementation

#### 3.3.1 ConversionFileFormat.java (Modified)

```java
public enum ConversionFileFormat {
    embl,
    gff3,
    tsv  // NEW
}
```

#### 3.3.2 TSVEntryReader.java (New) - Core Component

The `TSVEntryReader` is the central component that wraps sequencetools' template processing
and provides an iterator-like interface for reading `Entry` objects from TSV input.

```java
package uk.ac.ebi.embl.gff3tools.tsvconverter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.validation.submission.SubmissionOptions;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.template.*;

/**
 * Reads TSV rows and converts each to an Entry object using sequencetools template processing.
 * Supports both gzipped and plain text TSV files.
 * Integrates with gff3tools ValidationEngine for consistent error reporting.
 */
public class TSVEntryReader implements Closeable {
    private final CSVReader csvReader;
    private final TemplateProcessor templateProcessor;
    private final SubmissionOptions options;
    private final ValidationEngine validationEngine;
    private final TemplateInfo templateInfo;

    /**
     * Creates a TSVEntryReader from a file path.
     * Automatically detects gzipped vs plain text input.
     */
    public TSVEntryReader(Path inputPath, ValidationEngine validationEngine) 
            throws ReadException {
        this.validationEngine = validationEngine;
        this.options = createDefaultOptions();
        
        try {
            // 1. Extract template ID (need to read file twice - once for ID, once for data)
            String templateId = extractTemplateId(inputPath);
            
            // 2. Load template
            this.templateInfo = loadTemplate(templateId);
            this.templateProcessor = new TemplateProcessor(templateInfo, options);
            
            // 3. Create input stream (handles gzip detection)
            InputStream inputStream = createInputStream(inputPath);
            this.csvReader = new CSVReader(inputStream, templateInfo.getTokens(), 0);
            
        } catch (Exception e) {
            throw new ReadException("Failed to initialize TSV reader: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a TSVEntryReader from a BufferedReader (for streaming).
     * Requires template ID to be provided since we can't peek at the stream.
     */
    public TSVEntryReader(BufferedReader reader, String templateId, 
                          ValidationEngine validationEngine) throws ReadException {
        this.validationEngine = validationEngine;
        this.options = createDefaultOptions();
        
        try {
            this.templateInfo = loadTemplate(templateId);
            this.templateProcessor = new TemplateProcessor(templateInfo, options);
            
            // Wrap reader as InputStream for CSVReader
            InputStream inputStream = new ReaderInputStream(reader, StandardCharsets.UTF_8);
            this.csvReader = new CSVReader(inputStream, templateInfo.getTokens(), 0);
            
        } catch (Exception e) {
            throw new ReadException("Failed to initialize TSV reader: " + e.getMessage(), e);
        }
    }

    /**
     * Reads the next TSV row and converts it to an Entry.
     * @return Entry object or null if no more rows
     */
    public Entry read() throws ReadException, ValidationException {
        try {
            CSVLine csvLine = csvReader.readTemplateSpreadsheetLine();
            if (csvLine == null) {
                return null;
            }
            
            TemplateProcessorResultSet result = templateProcessor.process(
                csvLine.getEntryTokenMap(), options);
            
            // Convert sequencetools validation to gff3tools validation
            handleValidationResult(result.getValidationResult(), csvLine.getLineNumber());
            
            return result.getEntry();
            
        } catch (TemplateUserError e) {
            throw new ValidationException("TSV validation error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ReadException("Error reading TSV row: " + e.getMessage(), e);
        }
    }

    /**
     * Detects if file is gzipped and creates appropriate InputStream.
     */
    private InputStream createInputStream(Path path) throws IOException {
        InputStream fis = Files.newInputStream(path);
        BufferedInputStream bis = new BufferedInputStream(fis);
        
        // Check for gzip magic number
        bis.mark(2);
        int byte1 = bis.read();
        int byte2 = bis.read();
        bis.reset();
        
        boolean isGzipped = (byte1 == 0x1f && byte2 == 0x8b);
        
        if (isGzipped) {
            return new GZIPInputStream(bis);
        }
        return bis;
    }

    /**
     * Extracts template ID from TSV file header.
     */
    private String extractTemplateId(Path path) throws IOException, TemplateNotFoundException {
        try (InputStream is = createInputStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            
            // Read first 10 lines looking for template ID
            for (int i = 0; i < 10; i++) {
                String line = reader.readLine();
                if (line == null) break;
                
                String templateId = CSVReader.getChecklistIdFromIdLine(line);
                if (templateId != null) {
                    return templateId;
                }
            }
        }
        throw new TemplateNotFoundException("No template ID found in TSV file header");
    }

    /**
     * Loads template XML from sequencetools resources.
     */
    private TemplateInfo loadTemplate(String templateId) throws TemplateNotFoundException {
        try {
            TemplateProcessor processor = new TemplateProcessor();
            String templateXml = processor.getTemplate(templateId);
            
            if (templateXml == null || templateXml.isEmpty()) {
                throw new TemplateNotFoundException("Template not found: " + templateId);
            }
            
            // Parse XML to TemplateInfo
            TemplateLoader loader = new TemplateLoader();
            return loader.loadTemplateFromString(templateXml);
            
        } catch (Exception e) {
            throw new TemplateNotFoundException("Failed to load template " + templateId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Converts sequencetools ValidationResult to gff3tools validation.
     */
    private void handleValidationResult(
            uk.ac.ebi.embl.api.validation.ValidationResult result, 
            int lineNumber) throws ValidationException {
        
        if (!result.isValid()) {
            // Map sequencetools messages to gff3tools ValidationEngine
            for (var message : result.getMessages()) {
                validationEngine.addMessage(
                    mapSeverity(message.getSeverity()),
                    "TSV line " + lineNumber + ": " + message.getMessage()
                );
            }
            
            // Check if any errors should stop processing
            if (validationEngine.hasErrors()) {
                throw new ValidationException("TSV validation failed at line " + lineNumber);
            }
        }
    }

    private SubmissionOptions createDefaultOptions() {
        // Minimal options for template processing
        SubmissionOptions options = new SubmissionOptions();
        // Configure as needed
        return options;
    }

    @Override
    public void close() throws IOException {
        // Close underlying resources
    }
}
```

#### 3.3.3 TSVToGFF3Converter.java (New)

```java
package uk.ac.ebi.embl.gff3tools.tsvconverter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.validation.submission.SubmissionOptions;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.fftogff3.GFF3AnnotationFactory;
import uk.ac.ebi.embl.gff3tools.fftogff3.GFF3DirectivesFactory;
import uk.ac.ebi.embl.gff3tools.gff3.*;
import uk.ac.ebi.embl.gff3tools.gff3.directives.*;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.template.*;

public class TSVToGFF3Converter implements Converter {
    private final ValidationEngine validationEngine;
    private final Path inputFilePath;  // Needed to determine template ID
    private final SubmissionOptions options;

    public TSVToGFF3Converter(ValidationEngine validationEngine, Path inputFilePath) {
        this.validationEngine = validationEngine;
        this.inputFilePath = inputFilePath;
        this.options = createDefaultOptions();
    }

    @Override
    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {
        
        Path fastaPath = createTempFastaPath();
        try {
            // 1. Load template based on TSV file header
            TemplateInfo templateInfo = loadTemplate(inputFilePath);
            
            // 2. Create TSV entry reader (iterates over TSV rows as Entry objects)
            TSVEntryReader entryReader = new TSVEntryReader(reader, templateInfo, options);
            
            // 3. Use existing GFF3 conversion infrastructure
            GFF3DirectivesFactory directivesFactory = new GFF3DirectivesFactory();
            GFF3AnnotationFactory annotationFactory = 
                new GFF3AnnotationFactory(validationEngine, directivesFactory, fastaPath);
            
            GFF3Header header = new GFF3Header("3.1.26");
            GFF3Species species = null;
            List<GFF3Annotation> annotations = new ArrayList<>();
            
            Entry entry;
            while ((entry = entryReader.read()) != null) {
                if (species == null) {
                    species = directivesFactory.createSpecies(entry, null);
                }
                annotations.add(annotationFactory.from(entry));
            }
            
            GFF3File file = GFF3File.builder()
                .header(header)
                .species(species)
                .annotations(annotations)
                .fastaFilePath(fastaPath)
                .parsingWarnings(validationEngine.getParsingWarnings())
                .build();
            
            file.writeGFF3String(writer);
        } finally {
            deleteTempFile(fastaPath);
        }
    }
    
    private TemplateInfo loadTemplate(Path tsvFilePath) throws ReadException {
        // Extract template ID from TSV and load corresponding XML template
        // Uses sequencetools' TemplateLoader and TemplateProcessor.getTemplate()
    }
    
    private SubmissionOptions createDefaultOptions() {
        // Create minimal SubmissionOptions for template processing
    }
}
```

#### 3.3.4 TSVToFFConverter.java (New)

```java
package uk.ac.ebi.embl.gff3tools.tsvconverter;

import java.io.*;
import java.nio.file.Path;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.flatfile.writer.embl.EmblEntryWriter;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

/**
 * Converts TSV files to EMBL flat file format.
 * Uses sequencetools' EmblEntryWriter for output.
 */
public class TSVToFFConverter implements Converter {
    private final ValidationEngine validationEngine;
    private final Path inputFilePath;

    public TSVToFFConverter(ValidationEngine validationEngine, Path inputFilePath) {
        this.validationEngine = validationEngine;
        this.inputFilePath = inputFilePath;
    }

    @Override
    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {
        
        try (TSVEntryReader entryReader = new TSVEntryReader(inputFilePath, validationEngine)) {
            Entry entry;
            while ((entry = entryReader.read()) != null) {
                // Use sequencetools' EmblEntryWriter
                new EmblEntryWriter(entry).write(writer);
            }
        } catch (IOException e) {
            throw new WriteException("Error writing EMBL output: " + e.getMessage(), e);
        }
    }
}
```

#### 3.3.5 FileConversionCommand.java (Modified)

```java
private Converter getConverter(
        ValidationEngine engine, ConversionFileFormat inputFileType, 
        ConversionFileFormat outputFileType) throws FormatSupportException {
    
    if (inputFileType == ConversionFileFormat.gff3 && 
        outputFileType == ConversionFileFormat.embl) {
        return new Gff3ToFFConverter(engine, inputFilePath);
        
    } else if (inputFileType == ConversionFileFormat.embl && 
               outputFileType == ConversionFileFormat.gff3) {
        return masterFilePath == null
            ? new FFToGff3Converter(engine)
            : new FFToGff3Converter(engine, masterFilePath);
            
    } else if (inputFileType == ConversionFileFormat.tsv &&
               outputFileType == ConversionFileFormat.gff3) {
        // NEW: TSV → GFF3
        return new TSVToGFF3Converter(engine, inputFilePath);
        
    } else if (inputFileType == ConversionFileFormat.tsv &&
               outputFileType == ConversionFileFormat.embl) {
        // NEW: TSV → EMBL
        return new TSVToFFConverter(engine, inputFilePath);
        
    } else {
        throw new FormatSupportException(fromFileType, toFileType);
    }
}
```

---

## 4. Challenges and Considerations

### 4.1 Template ID Discovery

The template ID must be extracted from the TSV file before processing. The first 10 lines are scanned for patterns like:
- `Checklist ERT000028`
- `#template_accession ERT000028`

**Decision:** Support both gzipped and plain TSV files. The `TSVEntryReader` will detect gzip format using magic bytes (`0x1f 0x8b`).

### 4.2 Template Support

**Decision:** Support ALL templates available in sequencetools. Templates are loaded from sequencetools' resources at runtime (`templates/*.xml`). This ensures full compatibility with the ENA submission system.

Available templates (as of sequencetools 2.x):
- ERT000002 - rRNA gene
- ERT000003 - ITS
- ERT000006 - Satellite DNA
- ERT000009 - Mobile element
- ERT000020 - COI
- ERT000024 - D-loop/control region
- ERT000028 - Single Viral CDS
- ERT000029 - Single CDS
- ... and more

### 4.3 SubmissionOptions Dependency

`TemplateProcessor` requires `SubmissionOptions` which has many optional fields. We need to determine:
- Minimum required options for template processing
- How to expose relevant options via CLI (e.g., `--project-id`)

### 4.3 External Service Dependencies

`TemplateEntryProcessor` can call external services:
- **TaxonomyClient** - validates organism names/tax IDs
- **SampleRetrievalService** - retrieves sample metadata

**Options:**
1. Allow external calls (requires network access)
2. Make external validation optional via CLI flag
3. Provide mock/stub implementations for offline use

### 4.4 Validation Result Handling

**Decision:** Use gff3tools' `ValidationEngine` for all validation reporting.

Template processing produces sequencetools `ValidationResult` objects. These must be:
1. Mapped to gff3tools' `RuleSeverity` levels
2. Reported through the unified `ValidationEngine`
3. Subject to the same rule override mechanism (`--rules` CLI option)

**Severity Mapping:**
| sequencetools Severity | gff3tools RuleSeverity |
|------------------------|------------------------|
| `ERROR` | `ERROR` |
| `WARNING` | `WARN` |
| `INFO` | `WARN` (logged only) |
| `FIX` | N/A (applied silently) |

### 4.5 Streaming Limitations

The current implementation in sequencetools:
- Requires file path for template ID extraction (reads beginning of file)
- Uses `GZIPInputStream` expecting compressed input

**For stdin support:** May need to buffer input to extract template ID before processing.

---

## 5. CLI Usage

### 5.1 TSV to GFF3 Conversion

```bash
# File to file (format inferred from extensions)
java -jar gff3tools.jar conversion input.tsv output.gff3

# Gzipped input
java -jar gff3tools.jar conversion input.tsv.gz output.gff3

# With explicit formats
java -jar gff3tools.jar conversion -f tsv -t gff3 input.tsv.gz output.gff3

# Streaming output to stdout
java -jar gff3tools.jar conversion -t gff3 input.tsv -
```

### 5.2 TSV to EMBL Conversion

```bash
# File to file
java -jar gff3tools.jar conversion input.tsv output.embl

# With explicit formats
java -jar gff3tools.jar conversion -f tsv -t embl input.tsv output.embl

# Gzipped input, EMBL output
java -jar gff3tools.jar conversion input.tsv.gz output.embl
```

### 5.3 Additional Options (Potential)

```bash
# Specify project ID for entry metadata
java -jar gff3tools.jar conversion --project-id PRJEB12345 input.tsv output.gff3

# Skip taxonomy validation (offline mode)
java -jar gff3tools.jar conversion --skip-taxonomy-validation input.tsv output.gff3

# Strict validation (fail on first error)
java -jar gff3tools.jar conversion --strict input.tsv output.gff3

# Override validation rules
java -jar gff3tools.jar conversion --rules TSV_MANDATORY_FIELD=WARN input.tsv output.gff3
```

---

## 6. Implementation Plan

### Phase 1: Core Infrastructure
1. Add `tsv` to `ConversionFileFormat` enum
2. Create new exceptions: `TemplateNotFoundException`, `TSVParseException`
3. Implement `TSVEntryReader` with gzip detection and template loading
4. Add validation mapping (sequencetools → gff3tools)

### Phase 2: TSV → GFF3 Conversion
1. Implement `TSVToGFF3Converter` using `TSVEntryReader` + existing `GFF3FileFactory` logic
2. Update `FileConversionCommand` to support TSV→GFF3
3. Add tests with sample TSV files for multiple templates

### Phase 3: TSV → EMBL Conversion
1. Implement `TSVToFFConverter` using `TSVEntryReader` + `EmblEntryWriter`
2. Update `FileConversionCommand` to support TSV→EMBL
3. Add tests for TSV→EMBL conversion

### Phase 4: Polish & Extended Features
1. Add CLI options (project ID, offline mode)
2. Comprehensive test coverage for all templates
3. Documentation and examples
4. Error message improvements

---

## 7. Test Strategy

### 7.1 Test Data

Create test file pairs in appropriate directories:

**TSV → GFF3 tests:** `src/test/resources/tsvtogff3_rules/`
- `rrna_ERT000002.tsv` + `rrna_ERT000002.gff3`
- `cds_ERT000029.tsv` + `cds_ERT000029.gff3`
- `viral_cds_ERT000028.tsv` + `viral_cds_ERT000028.gff3`
- `its_ERT000003.tsv` + `its_ERT000003.gff3`

**TSV → EMBL tests:** `src/test/resources/tsvtoembl_rules/`
- `rrna_ERT000002.tsv` + `rrna_ERT000002.embl`
- `cds_ERT000029.tsv` + `cds_ERT000029.embl`

### 7.2 Test Cases

| Test | Description |
|------|-------------|
| **TSVEntryReader Tests** | |
| `testReadPlainTSV` | Read uncompressed TSV file |
| `testReadGzippedTSV` | Read gzip-compressed TSV file |
| `testExtractTemplateId` | Extract template ID from header |
| `testTemplateNotFound` | Error when template ID not found |
| `testInvalidTemplateId` | Error for malformed template ID |
| **TSVToGFF3Converter Tests** | |
| `testBasicRRNAConversion` | Single rRNA gene entry (ERT000002) |
| `testCDSConversion` | CDS with translation (ERT000029) |
| `testMultipleEntries` | Multiple rows → multiple annotations |
| `testEnvironmentalSample` | Entry with environmental_sample flag |
| **TSVToFFConverter Tests** | |
| `testTSVToEMBL` | Basic TSV to EMBL conversion |
| `testTSVToEMBLMultipleEntries` | Multiple entries in output |
| **Validation Tests** | |
| `testMissingMandatoryField` | Error handling for invalid TSV |
| `testValidationErrorMapping` | sequencetools errors mapped correctly |
| `testValidationRuleOverride` | Rule severity can be overridden |

---

## 8. Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| sequencetools API changes | Breaking changes | Pin to specific version; add integration tests |
| External service unavailability | Conversion fails | Add offline mode; cache taxonomy data |
| Template XML updates | Output changes | Include template version in tests; document supported versions |
| Memory usage for large files | OOM errors | Use streaming where possible; document limits |

---

## 9. Resolved Design Decisions

| Question | Decision |
|----------|----------|
| Which templates to support? | **All templates** in sequencetools - loaded dynamically from library resources |
| Support TSV→EMBL? | **Yes** - adds minimal complexity since Entry is intermediate representation |
| How to handle validation? | **Use gff3tools ValidationEngine** - map sequencetools errors to gff3tools severity levels |
| Input format (gzip/plain)? | **Support both** - auto-detect using gzip magic bytes |

---

## 10. References

- sequencetools repository: `~/code/ebi/sequencetools`
- Template XML files: `sequencetools/src/main/resources/templates/`
- Key classes: `TSVFileValidationCheck`, `TemplateProcessor`, `TemplateEntryProcessor`, `CSVReader`
- Existing EMBL→GFF3 converter: `gff3tools/src/main/java/.../fftogff3/`
