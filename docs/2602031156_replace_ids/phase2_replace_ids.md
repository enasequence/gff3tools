# Phase 2: Replace IDs Sub-command

**Estimated Effort**: 3 days

## Overview
Phase 2 implements the `replace-ids` sub-command that replaces sequence region identifiers throughout a GFF3 file with externally provided accessions. The command validates that the number of provided accessions matches the number of sequence regions, builds a replacement map in file order, and performs replacements in both `##sequence-region` directives and feature seqid columns. The FASTA section is copied unchanged. The implementation supports both file I/O and stdin/stdout for Unix pipes.

## Prerequisites
- Phase 1 complete (FileProcessCommand refactored, CountRegionsCommand implemented)
- Gradle build system configured
- JUnit 5 test framework available

## Codebase Context

Based on exploration, the following patterns and structures will guide implementation:

### Existing Patterns
- **Sub-command Registration**: FileProcessCommand now accepts sub-commands via `@Command(subcommands={...})`
- **Stdin/Stdout Support**: AbstractCommand.getPipe() handles file vs. stdin/stdout via empty path strings
- **GFF3 Parsing**: GFF3FileReader parses `##sequence-region` using `SEQUENCE_REGION_DIRECTIVE` pattern
- **GFF3 Writing**: Each IGFF3Feature implements `writeGFF3String(Writer)` method
- **Accession Building**: GFF3SequenceRegion.accession() and GFF3Feature.accession() build full accession with version suffix
- **Error Handling**: ExitException subclasses map to CLIExitCode via ExecutionExceptionHandler
- **Logging Control**: Set LoggerContext to ERROR level when writing to stdout

### Critical Discovery
**Insertion Order Requirement**: The spec requires sequential mapping (1st accession in file → 1st provided accession). GFF3FileReader uses TreeMap which sorts by key. **Solution**: Use LinkedHashMap in our implementation to preserve file order.

**FASTA Section Handling**: FASTA headers in gff3tools-generated files reference feature IDs (translations), not sequence regions. Per spec, FASTA section is copied unchanged without ID replacement.

**Version Number Handling**: Sequence regions may have versions (e.g., `BN000065.1`). Provided accessions are versionless. When replacing, use provided accession as-is, removing any original version suffix.

### Implementation Strategy
1. **Two-Pass Approach**:
   - Pass 1: Count sequence regions, validate count matches accessions, build replacement map
   - Pass 2: Stream read, replace, and write

2. **Streaming for Memory Efficiency**: Process line-by-line rather than loading entire file

3. **Replacement Map**: `LinkedHashMap<String, String>` maps original accession → new accession in insertion order

4. **Line-by-Line Processing**:
   - Detect `##sequence-region` directive → replace accession in directive
   - Detect feature line (non-directive, non-comment) → replace column 1 (seqid)
   - Detect `##FASTA` → switch to copy mode (no replacements)

## Steps

### Step 2.1: Create ReplaceIdsCommand Class
**Files**: `src/main/java/uk/ac/ebi/embl/gff3tools/cli/ReplaceIdsCommand.java` (new)  
**Pattern Reference**: Based on FileConversionCommand.java and CountRegionsCommand.java

**Action**: Create new sub-command that replaces sequence region IDs throughout a GFF3 file.

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
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;

@CommandLine.Command(
    name = "replace-ids",
    description = "Replaces sequence region IDs in a GFF3 file with provided accessions"
)
@Slf4j
public class ReplaceIdsCommand extends AbstractCommand {

    // Patterns from GFF3FileReader
    private static final Pattern SEQUENCE_REGION_DIRECTIVE = Pattern.compile(
        "^##sequence-region\\s+(?<accession>(?<accessionId>[^.]+)(?:\\.(?<accessionVersion>\\d+))?)\\s+(?<start>[0-9]+)\\s+(?<end>[0-9]+)$"
    );
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile(
        "^##gff-version (?<version>(?<major>[0-9]+)(\\.(?<minor>[0-9]+)(:?\\.(?<patch>[0-9]+))?)?)\\s*$"
    );
    private static final Pattern COMMENT = Pattern.compile("^#.*$");
    private static final Pattern FASTA_DIRECTIVE = Pattern.compile("^##FASTA$");
    
    // Pattern to detect feature lines (9 tab-separated columns)
    private static final Pattern FEATURE_LINE = Pattern.compile("^[^#].*");

    @CommandLine.Option(
        names = "--accessions",
        description = "Comma-separated list of accessions to replace sequence region IDs with",
        required = true,
        split = ","
    )
    public List<String> accessions;

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
            // Trim whitespace from accessions
            List<String> trimmedAccessions = accessions.stream()
                .map(String::trim)
                .toList();
            
            // Validate accessions are non-empty
            validateAccessions(trimmedAccessions);
            
            // Determine if writing to stdout
            boolean writingToStdout = outputFilePath == null || outputFilePath.isEmpty();
            
            if (writingToStdout) {
                // Suppress info logs when writing to stdout
                LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
                ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ERROR);
            }

            // Build replacement map (validates count matches)
            Map<String, String> replacementMap = buildReplacementMap(trimmedAccessions);
            
            // Perform replacement
            performReplacement(replacementMap, writingToStdout);
            
        } catch (CLIException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void validateAccessions(List<String> accessions) throws CLIException {
        for (int i = 0; i < accessions.size(); i++) {
            if (accessions.get(i).isEmpty()) {
                throw new CLIException("Accession at position " + (i + 1) + " is empty or blank");
            }
        }
    }

    private Map<String, String> buildReplacementMap(List<String> newAccessions) throws Exception {
        Map<String, String> replacementMap = new LinkedHashMap<>();
        List<String> originalAccessions = new ArrayList<>();
        boolean headerFound = false;
        int lineNumber = 0;

        // First pass: collect sequence regions in order
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

                // Stop at FASTA section
                if (FASTA_DIRECTIVE.matcher(line).matches()) {
                    break;
                }

                // Validate GFF3 header
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

                // Collect sequence-region directives
                Matcher matcher = SEQUENCE_REGION_DIRECTIVE.matcher(line);
                if (matcher.matches()) {
                    String accession = matcher.group("accession");
                    originalAccessions.add(accession);
                }
            }

            if (!headerFound) {
                throw new ValidationException("Invalid GFF3 file: no ##gff-version directive found");
            }
        }

        // Validate count matches
        if (originalAccessions.size() != newAccessions.size()) {
            throw new CLIException(
                "Accession count mismatch: file has " + originalAccessions.size() + 
                " sequence regions but " + newAccessions.size() + " accessions were provided"
            );
        }

        // Build replacement map
        for (int i = 0; i < originalAccessions.size(); i++) {
            replacementMap.put(originalAccessions.get(i), newAccessions.get(i));
        }

        return replacementMap;
    }

    private void performReplacement(Map<String, String> replacementMap, boolean writingToStdout) throws Exception {
        int replacementCount = 0;
        boolean inFastaSection = false;

        try (BufferedReader reader = getPipe(
                Files::newBufferedReader,
                () -> new BufferedReader(new InputStreamReader(System.in)),
                inputFilePath);
             BufferedWriter writer = getPipe(
                Files::newBufferedWriter,
                () -> new BufferedWriter(new OutputStreamWriter(System.out)),
                outputFilePath.isEmpty() ? null : java.nio.file.Path.of(outputFilePath))) {

            String line;
            while ((line = reader.readLine()) != null) {

                // Check if we've entered the FASTA section
                if (FASTA_DIRECTIVE.matcher(line).matches()) {
                    inFastaSection = true;
                    writer.write(line);
                    writer.write("\n");
                    continue;
                }

                // In FASTA section: copy unchanged
                if (inFastaSection) {
                    writer.write(line);
                    writer.write("\n");
                    continue;
                }

                // Replace in ##sequence-region directives
                Matcher seqRegionMatcher = SEQUENCE_REGION_DIRECTIVE.matcher(line);
                if (seqRegionMatcher.matches()) {
                    String originalAccession = seqRegionMatcher.group("accession");
                    String newAccession = replacementMap.get(originalAccession);
                    
                    if (newAccession != null) {
                        String start = seqRegionMatcher.group("start");
                        String end = seqRegionMatcher.group("end");
                        
                        String replacedLine = String.format("##sequence-region %s %s %s", 
                            newAccession, start, end);
                        
                        writer.write(replacedLine);
                        writer.write("\n");
                        
                        if (!writingToStdout) {
                            log.info("Replaced sequence region: {} -> {}", originalAccession, newAccession);
                        }
                        replacementCount++;
                        continue;
                    }
                }

                // Replace in feature lines (column 1 is seqid)
                if (FEATURE_LINE.matcher(line).matches() && line.contains("\t")) {
                    String[] parts = line.split("\t", 2);
                    if (parts.length >= 2) {
                        String seqid = parts[0];
                        
                        // Check if this seqid needs replacement
                        String newAccession = replacementMap.get(seqid);
                        if (newAccession != null) {
                            writer.write(newAccession);
                            writer.write("\t");
                            writer.write(parts[1]);
                            writer.write("\n");
                            continue;
                        }
                    }
                }

                // No replacement needed: write line as-is
                writer.write(line);
                writer.write("\n");
            }

            if (!writingToStdout) {
                log.info("Replacement complete: {} sequence regions replaced", replacementCount);
            }
        }
    }
}
```

**Verify**:
```bash
./gradlew build

# Create test file
cat > test.gff3 << 'EOF'
##gff-version 3
##sequence-region BN000065.1 1 5000
##sequence-region BN000066.2 1 3000
BN000065.1	ENA	gene	100	500	.	+	.	ID=gene1
BN000066.2	ENA	gene	200	600	.	-	.	ID=gene2
##FASTA
>cds1
ATGC
EOF

# Test replacement
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions ACC123,ACC456 test.gff3 out.gff3
cat out.gff3
# Expected: ACC123 and ACC456 in place of BN000065.1 and BN000066.2

rm test.gff3 out.gff3
```

---

### Step 2.2: Register ReplaceIdsCommand in FileProcessCommand
**Files**: `src/main/java/uk/ac/ebi/embl/gff3tools/cli/FileProcessCommand.java`  
**Pattern Reference**: Existing subcommand registration

**Action**: Add ReplaceIdsCommand to FileProcessCommand's subcommands list.

**Before**:
```java
@CommandLine.Command(
    name = "process",
    description = "Performs file processing operations on GFF3 files",
    subcommands = {
        CountRegionsCommand.class,
        CommandLine.HelpCommand.class
    }
)
```

**After**:
```java
@CommandLine.Command(
    name = "process",
    description = "Performs file processing operations on GFF3 files",
    subcommands = {
        CountRegionsCommand.class,
        ReplaceIdsCommand.class,
        CommandLine.HelpCommand.class
    }
)
```

**Verify**:
```bash
./gradlew build
java -jar build/libs/gff3tools-1.0-all.jar process --help
# Should list both count-regions and replace-ids
```

---

### Step 2.3: Create Unit Tests for ReplaceIdsCommand
**Files**: `src/test/java/uk/ac/ebi/embl/gff3tools/cli/ReplaceIdsCommandTest.java` (new)  
**Pattern Reference**: Based on CountRegionsCommandTest.java

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class ReplaceIdsCommandTest {

    @Test
    public void testReplaceOneRegion() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n"
        );

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        String[] args = new String[] {
            "--accessions", "ACC123",
            tempInput.toString(),
            tempOutput.toString()
        };
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        assertTrue(output.contains("##sequence-region ACC123 1 5000"));
        assertTrue(output.contains("ACC123\tENA\tgene\t100\t500"));
        assertFalse(output.contains("BN000065"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testReplaceMultipleRegions() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "##sequence-region BN000066.2 1 3000\n" +
            "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n" +
            "BN000066.2\tENA\tgene\t200\t600\t.\t-\t.\tID=gene2\n"
        );

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        String[] args = new String[] {
            "--accessions", "ACC123,ACC456",
            tempInput.toString(),
            tempOutput.toString()
        };
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        assertTrue(output.contains("##sequence-region ACC123 1 5000"));
        assertTrue(output.contains("##sequence-region ACC456 1 3000"));
        assertTrue(output.contains("ACC123\tENA\tgene\t100\t500"));
        assertTrue(output.contains("ACC456\tENA\tgene\t200\t600"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testPreservesFastaSection() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n" +
            "##FASTA\n" +
            ">gene1\n" +
            "ATGCATGC\n"
        );

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        String[] args = new String[] {
            "--accessions", "ACC123",
            tempInput.toString(),
            tempOutput.toString()
        };
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        assertTrue(output.contains("##FASTA"));
        assertTrue(output.contains(">gene1"));
        assertTrue(output.contains("ATGCATGC"));
        // FASTA header should NOT be replaced
        assertFalse(output.contains(">ACC123"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testAccessionCountMismatch_TooFew() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "##sequence-region BN000066.2 1 3000\n"
        );

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        String[] args = new String[] {
            "--accessions", "ACC123",  // Only 1 accession for 2 regions
            tempInput.toString(),
            tempOutput.toString()
        };
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> command.run());
        assertTrue(exception.getMessage().contains("Accession count mismatch"));
        assertTrue(exception.getMessage().contains("file has 2 sequence regions"));
        assertTrue(exception.getMessage().contains("1 accessions were provided"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testAccessionCountMismatch_TooMany() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n"
        );

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        String[] args = new String[] {
            "--accessions", "ACC123,ACC456,ACC789",  // 3 accessions for 1 region
            tempInput.toString(),
            tempOutput.toString()
        };
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> command.run());
        assertTrue(exception.getMessage().contains("Accession count mismatch"));
        assertTrue(exception.getMessage().contains("file has 1 sequence regions"));
        assertTrue(exception.getMessage().contains("3 accessions were provided"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testEmptyAccession() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n"
        );

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        String[] args = new String[] {
            "--accessions", "",
            tempInput.toString(),
            tempOutput.toString()
        };
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> command.run());
        assertTrue(exception.getMessage().contains("empty or blank"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testBlankAccessionInList() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "##sequence-region BN000066.2 1 3000\n"
        );

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        String[] args = new String[] {
            "--accessions", "ACC123, ,ACC456",  // Blank accession in middle
            tempInput.toString(),
            tempOutput.toString()
        };
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> command.run());
        assertTrue(exception.getMessage().contains("empty or blank"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testTrimsWhitespaceAroundAccessions() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "##sequence-region BN000066.2 1 3000\n"
        );

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        String[] args = new String[] {
            "--accessions", " ACC123 , ACC456 ",  // Whitespace around accessions
            tempInput.toString(),
            tempOutput.toString()
        };
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        assertTrue(output.contains("##sequence-region ACC123 1 5000"));
        assertTrue(output.contains("##sequence-region ACC456 1 3000"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testRemovesVersionFromOriginal() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        // Original has version .1 and .12
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "##sequence-region BN000066.12 1 3000\n" +
            "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n" +
            "BN000066.12\tENA\tgene\t200\t600\t.\t-\t.\tID=gene2\n"
        );

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        String[] args = new String[] {
            "--accessions", "ACC123,ACC456",  // No versions in provided accessions
            tempInput.toString(),
            tempOutput.toString()
        };
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        // New accessions should not have versions
        assertTrue(output.contains("##sequence-region ACC123 1 5000"));
        assertTrue(output.contains("##sequence-region ACC456 1 3000"));
        assertTrue(output.contains("ACC123\tENA\tgene\t100\t500"));
        assertTrue(output.contains("ACC456\tENA\tgene\t200\t600"));
        // Old accessions with versions should be gone
        assertFalse(output.contains("BN000065.1"));
        assertFalse(output.contains("BN000066.12"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testInvalidGFF3NoHeader() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        Files.writeString(tempInput,
            "##sequence-region BN000065.1 1 5000\n"
        );

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        String[] args = new String[] {
            "--accessions", "ACC123",
            tempInput.toString(),
            tempOutput.toString()
        };
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertThrows(RuntimeException.class, () -> command.run());

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testSequentialMapping() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        // Important: File order is ZZZ, AAA, MMM (not alphabetical)
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region ZZZ999 1 1000\n" +
            "##sequence-region AAA111 1 2000\n" +
            "##sequence-region MMM555 1 3000\n" +
            "ZZZ999\tENA\tgene\t100\t200\t.\t+\t.\tID=gene1\n" +
            "AAA111\tENA\tgene\t100\t200\t.\t+\t.\tID=gene2\n" +
            "MMM555\tENA\tgene\t100\t200\t.\t+\t.\tID=gene3\n"
        );

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        String[] args = new String[] {
            "--accessions", "FIRST,SECOND,THIRD",  // Sequential mapping
            tempInput.toString(),
            tempOutput.toString()
        };
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        // First region in file (ZZZ999) -> FIRST
        assertTrue(output.contains("##sequence-region FIRST 1 1000"));
        assertTrue(output.contains("FIRST\tENA\tgene\t100\t200\t.\t+\t.\tID=gene1"));
        // Second region in file (AAA111) -> SECOND
        assertTrue(output.contains("##sequence-region SECOND 1 2000"));
        assertTrue(output.contains("SECOND\tENA\tgene\t100\t200\t.\t+\t.\tID=gene2"));
        // Third region in file (MMM555) -> THIRD
        assertTrue(output.contains("##sequence-region THIRD 1 3000"));
        assertTrue(output.contains("THIRD\tENA\tgene\t100\t200\t.\t+\t.\tID=gene3"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }
}
```

**Verify**:
```bash
./gradlew test --tests ReplaceIdsCommandTest
# All tests should pass
```

---

### Step 2.4: Create Integration Tests
**Files**: `src/test/java/uk/ac/ebi/embl/gff3tools/cli/ReplaceIdsIntegrationTest.java` (new)  
**Pattern Reference**: Based on MainIntegrationTest.java and CountRegionsIntegrationTest.java

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
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

public class ReplaceIdsIntegrationTest {

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
    public void testFullCommandFlowWithFile() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "##sequence-region BN000066.2 1 3000\n" +
            "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n" +
            "BN000066.2\tENA\tgene\t200\t600\t.\t-\t.\tID=gene2\n"
        );

        String[] args = new String[] {
            "process", "replace-ids",
            "--accessions", "ACC123,ACC456",
            tempInput.toString(),
            tempOutput.toString()
        };

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);
            
            Main.main(args);
            
            mock.verify(() -> Main.exit(0));
        }

        String output = Files.readString(tempOutput);
        assertTrue(output.contains("ACC123"));
        assertTrue(output.contains("ACC456"));
        assertFalse(output.contains("BN000065.1"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testStdinStdoutFlow() throws IOException {
        String input = "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n";
        
        Path tempInput = Files.createTempFile("input", ".gff3");
        Files.writeString(tempInput, input);

        // Simulate: cat input.gff3 | replace-ids --accessions ACC123
        // We'll use a file input but empty output to trigger stdout mode
        String[] args = new String[] {
            "process", "replace-ids",
            "--accessions", "ACC123",
            tempInput.toString()
            // No output file -> stdout
        };

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);
            
            Main.main(args);
            
            mock.verify(() -> Main.exit(0));
        }

        String output = outContent.toString();
        assertTrue(output.contains("ACC123"));
        assertFalse(output.contains("BN000065.1"));

        Files.deleteIfExists(tempInput);
    }

    @Test
    public void testAccessionCountMismatchExitCode() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n" +
            "##sequence-region BN000066.2 1 3000\n"
        );

        String[] args = new String[] {
            "process", "replace-ids",
            "--accessions", "ACC123",  // Only 1 for 2 regions
            tempInput.toString()
        };

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);
            
            Main.main(args);
            
            // Should exit with USAGE error
            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
        }

        String errorOutput = errContent.toString();
        assertTrue(errorOutput.contains("Accession count mismatch"));

        Files.deleteIfExists(tempInput);
    }

    @Test
    public void testEmptyAccessionExitCode() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n"
        );

        String[] args = new String[] {
            "process", "replace-ids",
            "--accessions", "",
            tempInput.toString()
        };

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);
            
            Main.main(args);
            
            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
        }

        String errorOutput = errContent.toString();
        assertTrue(errorOutput.contains("empty or blank"));

        Files.deleteIfExists(tempInput);
    }

    @Test
    public void testInvalidGFF3ExitCode() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        
        // Invalid GFF3: no header
        Files.writeString(tempInput,
            "##sequence-region BN000065.1 1 5000\n"
        );

        String[] args = new String[] {
            "process", "replace-ids",
            "--accessions", "ACC123",
            tempInput.toString()
        };

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);
            
            Main.main(args);
            
            mock.verify(() -> Main.exit(CLIExitCode.VALIDATION_ERROR.asInt()));
        }

        Files.deleteIfExists(tempInput);
    }

    @Test
    public void testHelpCommand() {
        String[] args = new String[] {"process", "replace-ids", "--help"};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);
            
            Main.main(args);
            
            mock.verify(() -> Main.exit(0));
        }

        String output = outContent.toString();
        assertTrue(output.contains("replace-ids"));
        assertTrue(output.contains("--accessions"));
    }

    @Test
    public void testMissingAccessions() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        
        Files.writeString(tempInput,
            "##gff-version 3\n" +
            "##sequence-region BN000065.1 1 5000\n"
        );

        String[] args = new String[] {
            "process", "replace-ids",
            // Missing --accessions
            tempInput.toString()
        };

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);
            
            Main.main(args);
            
            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
        }

        String errorOutput = errContent.toString();
        assertTrue(errorOutput.contains("Missing required option") || 
                   errorOutput.contains("--accessions"));

        Files.deleteIfExists(tempInput);
    }
}
```

**Verify**:
```bash
./gradlew test --tests ReplaceIdsIntegrationTest
# All tests should pass
```

---

### Step 2.5: Add Test Resources
**Files**: `src/test/resources/process/replace-ids-test.gff3` (new)  

**Action**: Create test GFF3 file for manual testing.

**Implementation**:
```gff3
##gff-version 3
##sequence-region BN000065.1 1 5000
##sequence-region BN000066.2 1 3000
##sequence-region BN000067 1 2000
BN000065.1	ENA	gene	100	500	.	+	.	ID=gene1
BN000065.1	ENA	CDS	100	500	.	+	0	ID=cds1;Parent=gene1
BN000066.2	ENA	gene	200	600	.	-	.	ID=gene2
BN000067	ENA	gene	300	700	.	+	.	ID=gene3
##FASTA
>cds1
ATGCATGC
>gene2
TTAACCGG
```

**Verify**: File created and parseable.

---

### Step 2.6: Update Documentation
**Files**: `README.md`  

**Action**: Add documentation for the replace-ids sub-command.

**Add to README.md** (after the count-regions section):

```markdown
#### Replace Sequence Region IDs

Replace sequence region IDs throughout a GFF3 file with externally provided accessions:

```bash
java -jar gff3tools-1.0-all.jar process replace-ids --accessions ACC1,ACC2,ACC3 input.gff3 output.gff3
```

**How it works:**
1. Sequence regions are replaced **in the order they appear** in the file
2. The first provided accession replaces the first `##sequence-region` directive
3. The second provided accession replaces the second `##sequence-region` directive, and so on
4. All feature references (column 1) are updated to match the new accessions
5. The FASTA section is copied unchanged (FASTA headers are not replaced)

**Using stdin/stdout:**

```bash
cat input.gff3 | java -jar gff3tools-1.0-all.jar process replace-ids --accessions ACC1,ACC2 > output.gff3
```

**Requirements:**
- The number of provided accessions must **exactly match** the number of sequence regions in the file
- Use `count-regions` to determine how many accessions you need
- Accessions must be non-empty strings
- Whitespace around commas is trimmed automatically

**Example Workflow:**

```bash
# Step 1: Count how many accessions you need
$ java -jar gff3tools-1.0-all.jar process count-regions sample.gff3
3

# Step 2: Replace IDs with 3 external accessions
$ java -jar gff3tools-1.0-all.jar process replace-ids \
    --accessions ERZ123456,ERZ123457,ERZ123458 \
    sample.gff3 \
    output.gff3
```

**Version Number Handling:**
- Original sequence regions may have version numbers (e.g., `BN000065.1`)
- Provided accessions should be without version numbers
- The tool removes version suffixes during replacement

**Example:**
```
Original: BN000065.1
Provided: ACC123
Result:   ACC123 (version .1 is removed)
```

**Error Handling:**
- **Exit code 2 (USAGE)**: If accession count doesn't match sequence region count
- **Exit code 2 (USAGE)**: If any accession is empty or blank
- **Exit code 20 (VALIDATION_ERROR)**: If input file is not valid GFF3
- **Exit code 12 (NON_EXISTENT_FILE)**: If input file doesn't exist
```

**Verify**: Documentation is clear and consistent with existing style.

---

### Step 2.7: Update AbstractCommand getPipe() for Null Path Handling
**Files**: `src/main/java/uk/ac/ebi/embl/gff3tools/cli/AbstractCommand.java`  
**Pattern Reference**: Existing getPipe() implementation

**Action**: Ensure getPipe() correctly handles null Path (for stdout mode).

**Before**:
```java
protected <T> T getPipe(NewPipeFunction<T> newFilePipe, Function0<T> newStdPipe, Path filePath)
        throws ExitException {
    if (!filePath.toString().isEmpty()) {
        try {
            return newFilePipe.apply(filePath, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new NonExistingFile("The file does not exist: " + filePath, e);
        } catch (IOException e) {
            throw new ReadException("Error opening file: " + filePath, e);
        }
    } else {
        return newStdPipe.apply();
    }
}
```

**After** (add null check):
```java
protected <T> T getPipe(NewPipeFunction<T> newFilePipe, Function0<T> newStdPipe, Path filePath)
        throws ExitException {
    if (filePath != null && !filePath.toString().isEmpty()) {
        try {
            return newFilePipe.apply(filePath, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new NonExistingFile("The file does not exist: " + filePath, e);
        } catch (IOException e) {
            throw new ReadException("Error opening file: " + filePath, e);
        }
    } else {
        return newStdPipe.apply();
    }
}
```

**Verify**:
```bash
./gradlew build
# Test that stdout mode works
echo "##gff-version 3" | java -jar build/libs/gff3tools-1.0-all.jar process count-regions
# Should output: 0
```

---

## Files Summary

### New Files
| File | Purpose | Pattern From |
|------|---------|--------------|
| `src/main/java/uk/ac/ebi/embl/gff3tools/cli/ReplaceIdsCommand.java` | Replace IDs sub-command implementation | FileConversionCommand.java, CountRegionsCommand.java |
| `src/test/java/uk/ac/ebi/embl/gff3tools/cli/ReplaceIdsCommandTest.java` | Unit tests for replace-ids | CountRegionsCommandTest.java |
| `src/test/java/uk/ac/ebi/embl/gff3tools/cli/ReplaceIdsIntegrationTest.java` | Integration tests | MainIntegrationTest.java |
| `src/test/resources/process/replace-ids-test.gff3` | Test resource file | Existing test resources |

### Modified Files
| File | Changes |
|------|---------|
| `src/main/java/uk/ac/ebi/embl/gff3tools/cli/FileProcessCommand.java` | Added ReplaceIdsCommand to subcommands list |
| `src/main/java/uk/ac/ebi/embl/gff3tools/cli/AbstractCommand.java` | Added null check in getPipe() method for stdout mode |
| `README.md` | Added documentation for replace-ids sub-command |

## Testing Strategy

### Unit Tests (Step 2.3)
- ✅ Replace one region
- ✅ Replace multiple regions
- ✅ Preserve FASTA section unchanged
- ✅ Accession count mismatch (too few)
- ✅ Accession count mismatch (too many)
- ✅ Empty accession validation
- ✅ Blank accession in list validation
- ✅ Trim whitespace around accessions
- ✅ Remove version numbers from original
- ✅ Invalid GFF3 (no header)
- ✅ Sequential mapping (not alphabetical)

### Integration Tests (Step 2.4)
- ✅ Full command flow with file I/O
- ✅ Stdin/stdout flow
- ✅ Accession count mismatch exit code
- ✅ Empty accession exit code
- ✅ Invalid GFF3 exit code
- ✅ Help command
- ✅ Missing required --accessions option

### Manual Testing
```bash
# Build the project
./gradlew clean build

# Test 1: Basic replacement with file I/O
cat > test.gff3 << 'EOF'
##gff-version 3
##sequence-region BN000065.1 1 5000
##sequence-region BN000066.2 1 3000
BN000065.1	ENA	gene	100	500	.	+	.	ID=gene1
BN000066.2	ENA	gene	200	600	.	-	.	ID=gene2
##FASTA
>cds1
ATGC
EOF

java -jar build/libs/gff3tools-1.0-all.jar process replace-ids \
  --accessions ACC123,ACC456 test.gff3 out.gff3
cat out.gff3
# Expected: ACC123 and ACC456 in place of BN000065.1 and BN000066.2
# FASTA section should be unchanged

# Test 2: Stdin/stdout
cat test.gff3 | java -jar build/libs/gff3tools-1.0-all.jar process replace-ids \
  --accessions ACC123,ACC456 > out2.gff3
cat out2.gff3
# Expected: Same as Test 1

# Test 3: Count mismatch (too few accessions)
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids \
  --accessions ACC123 test.gff3 out.gff3
echo $?
# Expected: Exit code 2 (USAGE)
# Expected error: "file has 2 sequence regions but 1 accessions were provided"

# Test 4: Count mismatch (too many accessions)
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids \
  --accessions ACC123,ACC456,ACC789 test.gff3 out.gff3
echo $?
# Expected: Exit code 2 (USAGE)
# Expected error: "file has 2 sequence regions but 3 accessions were provided"

# Test 5: Empty accession
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids \
  --accessions '' test.gff3 out.gff3
echo $?
# Expected: Exit code 2 (USAGE)
# Expected error: "empty or blank"

# Test 6: Whitespace trimming
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids \
  --accessions " ACC123 , ACC456 " test.gff3 out.gff3
cat out.gff3
# Expected: Works correctly, trims whitespace

# Test 7: Invalid GFF3 (no header)
echo "##sequence-region BN000065.1 1 5000" > invalid.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids \
  --accessions ACC123 invalid.gff3 out.gff3
echo $?
# Expected: Exit code 20 (VALIDATION_ERROR)

# Test 8: Non-existent file
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids \
  --accessions ACC123 nonexistent.gff3 out.gff3
echo $?
# Expected: Exit code 12 (NON_EXISTENT_FILE)

# Test 9: Sequential mapping (order matters)
cat > order.gff3 << 'EOF'
##gff-version 3
##sequence-region ZZZ999 1 1000
##sequence-region AAA111 1 2000
##sequence-region MMM555 1 3000
ZZZ999	ENA	gene	100	200	.	+	.	ID=gene1
AAA111	ENA	gene	100	200	.	+	.	ID=gene2
MMM555	ENA	gene	100	200	.	+	.	ID=gene3
EOF

java -jar build/libs/gff3tools-1.0-all.jar process replace-ids \
  --accessions FIRST,SECOND,THIRD order.gff3 out.gff3
cat out.gff3
# Expected: ZZZ999→FIRST, AAA111→SECOND, MMM555→THIRD (file order, not alphabetical)

# Test 10: Version number removal
cat > version.gff3 << 'EOF'
##gff-version 3
##sequence-region BN000065.1 1 5000
##sequence-region BN000066.12 1 3000
BN000065.1	ENA	gene	100	500	.	+	.	ID=gene1
BN000066.12	ENA	gene	200	600	.	-	.	ID=gene2
EOF

java -jar build/libs/gff3tools-1.0-all.jar process replace-ids \
  --accessions ACC123,ACC456 version.gff3 out.gff3
cat out.gff3
# Expected: ACC123 and ACC456 (no .1 or .12 versions)

# Test 11: Help command
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --help
# Expected: Help text with --accessions option description

# Cleanup
rm test.gff3 out.gff3 out2.gff3 invalid.gff3 order.gff3 version.gff3 2>/dev/null || true
```

---

## Completion Checklist

- [ ] Step 2.1: ReplaceIdsCommand class created
- [ ] Step 2.2: ReplaceIdsCommand registered in FileProcessCommand
- [ ] Step 2.3: Unit tests created and passing
- [ ] Step 2.4: Integration tests created and passing
- [ ] Step 2.5: Test resources added
- [ ] Step 2.6: README.md updated
- [ ] Step 2.7: AbstractCommand.getPipe() updated for null Path
- [ ] All tests pass: `./gradlew test`
- [ ] Build succeeds: `./gradlew build`
- [ ] Manual tests pass (see Testing Strategy section)
- [ ] Code follows project conventions (Lombok annotations, copyright headers, Slf4j logging)
- [ ] Exit codes are correct (USAGE=2, VALIDATION_ERROR=20, NON_EXISTENT_FILE=12)
- [ ] Stdin/stdout support works correctly
- [ ] Log level set to ERROR when writing to stdout
- [ ] Accession trimming works
- [ ] Version numbers are removed from originals
- [ ] Sequential mapping preserves file order
- [ ] FASTA section copied unchanged

## Dependencies for Phase 3

Phase 3 (Integration & Testing) will depend on:
- ✅ CountRegionsCommand implemented and tested (Phase 1)
- ✅ ReplaceIdsCommand implemented and tested (Phase 2)
- ✅ Both sub-commands registered under FileProcessCommand
- ✅ Stdin/stdout patterns working
- ✅ Error handling and exit codes correct

## Notes

### Critical Implementation Details

1. **Two-Pass Approach**: First pass counts and validates, second pass replaces. This is necessary because we read from stdin which cannot be rewound.

2. **LinkedHashMap for Insertion Order**: Unlike GFF3FileReader's TreeMap, we use LinkedHashMap to preserve the order sequence regions appear in the file for sequential mapping.

3. **Line-by-Line Processing**: The performReplacement() method processes the file line-by-line using regex matching rather than full GFF3 parsing to minimize memory usage.

4. **FASTA Section**: After detecting `##FASTA`, we enter copy mode where lines are written unchanged. This preserves FASTA headers that reference feature IDs.

5. **Version Number Handling**: 
   - Original: `BN000065.1` (with version)
   - Provided: `ACC123` (no version)
   - Result: `ACC123` (version removed)
   - The regex capture group `(?<accession>...)` includes the full accession with version, which becomes the map key

6. **Whitespace Trimming**: Picocli's `split = ","` creates a list, which we then trim using `map(String::trim)`.

7. **Feature Line Detection**: We use a simple pattern `^[^#].*` to detect non-directive lines, then split on tab to access column 1 (seqid).

8. **Logging Behavior**: When `writingToStdout` is true, we:
   - Set LoggerContext to ERROR level
   - Skip log.info() calls in the replacement loop
   - Only log errors to stderr

### Design Decisions

- **Why two passes?** Stdin cannot be rewound. We need to count regions before replacement, so we must read twice. For file input, we could optimize to one pass, but keeping consistent behavior is clearer.

- **Why not use GFF3FileReader?** GFF3FileReader loads entire annotations into memory and uses TreeMap (changes order). We need streaming + insertion order preservation.

- **Why regex instead of full GFF3 parsing?** For replacement, we only need to identify lines and extract/replace the accession column. Full parsing would be slower and use more memory.

- **Why LinkedHashMap?** Preserves insertion order, which is critical for sequential mapping per spec.

### Potential Issues & Mitigations

| Issue | Mitigation |
|-------|-----------|
| Large files causing memory issues | Use line-by-line streaming, not full file loading |
| Invalid GFF3 format | Validate header before building map; throw ValidationException |
| Accession count mismatch | Validate in buildReplacementMap() before replacement; throw CLIException |
| Empty accessions | Validate in validateAccessions() before processing |
| Multiple features with same seqid | Replacement map handles this - all features with same seqid get replaced |
| FASTA headers containing sequence region IDs | Spec states this doesn't happen in gff3tools-generated files; we copy FASTA unchanged |
| Version suffix edge cases | Regex pattern handles 1+ digits after dot: `\\.(?<accessionVersion>\\d+)` |

### Future Enhancements (Out of Scope for Phase 2)

- Support for `--accessions-file` option to read accessions from a file (for many sequence regions)
- Dry-run mode to preview replacements without writing output
- Support for key-value mapping format (e.g., `BN000065.1:ACC123,BN000066.2:ACC456`)
- Validation of accession format (e.g., regex patterns, repository-specific rules)
- Progress indicator for large files
- Batch processing multiple files in one command
