# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Educational Heroes of Might & Magic III implementation demonstrating Domain-Driven Design, Event Sourcing, and Event
Modeling patterns using Kotlin, Axon Framework 5, and Spring Boot 3.

## Build & Run Commands

```bash
# Install dependencies
./mvnw install -DskipTests

# Run all tests
./mvnw test

# Run single test class
./mvnw test -Dtest=BuildDwellingUnitTest

# Run single test method
./mvnw test -Dtest=BuildDwellingUnitTest#given_not_built_dwelling_when_build_then_built

# Run application
./mvnw spring-boot:run

# Start Axon Server (required for running app or integration tests)
docker compose up
```

## Architecture

### Vertical Slice Architecture

Each feature is a self-contained "slice" in a single file with three sections:

```kotlin
////////////////////////////////////////////
////////// Domain
///////////////////////////////////////////
// Commands (data classes), State, decide() and evolve() functions

////////////////////////////////////////////
////////// Application
///////////////////////////////////////////
// @EventSourced entity, @CommandHandler

////////////////////////////////////////////
////////// Presentation
///////////////////////////////////////////
// @RestController and request DTOs
```

### Event Sourcing Pattern

- **decide(command, state) → List<Event>**: Pure function for command validation and event generation
- **evolve(state, event) → State**: Pure function for state reconstruction from events
- **@EventSourced(tagKey = ...)**: Defines consistency boundary via event tags
- **@InjectEntity(idProperty = ...)**: Injects reconstructed state into command handler
- **EventAppender**: Appends events with metadata to event store

### Bounded Contexts

Located in `com.dddheroes.heroesofddd.*`:

- `armies/` - Army management
- `creaturerecruitment/` - Dwelling building and creature recruitment
- `resourcespool/` - Resource management
- `scenario/` - Game scenarios
- `shared/` - Cross-cutting concerns (domain primitives, metadata, REST headers)

### Event Tags and Metadata

- **EventTags.kt**: Constants for consistency boundaries (`DWELLING_ID`, `ARMY_ID`, `RESOURCES_POOL_ID`)
- **GameMetadata**: Correlation context (`gameId`, `playerId`) attached to commands and events
- **@EventTag annotation**: On event properties to enable tag-based filtering in Axon Server

## Testing Patterns

Unit tests use Axon Test Fixtures with Given-When-Then:

```kotlin
private val sliceUnderTest = AxonTestFixture.with(
    UnitTestAxonApplication.configurer(
        { registerCommandHandlingModule { BuildDwellingWriteSliceConfig().buildDwellingSlice() } },
        { axonServerEnabled = false }
    ))

@Test
fun `given not built dwelling, when build, then built`() {
    sliceUnderTest
        .given().noPriorActivity()
        .`when`().command(BuildDwelling(...))
    .then().events(DwellingBuilt(...))
}
```

## Key Conventions

- All domain events implement `HeroesEvent` marker interface
- Module-specific events have their own interface (e.g., `DwellingEvent`)
- Slice files named as `FeatureName.Slice.kt`
- State classes are `internal`, commands are `public`
- REST endpoints follow: `games/{gameId}/resource/{resourceId}` pattern
- Player ID passed via `X-Player-ID` header

## Commit Conventions

Commits follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) combined with
[Gitmoji](https://gitmoji.dev/). Always use the **actual Unicode emoji** (e.g. `✨`), never the shortcode (e.g.
`:sparkles:`).

### Format

```
<emoji> <type>(optional-scope): <description>
```

Use backticks around class/entity names in descriptions (e.g. `` `BuildDwelling` ``).

### Event Modeling Slice Commits

When implementing a slice from Event Modeling, use `✨ feat:` with the bounded context name, slice type, and flow:

```
✨ feat: <BoundedContext> | write slice: <CommandName> -> <EventName(s)>
✨ feat: <BoundedContext> | read slice: (<EventName(s)>) -> <ReadModelName>
✨ feat: <BoundedContext> | automation: <EventName> -> <CommandName>
✨ feat: <BoundedContext> | write slices: <CommandName1> and <CommandName2>
```

Examples:

```
✨ feat: Creature Recruitment | write slice: BuildDwelling -> `DwellingBuilt`
✨ feat: Creature Recruitment | write slice: RecruitCreature -> (`CreatureRecruited`, `AvailableCreaturesChanged`)
✨ feat: Creature Recruitment | read slice: (`CreatureRecruited`, `AvailableCreaturesChanged`) -> `GetAllDwellings`
✨ feat: Creature Recruitment | automation: `CreatureRecruited` -> `AddCreatureToArmy`
✨ feat: Astrologers | write slice: `ProclaimWeekSymbol` -> `WeekSymbolProclaimed`
✨ feat: Calendar | write slices: `StartDay` and `FinishDay`
```

### Emoji-Type Mapping

| Emoji | Type                              | When to use                                           |
|-------|-----------------------------------|-------------------------------------------------------|
| ✨     | `feat`                            | New feature or slice implementation                   |
| 🐛    | `fix`                             | Bug fix                                               |
| ♻️    | `refactor`                        | Code refactoring (no behavior change)                 |
| 🏗️   | `refactor(architecture)`          | Architectural restructuring                           |
| ✅     | `test`                            | Adding or updating tests                              |
| 🧪    | `chore(tests)`                    | Test infrastructure/configuration                     |
| 📦    | `build(deps)`                     | Dependency additions or upgrades                      |
| ⬆️    | `deps`                            | Dependency version upgrades                           |
| 🐳    | `chore(docker)`                   | Docker and container configuration                    |
| 👷    | `ci`                              | CI/CD pipeline changes                                |
| 📝    | `docs`                            | Documentation                                         |
| 🔧    | `config(scope)` or `chore(scope)` | Configuration changes                                 |
| 🎉    | `chore`                           | Project initialization                                |
| 🔥    | `remove`                          | Removing code or files                                |
| 🤖    | `ai-agent(scope)`                 | AI agent configuration (e.g. `ai-agent(claude-code)`) |
| 🧑‍💻 | `chore(dx)`                       | Developer experience improvements                     |
| 📸    | `chore`                           | Snapshotting configuration                            |
| 🪲    | `chore(debugging)`                | Debugging helpers (log levels, etc.)                  |
| 🚀    | `feat(scripts)`                   | Deployment or utility scripts                         |
