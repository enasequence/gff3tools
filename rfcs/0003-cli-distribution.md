- Feature Name: cli-distribution
- Start Date: 2025-07-04

# (Summary

This RFC proposes a strategy for distributing the Java CLI tool across Windows, Linux, and macOS. The core idea is to provide a user-friendly experience by abstracting away the need for users to explicitly call `java -jar` when executing the tool. This will be achieved through a combination of platform-specific wrapper scripts and native executables.

# Motivation

Currently, users of the Java CLI tool are required to have a Java Runtime Environment (JRE) installed and to execute the tool using the `java -jar your_tool.jar` command. This presents several challenges:

1.  **User Experience Barrier:** Many users, especially those less familiar with Java development, find the `java -jar` command cumbersome and non-intuitive. They expect to execute CLI tools directly, similar to native applications (e.g., `your_tool` or `your_tool.exe`).
2.  **JRE Dependency Management:** Users must ensure they have a compatible JRE installed and configured in their system's PATH. This can lead to support issues related to incorrect JRE versions or missing installations.
3.  **Platform Inconsistency:** The execution method differs slightly between operating systems (e.g., shell scripts for Linux/macOS vs. batch files for Windows, or direct execution on systems with proper `.jar` associations). A unified and simplified approach is desired.
4.  **Distribution Complexity:** Distributing a `.jar` file alone requires additional instructions for users to run it. Providing a self-contained or easily executable package simplifies the distribution process.

This proposal aims to address these problems by:

*   **Simplifying Execution:** Allowing users to run the CLI tool directly without `java -jar`.
*   **Reducing Setup Overhead:** Minimizing the need for users to manually manage JRE installations for the tool.
*   **Improving Cross-Platform Consistency:** Providing a more consistent execution experience across different operating systems.
*   **Streamlining Distribution:** Making the tool easier to package and distribute to end-users.

Specific use cases where this proposal can help a user:

*   **New User Onboarding:** A new user can download a single package, extract it, and immediately run the CLI tool without needing to understand Java-specific commands or set up their environment.
*   **Automated Scripts:** Integrators can easily incorporate the CLI tool into their scripts and automation workflows by calling a simple executable, rather than a more complex `java -jar` command.
*   **Casual Users:** Users who only occasionally use the tool will appreciate the direct execution, as they won't need to remember the `java -jar` syntax.

# Guide-level explanation

Upon implementation of this RFC, users will interact with the CLI tool as follows:

*   **For Linux and macOS users:** They will download a distribution package (e.g., a `.tar.gz` or `.zip` file) containing the JAR file and a thin wrapper shell script. After extracting the package, they will navigate to the tool's directory and execute it directly from their terminal:
    ```bash
    ./your_tool [arguments]
    ```
    The wrapper script will handle the `java -jar` invocation internally, ensuring the correct JRE is used (e.g., by bundling a JRE or relying on a system-wide JRE if available).

*   **For Windows users:** They will download a distribution package (e.g., a `.zip` file) containing a native executable (e.g., `your_tool.exe`). After extracting the package, they can double-click the executable or run it from Command Prompt or PowerShell:
    ```cmd
    your_tool.exe [arguments]
    ```
    This native executable will be a self-contained binary, meaning it will not require a separate JRE installation on the user's system.

The distribution package will be designed to be self-contained, including all necessary dependencies (including a JRE for the native executable on Windows, or potentially for all platforms if a bundled JRE approach is chosen for wrapper scripts).

This change will make the project significantly easier to use for non-developers and will reduce the barrier to entry for new users. Developers will find it simpler to integrate the tool into their existing workflows. The maintenance of the project's code itself will not be significantly impacted, as the core logic remains in Java. The primary impact will be on the build and packaging processes.

# Reference-level explanation

The proposed distribution strategy involves two main components:

1.  **Wrapper Scripts (Linux/macOS):**
    *   A lightweight shell script will be created for Linux and macOS. This script will:
        *   Locate the bundled JAR file.
        *   Determine the appropriate Java executable (either a bundled JRE or rely on `JAVA_HOME`/system PATH).
        *   Execute the JAR using `java -jar` with all passed arguments.
    *   Example `your_tool` script:
        ```bash
        #!/bin/bash
        # Locate the directory where the script is located
        SCRIPT_DIR="$(dirname "$0")"
        # Path to the JAR file, relative to the script
        JAR_PATH="$SCRIPT_DIR/lib/your_tool.jar"
        # Execute the JAR
        exec java -jar "$JAR_PATH" "$@"
        ```
    *   The build process will ensure this script is placed in the distribution package alongside the JAR.

2.  **Native Executable (Windows):**
    *   For Windows, the Java application will be compiled into a native executable using a tool like [Launch4j](https://launch4j.sourceforge.net/) or [GraalVM Native Image](https://www.graalvm.org/reference-manual/native-image/).
    *   **Launch4j:** This tool wraps the JAR file in a Windows executable (`.exe`). It can configure the executable to:
        *   Search for a compatible JRE on the user's system.
        *   Bundle a private JRE within the executable or alongside it. (Bundling a JRE is preferred for a truly self-contained experience).
        *   Set JVM options, splash screens, and other executable properties.
    *   **GraalVM Native Image:** This is a more advanced option that compiles Java bytecode directly into a standalone native executable, eliminating the need for a separate JRE. While offering superior performance and smaller distribution size, it can introduce complexities with reflection, JNI, and other dynamic features that might require specific configurations. Initial exploration will focus on Launch4j due to its simpler integration for existing JARs.
    *   The build process will integrate the chosen native executable generation tool to produce `your_tool.exe`.

**Build Process Integration:**

*   The existing Maven/Gradle build system will be extended to include profiles or tasks for creating these distribution packages.
*   For Linux/macOS, a task will be added to create the wrapper script and package it with the JAR.
*   For Windows, a task will be added to invoke Launch4j (or a similar tool) to create the `.exe` file.

**Packaging:**

*   Each platform will have its own release artifact (e.g., `your_tool-linux-x64.tar.gz`, `your_tool-macos-x64.tar.gz`, `your_tool-windows-x64.zip`).
*   These packages will contain the executable (wrapper script or `.exe`) and the necessary JAR files and any other dependencies.

# Drawbacks

*   **Increased Build Complexity:** The build process will become more complex due to the need to generate platform-specific artifacts and potentially integrate native compilation tools.
*   **Maintenance Overhead:** Maintaining wrapper scripts and native executable configurations for multiple platforms adds to the maintenance burden.
*   **Larger Distribution Size (for native executables):** Bundling a JRE or creating a native image can result in larger distribution file sizes compared to just distributing the JAR.
*   **Potential Compatibility Issues:** Native executables can sometimes face unforeseen compatibility issues with certain system configurations or antivirus software.
*   **Debugging Challenges (for native executables):** Debugging native executables can be more challenging than debugging standard Java applications.

# Rationale and alternatives

*   **Why is this design the best in the space of possible designs?**
    This hybrid approach (wrapper scripts for Unix-like, native executable for Windows) strikes a balance between ease of use, cross-platform compatibility, and development effort. It addresses the primary pain points of explicit `java -jar` invocation while leveraging existing, mature tools for native executable generation where it provides the most benefit (Windows).

*   **What other designs have been considered and what is the rationale for not choosing them?**
    *   **Pure Wrapper Scripts (all platforms):** While simpler to implement across all platforms, Windows batch scripts are often less robust and user-friendly than native executables. They still expose the underlying Java nature more than a `.exe`.
    *   **Pure Native Executables (all platforms via GraalVM):** While ideal for a truly native experience, GraalVM Native Image compilation can be complex, especially with existing large Java codebases that heavily use reflection or dynamic class loading. The initial setup and potential for runtime issues are higher compared to simpler wrapper solutions or Launch4j. The development effort and debugging complexity would be significantly greater.
    *   **Java Packager (jpackage):** `jpackage` (from Java 14+) can create native installers and self-contained application bundles. While promising, it might still require a bundled JRE and the output is typically an installer, not just a standalone executable, which might be overkill for a simple CLI tool. It's a strong contender for future consideration, but the immediate goal is a simpler, direct executable.

*   **What is the impact of not doing this?**
    If this proposal is not implemented, the CLI tool will continue to suffer from suboptimal user experience, requiring users to manage JRE installations and use verbose `java -jar` commands. This could hinder adoption, increase support requests related to environment setup, and make the tool less appealing for integration into automated workflows.

*   **Could this be done in a library or as an external tool instead?**
    No, this is a distribution concern, not a core library feature. The proposed changes are about how the tool is packaged and presented to the end-user, not about its internal functionality.

*   **Does the proposed change make the project's code easier or harder to read, understand, and maintain?**
    The core Java code will remain unaffected, so its readability and maintainability will not change. The build scripts and distribution configurations will become more complex, requiring careful documentation and maintenance. However, this complexity is confined to the build system, not the application logic.

# Prior art

*   **Other Java CLI tools:** Many popular Java CLI tools (e.g., Maven, Gradle, Apache Ant) use wrapper scripts (shell scripts for Unix-like, batch files for Windows) to simplify execution. This is a well-established and proven method for distributing Java command-line applications.
*   **Native application packaging tools:** Tools like Launch4j, JSmooth, and Excelsior JET have long been used to create Windows executables from JARs.
*   **GraalVM Native Image:** Represents a newer trend in the Java ecosystem for creating truly native, self-contained executables, addressing the "JRE dependency" issue more fundamentally. Its adoption is growing for CLI tools and microservices.
*   **Electron (for desktop apps):** While not directly applicable to CLI, frameworks like Electron for desktop applications demonstrate the user expectation for single-click, self-contained applications that abstract away underlying runtimes (like Node.js). This proposal aims for a similar level of abstraction for CLI tools.

# Unresolved questions

*   Should a JRE be bundled with the wrapper scripts for Linux/macOS, or should they rely on a system-wide JRE? Bundling simplifies distribution but increases package size. Relying on system JRE reduces size but increases user setup burden. This will likely be resolved during implementation, starting with relying on system JRE and moving to bundled if user feedback indicates issues.
*   What is the specific tool to be used for native executable generation on Windows (e.g., Launch4j vs. GraalVM Native Image)? Initial preference is Launch4j for simplicity, but a deeper dive into GraalVM's suitability will be required.
*   How will updates and versioning be handled with this new distribution method, especially for native executables?

# Future possibilities

*   **Automated Release Pipelines:** Integrate the new build artifacts into an automated release pipeline (CI/CD) to streamline the creation and distribution of cross-platform executables.
*   **Package Managers:** Explore distribution through platform-specific package managers (e.g., Homebrew for macOS, apt/yum for Linux, Chocolatey for Windows) to further simplify installation for users.
*   **Self-Updating Mechanism:** Implement a self-updating mechanism within the CLI tool to automatically download and install new versions.
*   **GraalVM Native Image for all platforms:** As GraalVM Native Image matures and its ecosystem support expands, consider transitioning to native compilation for all platforms to achieve true standalone binaries across the board.

