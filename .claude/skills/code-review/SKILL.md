---
name: code-review
description: "Project-specific review checklist for this repository (DDD + Event Sourcing + Axon Framework 5 invariants). Use when: (1) reviewing code changes, a diff, or a pull request in this repository, (2) user says 'review', 'code review', 'check this change', (3) any automated review action runs for this repo."
---

# Code Review Checklist

Project-specific invariants to verify when reviewing changes in this repository.
These complement (not replace) general review practice: correctness, security,
performance, and missing tests.

## Event Sourcing invariants

- **Exhaustive `when (event)` in `evolve()`** — must switch over the sealed
  interface with an explicit branch per subtype. No `else ->` branch, ever.
  No-op branches (`is SomeEvent -> state`) are required and intentional —
  they document a conscious "this event doesn't affect my state here" decision.
- **`@EventSourcingHandler` only on state-mutating events** — branches that
  return `state` unchanged must NOT have a corresponding handler.
- **Every state-mutating branch has a test** for that state transition.
- **`decide()` and `evolve()` stay pure** — no side effects, no infrastructure
  dependencies in the Domain section.

## Testing invariants

- **`gameMetadata` passed to every `event()` and `command()` call in tests** —
  events without metadata break `MetadataSequencingPolicy` and cause processor
  failures with shared Testcontainers.
- Integration tests use `@HeroesAxonSpringBootTest`, never bare
  `@AxonSpringBootTest`.
- Prefer constructor injection over field injection, also in tests.

## Domain modeling invariants

- **Value classes validate invariants in `init` blocks** — domain concepts
  (`Day`, `Gold`, `Quantity`, …) must reject invalid values at construction.
- All domain events implement `HeroesEvent`; module events extend their own
  sealed interface (e.g. `DwellingEvent`).
- State classes are `internal`; commands are `public`.

## Slice structure invariants

- One slice per file (`FeatureName.Slice.kt`) with Domain / Application /
  Presentation sections.
- **Feature flags** — every new slice needs `@ConditionalOnProperty` plus
  entries in `application.yaml` and Spring configuration metadata.
- REST endpoints follow `games/{gameId}/...`; player ID via `X-Player-ID`
  header; game-scoped singletons use a fixed path segment, not a path variable.

## Verification

Prefer the narrowest check: run the affected slice's test class
(`./mvnw test -Dtest=FeatureNameTest`), not the whole suite.
