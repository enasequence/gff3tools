# AGENTS.md - Agent Guidelines for gff3tools

## Project Management

All work items related to **gff3tools** are tracked on a dedicated Jira board.

### Jira Board Details

| Property | Value |
|----------|-------|
| **Board Name** | ENA Gff3 Annotation |
| **Board ID** | 488 |
| **Board Type** | Scrum |
| **Project** | ENA |

### Useful ACLI Commands

```bash
# View current sprint work items
acli jira sprint list-workitems --sprint <SPRINT_ID> --board 488

# List sprints to find the active one
acli jira board list-sprints --id 488

# Search for gff3tools items
acli jira workitem search --jql "project = ENA AND text ~ 'gff3tools'" \
  --fields "key,summary,status,assignee"

# View a specific work item
acli jira workitem view ENA-<NUMBER>

# Create a new gff3tools work item
acli jira workitem create --project "ENA" --type "Task" \
  --summary "Gff3tools: <description>" \
  --description "<detailed description>"
```

### Work Item Naming Convention

For consistency, prefix gff3tools-related work items with:
- `Gff3tools:` - General tasks
- `GFF3-migration-pipeline:` - Pipeline-specific issues
- `GFF3-EMBL` - Conversion-related issues

---

## Development Workflow

To ensure clarity and developer oversight, agents must adhere to the following workflow:

1. **User Prompt**: The developer initiates a task with a specific goal.
2. **Codebase Grounding**: Before proposing a solution, ground your understanding by exploring the existing codebase. Identify relevant files, understand data structures, and analyze existing patterns. **Avoid searching in the `build/` folder.**
3. **Propose Changes**: Based on findings, outline the proposed changes including:
   - Files to be modified
   - Specific code additions, deletions, or modifications
   - Any new dependencies required
   - Expected outcome of the changes
4. **User Review**: Wait for developer approval before implementing.
5. **Implementation**: Follow Test-Driven Development (TDD) principles where appropriate - write failing tests, implement code to pass, then refactor.
6. **Verification**: After implementation, run `./gradlew test` to verify changes work and don't introduce regressions.

## Project Overview

**gff3tools** is a Java-based CLI tool and library for converting between EMBL flat files and GFF3 format. It is developed by the European Bioinformatics Institute (EBI) and uses the [sequencetools](https://github.com/enasequence/sequencetools) library for reading flat files.

### Key Features
- Bidirectional conversion: EMBL flat file (`.embl`) ↔ GFF3 (`.gff3`)
- Support for Unix pipes (stdin/stdout)
- Configurable validation rules with severity levels
- Streaming support for large files

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 17 |
| Build System | Gradle with Shadow plugin |
| CLI Framework | picocli |
| Logging | SLF4J + Logback |
| Testing | JUnit 5, Mockito |
| Code Style | Spotless (Palantir Java Format) |
| Utilities | Lombok, Vavr, JGraphT |

## Project Structure

```
src/main/java/uk/ac/ebi/embl/gff3tools/
├── cli/                    # CLI commands (Main, FileConversionCommand, ValidationCommand)
├── exception/              # Custom exceptions extending ExitException
├── fftogff3/               # Flat file to GFF3 conversion logic
├── gff3/                   # GFF3 data model and I/O
│   ├── directives/         # GFF3 directive handling
│   ├── reader/             # GFF3 file reading
│   └── writer/             # GFF3 file writing
├── gff3toff/               # GFF3 to flat file conversion logic
├── utils/                  # Utility classes (ontology, conversion helpers)
└── validation/             # Validation framework
    ├── builtin/            # Built-in validation rules
    ├── fix/                # Auto-fix implementations
    └── meta/               # Validation metadata (RuleSeverity, etc.)

src/test/
├── java/                   # Test classes mirror main structure
└── resources/              # Test data files
    ├── fftogff3_rules/     # Flat file to GFF3 test cases
    ├── gff3toff_rules/     # GFF3 to flat file test cases
    ├── reader/             # Reader test data
    └── validation_errors/  # Validation error test cases

docs/                       # Design documents
```

## Build & Test Commands

```bash
# Build the project
./gradlew clean build

# Run tests
./gradlew test

# Run tests with detailed output
./gradlew test --info

# Check code formatting
./gradlew spotlessCheck

# Apply code formatting
./gradlew spotlessApply

# Build shadow JAR (runnable)
./gradlew shadowJar
# Output: build/libs/gff3tools-{version}-all.jar
```

**Note:** Add `-Pgitlab_private_token=dummy-token` if you don't have a real token and just want to build/test locally.

## Code Style & Formatting

The project uses **Spotless** with **Palantir Java Format**. Key requirements:

- **License header**: All Java files must include the Apache 2.0 license header
- **Import order**: `java`, `javax`, `org`, `com`, `<blank>`, `uk`
- **Line endings**: UNIX style (`\n`)
- **Encoding**: UTF-8

Run `./gradlew spotlessApply` before committing to auto-format code.

## Code Organization Convention

The project follows a natural, organic approach to code organization where structure emerges from complexity rather than being imposed prematurely.

### The Natural Growth Pattern

1. **Start Simple**: Begin with a single `.java` file for new functionality
2. **Grow**: Add features to that file as the module evolves
3. **Split**: When complexity emerges (~500-800 lines or clear conceptual divisions), create a package and split into logical components
4. **Nest**: When a component grows complex, apply the same pattern recursively
5. **Elevate**: When multiple packages need the same code, move it up to the lowest common ancestor (e.g., `utils/`)

### Key Principles

- **Avoid Premature Abstraction**: Don't create packages or split files "just in case"
- **Follow Domain Boundaries**: Group by feature/responsibility, not by technical layer
- **Keep Related Code Close**: Code that changes together should live together
- **Test Structure Mirrors Source**: When splitting a module, consider splitting its tests accordingly

### Example Evolution

```
# Stage 1: Single file
src/.../validation/MyValidation.java

# Stage 2: Package with components  
src/.../validation/myfeature/
  MyValidation.java
  MyHelper.java
  MyConfig.java

# Stage 3: Elevation for reuse
src/.../utils/SharedHelper.java  # Elevated when multiple packages need it
```

## Key Conventions

### Conversion Rules Documentation

Document conversion rules and assumptions directly in code using comments:
```java
// Rule: Use the phase value if present in a qualifier.
// Rule: If phase qualifier is not present, calculate it only for CDS
```

### Defensive Programming: Input Validation Inside Functions

Move input validation inside functions rather than requiring callers to validate before calling.

**Rationale:**
- **Encapsulates responsibility** - The function handles its own edge cases instead of relying on every caller to remember the preconditions
- **Reduces code duplication** - If multiple places call the function, each would need the same validation check
- **Improves maintainability** - If validation logic changes, you update one place instead of hunting down all call sites
- **Makes functions self-documenting** - The function signature and behavior clearly show what happens with invalid inputs

**Before (caller validates - avoid this):**
```java
if (candidates == null || candidates.isEmpty()) {
    return null;
}
return selectBestMatch(candidates, attributes);
```

**After (function validates - preferred):**
```java
return selectBestMatch(candidates, attributes);

// Inside selectBestMatch:
private static Result selectBestMatch(List<Candidate> candidates, Map<String, List<String>> attributes) {
    if (candidates == null || candidates.isEmpty()) {
        return null;
    }
    // ... rest of implementation
}
```

### Exception Handling

All exceptions that should cause specific exit codes must extend `ExitException`:

```java
public abstract class ExitException extends RuntimeException {
    public abstract CLIExitCode exitCode();
}
```

**Exit Code Categories:**
| Code | Name | Description |
|------|------|-------------|
| 0 | SUCCESS | Successful execution |
| 1 | GENERAL | Unexpected errors (bugs) |
| 2 | USAGE | Invalid CLI arguments |
| 3 | UNSUPPORTED_FORMAT_CONVERSION | Unsupported format conversion |
| 10 | READ_ERROR | File read error |
| 11 | WRITE_ERROR | File write error |
| 12 | NON_EXISTENT_FILE | Input file not found |
| 20 | VALIDATION_ERROR | Data validation failure |
| 30 | OUT_OF_MEMORY | Memory exhausted |

**Important:** Do NOT renumber existing exit codes - this breaks automation pipelines.

### Adding New Exceptions

1. Extend `ExitException` or an existing subclass
2. If introducing a new error category, add to `CLIExitCode` enum
3. Each distinct `CLIExitCode` should have only one direct implementing exception class
4. Related exceptions should inherit from that class

### Conversion Mapping System

The `feature-mapping.tsv` defines mappings between SO terms (GFF3) and INSDC features (EMBL). Key design points:

1. **Multiple entries per key**: The TSV can have multiple rows for the same SO term or INSDC feature, differentiated by qualifier requirements. For example:
   ```
   SO:0000730  gap  ...  gap           /estimated_length=<length of feature>
   SO:0000730  gap  ...  assembly_gap  (no qualifiers)
   ```

2. **Qualifier-based selection**: When multiple mappings exist, the conversion logic must:
   - Store ALL entries (not just one per key)
   - Select the best match based on qualifier/attribute presence
   - Prefer entries with the most matching qualifiers
   - Fall back to entries with no qualifier requirements

3. **Wildcard values**: Qualifier values like `<length of feature>` are wildcards that match any value. The pattern `ConversionUtils.WILDCARD_TEXT` handles this.

4. **Reference implementation**: See `FeatureMapping.getGFF3FeatureName()` for the FF→GFF3 direction, which correctly implements qualifier-based selection.

### Validation System

The validation framework allows configurable rule severities:

- `OFF` - Rule disabled
- `WARN` - Log warning, continue execution
- `ERROR` - Log error, stop execution

Validation rules are defined in `validation/builtin/` and can be overridden via CLI `--rules` option.

## Testing Guidelines

### Framework & Conventions
- Use JUnit 5 (`@Test`, `@BeforeEach`, etc.)
- Use Mockito for mocking
- Test resources go in `src/test/resources/`
- Name test classes with `Test` suffix (e.g., `MainTest.java`)
- Integration tests can use `IntegrationTest` suffix
- **Use package-private visibility** for test classes (no `public` modifier) - JUnit 5 does not require public classes
- **Use static imports** for assertion methods to improve readability:
  ```java
  import static org.junit.jupiter.api.Assertions.*;
  
  // Then use directly:
  assertDoesNotThrow(() -> ...);
  assertTrue(condition);
  assertEquals(expected, actual);
  ```

### Test Categories
- Unit tests in same package as source
- Integration tests for CLI commands
- Conversion rule tests use file pairs in `resources/fftogff3_rules/` and `resources/gff3toff_rules/`

### Conversion Test File Pairs

The conversion tests use **auto-discovered file pairs**. To add a new conversion test case:

1. Create a `.gff3` + `.embl` file pair with the same base name in the appropriate directory:
   - `src/test/resources/gff3toff_rules/` for GFF3 → EMBL tests
   - `src/test/resources/fftogff3_rules/` for EMBL → GFF3 tests

2. The `.embl` file must include the **complete EMBL format**, not just feature lines:
   ```
   ID   ACC123; SV 1; XXX; XXX; XXX; XXX; 0 BP.
   XX
   AC   ACC123;
   XX
   DE   .
   XX
   KW   .
   XX
   FH   Key             Location/Qualifiers
   FH
   FT   source          1..1000
   FT   gene            100..500
   FT                   /gene="example"
   XX
   //
   ```

3. The test runner (`GFF3ToFFConverterTest`, `FFToGFF3ConverterTest`) automatically discovers and runs all pairs.

4. **Important**: Existing test expectations may reflect buggy behavior. When fixing bugs, verify whether test expectations need updating.

### Guidelines for Writing Tests

- **Idempotency & Repeatability**: Tests must pass when run multiple times. Avoid hardcoded values that may change; check for specific entities you create.
- **Write Independent Tests**: Tests should be self-contained and not depend on the state or outcome of other tests.
- **Use Test Resources**: When a test requires specific input data, place it in `src/test/resources/` in the appropriate subdirectory.
- **Prioritize Using Existing Patterns**: Before writing new test utilities, check `TestUtils.java` and existing tests for reusable patterns.
- **Creating Test Data**: If a test requires specific data not provided by existing resources, create that data as part of test setup or add new resource files.

## CI/CD

GitHub Actions workflow (`.github/workflows/validation.yml`) runs on PRs to `main`:
1. Spotless check (formatting)
2. All tests

## Design Documentation

Design documents are in `docs/` following the template in `0000_design_document_template.md`:
- `0001_error_handling.md` - Exception hierarchy and exit codes
- `0002_validation_rules.md` - Validation rule system
- `0003_validation_engine.md` - Validation engine architecture

## Common Tasks

### Adding a New CLI Command

1. Create class in `cli/` extending `AbstractCommand`
2. Annotate with `@CommandLine.Command`
3. Register in `Main.java` `@Command(subcommands = {...})`

### Adding a New Validation Rule

1. Create class in `validation/builtin/`
2. Implement validation interface
3. Register with `ValidationRegistry`
4. Add tests in `src/test/.../validation/builtin/`

### Adding a New Feature Fix

1. Create class in `validation/fix/`
2. Implement fix logic
3. Add tests in `src/test/.../validation/fix/`

## Dependencies

Main external dependency for flat file parsing:
```groovy
implementation('uk.ac.ebi.ena.sequence:sequencetools:2.+')
```

This is fetched from EBI's GitLab Maven repository.

## Publishing

To publish to EBI GitLab Maven repository:
1. Create `gradle.properties` with `gitlab_private_token=<token>`
2. Run `./gradlew publish`

To deploy to Codon cluster:
```bash
./gradlew deploy
```

## Local Development

### Running the CLI Tool

After building, you can run the tool directly:
```bash
# Using the shadow JAR
java -jar build/libs/gff3tools-*-all.jar help

# Example conversion
java -jar build/libs/gff3tools-*-all.jar conversion input.embl output.gff3

# With increased memory for large files
java -Xmx2G -jar build/libs/gff3tools-*-all.jar conversion large_file.embl output.gff3
```

### Code Formatting and Verification

Before committing code, always run:
```bash
# Format code
./gradlew spotlessApply

# Run tests
./gradlew test

# Full verification (what CI runs)
./gradlew spotlessCheck test
```

The goal should be zero spotless violations and all tests passing.
