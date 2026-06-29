# Event transformation (upcasting) — `DwellingBuilt` → `DwellingConstructed`

This document describes how event transformation (the Axon Framework 5.2 successor to AF4
"upcasters") was introduced into this project, and how to exercise it with and without the
transformation enabled.

## What and why

We renamed the creature-recruitment event `DwellingBuilt` to `DwellingConstructed` and added a new
`level` property. Events already stored under the old identity (`CreatureRecruitment.DwellingBuilt`
version `1.0.0`) must still be readable by the new code, which only knows
`CreatureRecruitment.DwellingConstructed` version `2.0.0`.

In AF5.2 this is solved by registering an `EventTransformerChain`: stored events are rewritten **on
read**, before any handler or projection sees them, so the rest of the application only ever deals
with the current event shape. Nothing in the event store is mutated.

The feature lives in the commercial module `io.axoniq.framework:axoniq-message-transformation`.

## The transformation chain

The identity history of the event:

| Step | Qualified name | Version | Payload |
|------|----------------|---------|---------|
| original | `CreatureRecruitment.DwellingBuilt` | `1.0.0` | `dwellingId, creatureId, costPerTroop` |
| rename | `CreatureRecruitment.DwellingConstructed` | `1.0.0` | unchanged |
| upcast | `CreatureRecruitment.DwellingConstructed` | `2.0.0` | adds `level` |

The chain (see `creaturerecruitment/transformation/DwellingEventTransformations.kt`) registers two
steps, applied in order, and they compose:

1. **`rename`** — `DwellingBuilt@1.0.0` → `DwellingConstructed@1.0.0`. A rename changes the identity
   only; the payload is carried over untouched. (`transform(...)` may change the version and payload
   but not the qualified name, so the name change must be a `rename`.)
2. **`transform`** — `DwellingConstructed@1.0.0` → `@2.0.0`. A `JsonNode` mapper backfills the new
   `level` field with `0` ("unknown") for events that predate it.

On read, a stored `DwellingBuilt@1.0.0` flows `rename → transform`, reaching handlers as the current
`DwellingConstructed@2.0.0` with `level = 0`. Newly built dwellings are written directly as
`DwellingConstructed@2.0.0` with `level = 1` (see `BuildDwelling` `decide`).

Registration is automatic: the chain is exposed as a Spring `@Bean` of type `EventTransformerChain`,
and the module's `EventTransformationConfigurationEnhancer` decorates the `EventStore` with a
`TransformingEventStore`. If no chain bean exists, the decorator is a no-op pass-through.

## Step-by-step: what was changed

1. **Dependency** (`pom.xml`): added `io.axoniq.framework:axoniq-message-transformation` at
   `${axon.version}`.
2. **Event** (`creaturerecruitment/events/`): renamed `DwellingBuilt.kt` → `DwellingConstructed.kt`,
   set `@Event(namespace = "CreatureRecruitment", name = "DwellingConstructed", version = "2.0.0")`,
   and added `val level: Int`.
3. **Producer** (`write/builddwelling/BuildDwelling.Slice.kt`): `decide` now emits
   `DwellingConstructed(..., level = 1)`.
4. **All references** updated `DwellingBuilt` → `DwellingConstructed`: the exhaustive
   `is DwellingBuilt ->` branches and `@EventSourcingHandler`s across the creature-recruitment write
   slices, the read slices, and the astrologers automation. In `write/recruitcreaturedcb`, the DCB
   `EventCriteria` type string now lists the current name `CreatureRecruitment.DwellingConstructed`;
   the transforming store automatically widens reads to also fetch events stored under the old name.
5. **Visibility** (`read/getdwellingbyid/GetDwellingById.Slice.kt`): `Result` now exposes `level`, so
   the upcast value is observable over REST. (`GetDwellingById` is an inline `@EventSourced`
   projection, so every query sources events live through the transforming store.)
6. **Transformation chain** (`creaturerecruitment/transformation/DwellingEventTransformations.kt`):
   new `@Configuration` providing the `EventTransformerChain` `@Bean`.
7. **Feature flag**: the chain bean is gated by `@ConditionalOnProperty`
   (`transformations.creaturerecruitment.dwelling.enabled`, default `true`), declared in
   `application.yaml` and `META-INF/additional-spring-configuration-metadata.json`.
8. **Tests**: updated existing slice tests to the new event/`level`.

## How to test it

### Prerequisites

A **persistent** Axon Server is required so events survive an app restart:

```bash
docker compose up -d
```

The app connects to Axon Server only when `AXON_AXONSERVER_ENABLED=true`.

### Step 1 — store a legacy event with the OLD code

On the commit **before** this change (where the event is still `DwellingBuilt@1.0.0`), run the app
and build a dwelling so a `DwellingBuilt@1.0.0` event is persisted:

```bash
AXON_AXONSERVER_ENABLED=true ./mvnw spring-boot:run
curl -i -X PUT http://localhost:3775/games/scenario-1/dwellings/dwelling-4 \
  -H 'Content-Type: application/json' -H 'X-Player-Id: player-1' \
  -d '{"creatureId":"angel","costPerTroop":{"gold":3000,"gems":1}}'
```

Confirm in the Axon Server dashboard (http://localhost:8024) that the stored event is
`CreatureRecruitment.DwellingBuilt` version `1.0.0`.

### Step 2 — run the NEW code WITH the transformation (default)

```bash
AXON_AXONSERVER_ENABLED=true ./mvnw spring-boot:run
curl -s http://localhost:3775/games/scenario-1/dwellings/dwelling-4 | jq
```

Expected: `"level": 0`. The legacy event was renamed and upcast on read.

Contrast with a freshly built dwelling (written as `DwellingConstructed@2.0.0`):

```bash
curl -i -X PUT http://localhost:3775/games/scenario-1/dwellings/dwelling-9 \
  -H 'Content-Type: application/json' -H 'X-Player-Id: player-1' \
  -d '{"creatureId":"angel","costPerTroop":{"gold":3000,"gems":1}}'
curl -s http://localhost:3775/games/scenario-1/dwellings/dwelling-9 | jq   # "level": 1
```

### Step 3 — run the NEW code WITHOUT the transformation

Disable the chain via the feature flag and restart:

```bash
AXON_AXONSERVER_ENABLED=true TRANSFORMATIONS_CREATURERECRUITMENT_DWELLING_ENABLED=false \
  ./mvnw spring-boot:run
curl -s http://localhost:3775/games/scenario-1/dwellings/dwelling-4 | jq
```

With the chain disabled, the stored `DwellingBuilt@1.0.0` event is no longer rewritten to the current
identity, so the inline projection does not recognise it — the legacy `dwelling-4` is not returned as
a current dwelling (where the WITH run returned it with `level = 0`). A dwelling built by the new code
(e.g. `dwelling-9`) is unaffected, since it is already stored as `DwellingConstructed@2.0.0`.

Toggling this flag between runs is the simplest way to see the difference the upcaster makes.

## Notes

- `axoniq-message-transformation` is a commercial AxonIQ module; it resolves from the local Maven
  repository (built from the AxonIQ Framework monorepo).
- Mappers must be pure functions (deterministic, side-effect-free) — they run on every read of a
  matching event.
- Write one transformation per version step (rename, then `1.0.0 → 2.0.0`, then `2.0.0 → 3.0.0`, …);
  the chain composes them. Adding a future version means appending one more step.
