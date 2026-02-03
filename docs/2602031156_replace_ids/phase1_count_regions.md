# Phase 1: Count Regions Sub-command

**Estimated Effort**: 2 days

## Overview
Phase 1 implements the `count-regions` sub-command that counts the number of sequence regions in a GFF3 file. This command provides users with the count needed to prepare external accessions for the subsequent `replace-ids` operation. The implementation uses streaming to handle large files efficiently and supports both file input and stdin.

## Prerequisites
- None (this is the first phase)
- Gradle build system configured
- JUnit 5 test framework available

## Codebase Context

Based on exploration, the following patterns and structures will guide implementation:

### Existing Patterns
- **Sub-command Registration**: `Main.java` uses Picocli's `@Command(subcommands={...})` to register commands
- **Stdin/Stdout Support**: `AbstractCommand.getPipe()` handles file vs. stdin/stdout detection via empty path strings
- **GFF3 Parsing**: `GFF3FileReader` already parses `##sequence-region` directives using `SEQUENCE_REGION_DIRECTIVE` pattern
- **Error Handling**: `ExitException` subclasses map to `CLIExitCode` values via `ExecutionExceptionHandler`
- **Logging Control**: `LoggerContext` set to ERROR level when writing to stdout (see `FileConversionCommand`)

### Critical Discovery
**Sequence Region Ordering**: `GFF3FileReader.accessionSequenceRegionMap` uses `TreeMap<String, GFF3SequenceRegion>`, which sorts by key (accession). However, the spec requires **sequential mapping in file order** (first accession in file → first provided accession). 

**Solution**: For counting, use streaming to avoid loading into TreeMap. For replace-ids (Phase 2), we'll need a `LinkedHashMap` to preserve insertion order.

## Steps

### Step 1.1: Refactor FileProcessCommand to Parent Command
**Files**: `src/main/java/uk/ac/ebi/embl/gff3tools/cli/FileProcessCommand.java`  
**Pattern Reference**: Based on `Main.java` subcommand registration pattern

**Action**: Transform FileProcessCommand from a runnable command to a parent command container.

**Before**:
```java
@CommandLine.Command(name = "process", description = "Performs the file processing of gff3 & fasta files")
@Slf4j
public class FileProcessCommand extends AbstractCommand {
    @CommandLine.Option(names = "-accessions", ...)
    private List<String> accessions;
    // ... existing options ...
    
    @Override
    public void run() {
        // TODO: process gff3 + fasta files
    }
}
```

**After**:
```java
@CommandLine.Command(
    name = "process", 
    description = "Performs file processing operations on GFF3 files",
    subcommands = {
        CountRegionsCommand.class,
        CommandLine.HelpCommand.class
    }
)
@Slf4j
public class FileProcessCommand extends AbstractCommand {
    // Remove instance variables - they belong in sub-commands now
    
    @Override
    public void run() {
        // Parent command does nothing - user must specify a sub-command
        // Picocli will show help automatically if no sub-command provided
    }
}
```

**Verify**: 
```bash
./gradlew build
java -jar build/libs/gff3tools-1.0-all.jar process --help
# Should show: Available commands: count-regions
```

---

### Step 1.2: Create CountRegionsCommand Class
**Files**: `src/main/java/uk/ac/ebi/embl/gff3tools/cli/CountRegionsCommand.java` (new)  
**Pattern Reference**: Based on `ValidationCommand.java` structure

**Action**: Create new sub-command that counts sequence regions using streaming.

**Implementation**:
```java
/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.gff3tools.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.InvalidGFF3HeaderException;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

import java.util.HashMap;
import java.util.Map;

@CommandLine.Command(
    name = "count-regions",
    description = "Counts the number of sequence regions in a GFF3 file"
)
@Slf4j
public class CountRegionsCommand extends AbstractCommand {

    // Pattern from GFF3FileReader
    private static final Pattern SEQUENCE_REGION_DIRECTIVE = Pattern.compile(
        "^##sequence-region\\s+(?<accession>(?<accessionId>[^.]+)(?:\\.(?<accessionVersion>\\d+))?)\\s+(?<start>[0-9]+)\\s+(?<end>[0-9]+)$"
    );
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile(
        "^##gff-version (?<version>(?<major>[0-9]+)(\\.(?<minor>[0-9]+)(:?\\.(?<patch>[0-9]+))?)?)\\s*$"
    );
    private static final Pattern COMMENT = Pattern.compile("^#.*$");

    @CommandLine.Parameters(
        paramLabel = "[output-file]",
        defaultValue = "",
        showDefaultValue = CommandLine.Help.Visibility.NEVER,
        arity = "0..1",
        description = "Optional output file (default: stdout)"
    )
    public String outputFilePath;

    @Override
    public void run() {
        try {
            // Determine if writing to stdout
            boolean writingToStdout = outputFilePath == null || outputFilePath.isEmpty();
            
            if (writingToStdout) {
                // Suppress info logs when writing to stdout
                LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
                ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ERROR);
            }

            // Count regions using streaming
            int count = countSequenceRegions();
            
            // Output count
            System.out.println(count);
            
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private int countSequenceRegions() throws Exception {
        int count = 0;
        boolean headerFound = false;
        int lineNumber = 0;

        try (BufferedReader reader = getPipe(
                Files::newBufferedReader,
                () -> new BufferedReader(new InputStreamReader(System.in)),
                inputFilePath)) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip blank lines
                if (line.isBlank()) {
                    continue;
                }

                // Check for FASTA section - stop counting after this
                if (line.startsWith("##FASTA")) {
                    break;
                }

                // Validate GFF3 header as first directive
                if (!headerFound && !COMMENT.matcher(line).matches()) {
                    Matcher versionMatcher = VERSION_DIRECTIVE.matcher(line);
                    if (!versionMatcher.matches()) {
                        throw new ValidationException(
                            "Invalid GFF3 file: expected ##gff-version directive at line " + lineNumber
                        );
                    }
                    headerFound = true;
                    continue;
                }

                // Count sequence-region directives
                Matcher matcher = SEQUENCE_REGION_DIRECTIVE.matcher(line);
                if (matcher.matches()) {
                    count++;
                }
            }

            if (!headerFound) {
                throw new ValidationException("Invalid GFF3 file: no ##gff-version directive found");
            }

        }

        return count;
    }
}
```

**Verify**:
```bash
./gradlew build

# Test with file input
echo -e '##gff-version 3\n##sequence-region seq1 1 100\n##sequence-region seq2 1 200' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process count-regions test.gff3
# Expected output: 2

# Test with stdin
cat test.gff3 | java -jar build/libs/gff3tools-1.0-all.jar process count-regions
# Expected output: 2

rm test.gff3
```

---

### Step 1.3: Update FileProcessCommand to Remove Old Implementation
**Files**: `src/main/java/uk/ac/ebi/embl/gff3tools/cli/FileProcessCommand.java`  

**Action**: Remove the old implementation that accepted accessions, gff3, and fasta parameters. Keep only the parent command structure.

**Complete Updated File**:
```java
/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.gff3tools.cli;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "process",
    description = "Performs file processing operations on GFF3 files",
    subcommands = {
        CountRegionsCommand.class,
        CommandLine.HelpCommand.class
    }
)
@Slf4j
public class FileProcessCommand extends AbstractCommand {

    @Override
    public void run() {
        // Parent command does nothing - Picocli shows help if no sub-command specified
        CommandLine.usage(this, System.out);
    }
}
```

**Verify**:
```bash
./gradlew build
java -jar build/libs/gff3tools-1.0-all.jar process
# Should show help with count-regions sub-command listed
```

---

### Step 1.4: Create Unit Tests for CountRegionsCommand
**Files**: `src/test/java/uk/ac/ebi/embl/gff3tools/cli/CountRegionsCommandTest.java` (new)  
**Pattern Reference**: Based on `ValidationCommandTest.java` and `MainTest.java`

**Action**: Create comprehensive unit tests covering various scenarios.

**Implementation**:
```java
/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.gff3tools.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class CountRegionsCommandTest {

    private CountRegionsCommand command;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    public void setUp() {
        command = new CountRegionsCommand();
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    public void testCountZeroRegions() throws IOException {
        // GFF3 file with no sequence regions
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile, "##gff-version 3\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("0", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testCountOneRegion() throws IOException {
        // GFF3 file with one sequence region
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile, 
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n"
        );

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("1", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testCountMultipleRegions() throws IOException {
        // GFF3 file with multiple sequence regions
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "##sequence-region BN000066.2 1 3000\n" +
            "##sequence-region BN000067 1 2000\n"
        );

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("3", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testCountRegionsWithFeatures() throws IOException {
        // GFF3 file with sequence regions and features
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n" +
            "##sequence-region BN000066.2 1 3000\n" +
            "BN000066.2\tENA\tgene\t200\t600\t.\t-\t.\tID=gene2\n"
        );

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("2", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testCountRegionsStopsAtFASTA() throws IOException {
        // GFF3 file with FASTA section - should only count regions before FASTA
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "##sequence-region BN000066.2 1 3000\n" +
            "##FASTA\n" +
            ">seq1\n" +
            "ATGCATGC\n"
        );

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("2", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testCountRegionsWithBlankLines() throws IOException {
        // GFF3 file with blank lines (should be ignored)
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile,
            "##gff-version 3\n" +
            "\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "\n" +
            "##sequence-region BN000066.2 1 3000\n"
        );

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("2", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testInvalidGFF3NoHeader() throws IOException {
        // Invalid GFF3 file - no version directive
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile,
            "##sequence-region BN000065.1 1 5000\n"
        );

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertThrows(RuntimeException.class, () -> command.run());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testNonExistentFile() {
        String[] args = new String[] {"non_existent_file.gff3"};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertThrows(RuntimeException.class, () -> command.run());
    }

    @Test
    public void testCountRegionsWithVersionNumbers() throws IOException {
        // Test both with and without version numbers
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "##sequence-region BN000066 1 3000\n" +
            "##sequence-region BN000067.12 1 2000\n"
        );

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("3", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }
}
```

**Verify**:
```bash
./gradlew test --tests CountRegionsCommandTest
# All tests should pass
```

---

### Step 1.5: Create Integration Tests
**Files**: `src/test/java/uk/ac/ebi/embl/gff3tools/cli/CountRegionsIntegrationTest.java` (new)  
**Pattern Reference**: Based on `MainIntegrationTest.java`

**Action**: Create integration tests that test the full command flow through Main.

**Implementation**:
```java
/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.gff3tools.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class CountRegionsIntegrationTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    public void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    public void testFullCommandFlow() throws IOException {
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "##sequence-region BN000066.2 1 3000\n"
        );

        String[] args = new String[] {
            "process", "count-regions", tempFile.toString()
        };

        int exitCode = new CommandLine(new Main()).execute(args);
        assertEquals(0, exitCode);
        assertEquals("2", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testWithRealGFF3File() throws IOException {
        // Use the demo file from test resources if available
        Path demoFile = Path.of("src/test/resources/demo/OZ026791.gff3");
        if (Files.exists(demoFile)) {
            String[] args = new String[] {
                "process", "count-regions", demoFile.toString()
            };

            int exitCode = new CommandLine(new Main()).execute(args);
            assertEquals(0, exitCode);
            // OZ026791.gff3 has 1 sequence region
            assertEquals("1", outContent.toString().trim());
        }
    }

    @Test
    public void testNonExistentFile() {
        String[] args = new String[] {
            "process", "count-regions", "non_existent_file.gff3"
        };

        int exitCode = new CommandLine(new Main()).execute(args);
        assertNotEquals(0, exitCode);
    }

    @Test
    public void testHelpCommand() {
        String[] args = new String[] {"process", "count-regions", "--help"};

        int exitCode = new CommandLine(new Main()).execute(args);
        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("count-regions"));
        assertTrue(outContent.toString().contains("Counts the number of sequence regions"));
    }
}
```

**Verify**:
```bash
./gradlew test --tests CountRegionsIntegrationTest
# All tests should pass
```

---

### Step 1.6: Add Test Resources
**Files**: `src/test/resources/process/count-regions-test.gff3` (new)  

**Action**: Create test GFF3 files for testing.

**Implementation**:
```gff3
##gff-version 3
##sequence-region BN000065.1 1 5000
##sequence-region BN000066.2 1 3000
##sequence-region BN000067 1 2000
BN000065.1	ENA	gene	100	500	.	+	.	ID=gene1
BN000065.1	ENA	CDS	100	500	.	+	0	ID=cds1;Parent=gene1
BN000066.2	ENA	gene	200	600	.	-	.	ID=gene2
##FASTA
>cds1
ATGCATGC
```

**Verify**: File created and parseable.

---

### Step 1.7: Update Documentation
**Files**: `README.md`  

**Action**: Add documentation for the new count-regions sub-command.

**Add to README.md** (after the "Command Line Tool Usage" section):

```markdown
### Process Command

#### Count Sequence Regions

Count the number of sequence regions in a GFF3 file:

```bash
java -jar gff3tools-1.0-all.jar process count-regions input.gff3
```

Output: A single integer representing the number of `##sequence-region` directives found.

**Using stdin:**

```bash
cat input.gff3 | java -jar gff3tools-1.0-all.jar process count-regions
```

**Use Case**: Determine how many external accessions are needed before running `replace-ids` (Phase 2).

**Example**:
```bash
# Count regions
$ java -jar gff3tools-1.0-all.jar process count-regions sample.gff3
3

# This tells you that you need to provide 3 accessions for replace-ids
```
```

**Verify**: Documentation is clear and consistent with existing style.

---

## Files Summary

### New Files
| File | Purpose | Pattern From |
|------|---------|--------------|
| `src/main/java/uk/ac/ebi/embl/gff3tools/cli/CountRegionsCommand.java` | Count regions sub-command implementation | ValidationCommand.java |
| `src/test/java/uk/ac/ebi/embl/gff3tools/cli/CountRegionsCommandTest.java` | Unit tests for count-regions | ValidationCommandTest.java |
| `src/test/java/uk/ac/ebi/embl/gff3tools/cli/CountRegionsIntegrationTest.java` | Integration tests | MainIntegrationTest.java |
| `src/test/resources/process/count-regions-test.gff3` | Test resource file | Existing test resources |

### Modified Files
| File | Changes |
|------|---------|
| `src/main/java/uk/ac/ebi/embl/gff3tools/cli/FileProcessCommand.java` | Refactored to parent command with subcommands; removed old implementation |
| `README.md` | Added documentation for count-regions sub-command |

## Testing Strategy

### Unit Tests (Step 1.4)
- ✅ Zero regions
- ✅ One region
- ✅ Multiple regions
- ✅ Regions with features
- ✅ Stops counting at FASTA section
- ✅ Handles blank lines
- ✅ Validates GFF3 header
- ✅ Rejects invalid GFF3
- ✅ Handles non-existent files
- ✅ Handles version numbers in accessions

### Integration Tests (Step 1.5)
- ✅ Full command flow through Main
- ✅ Real GFF3 file from test resources
- ✅ Non-existent file error handling
- ✅ Help command

### Manual Testing
```bash
# Build the project
./gradlew clean build

# Test 1: Count regions in a file
echo -e '##gff-version 3\n##sequence-region seq1 1 100\n##sequence-region seq2 1 200' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process count-regions test.gff3
# Expected: 2

# Test 2: Count regions from stdin
cat test.gff3 | java -jar build/libs/gff3tools-1.0-all.jar process count-regions
# Expected: 2

# Test 3: Invalid GFF3 (no header)
echo '##sequence-region seq1 1 100' > invalid.gff3
java -jar build/libs/gff3tools-1.0-all.jar process count-regions invalid.gff3
# Expected: Error message about missing gff-version directive
echo $?
# Expected: Non-zero exit code (20 for VALIDATION_ERROR)

# Test 4: Non-existent file
java -jar build/libs/gff3tools-1.0-all.jar process count-regions nonexistent.gff3
echo $?
# Expected: Exit code 12 (NON_EXISTENT_FILE)

# Test 5: Help
java -jar build/libs/gff3tools-1.0-all.jar process count-regions --help
# Expected: Help text with command description

# Cleanup
rm test.gff3 invalid.gff3
```

## Completion Checklist

- [ ] Step 1.1: FileProcessCommand refactored to parent command
- [ ] Step 1.2: CountRegionsCommand class created
- [ ] Step 1.3: Old FileProcessCommand implementation removed
- [ ] Step 1.4: Unit tests created and passing
- [ ] Step 1.5: Integration tests created and passing
- [ ] Step 1.6: Test resources added
- [ ] Step 1.7: README.md updated
- [ ] All tests pass: `./gradlew test`
- [ ] Build succeeds: `./gradlew build`
- [ ] Manual tests pass (see Testing Strategy section)
- [ ] Code follows project conventions (Lombok annotations, copyright headers, Slf4j logging)
- [ ] Exit codes are correct (SUCCESS=0, VALIDATION_ERROR=20, NON_EXISTENT_FILE=12)
- [ ] Stdin/stdout support works correctly
- [ ] Log level set to ERROR when writing to stdout

## Dependencies for Phase 2

Phase 2 (Replace IDs Sub-command) will depend on:
- ✅ FileProcessCommand structure in place
- ✅ Sub-command pattern established
- ✅ Stdin/stdout handling pattern verified
- ✅ Testing patterns established
- ✅ Understanding of sequence region parsing requirements

## Notes

### Critical Implementation Details

1. **Streaming Approach**: CountRegionsCommand uses `BufferedReader.readLine()` directly instead of `GFF3FileReader` to avoid loading the entire file into memory. This is essential for large files.

2. **Ordering Issue**: The existing `GFF3FileReader.accessionSequenceRegionMap` uses `TreeMap`, which sorts by key. For Phase 2's replace-ids, we'll need to preserve insertion order using `LinkedHashMap` instead.

3. **Validation**: CountRegionsCommand performs basic GFF3 validation (checks for ##gff-version header) but doesn't use the full `ValidationEngine` to keep it lightweight and fast.

4. **FASTA Handling**: Counting stops at the `##FASTA` directive, as sequence regions should only appear in the header/annotation section.

5. **Blank Lines**: Blank lines are explicitly ignored per GFF3 spec.

6. **Exit Codes**: ValidationException is used for invalid GFF3 files, which will be caught by ExecutionExceptionHandler and translated to exit code 20 (VALIDATION_ERROR).

### Design Decisions

- **Why not use GFF3FileReader?** GFF3FileReader loads all annotations into memory and uses a TreeMap that changes ordering. For counting, we only need to parse directives, not full annotations. This streaming approach is more memory-efficient.

- **Why separate unit and integration tests?** Unit tests focus on the command logic in isolation. Integration tests verify the full CLI pipeline including argument parsing, exception handling, and exit codes.

- **Why suppress logs to stdout?** When outputting the count to stdout (for piping), we don't want log messages to contaminate the numeric output. This follows the same pattern as FileConversionCommand.

### Potential Issues & Mitigations

| Issue | Mitigation |
|-------|-----------|
| Large files causing memory issues | Use streaming (BufferedReader) not GFF3FileReader |
| Invalid GFF3 format | Validate header before counting; throw ValidationException |
| Mixed line endings (CRLF vs LF) | BufferedReader.readLine() handles both |
| Files without ##FASTA section | No issue - counting continues to EOF |
| Empty files | Returns 0 (valid GFF3 can have zero sequence regions) |

### Future Enhancements (Out of Scope for Phase 1)

- Support for counting other GFF3 directive types
- JSON output format option
- Detailed statistics (min/max region lengths, etc.)
- Progress indicator for large files
