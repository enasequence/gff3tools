- Feature Name: `validation_context`
- Status: Implemented
- Document Date: 2026-03-02
- Jira Ticket: ENA-6873

# Summary

A lazy, typed provider system that injects shared context into validators and fixes via class-level properties. Providers are auto-discovered by ClassGraph, declare a `ProviderScope`, and are automatically invalidated at annotation boundaries.

# Problem Statement

Three architectural problems motivated this work:

1. **Duplicated computation**: `GeneFeatureValidation` (3 methods), `LocusTagAssociationFix`, and `FeatureSpecificValidation` each independently built locus-tag maps and `OntologyClient` instances via `ConversionUtils.getOntologyClient()`.
2. **Stale instance state**: Validators are singletons reused across annotation blocks. `DuplicateSeqIdValidation.processedAnnotations` accumulated across `###` boundaries without reset.
3. **No cross-object access**: Feature-level validators received only `GFF3Feature` and line number — no access to sibling features or the parent annotation.

# Solution

`ValidationContext` provides typed, lazily-resolved providers accessible to all validators via the inherited `getContext()` method. No validation method signatures change.

## API

```java
public interface ContextProvider<T> {
    T get(ValidationContext context);   // lazy compute or return cached
    void invalidate();                  // flush cache
    ProviderScope scope();              // GLOBAL or ANNOTATION
}

public enum ProviderScope { GLOBAL, ANNOTATION }
```

`ValidationContext` holds a `Map<Class<? extends ContextProvider<?>>, ContextProvider<?>>` registry. Key methods: `get(providerClass)`, `register(providerClass, provider)`, `invalidate(scope)`, `setCurrentAnnotation(annotation)` (triggers `ANNOTATION` invalidation).

Circular dependency detection: `get()` tracks in-flight resolutions via a `Set<Class<?>>` and throws `CircularDependencyException` on cycles.

## Delivered Providers

| Provider | Scope | Replaces |
|----------|-------|---------|
| `OntologyClientProvider` | GLOBAL | Per-validator `ConversionUtils.getOntologyClient()` calls |
| `LocusTagIndexProvider` | ANNOTATION | Duplicated feature-scanning across `GeneFeatureValidation` (3×), `LocusTagAssociationFix`, `FeatureSpecificValidation` |

`LocusTagIndex` is built in a single pass: `geneToLocusTag`, `locusTagToGeneFeature`, `locusTagToGene`, `locusTagToSynonyms`, `locusTagToPeptides`.

## Integration Points

- **`Validation` base class**: added `@Getter @Setter ValidationContext context` field — no method signature changes
- **`ValidationEngineBuilder`**: auto-discovers providers via ClassGraph; `withProvider()` for test overrides
- **`ValidationRegistry`**: refactored from singleton to instance-based (required for per-engine context injection)
- **`GFF3FileReader` / `GFF3AnnotationFactory`**: call `context.setCurrentAnnotation(annotation)` before each block

## Usage

Consuming context in a validator:
```java
@ValidationMethod(rule = "MY_RULE", type = ValidationType.ANNOTATION)
public void validate(GFF3Annotation annotation, int line) throws ValidationException {
    LocusTagIndex index = getContext().get(LocusTagIndexProvider.class);
}
```

Overriding a provider for testing:
```java
ValidationEngine engine = new ValidationEngineBuilder()
    .withProvider(new MockOntologyClientProvider())
    .build();
```

# Key Design Decisions

**Custom implementation over Dagger**: `ValidationRegistry` uses ClassGraph for runtime discovery. Dagger's compile-time annotation processor conflicts with this model and cannot support mutable, runtime-extensible provider registration.

**Class-level injection over method parameters**: Adding `ValidationContext` as a third parameter would require changing every validator signature and the reflection-based dispatch in `ValidationEngine`. Base-class injection is purely additive.

**Typed providers over string-keyed map**: Class keys are refactor-safe, carry compile-time type safety, and integrate with IDE navigation.

**Providers separate from validators**: Dual-role classes create ordering dependencies and violate single responsibility.

# Alternatives Considered

- **Dagger**: Compile-time model incompatible with runtime ClassGraph discovery; `Lazy<T>` doesn't support mutable, extensible contexts.
- **Method parameter injection**: Requires changing every validation/fix method signature and `ValidationEngine` dispatch.
- **Validators as providers**: Creates ordering dependencies; entangles data computation with rule enforcement.
- **String-keyed context map**: Fragile to rename, no compile-time type safety.

# Out of Scope

- Migrating `GeneFeatureValidation`, `LocusTagAssociationFix`, `FeatureSpecificValidation` to consume providers instead of local maps — follow-up work.
- `PER_FILE` scope (between `GLOBAL` and `ANNOTATION`) for multi-file engine reuse.
- Moving `DuplicateSeqIdValidation.processedAnnotations` into a scoped provider (requires `PER_FILE` scope first).
- Thread safety and provider ordering guarantees.

# Future Considerations

- **`PER_FILE` scope**: For long-lived engines processing multiple files; would also unblock migrating `DuplicateSeqIdValidation` stale state.
- **Provider ordering**: Add explicit dependency declarations if deterministic initialization order is ever needed.

# Related Documentation

- [0003_validation_engine.md](0003_validation_engine.md) — Validation engine design
- [0002_validation_rules.md](0002_validation_rules.md) — Validation rules and configuration
- [0001_error_handling.md](0001_error_handling.md) — Error handling patterns
- Jira: ENA-6873
