- Feature Name: progress-indicator
- Start Date: 2025-07-04

# Summary
[summary]: #summary

This RFC proposes the introduction of progress indicators to the `gff3tools` command-line interface (CLI) to enhance user experience during long-running conversion tasks.

# Motivation
[motivation]: #motivation

The current `gff3tools` CLI, while functional, lacks visual feedback for users during lengthy operations.

The primary problem this proposal aims to solve is:

1.  **No Progress Indicators for Large Files:** For large conversion tasks, users receive no feedback that the tool is actively working, which can lead to uncertainty and a perception of the tool being frozen.

**Specific Use Cases and How This Proposal Helps:**

*   **Use Case 1: Converting a very large GFF3 file.**
    *   **Problem:** User starts a conversion of a 10GB GFF3 file and sees no output for several minutes, wondering if the tool is still running.
    *   **Solution:** A simple progress indicator (e.g., "Processing... [50%]") would provide visual confirmation that the tool is active, improving user confidence.

# Guide-level explanation
[guide-level-explanation]: #guide-level-explanation

This proposal introduces enhancements to how users interact with `gff3tools` from the command line by providing progress indicators.

**Progress Indicators (Proposed):**

For conversions of large files, `gff3tools` will provide visual feedback to indicate that the process is ongoing. This might be a simple percentage complete or a spinning indicator, depending on what's feasible and non-intrusive.

*   **Example output during a long conversion:**
    ```
    Converting large_file.gff3 to large_file.embl... (25% complete)
    ```
    This helps you understand that the tool is active and not frozen.

**Impact on Project Maintenance:**

These changes will make the `gff3tools` CLI more user-friendly and robust. Progress indicators will improve perceived performance.

# Reference-level explanation
[reference-level-explanation]: #reference-level-explanation

**1. Progress Indicators:**

*   **Approach:** Integrate a simple progress reporting mechanism within the `convert` methods of `FFToGff3Converter` and `Gff3ToFFConverter`. This could involve:
    *   **Line-based progress:** Counting lines read/written if the file format allows for easy line iteration.
    *   **Byte-based progress:** If file size is known, reporting progress based on bytes processed.
*   **Technical Implementation:**
    *   Pass a `ProgressMonitor` interface or a `Consumer<Double>` callback to the `convert` method.
    *   The `Converter` implementation would periodically invoke this callback with the current progress.
    *   The `CommandConversion.run()` method would then print the progress to `System.err` (to avoid polluting `stdout`).
    *   **Considerations:** Avoid complex UI libraries for CLI tools. Simple text-based updates are sufficient. Ensure progress reporting doesn't significantly impact performance.

# Drawbacks
[drawbacks]: #drawbacks

*   **Increased Code Complexity:** Implementing this feature will add new logic, increasing the overall codebase size and potentially maintenance effort.
*   **Development Time:** Implementing this feature will require development time and testing resources.

# Rationale and alternatives
[rationale-and-alternatives]: #rationale-and-alternatives

*   **Why is this design the best in the space of possible designs?**
    *   The proposed changes leverage Picocli's capabilities for a consistent and robust CLI.

*   **What other designs have been considered and what is the rationale for not choosing them?**
    *   **More Complex Progress Bars:** More elaborate, interactive progress bars (e.g., using external libraries) were considered. However, for a command-line utility, a simple text-based indicator is sufficient and avoids adding unnecessary dependencies or complexity.

*   **What is the impact of not doing this?**
    *   The overall user experience of the CLI will remain at its current level, missing opportunities for significant improvement.

*   **Could this be done in a library or as an external tool instead?**
    *   The core conversion logic already resides in libraries within the project. The proposed changes are specifically about the *CLI wrapper* around these libraries. Implementing these CLI enhancements externally would mean creating a separate executable, which is less ideal than enhancing the existing `gff3tools.jar`.

*   **Does the proposed change make the project's code easier or harder to read, understand, and maintain?**
    *   The proposed changes will make the CLI code slightly more complex due to added features. However, by adhering to Picocli's conventions, the overall project should remain maintainable. The benefits of improved user experience and reduced support burden are expected to outweigh the marginal increase in code complexity.

# Prior art
[prior-art]: #prior-art

Many well-designed command-line tools offer excellent user experiences, which have influenced these proposals:

*   **`rsync` / `cp`:** These utilities often provide verbose output or progress indicators for long-running file operations, which is the inspiration for adding progress feedback.

# Unresolved questions
[unresolved-questions]: #unresolved-questions

*   What is the exact level of detail for the progress indicator? (e.g., percentage, simple spinner, number of records processed).

# Future possibilities
[future-possibilities]: #future-possibilities

*   **Additional File Formats:** Extend the tool to support more biological annotation formats (e.g., BED, GTF, VCF).
*   **Specific Feature Extraction/Manipulation:** Add subcommands or options to extract specific features from GFF3 files or perform common manipulations (e.g., filter by type, merge overlapping features).
*   **Integration with Databases:** Develop functionality to directly import/export GFF3 data from/to biological databases.
*   **Graphical User Interface (GUI):** While a CLI tool, a future possibility could be a lightweight GUI wrapper for users who prefer a visual interface, especially for common conversion tasks.
*   **Plugin System:** For advanced users, a plugin system could allow custom conversion or validation logic to be easily added without modifying the core tool.

