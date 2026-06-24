# gff3tools

gff3tools is a Java based library and command line utility for converting EMBL flat files to GFF3 format, and vice versa.
It uses [sequencetools](https://github.com/enasequence/sequencetools) to read the flat file.

# Conversion Rules and Assumptions

Conversion rules and Assumptions are added to the code under `// Rule:` and `// Assumption:` comments for now.

# Building the project

Checkout the project

* Clone the project

```git clone https://github.com/enasequence/gff3tools.git```

* Change dir

```cd gff3tools```

* Build the project

```./gradlew clean build```

After build, you will find two JARs in build/libs:
* gff3tools-1.0.jar → plain JAR (library, not runnable directly)
* gff3tools-1.0-all.jar → shadow JAR (includes all dependencies, runnable)
* Use the shadow JAR for runnable

# Command Line Tool Usage

```bash
java -jar gff3tools-*-all.jar help
```

Quick examples:

```bash
# EMBL → GFF3
java -jar gff3tools-*-all.jar conversion OZ026791.embl OZ026791.gff3

# GFF3 → EMBL
java -jar gff3tools-*-all.jar conversion OZ026791.gff3 OZ026791.embl

# Pipe: GFF3 stdin → EMBL stdout
cat OZ026791.gff3 | java -jar gff3tools-*-all.jar conversion -f gff3 -t embl > OZ026791.embl
```

For the full reference — all subcommands, options, and workflows — see the **[CLI Usage Guide](docs/cli-usage-guide.md)**.

# Exit codes

The CLI will exit with the following codes:

* `0` (SUCCESS)
* `1` (GENERAL): General unexpected errors that were not properly handled. This likely indicates a bug in the application and will be accompanied by a stack trace.
* `2` (USAGE): Errors due to incorrect command-line arguments. Use `--help` to see the valid parameters for your command.
* `3` (UNSUPPORTED_FORMAT_CONVERSION): Errors when an unsupported file format conversion is attempted.
* `10` (READ_ERROR): Error reading from an input file or stream.
* `11` (WRITE_ERROR): Error writing to an output file or stream.
* `12` (NON_EXISTENT_FILE): Error when an input file does not exist.
* `20` (VALIDATION_ERROR): Errors related to data validation failures.
* `30` (OUT_OF_MEMORY): Errors indicating that the application ran out of memory.

If using bash, you can see the exit code of the last command using `echo $?`

# Logging

* **Errors** are written to `stderr` with a stack trace if unexpected.
* **Warnings** are written to `stderr` and do not stop execution.
* **Info** messages are written to `stdout`.
* When writing conversion output to `stdout` (pipe mode), info and warning logs are suppressed to avoid mixing with the data stream; only errors reach `stderr`.

See the [CLI Usage Guide](docs/cli-usage-guide.md) for how to configure validation rule severities with `--rules`.

# publishing

To publish, create the `gradle.properties` file and add your private EBI gitlab token in the following format.

```gitlab_private_token=<token>```
