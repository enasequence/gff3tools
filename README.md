# gff3tools

gff3tools is a Java library for converting EMBL flat files to GFF3 format. 
It uses [sequencetools](https://github.com/enasequence/sequencetools) to read the flat file.

# Compiling
To access the sequencetools library, create the `gradle.properties` file and add your private 
EBI gitlab token in the following format.   

```gitlab_private_token=<token>```

# Conversion Rules and Assumptions

Conversion rules and Assumptions are added to the code under `// Rule: ` and `// Assumption:` comments for now. 

# Building the project
Checkout the project
* Clone the project

```git clone https://github.com/EBIBioStudies/gff3tools.git```
* Change dir

```cd gff3tools```

* Build the project 

```./gradlew clean build``` 

The jar file will be found in /build/lib/gff3tools*.jar. You can use this jar to run the converter.

# Usage 

```java -jar gff3tools-1.0.jar help```


### Defaults and conventions

- The conversion tool will identify file formats using the file extension. Only `gff3` and `ff` are recognised file extensions
- The parameters `-f` (from) and `-t` (to)  can be used to specify the file format if the extension is not recognised or if using std-in/std-out
- If not provided the parameter `-t` (to) will default to `gff3`

## Converter

**FF to GFF3**
```java -jar gff3tools-1.0.jar conversion OZ026791.ff OZ026791.gff3```

**GFF3 to FF**
```java -jar gff3tools-1.0.jar conversion OZ026791.gff3 OZ026791.ff```

### Using unix pipes

The converter supports unix pipes, input and output using std-in and std-out.

**From gff3 stdin to ff stdout**

```cat OZ026791.gff3 | java -jar gff3tools-1.0.jar conversion -f gff3 -t ff > OZ026791.ff```

# Exit codes

The CLI will exit with status code `0` on success. If an error occurs, the following error codes will be used:

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
