- Feature Name: Validation Rules
- Document Date: 2025-07-30
- Last Updated: 2025-09-10

# Summary

This document describes the validation rules framework, a system designed to enforce data integrity and consistency across various data inputs within the GFF3Tools project. It provides a flexible and extensible mechanism for defining, applying, and reporting on validation rules.

# Motivation & Rationale

The primary motivation for this framework is to ensure the quality and correctness of GFF3 data processed by the tools. Inconsistent or malformed data can lead to errors in downstream analysis, incorrect interpretations, and unreliable results. This framework addresses the need for a standardized and automated way to validate GFF3 files against a set of predefined rules.

Specific use cases include:
- **Input Data Validation:** Ensuring that GFF3 files provided as input to any of the GFF3Tools conform to the GFF3 specification and any additional project-specific constraints.
- **Data Transformation Validation:** Validating data after transformations (e.g., conversion from other formats) to ensure data integrity is maintained.
- **User-Selected Rules:** Allowing users to cherry-pick the validation rules based on their specific requirements.

The rationale behind the chosen design emphasizes extensibility, maintainability, and performance. A rules-based approach allows for easy addition of new validation checks without modifying core logic. The design prioritizes clear separation of concerns, making it easier to understand, debug, and enhance the framework.


# System Overview / High-Level Design

The validation rules framework is designed around a clear separation of concerns, with distinct components responsible for defining rules, managing their severity, and handling validation outcomes.

**Main Components:**
- **Validation** The actual validation logic to be performed.
- **Validation Engine** Manages the execution of the validations and handles the outcome of the validation deciding if necessary to halt the execution.
- **RuleSeverity:** This enum defines the possible severity levels for a validation rule: `OFF`, `WARN`, and `ERROR`. These severities dictate how a rule violation should be treated by the `ValidationEngine`.

**High-Level Interaction:**
1.  **Rule Definition:** Developers define new validation rules by implementing classes that extend  the `Validation` interface.
2.  **Severity Configuration:** Default severities for these validations are set in `default-rule-severities.properties`.
3.  **Rule Execution:** The `ValidationEngine` executes validation logic. When a validation check fails, it identifies the `Validation` that was violated.
4.  **Violation Handling:** The `ValidationEngine` consults the configured `RuleSeverity` for the violated `Validation` to determine the appropriate action:
    *   If the rule's severity is `OFF`, the violation is ignored.
    *   If the rule's severity is `WARN`, a warning message is logged.
    *   If the rule's severity is `ERROR`, an exception is thrown, halting further processing.
    *   **Default Behavior:** If a validation rule is not explicitly configured in the properties file or via CLI, its severity defaults to `ERROR`. This ensures that new or unconfigured rules are treated as critical failures by default.

**Integration with Existing System:**
The `ValidationEngine` integrates the framework by processing data and applying `Validation`s. It ensures consistent error reporting and handling based on the configured `RuleSeverity` across the application.


# Usage Guidelines

The validation rules framework is primarily used internally by the GFF3Tools to ensure data quality. However, its configuration can be customized.

**Configuration:**
- **Default Severities:** The default severity for each validation is defined in `src/main/resources/default-rule-severities.properties`. This file maps `Validation` identifiers to `RuleSeverity` enum names.
- **Overriding Severities:** Users can override default rule severities using the `--rules` command-line option. This option accepts a comma-separated list of `key:value` pairs, where `key` is the `Validation` id and `value` is the `RuleSeverity` enum name (e.g., `--rules MY_RULE:WARN,ANOTHER_RULE:OFF`).


# Technical Debt / Future Considerations

**Known Limitations / Technical Debt:**

**Potential Future Extensions / Improvements:**
- **Rule Groups/Profiles:** Introduce the concept of rule groups or profiles, allowing users to enable/disable sets of rules or apply different severity configurations based on specific use cases (e.g., "strict validation profile," "lenient validation profile").


# Related Documentation & Resources

**Related Documentation & Resources:**

- **Relevant Code Directories:**
    - `src/main/java/uk/ac/ebi/embl/converter/validation/`: Contains the core classes of the validation framework 
    - `src/main/java/uk/ac/ebi/embl/converter/exception/ValidationException.java`: The custom exception class for validation errors.
    - `src/main/resources/default-rule-severities.properties`: The configuration file for default rule severities.

- [**Validation Engine Documentation**](./0003_validation_engine.md)
- [**GFF3 INSDC Specification**](https://docs.google.com/document/d/1HrF-H3K_e9uOcgBzTpi53FZerplPXmyC/edit?pli=1&tab=t.0)
- [**GFF3 Lincoln Stein Specification**](https://github.com/The-Sequence-Ontology/Specifications/blob/master/gff3.md)


