- Feature Name: validation_priority
- Document Date: 2026-03-02
- Last Updated: 2026-03-06
- Jira Ticket: ENA-6869

# Summary

Add priority-based execution ordering to the ValidationEngine. A `ValidationPriority` enum defines four tiers — `CRITICAL`, `HIGH`, `NORMAL`, `LOW` — that control the order in which fixes and validations execute. Ordering is determined by enum declaration order (ordinal). For each tier, fixes run first, then validations. In fail-fast mode, an error at a given tier prevents lower-priority tiers from executing.

# Motivation

The current ValidationEngine executes fixes and validations in an undefined order determined by ClassGraph's classpath scanning. This means:

1. Critical structural checks run at the same level as cosmetic checks, producing cascading noise on failure.
2. Prerequisite fixes may run after the validations that depend on them.

Priority tiers solve both: critical checks short-circuit early (in fail-fast mode), and the interleaved model guarantees fixes prepare data for validations at the same tier.

# Design

## `ValidationPriority` Enum

```java
public enum ValidationPriority {
    CRITICAL, HIGH, NORMAL, LOW;
}
```

Ordering is determined by enum declaration order (ordinal). New intermediate tiers can be added by inserting constants at the desired position.

## Annotation Changes

`@ValidationMethod` and `@FixMethod` gain a `priority` attribute defaulting to `NORMAL`. `@ExitMethod` is unchanged — exit methods run after all tiers complete.

## `ValidatorDescriptor`

Stores priority extracted at registration time to avoid repeated annotation lookups.

## `ValidationRegistry`

Provides `getValidationsByPriority()` and `getFixesByPriority()` returning `Map<ValidationPriority, List<ValidatorDescriptor>>`. The engine iterates `ValidationPriority.values()` to guarantee tier ordering.

## `ValidationEngine` — Tiered Execution

```
For each priority tier [CRITICAL → HIGH → NORMAL → LOW]:
  1. Execute fixes at this tier
  2. Execute validations at this tier
     - On ERROR + fail-fast: throw immediately (skips remaining tiers)
     - On ERROR + no-fast-fail: collect error, continue

After all tiers: execute exit methods (unchanged)
```

# Scope

- All existing validations and fixes default to `NORMAL` — no behavioural change.
- Priority is a compile-time structural property, not runtime-configurable.

# Testing

- Interleaved execution order: CRITICAL fixes → CRITICAL validations → NORMAL fixes → NORMAL validations.
- Fail-fast short-circuits at current tier, skips lower tiers.
- No-fast-fail executes all tiers and collects all errors.

# Related

- [Validation Engine](./0003_validation_engine.md) — current engine design (this spec extends it)
- Jira: ENA-6869, parent epic ENA-6733
