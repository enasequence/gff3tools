- Feature Name: fail_fast_flag
- Document Date: 2026-01-21
- Last Updated: 2026-01-21
- Jira: ENA-6796

# Summary

Change the default behavior of the conversion command to continue processing when errors are encountered (collecting all errors), rather than stopping at the first error. Add a `--fail-fast` CLI flag that restores the original behavior of stopping immediately on the first error. By default, errors are collected and reported at the end of processing, with an appropriate exit code indicating that errors occurred.

# Motivation & Rationale

## Problem Statement

Currently, gff3tools operates in "fail-fast" mode: when a validation or parsing error is encountered during conversion, the tool immediately stops processing and reports only the first error. This behavior forces users into an iterative workflow where they must:

1. Run the conversion
2. Fix the reported error
3. Re-run the conversion
4. Discover the next error
5. Repeat until all errors are fixed

For files with multiple issues, this can require many iterations, significantly slowing down the debugging and correction process.

## Use Cases

1. **Batch Error Discovery**: Users processing large files want to discover all validation errors in a single run, enabling them to fix multiple issues before re-running the tool.

2. **Partial Output Generation**: In some scenarios, users may want to generate output for the valid portions of a file while collecting information about problematic records.

3. **CI/CD Validation**: Automated pipelines may want to collect all validation errors for reporting purposes rather than stopping at the first issue.

## Design Rationale

The new default (collect all errors) with `--fail-fast` as opt-in was chosen because:

- **User-friendly default**: Most users benefit from seeing all errors at once, reducing iteration cycles.
- **Opt-in strictness**: Users who need fail-fast behavior (e.g., strict pipelines) can explicitly request it with `--fail-fast`.
- **CLI convention**: The `--fail-fast` flag follows common CLI conventions (e.g., Maven's `--fail-fast` / `-ff`).
- **Clear intent**: The flag name explicitly describes what it enables (fail-fast behavior).

# Usage Guidelines

## Basic Usage

```bash
# Default behavior - collects all errors and reports at end
java -jar gff3tools.jar conversion input.gff3 output.embl

# Fail-fast mode - stops on first error
java -jar gff3tools.jar conversion --fail-fast input.gff3 output.embl

# Can be combined with other options
java -jar gff3tools.jar conversion --rules "RULE_001:WARN" input.gff3 output.embl
```

## Output Behavior

By default (without `--fail-fast`):

1. **Errors are logged as they occur**: Each error is logged with `ERROR` level, including line number and context where available.

2. **Processing continues**: The tool attempts to process all records, skipping those that cannot be processed.

3. **Summary at end**: A summary is printed showing total errors encountered.

4. **Exit code reflects errors**: The tool exits with `VALIDATION_ERROR` (20) if any errors occurred, even though processing completed.

5. **No partial output on error**: If any errors occur during conversion, the output file is **not created**. The tool writes to a temporary file during processing and only moves it to the final destination if conversion succeeds without errors. This ensures users never end up with corrupted or incomplete output files.

### Example Output

```
ERROR: Violation of rule UNDEFINED_SEQ_ID on line 15: Undefined sequence region for accession "ACC001"
ERROR: Violation of rule INVALID_GFF3_RECORD on line 42: Invalid gff3 record "malformed line"
ERROR: Violation of rule UNDEFINED_SEQ_ID on line 89: Undefined sequence region for accession "ACC002"
INFO: Conversion completed with 3 errors
```

## Extending Error Collection

If adding new validation rules or error types, ensure they:

1. Throw `ValidationException` (or subclass) with appropriate rule name and line number
2. The `ValidationEngine` will handle collection vs. immediate throw based on the fail-fast mode

# System Overview / High-Level Design

## Architecture Changes

The implementation introduces a "fail-fast mode" concept that propagates through the conversion pipeline:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLI Layer                                       │
│  ┌─────────────────────┐                                                    │
│  │ FileConversionCommand│──── --fail-fast flag                              │
│  └──────────┬──────────┘                                                    │
│             │                                                               │
│             ▼                                                               │
│  ┌─────────────────────┐                                                    │
│  │  ValidationEngine   │◄─── failFast: boolean (default: false)             │
│  │  (via Builder)      │                                                    │
│  └──────────┬──────────┘                                                    │
└─────────────┼───────────────────────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Conversion Layer                                   │
│                                                                             │
│  ┌─────────────────────┐         ┌─────────────────────┐                   │
│  │  FFToGff3Converter  │         │  Gff3ToFFConverter  │                   │
│  └──────────┬──────────┘         └──────────┬──────────┘                   │
│             │                               │                               │
│             ▼                               ▼                               │
│  ┌─────────────────────┐         ┌─────────────────────┐                   │
│  │  GFF3FileFactory    │         │  GFF3FileReader     │                   │
│  └─────────────────────┘         └─────────────────────┘                   │
│                                                                             │
│  On error:                                                                  │
│  - failFast=true  → throw ValidationException immediately                  │
│  - failFast=false → collect in errorList, log, continue                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility |
|-----------|----------------|
| `FileConversionCommand` | Parse `--fail-fast` flag, pass to `ValidationEngineBuilder` |
| `ValidationEngineBuilder` | Accept `failFast` setting (default: `false`), configure `ValidationEngine` |
| `ValidationEngine` | Store error collection, decide throw vs. collect based on mode |
| Converters | Call `throwIfErrorsCollected()` at end of processing |

## Error Flow Comparison

### New Default (Collect All Errors) Flow
```
Error Occurs → ValidationEngine.handleSyntacticError() → add to collectedErrors list
                                                       → log.error(...)
                                                       → continue processing
                                                                    ↓
                                                        (more errors collected)
                                                                    ↓
                                                        End of processing
                                                                    ↓
                                                        Check collectedErrors
                                                                    ↓
                                              (if not empty) throw AggregatedValidationException
                                                                    ↓
                                                        ExecutionExceptionHandler
                                                                    ↓
                                                            Exit with code 20
```

### With `--fail-fast` Flag (Opt-in)
```
Error Occurs → ValidationEngine.handleSyntacticError() → throw ValidationException
                                                                    ↓
                                                        ExecutionExceptionHandler
                                                                    ↓
                                                            Exit with code 20
```

# Detailed Design & Implementation

## 1. CLI Flag Addition

### File: `AbstractCommand.java`

Add a new CLI option that can be shared across commands:

```java
@CommandLine.Option(
    names = "--fail-fast",
    description = "Stop processing on first error instead of collecting all errors"
)
public boolean failFast = false;
```

### File: `FileConversionCommand.java`

Pass the flag to the `ValidationEngineBuilder`:

```java
protected ValidationEngine initValidationEngine(Map<String, RuleSeverity> ruleOverrides)
        throws UnregisteredValidationRuleException {
    return new ValidationEngineBuilder()
            .overrideMethodRules(ruleOverrides)
            .failFast(failFast)
            .build();
}
```

## 2. ValidationEngine Modifications

### File: `ValidationEngineBuilder.java`

Add builder method for fail-fast configuration:

```java
public class ValidationEngineBuilder {
    private boolean failFast = false;  // Default: collect all errors
    // ... existing fields ...

    public ValidationEngineBuilder failFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }

    public ValidationEngine build() throws UnregisteredValidationRuleException {
        // ... existing build logic ...
        return new ValidationEngine(validationConfig, validationRegistry, failFast);
    }
}
```

### File: `ValidationEngine.java`

Modify to support error collection mode:

```java
public class ValidationEngine {
    private static Logger LOG = LoggerFactory.getLogger(ValidationEngine.class);

    private final List<ValidationException> parsingWarnings;
    private final List<ValidationException> collectedErrors;  // NEW
    private final boolean failFast;  // NEW
    
    public ValidationConfig validationConfig;
    public ValidationRegistry validationRegistry;

    ValidationEngine(ValidationConfig validationConfig, ValidationRegistry validationRegistry, boolean failFast) {
        this.parsingWarnings = new ArrayList<>();
        this.collectedErrors = new ArrayList<>();  // NEW
        this.failFast = failFast;  // NEW
        this.validationConfig = validationConfig;
        this.validationRegistry = validationRegistry;
    }

    // Modify handleSyntacticError to support collection mode
    public void handleSyntacticError(ValidationException exception) throws ValidationException {
        String rule = exception.getValidationRule() != null 
            ? exception.getValidationRule().toString() 
            : "SYNTAX_ERROR";
        RuleSeverity severity = validationConfig.getSeverity(rule, RuleSeverity.ERROR);
        
        switch (severity) {
            case OFF -> {}
            case WARN -> {
                parsingWarnings.add(exception);
                LOG.warn(exception.getMessage());
            }
            case ERROR -> {
                if (failFast) {
                    throw exception;
                } else {
                    collectedErrors.add(exception);
                    LOG.error(exception.getMessage());
                }
            }
        }
    }

    // Modify handleRuleException similarly
    private void handleRuleException(Exception e, RuleSeverity severity, String rule) throws ValidationException {
        Throwable cause = e;
        if (e instanceof InvocationTargetException) {
            cause = e.getCause();
        }
        if (cause instanceof ValidationException ve) {
            if (severity == RuleSeverity.WARN) {
                parsingWarnings.add(new ValidationException(rule, ve.getMessage()));
            } else if (severity == RuleSeverity.ERROR) {
                ValidationException validationException = new ValidationException(rule, ve.getLine(), ve.getMessage());
                if (failFast) {
                    throw validationException;
                } else {
                    collectedErrors.add(validationException);
                    LOG.error(validationException.getMessage());
                }
            } else {
                if (failFast) {
                    throw ve;
                } else {
                    collectedErrors.add(ve);
                    LOG.error(ve.getMessage());
                }
            }
        } else {
            throw new RuntimeException(cause);
        }
    }

    // NEW: Get collected errors
    public List<ValidationException> getCollectedErrors() {
        return collectedErrors;
    }

    // NEW: Check if errors were collected and throw aggregate if needed
    public void throwIfErrorsCollected() throws AggregatedValidationException {
        if (!collectedErrors.isEmpty()) {
            throw new AggregatedValidationException(collectedErrors);
        }
    }

    public boolean hasCollectedErrors() {
        return !collectedErrors.isEmpty();
    }
}
```

## 3. New Exception Class

### File: `exception/AggregatedValidationException.java`

```java
package uk.ac.ebi.embl.gff3tools.exception;

import java.util.List;
import java.util.stream.Collectors;
import uk.ac.ebi.embl.gff3tools.cli.CLIExitCode;

/**
 * Exception that aggregates multiple validation errors encountered during 
 * processing when --no-fast-fail mode is enabled.
 */
public class AggregatedValidationException extends ValidationException {

    private final List<ValidationException> errors;

    public AggregatedValidationException(List<ValidationException> errors) {
        super(formatMessage(errors));
        this.errors = List.copyOf(errors);
    }

    private static String formatMessage(List<ValidationException> errors) {
        return "Conversion completed with %d error(s)".formatted(errors.size());
    }

    public List<ValidationException> getErrors() {
        return errors;
    }

    public int getErrorCount() {
        return errors.size();
    }

    @Override
    public CLIExitCode exitCode() {
        return CLIExitCode.VALIDATION_ERROR;
    }
}
```

## 4. Converter Modifications

### File: `Gff3ToFFConverter.java`

Add error checking at end of conversion:

```java
public void convert(BufferedReader reader, BufferedWriter writer)
        throws ReadException, WriteException, ValidationException {

    try (GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader, gff3Path)) {
        gff3Reader.readHeader();
        gff3Reader.read(annotation -> {
            writeEntry(new GFF3Mapper(gff3Reader), annotation, writer);
            List<ValidationException> warnings = validationEngine.getParsingWarnings();
            for (ValidationException e : warnings) {
                log.warn("WARNING: %s".formatted(e.getMessage()));
            }
            addToWarningCount(warnings.size());
            warnings.clear();
        });

        // NEW: Check for collected errors at end of processing
        int errorCount = validationEngine.getCollectedErrors().size();
        if (errorCount > 0) {
            log.info("Conversion completed with %d error(s)".formatted(errorCount));
            validationEngine.throwIfErrorsCollected();
        } else if (warningCount > 0) {
            log.info("The file was converted with %d warnings".formatted(warningCount));
        } else {
            log.info("Completed conversion");
        }

    } catch (IOException e) {
        throw new RuntimeException(e);
    }
}
```

### File: `FFToGff3Converter.java`

Similar modification at end of `convert()` method:

```java
public void convert(BufferedReader reader, BufferedWriter writer)
        throws ReadException, WriteException, ValidationException {

    Path fastaPath = getFastaPath();
    try {
        EmblEntryReader entryReader =
                new EmblEntryReader(reader, EmblEntryReader.Format.EMBL_FORMAT, "embl_reader", getReaderOptions());

        GFF3FileFactory fftogff3 = new GFF3FileFactory(validationEngine, fastaPath);
        GFF3File file = fftogff3.from(entryReader, getMasterEntry(masterFilePath));
        file.writeGFF3String(writer);
        
        // NEW: Check for collected errors at end of processing
        validationEngine.throwIfErrorsCollected();
    } finally {
        deleteFastaFile(fastaPath);
    }
}
```

## 5. Skip Problematic Records

When `failFast=false`, individual records that fail validation should be skipped rather than causing the entire process to fail. This requires wrapping record processing in try-catch blocks.

### File: `GFF3FileReader.java`

Modify `readAnnotation()` to handle errors gracefully:

```java
private GFF3Feature readFeature(String line) throws ValidationException {
    // ... existing parsing logic ...
    
    try {
        validationEngine.validate(feature, lineCount);
        return feature;
    } catch (ValidationException e) {
        // In no-fast-fail mode, this won't throw but will collect
        // If it does throw (fast-fail mode), propagate it
        throw e;
    }
}
```

The key insight is that most of the error handling complexity is encapsulated in `ValidationEngine.handleSyntacticError()` and `handleRuleException()`. These methods already decide whether to throw or collect based on severity - we just need to add the `failFast` dimension to that decision.

# Alternatives Considered

## Alternative 1: `--no-fast-fail` flag (keep fail-fast as default)

Using `--no-fast-fail` to opt-in to error collection, keeping fail-fast as default.

**Pros**: Preserves current behavior; safer for existing pipelines.
**Cons**: Most users benefit from seeing all errors; fail-fast forces iterative debugging.

**Decision**: Rejected. The new default (collect all errors) is more user-friendly. Users who need strict fail-fast can opt-in with `--fail-fast`.

## Alternative 2: `--max-errors N` flag

Allow specifying a maximum number of errors before stopping.

**Pros**: More flexible; allows users to see "some" errors without processing the entire file.
**Cons**: More complex to implement and explain; unclear what a good default would be.

**Decision**: Deferred to future enhancement. Can be added later as a complementary feature.

## Alternative 3: Separate error collection at CLI level

Wrap all operations in try-catch at the command level and continue.

**Pros**: Simpler implementation.
**Cons**: 
- Loses context about which record caused which error
- Cannot generate partial output
- Doesn't integrate well with existing validation framework

**Decision**: Rejected. Integrating with `ValidationEngine` provides better error tracking and partial output support.

## Alternative 4: Error output to separate file

Write errors to a dedicated error file (e.g., `output.errors.txt`).

**Pros**: Clean separation of output and errors.
**Cons**: 
- Additional file management complexity
- Requires additional CLI parameter
- Standard error logging to stderr already provides this

**Decision**: Deferred. Can be added as a future enhancement. Current implementation uses standard logging which can be redirected.

# Technical Debt / Future Considerations

1. **`--max-errors N` flag**: Future enhancement to limit the number of errors collected before stopping.

2. **Structured error output**: Consider JSON error output format for programmatic consumption:
   ```bash
   java -jar gff3tools.jar conversion --no-fast-fail --error-format=json input.gff3 output.embl 2> errors.json
   ```

3. **Partial output marking**: When records are skipped, consider adding comments or markers in the output file indicating what was skipped.

4. **Error categorization**: Group errors by type in the summary (e.g., "5 undefined sequence regions, 3 invalid records").

5. **Recovery strategies**: For some error types, implement automatic recovery (e.g., skip malformed line and continue with next line).

# Testing Strategy

## Unit Tests

### `ValidationEngineTest.java`

```java
@Test
void handleSyntacticError_defaultBehavior_collectsError() {
    // Default is failFast=false (collect errors)
    ValidationEngine engine = new ValidationEngineBuilder().build();
    ValidationException exception = new ValidationException(1, "test error");
    
    assertDoesNotThrow(() -> engine.handleSyntacticError(exception));
    assertEquals(1, engine.getCollectedErrors().size());
}

@Test
void handleSyntacticError_failFastTrue_throwsImmediately() {
    ValidationEngine engine = new ValidationEngineBuilder().failFast(true).build();
    ValidationException exception = new ValidationException(1, "test error");
    
    assertThrows(ValidationException.class, () -> 
        engine.handleSyntacticError(exception));
}

@Test
void handleSyntacticError_failFastFalse_collectsError() {
    ValidationEngine engine = new ValidationEngineBuilder().failFast(false).build();
    ValidationException exception = new ValidationException(1, "test error");
    
    assertDoesNotThrow(() -> engine.handleSyntacticError(exception));
    assertEquals(1, engine.getCollectedErrors().size());
}

@Test
void throwIfErrorsCollected_withErrors_throwsAggregate() {
    ValidationEngine engine = new ValidationEngineBuilder().build();
    engine.handleSyntacticError(new ValidationException(1, "error 1"));
    engine.handleSyntacticError(new ValidationException(2, "error 2"));
    
    AggregatedValidationException thrown = assertThrows(
        AggregatedValidationException.class,
        () -> engine.throwIfErrorsCollected());
    
    assertEquals(2, thrown.getErrorCount());
}

@Test
void throwIfErrorsCollected_noErrors_doesNotThrow() {
    ValidationEngine engine = new ValidationEngineBuilder().build();
    
    assertDoesNotThrow(() -> engine.throwIfErrorsCollected());
}
```

### `AggregatedValidationExceptionTest.java`

```java
@Test
void exitCode_returnsValidationError() {
    List<ValidationException> errors = List.of(new ValidationException(1, "test"));
    AggregatedValidationException exception = new AggregatedValidationException(errors);
    
    assertEquals(CLIExitCode.VALIDATION_ERROR, exception.exitCode());
}

@Test
void getErrors_returnsImmutableCopy() {
    List<ValidationException> errors = new ArrayList<>();
    errors.add(new ValidationException(1, "test"));
    AggregatedValidationException exception = new AggregatedValidationException(errors);
    
    assertThrows(UnsupportedOperationException.class, () -> 
        exception.getErrors().add(new ValidationException(2, "another")));
}
```

## Integration Tests

### `FileConversionCommandTest.java`

```java
@Test
void conversion_withMultipleErrors_defaultBehavior_collectsAllErrors() {
    // Prepare a GFF3 file with multiple validation errors
    Path inputFile = createTempFileWithMultipleErrors();
    Path outputFile = tempDir.resolve("output.embl");
    
    // Default behavior (no --fail-fast) collects all errors
    int exitCode = runCommand("conversion", 
        inputFile.toString(), outputFile.toString());
    
    assertEquals(CLIExitCode.VALIDATION_ERROR.asInt(), exitCode);
    // Verify output contains partial results
    assertTrue(Files.exists(outputFile));
    // Verify logs contain all errors
    assertLogContains("error 1");
    assertLogContains("error 2");
    assertLogContains("error 3");
}

@Test
void conversion_withMultipleErrors_failFast_stopsAtFirst() {
    Path inputFile = createTempFileWithMultipleErrors();
    Path outputFile = tempDir.resolve("output.embl");
    
    int exitCode = runCommand("conversion", "--fail-fast",
        inputFile.toString(), outputFile.toString());
    
    assertEquals(CLIExitCode.VALIDATION_ERROR.asInt(), exitCode);
    // Verify only first error is logged
    assertLogContains("error 1");
    assertLogDoesNotContain("error 2");
}

@Test
void conversion_noErrors_defaultBehavior_succeeds() {
    Path inputFile = createValidTempFile();
    Path outputFile = tempDir.resolve("output.embl");
    
    int exitCode = runCommand("conversion",
        inputFile.toString(), outputFile.toString());
    
    assertEquals(CLIExitCode.SUCCESS.asInt(), exitCode);
}
```

## Test Resources

Add test files in `src/test/resources/no_fast_fail/`:

- `multiple_errors.gff3` - GFF3 file with multiple validation errors
- `multiple_errors.embl` - Corresponding EMBL file for bidirectional testing

# Deployment & Operations

## Deployment

No special deployment considerations. The feature is compiled into the existing JAR.

## Logging

When `--no-fast-fail` is used:
- Each error is logged at `ERROR` level as it occurs
- Summary is logged at `INFO` level at the end
- Standard logging configuration applies (can redirect stderr to file)

## Monitoring

In automated pipelines:
- Exit code 20 (`VALIDATION_ERROR`) indicates errors occurred
- Parse log output for error count: `"Conversion completed with N error(s)"`

## Backward Compatibility

- **Breaking change**: Default behavior changed from fail-fast to collect-all-errors
- Existing scripts that rely on fail-fast behavior should add `--fail-fast` flag
- No changes to existing exit codes
- No changes to output format

# Related Documentation & Resources

- [ENA-6796](https://www.ebi.ac.uk/panda/jira/browse/ENA-6796) - Jira ticket
- `docs/0001_error_handling.md` - Exception hierarchy and exit codes
- `docs/0002_validation_rules.md` - Validation rule system
- `docs/0003_validation_engine.md` - Validation engine architecture
- `src/main/java/uk/ac/ebi/embl/gff3tools/cli/` - CLI implementation
- `src/main/java/uk/ac/ebi/embl/gff3tools/validation/` - Validation framework
