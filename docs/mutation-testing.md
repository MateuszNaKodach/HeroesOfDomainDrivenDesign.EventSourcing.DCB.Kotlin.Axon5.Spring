# Mutation Testing with Pitest

This document is the deeper companion to the [README's Mutation Testing
section](../README.md#-mutation-testing-with-pitest). It covers *why* we run
Pitest in this project, *how* it's wired, and *how to read the report*.

## What is mutation testing?

Line and branch coverage tell you that a line was executed during a test. They
do not tell you whether the test would have failed if the line behaved
differently. Mutation testing closes that gap.

[Pitest](https://pitest.org/) takes the compiled bytecode, makes a small,
behaviour-changing edit (a *mutant*) — flips a `>` to `>=`, removes a
conditional, replaces a return value with a default — and re-runs the tests
that cover the mutated code. If at least one test fails, the mutant is
*killed*. If every test still passes, the mutant *survives* — meaning the
production code can be wrong in that exact way and your tests would never
notice.

### A worked example from this project

Take the cost check inside `RecruitCreature.decide()`:

```kotlin
if (totalCost > availableResources) {
    throw NotEnoughResourcesException(...)
}
```

Pitest will produce mutants such as:

- replace `>` with `>=` (boundary condition)
- replace `>` with `<` (negated condition)
- remove the `if` entirely (conditional removal)
- replace the throw with a no-op (negate conditional)

A test that recruits with *exactly* the affordable amount kills the `>=`
mutant. A test that recruits with *more than* affordable resources kills the
"remove conditional" mutant. If neither test exists, both mutants survive —
and the survivor list in the report is the actionable feedback: write the
missing test.

## Why it fits this project

Most legacy advice on mutation testing warns about generated noise, slow
runs, and equivalent mutants. This project happens to be unusually
mutation-friendly:

- **`decide()` and `evolve()` are pure functions.** No mocks, no time, no
  randomness, no I/O. Every mutant is deterministically killed or survives.
- **Tests assert on observable behaviour** (emitted events, read-model state,
  dispatched commands) via the `Given/When/Then` DSL. They don't assert on
  internal method calls, so refactors don't break them and Pitest mutants
  are measured against the same contract a user cares about.
- **Vertical slices keep each test focused on one feature**, which means a
  mutant in `BuildDwelling.Slice.kt` is generally killed by tests in the
  same slice. Locality keeps the per-mutant cost low.

## How it's wired

The plugin block lives in [`pom.xml`](../pom.xml) under
`<build><plugins>` after `kotlin-maven-plugin`. Key settings:

- `targetClasses = com.dddheroes.heroesofddd.*` — every class in the
  production code is a mutation target. No exclusions: application
  bootstrap, REST controllers, Spring `@Configuration`, DTOs all stay in
  scope. Pitest only mutates classes that have actual test coverage, so
  truly unused code shows up as "no coverage" in the report rather than
  being silently skipped.
- `targetTests = com.dddheroes.heroesofddd.*` — every test participates.
  This includes pure `AxonTestFixture` unit tests *and*
  `@HeroesAxonSpringBootTest` Spring/Testcontainers slice tests.
- `avoidCallsTo = org.slf4j, java.util.logging` — Pitest never mutates
  logging calls. These mutants are equivalent by definition.
- `mutators = DEFAULTS` — Pitest's curated set. Includes the modern
  return-value mutators (`TRUE_RETURNS`, `FALSE_RETURNS`, `EMPTY_RETURNS`,
  `NULL_RETURNS`, `PRIMITIVE_RETURNS`) and the conditional / arithmetic
  mutators. `STRONGER` is a possible follow-up once a baseline is stable.
- `threads = 2`, `jvmArgs = -Xmx2g` — explicitly bounded parallelism and
  child-fork heap. Pitest forks one JVM per worker thread to run mutation
  tests; with Spring/Testcontainers tests in scope each fork may boot its
  own Spring context, so high thread counts multiply memory usage. The
  `+auto_threads` feature (one thread per CPU) is *not* used here — on an
  11-core machine it picks 11 threads, which is enough to OOM-kill the
  process. See "Limitations and caveats" below for the full story and how
  to tune for your hardware.
- `timeoutFactor = 1.5`, `timeoutConstant = 4000` — generous timeouts
  because some Testcontainers-backed tests take real time.

Incremental analysis (re-mutating only classes that changed since the last
run) is intentionally **not** wired up. As of Pitest 1.22, the
`historyInputFile` / `historyOutputFile` settings require the commercial
[Arcmutate history plugin](https://blog.arcmutate.com/history/). Every
local or CI run is a full pass.

## Running it locally

Full run:

```bash
./mvnw test org.pitest:pitest-maven:mutationCoverage
```

Scoped to a single bounded context — much faster while iterating:

```bash
./mvnw test org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses=com.dddheroes.heroesofddd.creaturerecruitment.* \
  -DtargetTests=com.dddheroes.heroesofddd.creaturerecruitment.*
```

Scoped to a single slice:

```bash
./mvnw test org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses=com.dddheroes.heroesofddd.creaturerecruitment.write.recruitcreaturedcb.* \
  -DtargetTests=com.dddheroes.heroesofddd.creaturerecruitment.write.recruitcreaturedcb.*
```

Open the report:

```bash
open target/pit-reports/index.html
```

The Testcontainers stack starts automatically as part of the underlying
test run — you do not need to `docker compose up` separately for Pitest.

## Running it on CI

The workflow at
[`.github/workflows/mutation-testing.yml`](../.github/workflows/mutation-testing.yml)
exposes a single `workflow_dispatch` trigger. Open *Actions* → "🧬 Mutation
Testing" → "Run workflow", pick the branch, click. The HTML report is
uploaded as the `pit-report` artifact on the workflow run page.

Why manual only:

- Mutation runs are expensive. The Spring/Testcontainers tests in scope
  mean a full pass is on the order of minutes — too slow to gate every PR.
- A scheduled cron would just produce reports nobody reads. Running on
  demand keeps the signal-to-noise ratio high.
- Every run is a full pass — incremental analysis would require a paid
  Arcmutate licence, which is out of scope for this educational repo.

## Reading the report

`target/pit-reports/index.html` is the entry point. Drill down
to a package, then a class, to see line-by-line mutant detail. Colours:

- **Green** — line is covered *and* every mutant on it was killed.
- **Yellow** — line is covered, but at least one mutant survived.
- **Red** — line was not covered by any test.
- **Grey** — Pitest had no mutator that applied to this line.

For each surviving mutant, the report shows the mutator that produced it
and the source line. Survivors fall into three buckets:

1. **Real test gap.** The mutant changed observable behaviour and no test
   detected it. Write the missing assertion.
2. **Equivalent mutant.** The mutant changed bytecode but not observable
   behaviour (e.g. swapping `for (i in 0..n)` with `for (i in 0..<n+1)`).
   No test could reasonably kill it. Move on.
3. **Generated/synthetic code.** Kotlin generates a lot of bytecode that
   isn't directly meaningful at the source level — `equals` / `hashCode` /
   `copy` on `data class`, the synthetic dispatch table for
   `when (x: SealedType)`, `kotlin.jvm.internal.Intrinsics.checkNotNull*`
   for null-safety, default-argument bridges (`*$default`). Mutants here
   are *usually* equivalent and *occasionally* a real signal. Read each
   one. We deliberately do not exclude these so readers can develop the
   judgement to recognise them.

A practical workflow: filter the report to surviving mutants in `*.Slice.kt`
first. That's where killing mutants directly improves your `decide()` /
`evolve()` confidence.

## Two flavours of `AxonTestFixture` tests

Every test in this repo uses `AxonTestFixture`, but the construction differs:

- **Pure** — e.g.
  [`RecruitCreatureUnitTest`](../src/test/kotlin/com/dddheroes/heroesofddd/creaturerecruitment/write/recruitcreaturedcb/RecruitCreatureUnitTest.kt).
  Builds the fixture directly with `axonTestFixture(configSlice { … })`.
  No Spring context, no Testcontainers. Milliseconds per mutant.
- **Spring** — e.g. `*SpringSliceTest`, `*RestApiTest`. Autowires the
  fixture via `@HeroesAxonSpringBootTest`, which boots a Spring context and
  a Testcontainers Axon Server stack (cached across the suite). Seconds per
  mutant.

Both styles contribute to mutation coverage and both stay in scope on
purpose. When you author a *new* slice, prefer the pure pattern — same
`Given/When/Then` DSL, same assertions, vastly faster mutation feedback.

## Limitations and caveats

- **Kotlin synthetics produce noise.** As described above, expect equivalent
  mutants on `data class` methods, `when`-over-sealed dispatch tables, null
  checks, and `*$default` argument bridges. We don't filter them — learning
  to spot them is part of using Pitest on a Kotlin codebase. The free
  Pitest Kotlin plugin (`org.pitest:pitest-kotlin-plugin`) was archived in
  2023 without ever publishing a release to Maven Central; the only
  Kotlin-specific filtering available today is the commercial Arcmutate
  Kotlin plugin.
- **Spring/Testcontainers mutants are slow and memory-hungry.** This
  warrants a longer explanation because it's the single most common way
  Pitest runs fail in this project. The pom defaults (`<threads>2</threads>`,
  `<jvmArgs>-Xmx2g</jvmArgs>`) already lean conservative, but
  understanding the mechanism helps you tune them.

  **What Pitest does under the hood:** Pitest forks one JVM per worker
  thread to run mutated tests. Each fork is independent of the Maven
  parent JVM — `MAVEN_OPTS=-Xmx…` does *not* size the forks. The forks
  inherit Surefire's `argLine` (modern Pitest auto-inherits this) and the
  pitest plugin's own `<jvmArgs>`.

  **Why this hurts here:** with `*SpringSliceTest` and `*RestApiTest` in
  scope, every fork may boot its own Spring `ApplicationContext` and its
  own Testcontainers stack. The test-context cache keeps that bounded per
  fork, but doesn't share state across forks. So `threads × per-fork heap`
  is the real memory cost.

  **Symptoms:** a Maven exit code `137` (process killed), Pitest stopping
  mid-run with no error in its own log, or `java.lang.OutOfMemoryError`
  in `target/surefire-reports/`.

  **Three levers to tune, in order of impact:**
  1. **Scope.** Run Pitest one bounded context at a time
     (`-DtargetClasses=…creaturerecruitment.*`). Cuts both runtime and
     peak memory linearly.
  2. **Thread count.** Lower `<threads>` (or pass `-Dthreads=1` on the
     command line). One thread = one fork = one Spring context in memory
     at a time. Slower wall-clock, predictable memory.
  3. **Per-fork heap.** Raise `<jvmArgs>-Xmx…</jvmArgs>` if individual
     forks fail with `OutOfMemoryError` rather than the parent being
     killed. The default `-Xmx2g` is enough for `*UnitTest`-only scopes;
     for Spring-heavy slices bump to `-Xmx3g` or `-Xmx4g`.

  On CI, GitHub's `ubuntu-latest` runner has roughly 7 GB of usable
  memory; with the default config (2 threads × 2 GB) you have ~3 GB of
  headroom for OS + Maven + Docker, which is workable.

### OOM mitigation playbook (try in order)

If the defaults aren't enough on a particular scope, try these in
ascending order of impact / invasiveness. The earlier options preserve
report completeness; the later ones trade coverage for survival.

1. **Scope the run.** `-DtargetClasses=…oneContext.*` is by far the
   biggest lever. Already covered above.

2. **Drop to a single fork.** Pass `-Dthreads=1` (or set
   `<threads>1</threads>` in the pom). Eliminates concurrent Spring
   contexts entirely.

3. **Switch the child JVM to G1GC with fixed sizing.** Add to
   `<jvmArgs>`:

   ```xml
   <jvmArg>-XX:+UseG1GC</jvmArg>
   <jvmArg>-Xms2g</jvmArg>
   <jvmArg>-Xmx2g</jvmArg>
   ```

   Equal `-Xms`/`-Xmx` prevents heap resize churn during long forks;
   G1GC handles the Spring-context churn better than the default
   collector on heaps in the 2-4 GB range.

4. **Enable Testcontainers container reuse.** Pitest re-runs tests once
   per mutant, so containers boot many times. Setting
   `TESTCONTAINERS_REUSE_ENABLE=true` in the environment keeps containers
   alive across runs. ⚠️ This has a known caveat with Pitest: if Spring
   binds dynamic properties (e.g. container ports) per context, a reused
   container may have *different* ports than what the first context
   cached. The project's `@DynamicPropertySource` integration usually
   handles this correctly, but if you see connection failures rather
   than OOMs, this is the prime suspect — disable reuse or add
   `@DirtiesContext` to the affected tests.
   See [Reusable Containers — Testcontainers for Java](https://java.testcontainers.org/features/reuse/).

5. **Raise per-fork heap, lower thread count proportionally.** On a
   machine with more RAM, e.g. `<threads>1</threads>` +
   `<jvmArgs><jvmArg>-Xmx6g</jvmArg></jvmArgs>` lets one well-fed fork
   crunch through harder slices without OOM.

6. **Limit mutations per class** (last resort — sacrifices coverage).
   Pitest's `<maxMutationsPerClass>` caps the number of mutants emitted
   per class. Useful only if a single huge class is producing thousands
   of mutants and dominating runtime; rarely needed in a vertical-slice
   codebase.

7. **Out of scope here but worth knowing:** the commercial Arcmutate
   plugins (`pitest-git-plugin`, Arcmutate Kotlin, Arcmutate Spring) all
   substantially reduce both runtime and memory by either narrowing the
   mutation set (`+GIT` diff scoping) or filtering Kotlin/Spring synthetic
   mutants that consume forks without adding signal.

### Confirmed working configurations on this repo

Measured locally on an 11-core / 32 GB Mac with the pom defaults
(`<threads>2</threads>`, `<jvmArgs>-Xmx2g</jvmArgs>`):

| Scope | Tests | Mutants | Killed | Wall clock |
|---|---|---|---|---|
| Single slice (`*UnitTest` only) | 1 | 38 | 24 (63%) | ~3 s |
| Single bounded context (`creaturerecruitment.*`, all 65 tests incl. Spring/Testcontainers) | 65 | 123 | 83 (67%) | ~2.5 min |
| Full project (`com.dddheroes.heroesofddd.*`, all 265 tests) | 265 | 525 | 383 (73%) | ~12 min |

All three runs reported **0 memory errors**. On CI (`ubuntu-latest`, ~7 GB
usable RAM), expect the full-project run to take longer; if it fails, drop
`<threads>` to 1 first.
- **Equivalent mutants are not bugs in Pitest.** Some surviving mutants are
  genuinely undetectable. The goal is a *trend* in the killed-mutant ratio,
  not 100%.

## Future tuning

- **Promote to a hard gate.** Once a few runs converge on a baseline,
  uncomment `<mutationThreshold>` and `<coverageThreshold>` in
  [`pom.xml`](../pom.xml). Suggested starting values: 60-70 mutation
  threshold, 80 line coverage. Adjust based on observed survivor patterns.
- **`STRONGER` mutator group.** Adds `REMOVE_CONDITIONALS_EQUAL_ELSE` and
  experimental switch mutators. Try once `DEFAULTS` is green.
- **PR-scoped runs.** The commercial
  [`com.arcmutate:pitest-git-plugin`](https://docs.arcmutate.com/docs/git-integration)
  enables `features=+GIT(from[origin/main])` to mutate only classes changed
  in a PR diff, cutting runtime by 10-50×. Requires an Arcmutate licence;
  out of scope for this educational repo.
- **Periodic baseline.** Add a `schedule:` cron to the workflow to publish
  a fresh report on `main` weekly. Only worth doing once the report is
  routinely read.
