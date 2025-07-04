- Feature Name: batch-processing 
- Start Date: 2025-07-04

# Summary

This RFC proposes enhancements to the `gff3tools` command-line interface (CLI) specifically to introduce robust batch processing capabilities for file conversions.

# Motivation

This RFC addresses the current limitations of the `gff3tools` CLI regarding batch processing, aiming to improve user experience and streamline workflows.

The primary problem this proposal aims to solve is:

1.  **Limited Batch Processing:** Users who need to convert multiple files currently have to script external loops, which can be cumbersome.

**Specific Use Case and How This Proposal Helps:**

*   **Converting all GFF3 files in a directory.**
    *   **Problem:** User has `dir1/file1.gff3`, `dir1/file2.gff3`, etc., and wants to convert all to EMBL in `dir2`. They currently need to write a shell script.
    *   **Solution:** Adding batch processing options (e.g., `java -jar gff3tools.jar conversion -f gff3 -t embl --input-dir dir1 --output-dir dir2`) would streamline this workflow directly within the tool.

# Guide-level explanation

This proposal introduces enhancements to how users interact with `gff3tools` from the command line, specifically focusing on batch processing.

**Batch Processing for `conversion` (Proposed):**

The `conversion` command will gain new options to facilitate converting multiple files in a directory.

*   **New Options:**
    *   `--input-dir <path>`: Specifies a directory containing input files.
    *   `--output-dir <path>`: Specifies a directory where converted files will be saved.
    *   `--recursive`: (Optional) Process files in subdirectories.
    *   `--force-overwrite`: (Optional) Overwrite existing files in the output directory.

*   **Example:**
    ```bash
    java -jar gff3tools.jar conversion -f gff3 -t embl --input-dir raw_gff3/ --output-dir converted_embl/
    ```
    This will convert all `.gff3` files found in `raw_gff3/` to `.embl` files in `converted_embl/`, inferring the output filename from the input.

## Error Handling

During batch processing, the tool will adopt a resilient error handling strategy to ensure that a single failure does not halt the entire process.

*   **Error Reporting:** If an error occurs while processing a file (e.g., due to malformed input), the tool will log a detailed error message to the console, clearly identifying the file that caused the issue.
*   **Continue on Error:** The tool will then skip the problematic file and continue to the next one in the batch.
*   **Summary Report:** Upon completion, a summary report will be displayed, indicating the total number of files processed, the number of successful conversions, and the number of failures.

**Impact on Project Maintenance:**

These changes will make the `gff3tools` CLI more user-friendly and robust. Batch processing will reduce the need for external scripting.

# Reference-level explanation

**1. Batch Processing for `conversion`:**

*   **Proposed Options:** Add `@Option` fields to `CommandConversion` for `--input-dir`, `--output-dir`, `--recursive`, and `--force-overwrite`.
*   **Logic Flow:**
    *   If `--input-dir` is specified, `inputFilePath` and `outputFilePath` would be treated as optional or as patterns within the directories.
    *   The `run()` method would iterate through files in `input-dir` (recursively if `--recursive` is set).
    *   For each input file, it would construct a corresponding output file path in `output-dir`.
    *   It would then invoke the existing `convert` logic for each pair of input/output files.
    *   Add checks for existing output files and the `--force-overwrite` flag.
*   **Technical Implementation:**
    ```java
    // In CommandConversion.java
    @Option(names = "--input-dir", description = "Input directory for batch conversion")
    public Path inputDirectory;

    @Option(names = "--output-dir", description = "Output directory for batch conversion")
    public Path outputDirectory;

    @Option(names = "--recursive", description = "Recursively process files in subdirectories")
    public boolean recursive;

    @Option(names = "--force-overwrite", description = "Overwrite existing output files")
    public boolean forceOverwrite;

    @Override
    public void run() {
        if (inputDirectory != null) {
            // Implement directory traversal and per-file conversion logic
            // Handle input/output file path construction
            // Call existing converter.convert(reader, writer) for each file
        } else {
            // Existing single-file conversion logic
            // ...
        }
    }
    ```

**2. Summary Report:**

Upon completion of a batch conversion, a summary report will be displayed to provide a clear overview of the process. The report will be printed to the console.

*   **Example Report:**

    ```txt
    Batch Conversion Summary
    ------------------------
    Total files processed: 100
    Successful conversions: 96
    Failed conversions: 4

    Errors:
    - /path/to/input/file3.gff3: Invalid GFF3 format on line 42
    - /path/to/input/file15.gff3: Permission denied
    - /path/to/input/file27.gff3: Malformed feature attribute
    - /path/to/input/file54.gff3: Output file already exists and --force-overwrite not specified
    Warnings:
    - /path/to/input/file88.gff3: Unknown sequence region 'chrZ'
    ```

*   **Implementation Details:**
    *   A dedicated reporting class (e.g., `BatchConversionReporter`) will be responsible for collecting statistics during processing.
    *   This class will track the total number of files, successful conversions, failures and warnings.
    *   For each failure, it will store the file path and a concise error message.
    *   At the end of the `run()` method in `CommandConversion`, the summary report will be generated and printed.

# Drawbacks

*   **Increased Code Complexity:** Batch processing adds new classes, options, and logic, increasing the overall codebase size and potentially maintenance effort.
*   **Potential for Over-featurization:** While the proposed feature is beneficial, there's a risk of adding too many options, which could make the CLI more complex for basic users.

# Rationale and alternatives

*   **Why is this design the best in the space of possible designs?**
    *   The proposed changes leverage Picocli's capabilities for a consistent and robust CLI.
    *   Adding batch processing directly to the tool improves convenience for common multi-file workflows, a feature frequently requested and implemented in other bioinformatics command-line tools.

*   **What other designs have been considered and what is the rationale for not choosing them?**
    *   **External Scripting for Batch Processing:** An alternative to implementing batch processing within the tool is to rely on users writing their own shell scripts (e.g., `find . -name "*.gff3" -exec java -jar gff3tools.jar conversion {} ... \;`). This is the current state. While flexible, it requires users to have scripting knowledge and can be less convenient for common use cases. Implementing it within the tool provides a more integrated and user-friendly experience.

*   **What is the impact of not doing this?**
    *   Users will continue to rely on external scripting for batch conversions, which is less convenient.

*   **Could this be done in a library or as an external tool instead?**
    *   The core conversion logic already resides in libraries within the project. The proposed changes are specifically about enhancing the *CLI wrapper* around these libraries to provide native batch processing capabilities.
    *   **External Scripting:** Currently, users can achieve batch processing using external shell scripts with commands like `find`, `xargs`, or simple `for` loops. For example:
        ```bash
        find input_dir -name "*.gff3" -print0 | xargs -0 -I {} java -jar gff3tools.jar conversion -f gff3 -t embl -i {} -o converted_embl/$(basename {} .gff3).embl
        ```
    *   **Why an integrated solution is better:** While external scripting offers flexibility, it has significant drawbacks:
        *   **Portability:** Shell scripts often vary between operating systems (e.g., differences between Bash, PowerShell, or even `find` implementations), leading to non-portable solutions. An integrated Java-based solution ensures consistent behavior across all platforms where `gff3tools` runs.
        *   **Complexity & Error Handling:** Writing robust shell scripts for batch processing, especially with proper error handling, logging, and progress reporting for individual file failures, can be complex and error-prone for users. A native implementation can provide more sophisticated, consistent, and user-friendly error reporting and recovery mechanisms.
        *   **Usability & Discoverability:** Users need to possess scripting knowledge to implement batch processing. An integrated solution makes the feature discoverable via `--help` and provides a simpler, single-command interface.
        *   **Performance & Resource Management:** While shell scripts can be efficient, a native implementation might allow for better internal optimizations, such as more efficient file traversal, resource management, and potential future parallelization, without incurring the overhead of spawning multiple external processes for each file.
        *   **Maintainability:** External scripts are outside the `gff3tools` project's control, leading to potential compatibility issues with future updates. An integrated feature is maintained alongside the tool.

*   **Does the proposed change make the project's code easier or harder to read, understand, and maintain?**
    *   The proposed changes will make the CLI code slightly more complex due to added features. However, by adhering to Picocli's conventions, the overall project should remain maintainable. The benefits of improved user experience and reduced support burden are expected to outweigh the marginal increase in code complexity.

# Prior art

Many well-designed command-line tools offer excellent user experiences, which have influenced these proposals:

*   **`pandoc`:** The `pandoc` CLI, which `gff3tools` we explicitly use as a convention, also provides clear input/output options and format specifications. The proposed `--input-dir`/`--output-dir` options are common in tools that handle batch processing.
*   **Bioinformatics CLIs with Batch Processing:** Many bioinformatics tools, such as QIAGEN CLC Genomics Workbench and various tools used with "Bash for Bioinformatics", demonstrate the common need and implementation of batch processing features.

# Unresolved questions

None

# File Naming Conflicts

By default, if a file with the same name already exists in the output directory, the tool will skip the conversion for that specific file to prevent accidental data loss. This behavior can be overridden by using the `--force-overwrite` flag, which will cause the tool to replace the existing file.

# Future possibilities

*   **Parallel/Concurrent Batch Processing:** Implement the ability to process multiple files in a batch concurrently, leveraging multi-core processors to significantly speed up large conversion tasks.
*   **Advanced Batch Processing Reports:** Enhance the summary report to be more detailed, providing insights into warnings, and performance metrics for each file.
*   **Integration with Workflow Management Systems:** Discuss how the robust batch processing capabilities make `gff3tools` more readily integratable into automated bioinformatics pipelines and workflow management systems (e.g., Snakemake, Nextflow, Cromwell).
*   **Machine-Readable Report:** Add a flag (e.g., `--report-format <format>`) to export the summary report in a machine-readable format like JSON or CSV, making it easier to parse programmatically.

