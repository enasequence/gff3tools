- Feature Name: `explicit_validation_engine_construction`
- Status: Implemented
- Document Date: 2026-03-10
- Implemented: 2026-03-10

# Summary

Added a construction path on `ValidationEngineBuilder` that allows callers to explicitly register fix, validator, and provider instances — bypassing or supplementing classpath-scanning in `ValidationRegistry`. This enables lightweight, targeted engine configurations and positions the validation engine as a composable public library API.

# Motivation

`ValidationRegistry` performed an eager one-time classpath scan (via ClassGraph) in a static initializer. Every engine received the full set of discovered rules. This caused two problems:

1. **Overhead for targeted operations.** Commands like `FileProcessCommand` need only accession replacement, but had to build the full engine or work around it entirely.

2. **No programmatic composition.** Library consumers had no way to register custom fix/validator implementations without relying on classpath scanning and annotation discovery.

# Solution Design

Added opt-in builder methods alongside the existing scanning path. Full backward compatibility preserved — `new ValidationEngineBuilder().build()` continues to produce a scanned engine.

## API

```java
// Minimal engine — only specific fixes, no scanning
ValidationEngine engine = new ValidationEngineBuilder()
    .disableClasspathScanning()
    .withFix(new AccessionReplacementFix())
    .withProvider(accessionProvider)
    .build();

// Override a scanned rule with a custom instance
ValidationEngine engine = new ValidationEngineBuilder()
    .withFix(new CustomLocusTagFix())   // replaces the scanned LocusTagFix
    .build();
```

Methods: `disableClasspathScanning()`, `withFix(Object)`, `withValidator(Object)`, `withProvider(ContextProvider<?>)`. All accept single instances (no bulk/varargs). Null arguments are rejected with `NullPointerException`.

## Key Design Decisions

**Lazy classpath scanning via `ScanHolder` inner class.** Replaced the eager static initializer with a lazy static inner class. The JVM guarantees thread-safe initialization on first access. Engines built with `disableClasspathScanning()` never trigger the scan. This matters for web server use cases where per-request engines should avoid unnecessary scan cost.

**Merge semantics: explicit wins.** When classpath scanning is active and an explicit instance shares the same `@Gff3Fix(name=...)` or `@Gff3Validation(name=...)` as a scanned one, the explicit instance replaces it. Duplicate explicit names throw `DuplicateValidationRuleException`.

**Single builder, not a separate class.** A dedicated `ManualValidationEngineBuilder` would have duplicated config-loading, provider-wiring, and context-injection logic. A single builder with an opt-in flag keeps the public API surface smaller.

**`Object` parameters, not typed interfaces.** `withFix()` and `withValidator()` accept `Object` because fixes and validators are plain annotated classes without a common interface. Annotation validation happens at build time.

## Invariants Preserved

- `ValidationEngine` is unaware of how the registry was populated. Priority-tiered execution is unchanged.
- `ValidationContext` and `ContextProvider` semantics are unchanged. Explicit providers override by type key.
- `ValidationConfig` (severity overrides, fix toggles, class-level toggles) applies on top of whatever the registry contains.
- `@InjectContext` injection works identically for explicit and scanned instances.

# Alternatives Considered

1. **Config-only approach** (disable all rules via properties, enable one). Requires callers to know all existing rule names and update the disable list as rules are added. Semantically wrong: intent is "run only these rules", not "run everything except a growing list".

2. **Eager static scan, ignore results when not needed.** Simpler but wastes scan cost in environments where only explicit-mode engines are built.

# Future Considerations

- **Engine reuse across requests.** `ValidationEngine` holds mutable per-request state (`parsingWarnings`, `collectedErrors`). A future `reset()` method or registry/engine separation could enable sharing. Currently, per-request engine creation with `disableClasspathScanning()` is cheap enough.

- **Type safety on `withFix()` / `withValidator()`.** Marker interfaces (`Fix`, `Validator`) could enable compile-time checking but would be a breaking change.

- **Merge semantics assume unique names.** If unnamed rules (empty `name` attribute) are ever supported, the merge logic would need a fallback strategy.
