# gff3tools

gff3tools is a Java based library and command line utility for converting EMBL flat files to GFF3 format, and vice versa.
It uses [sequencetools](https://github.com/enasequence/sequencetools) to read the flat file.

# Conversion Rules and Assumptions

Conversion rules and Assumptions are added to the code under `// Rule: ` and `// Assumption:` comments for now.

# Building the project
Checkout the project
* Clone the project

```git clone https://github.com/enasequence/gff3tools.git```
* Change dir

```cd gff3tools```

* Build the project

```./gradlew clean build```

After build, you will find two JARs in build/libs:
- gff3tools-1.0.jar → plain JAR (library, not runnable directly)
- gff3tools-1.0-all.jar → shadow JAR (includes all dependencies, runnable)
- Use the shadow JAR for runnable

# Command Line Tool Usage

```java -jar gff3tools-1.0-all.jar help```

### Flat file (.embl) to GFF3 (.gff3) Conversion

```java -jar gff3tools-1.0-all.jar conversion OZ026791.embl OZ026791.gff3```

### GFF3 (.gff3) to Flat file (.embl) Conversion

```java -jar gff3tools-1.0-all.jar conversion OZ026791.gff3 OZ026791.embl```

### Using unix pipes

The tool supports unix pipes, input and output using std-in and std-out.

**From gff3 stdin to ff stdout**

```cat OZ026791.gff3 | java -jar gff3tools-1.0-all.jar conversion -f gff3 -t embl > OZ026791.embl```

### Defaults & Conventions

- The tool currently supports `.embl` and `.gff3` as valid input and output formats. These formats are automatically recognised if the file extension is correct.
- If your input file has a different extension, like `.ff` or `.txt`, the tool cannot detect the format automatically.
- In such cases, you must explicitly tell the tool what format the input file is using `-f` option to specify input and `-t` option to specify output.
- **Examples**
    - Converting a flat file (.txt) to GFF3
        - ```java -jar gff3tools-1.0-all.jar conversion -f embl OZ026791.txt OZ026791.gff3```
    - Converting from GFF3 to a flat file (.txt):
        - ```java -jar gff3tools-1.0-all.jar conversion -t embl OZ026791.gff3 OZ026791.txt```

# Exit codes

The CLI will exit with the following codes:

- `0` (SUCCESS)
- `1` (GENERAL): General unexpected errors that were not properly handled. This likely indicates a bug in the application and will be accompanied by a stack trace.
- `2` (USAGE): Errors due to incorrect command-line arguments. Use `--help` to see the valid parameters for your command.
- `3` (UNSUPPORTED_FORMAT_CONVERSION): Errors when an unsupported file format conversion is attempted.
- `10` (READ_ERROR): Error reading from an input file or stream.
- `11` (WRITE_ERROR): Error writing to an output file or stream.
- `12` (NON_EXISTENT_FILE): Error when an input file does not exist.
- `20` (VALIDATION_ERROR): Errors related to data validation failures.
- `30` (OUT_OF_MEMORY): Errors indicating that the application ran out of memory.

If using bash, you can see the exit code of the last command using `echo $?`

# Logging

- **General Logging**:
    - **Errors**: The tool handles errors and logs them to `stderr`. We take care to ensure all errors are actionable by the end user. If an error is not actionable is likely a bug and should be reported. The error message in this case will include a stacktrace.
    - **Warnings**: The will log warnings to `stderr`. Warnings will not stop the execution of the tool, but will provide extra context on issues found in the input. Warning output implies a deviation from the validation rules specified. You can override the validation rules using the `--rules` argument.
    - **Info**: All other information messages will be output to `stdout`.

- **`conversion` command logging**: When using the `conversion` command, the logging behavior changes based on the output destination:
    - **Output to a file**: If you specify an `output-file` (e.g., `java -jar gff3tools-1.0.jar conversion input.embl output.gff3`), logging will work as usual.
    - **Output to `stdout` (using pipes)**: If you output the conversion results to `stdout` (e.g., `java -jar gff3tools-1.0.jar conversion input.embl -t gff3 > output.gff3`), only warning and error logs will be generated and sent to `stderr`. This is to prevent informational messages from mixing with the converted data on `stdout`.

# Validation Rules and Severities

The `gff3tools` application includes a validation system that allows users to configure the behavior of specific validation rules. Each rule has a `RuleSeverity` that determines how violations of that rule are handled.

## Validation Rules

The following validation rules are available:

- `FLATFILE_NO_SOURCE`: "The flatfile contains no source feature."
- `FLATFILE_NO_ONTOLOGY_FEATURE`: "The flatfile feature does not exist on the ontology."
- `GFF3_INVALID_RECORD`: "The record does not conform with the expected gff3 format."
- `GFF3_INVALID_HEADER`: "Invalid gff3 header."

## Rule Severities

Each `ValidationRule` can be assigned one of the following severities:

- `OFF`: The rule is disabled, and no warnings or errors will be generated for violations.
- `WARN`: Violations of the rule will generate a warning message (logged to `stderr`), but the application will continue execution.
- `ERROR`: Violations of the rule will generate an error message (logged to `stderr`) and will stop the execution of the application.

## Configuring Rule Severities

You can configure the severity of one or more validation rules using the `--rules` command-line option, followed by a comma-separated list of `key:value` pairs. The `key` is the `ValidationRule` name (case-insensitive), and the `value` is the desired `RuleSeverity` (case-insensitive).

**Example:**

To set `FLATFILE_NO_SOURCE` to `ERROR` and `FLATFILE_NO_ONTOLOGY_FEATURE` to `WARN`:

```bash
java -jar gff3tools-1.0.jar conversion -f embl -t gff3 --rules FLATFILE_NO_SOURCE:ERROR,FLATFILE_NO_ONTOLOGY_FEATURE:WARN input.embl output.gff3
```

# publishing
To publish, create the `gradle.properties` file and add your private EBI gitlab token in the following format.

```gitlab_private_token=<token>```

