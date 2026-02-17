---
name: migrate-axon4-to-axon5-write-slice
description: >
  Migrate Event Sourcing write slices from Axon Framework 4 to Axon Framework 5 (DCB pattern).
  Use when: (1) migrating an AF4 aggregate/command handler to AF5 vertical slice architecture,
  (2) porting a write slice from an AF4 project to an AF5 project,
  (3) user says "migrate", "port", "convert" a command/aggregate/write-slice from AF4 to AF5,
  (4) user references an AF4 source (Java or Kotlin) and asks to create the AF5 equivalent.
  Language agnostic - handles Java AF4 source or Kotlin AF4 source migrating to AF5 target.
  Write slice is a concept from Event Modeling: Command to decide to Events to evolve to State.
---

# Migrate Write Slice: Axon Framework 4 to Axon Framework 5

## Pre-Migration: Discover Target Project Conventions

Before writing any code, read the target project's CLAUDE.md and explore at least one existing write slice
implementation. Conventions vary between projects. Look for:

- File naming (`FeatureName.Slice.kt` vs separate files)
- Section markers (Domain / Application / Presentation comment blocks)
- State visibility (`private` vs `internal`)
- Command class visibility (`public` vs `internal`)
- How events are defined (primitives vs value objects, marker interfaces)
- How metadata is handled (`GameMetadata`, `AxonMetadata`)
- REST conventions (URL patterns, headers, response types)
- Feature flag patterns (`@ConditionalOnProperty`)
- Test fixture setup (unit test vs Spring integration test)

## Migration Workflow

### Step 1: Identify AF4 Source Components

From the AF4 codebase, locate and read:

1. **Command class** - the command record/data class (has `@TargetAggregateIdentifier`)
2. **Aggregate class** - the `@Aggregate` class containing `@CommandHandler` and `@EventSourcingHandler` methods for
   this command
3. **Event class(es)** - events produced by this command handler
4. **Domain rule classes** (if any) - `DomainRule` implementations used in the handler
5. **REST API class** (if exists) - the `@RestController` for this command

Note which events the handler produces and which `@EventSourcingHandler` methods are needed to reconstruct the state
required by this command's `decide` logic.

### Step 2: Determine AF5 Consistency Boundary Pattern

Choose between two AF5 patterns based on the consistency boundary:

**Simple (single tag)**: When the command only needs events from ONE tag/stream.

- Uses `@EventSourced(tagKey = "tagName")`
- Handler class annotated with `@Component`
- Auto-registered by Spring component scanning
- See [references/af4-to-af5-mapping.md](references/af4-to-af5-mapping.md) "Simple Pattern" section

**Advanced (multi-tag / DCB)**: When the command needs events from MULTIPLE tags/streams.

- Uses `@EventSourcedEntity` (no tagKey)
- Requires explicit `@EventCriteriaBuilder` companion method
- Requires `@Configuration` class with `EntityModule` and `CommandHandlingModule` beans
- Handler class is NOT annotated with `@Component`
- See [references/af4-to-af5-mapping.md](references/af4-to-af5-mapping.md) "Advanced Pattern" section

### Step 3: Create the Slice File

Create a single file: `FeatureName.Slice.kt` (follow target project naming convention).

Structure with three sections:

```
////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////
// Command data class, State data class, initialState, decide(), evolve()

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////
// @EventSourced entity, @CommandHandler component (+ @Configuration if advanced)

////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////
// @RestController
```

Apply the transformation rules from [references/af4-to-af5-mapping.md](references/af4-to-af5-mapping.md).

### Step 4: Verify Events Exist in Target Project

Check if the domain events already exist in the target project's `events/` package. If not, create them following the
target project's event conventions (marker interfaces, `@EventTag` annotations, value object types).

### Step 5: Add Feature Flag Configuration

Add `@ConditionalOnProperty` to:

- The `@EventSourced` / `@EventSourcedEntity` class
- The `@Component` command handler class (simple pattern only)
- The `@Configuration` class (advanced pattern only)
- The `@RestController` class

Add the property to `application.yaml`:

```yaml
slices:
  boundedcontext:
    write:
      featurename:
        enabled: true
```

### Step 6: Implement Tests

Follow the target project test conventions. Typical pattern:

```kotlin
// Unit test (no Spring context)
val sliceUnderTest = axonTestFixture(configSlice { ... })

// Spring integration test
@HeroesAxonSpringBootTest // or project-specific annotation
class FeatureNameSpringSliceTest @Autowired constructor(configuration: AxonConfiguration) {
    val sliceUnderTest = springTestFixture(configuration)
}

// Given-When-Then
sliceUnderTest
    .given().event(PriorEvent(...))  // or .noPriorActivity()
    .`when`().command(TheCommand(...))
    .then()
    .resultMessagePayload(CommandHandlerResult.Success)
    .events(ExpectedEvent(...))
```

## References

- [AF4 to AF5 Detailed Mapping](references/af4-to-af5-mapping.md) - Concept-by-concept transformation rules with
  before/after examples
