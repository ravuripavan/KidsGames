# KidsGames Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a sideloadable offline Android APK containing fourteen text-free
mini-games for ages 4–6, each with five difficulty levels, that holds a child's
attention for roughly two hours of travel.

**Architecture:** A host shell plus independent game plugins. Three frozen core
modules (`gameapi`, `designkit`, `vocab`) define the contract; each game is a leaf
Gradle module depending only on those. Games never depend on each other or on the
shell, which is what allows thirteen of them to be built concurrently.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, coroutines/Flow, Gradle with
version catalogs, JDK 21, minSdk 26.

**Spec:** `docs/superpowers/specs/2026-08-06-kids-travel-games-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- **No text.** No string resources, no literals passed to `Text`. Exemptions:
  numerals 1–5 as level indicators; `:games:talktime` for the taught word only.
- **No fail states.** No scores, timers, lives, countdowns, streaks, or losing.
  Wrong input gets a neutral response and play continues.
- **Levels advance on completion, never performance.** Never demoted, never lost,
  all unlocked levels stay replayable.
- **Difficulty grows only** by more elements, more discrimination, or longer
  sequence. Never by time pressure, tighter tolerance, or penalty.
- **Offline.** No `INTERNET` permission, no network calls, no remote assets.
- **No microphone.** No `RECORD_AUDIO`, no speech capture or evaluation, ever.
- **Audio optional.** Every game playable with volume at zero. Exemptions:
  `:games:talktime` and `:games:whatisit`, which must show a muted-speaker
  indicator.
- **Tap targets ≥ 64dp,** spaced so a clumsy hand cannot hit two.
- **Portrait, one-handed.** minSdk 26.
- **No analytics, ads, monetization, or data collection.**
- **Games are leaves.** A `:games:*` module may depend on `:core:gameapi`,
  `:core:designkit`, and (language games only) `:core:vocab`. Never on
  `:core:shell`, `:app`, or another game.
- **Commit after every task.** Author: Pavan Ravuri. No AI attribution in commit
  messages.

## Toolchain

- JDK 21: `C:\Program Files\Android\Android Studio\jbr`
- SDK: `%LOCALAPPDATA%\Android\Sdk`
- AVD: `Pixel_9_Pro_API_36-ext19` (single device — e2e is serialized)

---

## Phase 1 — Sequential (Tasks 1–7)

Phase 1 is the serialization point. Thirteen concurrent workers compile against
its output, so it is specified in full detail and nothing else starts until it is
merged and green.

### Task 1: Gradle skeleton and version catalog

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`
- Create: `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Produces: version catalog aliases `libs.androidx.compose.bom`,
  `libs.androidx.datastore.preferences`, `libs.kotlin.coroutines`,
  `libs.junit`, `libs.androidx.compose.ui.test.junit4` — every later module's
  `build.gradle.kts` references these aliases, never raw coordinates.

- [ ] **Step 1: Create `gradle/libs.versions.toml`** with versions for AGP,
      Kotlin, Compose BOM, DataStore, coroutines, JUnit, Compose UI test, and
      Espresso. Declare `compileSdk = 36`, `minSdk = 26` as `gradle.properties`
      values so every module reads one source of truth.

- [ ] **Step 2: Create root `build.gradle.kts`** declaring the AGP and Kotlin
      plugins with `apply false`.

- [ ] **Step 3: Create `settings.gradle.kts`** with `pluginManagement` and
      `dependencyResolutionManagement` repositories, and `include(":app")` only —
      game modules are added in Task 2.

- [ ] **Step 4: Verify the build resolves**

Run: `./gradlew --version && ./gradlew projects`
Expected: Gradle reports JDK 21 and lists the root project without error.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/
git commit -m "build: add Gradle skeleton and version catalog"
```

### Task 2: Module scaffolding for all seventeen modules

**Files:**
- Create: `core/gameapi/build.gradle.kts`, `core/designkit/build.gradle.kts`,
  `core/vocab/build.gradle.kts`, `core/shell/build.gradle.kts`,
  `app/build.gradle.kts`
- Create: `games/<name>/build.gradle.kts` × 14
- Modify: `settings.gradle.kts`

**Interfaces:**
- Produces: all module paths, so Phase 2 workers find an existing compiling
  scaffold rather than creating one.

The fourteen game module names, exactly:

`popballoons`, `whatisit`, `talktime`, `matchshapes`, `countanimals`,
`colorsort`, `tracelines`, `patterns`, `puzzleboard`, `memorypairs`,
`musicpad`, `carrace`, `cardesign`, `carwash`

- [ ] **Step 1: Add every module to `settings.gradle.kts`** as
      `include(":core:gameapi", ":core:designkit", ":core:vocab", ":core:shell", ":app")`
      plus `include(":games:popballoons")` … for all fourteen.

- [ ] **Step 2: Write a `build.gradle.kts` for each `:games:*` module** declaring
      the Android library plugin, Compose, and dependencies on `:core:gameapi` and
      `:core:designkit` only. `whatisit` and `talktime` additionally depend on
      `:core:vocab`. No module declares any other project dependency — this is the
      leafness guarantee, enforced by the build rather than by review.

- [ ] **Step 3: Verify every module compiles empty**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, seventeen modules configured.

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts core/ games/ app/
git commit -m "build: scaffold core, app and fourteen game modules"
```

### Task 3: `:core:gameapi` — the frozen contract

**Files:**
- Create: `core/gameapi/src/main/kotlin/com/kidsgames/gameapi/GameModule.kt`
- Test: `core/gameapi/src/test/kotlin/com/kidsgames/gameapi/GameModuleTest.kt`

**Interfaces:**
- Produces: `GameModule`, `AgeBand`, `Outcome` — every game module and the shell
  compile against these exact names.

- [ ] **Step 1: Write the failing test**

```kotlin
class GameModuleTest {
    @Test fun `outcome types are distinct singletons`() {
        assertNotEquals(Outcome.Completed as Outcome, Outcome.Abandoned as Outcome)
    }

    @Test fun `age bands cover the four to six range`() {
        assertEquals(2, AgeBand.entries.size)
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew :core:gameapi:test`
Expected: FAIL — unresolved reference `Outcome`.

- [ ] **Step 3: Write the contract, and nothing beyond it**

```kotlin
package com.kidsgames.gameapi

import androidx.compose.runtime.Composable

interface GameModule {
    val id: String
    val icon: Int
    val ageBand: AgeBand
    val estimatedMinutes: Int
    val levelCount: Int

    @Composable
    fun Play(level: Int, onFinished: (Outcome) -> Unit)
}

enum class AgeBand { FOUR_TO_FIVE, FIVE_TO_SIX }

sealed interface Outcome {
    data object Completed : Outcome
    data object Abandoned : Outcome
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :core:gameapi:test`
Expected: PASS.

- [ ] **Step 5: Commit and declare frozen**

```bash
git add core/gameapi/
git commit -m "feat(gameapi): freeze the GameModule contract"
```

### Task 4: `:core:designkit` — the shared language

**Files:**
- Create: `core/designkit/src/main/kotlin/com/kidsgames/designkit/KidButton.kt`
- Create: `.../designkit/Celebration.kt`, `.../designkit/SoundBank.kt`,
  `.../designkit/Palette.kt`, `.../designkit/Haptics.kt`
- Test: `core/designkit/src/test/kotlin/com/kidsgames/designkit/KidButtonTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces, and these exact signatures are what thirteen concurrent workers will
  call:
  - `@Composable fun KidButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit)` — enforces 64dp minimum internally
  - `@Composable fun Celebration(visible: Boolean, big: Boolean = false)` — `big = true` is the level-5 celebration
  - `class SoundBank` with `fun play(cue: Cue)` and `enum class Cue { TAP, SUCCESS, GENTLE_RETRY, CELEBRATION }`
  - `object KidPalette` exposing named high-contrast colours
  - `fun Modifier.kidTapFeedback(): Modifier` — light haptic plus scale-pop

- [ ] **Step 1: Write the failing test** asserting `KidButton` reports a minimum
      touch target of 64dp via `createComposeRule()` and
      `onNodeWithTag("kidbutton").assertHeightIsAtLeast(64.dp)`.

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew :core:designkit:testDebugUnitTest`
Expected: FAIL — unresolved reference `KidButton`.

- [ ] **Step 3: Implement all five files** to the signatures above. `SoundBank`
      wraps `SoundPool`, holds the four cues, and exposes cues by enum, never by
      resource id. `Celebration` honours the system animation scale: when
      animations are disabled system-wide, it appears instantly rather than not
      at all.

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :core:designkit:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit and declare frozen**

```bash
git add core/designkit/
git commit -m "feat(designkit): freeze shared visual and audio language"
```

### Task 5: `:core:vocab` — picture and word catalogue

**Files:**
- Create: `core/vocab/src/main/kotlin/com/kidsgames/vocab/VocabItem.kt`
- Create: `.../vocab/VocabCatalogue.kt`, `.../vocab/DailyPicker.kt`
- Create: `core/vocab/src/main/res/drawable/…` and `raw/…` (bundled assets)
- Test: `core/vocab/src/test/kotlin/com/kidsgames/vocab/VocabCatalogueTest.kt`

**Interfaces:**
- Produces:
  - `data class VocabItem(id, image: Int, audio: Int, word: String, sector: Sector, tier: Int)`
  - `enum class Sector { ANIMALS, FRUITS, VEGETABLES, VEHICLES, BODY, CLOTHES, HOUSEHOLD, FOOD, NATURE, JOBS, INSTRUMENTS, SPORTS }`
  - `object VocabCatalogue { val items: List<VocabItem>; fun forLevel(level: Int): List<VocabItem>; fun bySector(s: Sector): List<VocabItem> }`
  - `object DailyPicker { fun wordOfDay(dayOfYear: Int): VocabItem; fun sentenceOfDay(dayOfYear: Int): Sentence }`
  - `data class Sentence(val text: String, val audio: Int, val image: Int)`

- [ ] **Step 1: Write the failing tests**

```kotlin
class VocabCatalogueTest {
    @Test fun `catalogue holds at least one hundred items`() {
        assertTrue(VocabCatalogue.items.size >= 100)
    }

    @Test fun `every sector has at least six items`() {
        Sector.entries.forEach { s ->
            assertTrue("$s has too few", VocabCatalogue.bySector(s).size >= 6)
        }
    }

    @Test fun `daily pick is stable within a day and changes across days`() {
        assertEquals(DailyPicker.wordOfDay(42), DailyPicker.wordOfDay(42))
        assertNotEquals(DailyPicker.wordOfDay(42), DailyPicker.wordOfDay(43))
    }

    @Test fun `sentence pool covers a full year without repeating`() {
        val year = (1..365).map { DailyPicker.sentenceOfDay(it).text }
        assertEquals(365, year.toSet().size)
    }

    @Test fun `level one draws only the most familiar items`() {
        assertTrue(VocabCatalogue.forLevel(1).all { it.tier == 1 })
    }
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew :core:vocab:test`
Expected: FAIL — unresolved reference `VocabCatalogue`.

- [ ] **Step 3: Populate the catalogue** with at least 100 items, at least six per
      sector, each tagged `tier` 1–5 by how familiar it is to a 4–6 year old
      (tier 1: banana, dog, car; tier 5: helicopter, thermometer, mechanic). Add
      365 sentences of ordinary daily language, over-weighted toward travel
      phrasing. `DailyPicker` indexes by `dayOfYear` — deterministic, never
      random, never networked.

- [ ] **Step 4: Run and confirm pass**

Run: `./gradlew :core:vocab:test`
Expected: PASS, all five tests.

- [ ] **Step 5: Commit and declare frozen**

```bash
git add core/vocab/
git commit -m "feat(vocab): add picture and word catalogue with daily rotation"
```

### Task 6: `:games:popballoons` — the reference implementation

**Files:**
- Create: `games/popballoons/src/main/kotlin/com/kidsgames/popballoons/PopBalloonsState.kt`
- Create: `.../popballoons/PopBalloonsGame.kt`
- Test: `games/popballoons/src/test/kotlin/com/kidsgames/popballoons/PopBalloonsStateTest.kt`

**Interfaces:**
- Consumes: `GameModule`, `Outcome`, `KidButton`, `Celebration`, `SoundBank`.
- Produces: `object PopBalloonsGame : GameModule` — the pattern every Phase 2
  worker copies.

This task exists to prove the contract is sufficient. If a Phase 2 game cannot be
expressed against `gameapi` as written, that is discovered here, alone, and not by
thirteen workers at once.

Levels: L1 five still balloons; L2 eight drifting; L3 twelve varied sizes; L4
fifteen, pop only the named colour; L5 fifteen, pop in colour order.

- [ ] **Step 1: Write the failing state-machine tests**

```kotlin
class PopBalloonsStateTest {
    @Test fun `level one starts with five balloons`() {
        assertEquals(5, PopBalloonsState(level = 1).balloons.size)
    }

    @Test fun `popping every balloon completes the level`() {
        val s = PopBalloonsState(level = 1)
        s.balloons.toList().forEach { s.pop(it.id) }
        assertTrue(s.isComplete)
    }

    @Test fun `popping the wrong colour at level four does not end play`() {
        val s = PopBalloonsState(level = 4)
        val wrong = s.balloons.first { it.color != s.targetColor }
        s.pop(wrong.id)
        assertFalse(s.isComplete)
        assertEquals(0, s.penalties)   // there is no such thing as a penalty
    }
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew :games:popballoons:test`
Expected: FAIL — unresolved reference `PopBalloonsState`.

- [ ] **Step 3: Implement `PopBalloonsState`** as a plain Kotlin class with no
      Android imports, then `PopBalloonsGame` as the Composable rendering it. A
      wrong tap at L4/L5 plays `Cue.GENTLE_RETRY` and continues — it never resets
      progress, never decrements anything, never ends the level.

- [ ] **Step 4: Run and confirm pass**

Run: `./gradlew :games:popballoons:test`
Expected: PASS, all three tests.

- [ ] **Step 5: Commit**

```bash
git add games/popballoons/
git commit -m "feat(popballoons): add reference game implementation"
```

### Task 7: Minimal `:core:shell` and `:app`

**Files:**
- Create: `core/shell/src/main/kotlin/com/kidsgames/shell/GameRegistry.kt`,
  `.../shell/ProgressStore.kt`, `.../shell/PickerScreen.kt`, `.../shell/KidsApp.kt`
- Create: `app/src/main/kotlin/com/kidsgames/app/MainActivity.kt`,
  `app/src/main/AndroidManifest.xml`
- Test: `core/shell/src/test/kotlin/com/kidsgames/shell/ProgressStoreTest.kt`

**Interfaces:**
- Produces: `class GameRegistry(val games: List<GameModule>)`,
  `class ProgressStore` with `suspend fun levelFor(id: String): Int` and
  `suspend fun recordCompletion(id: String, level: Int)`.

- [ ] **Step 1: Write the failing progression tests**

```kotlin
class ProgressStoreTest {
    @Test fun `new game starts at level one`() = runTest {
        assertEquals(1, store.levelFor("popballoons"))
    }

    @Test fun `completing a level advances exactly one`() = runTest {
        store.recordCompletion("popballoons", 1)
        assertEquals(2, store.levelFor("popballoons"))
    }

    @Test fun `level never decreases`() = runTest {
        store.recordCompletion("popballoons", 4)
        store.recordCompletion("popballoons", 1)
        assertEquals(5, store.levelFor("popballoons"))
    }

    @Test fun `level five is the ceiling`() = runTest {
        repeat(10) { store.recordCompletion("popballoons", 5) }
        assertEquals(5, store.levelFor("popballoons"))
    }
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew :core:shell:testDebugUnitTest`
Expected: FAIL — unresolved reference `ProgressStore`.

- [ ] **Step 3: Implement** the registry, the DataStore-backed `ProgressStore`
      (written additively — highest level only ever raised, so a partial write
      cannot demote a child), a picture-only picker grid, and `MainActivity`.
      The manifest declares **no** `INTERNET` and **no** `RECORD_AUDIO`.

- [ ] **Step 4: Run and confirm pass**

Run: `./gradlew :core:shell:testDebugUnitTest`
Expected: PASS, all four tests.

- [ ] **Step 5: Verify the app runs on the emulator**

Run: `./gradlew :app:installDebug && adb shell am start -n com.kidsgames/.MainActivity`
Expected: picker appears, `popballoons` is reachable and playable, completing L1
returns to the picker with the level advanced to 2.

- [ ] **Step 6: Commit — Phase 1 complete**

```bash
git add core/shell/ app/
git commit -m "feat(shell): add registry, progress store, picker and app entry"
```

**Phase 1 gate:** `./gradlew build` green, the reference game playable on the
emulator, and the manifest confirmed free of network and microphone permissions.
Phase 2 does not start until this is true.

---

## Phase 2 — Parallel (Tasks 8–20)

Thirteen game modules, built concurrently, one `minigame-builder` per module. Each
worker receives the same task template below with its own module substituted, and
writes only inside `games/<its-module>/`.

Registration is **not** done by these workers. `settings.gradle.kts` already lists
every module from Task 2, and the registry is wired in Phase 3 — which keeps both
shared files entirely off the concurrent write path.

### Per-game task template

Applied to each of the thirteen modules below.

- [ ] **Step 1:** Read the spec's game table row for this module and the
      `popballoons` reference implementation.
- [ ] **Step 2:** Write failing tests for the state machine covering: level 1
      starting conditions, completion at each of the five levels, and — most
      importantly — that no input path sets any penalty, score, or terminal
      failure state.
- [ ] **Step 3:** Run them; confirm they fail for the right reason.
- [ ] **Step 4:** Implement `<Name>State` as a plain Kotlin class with no Android
      imports.
- [ ] **Step 5:** Implement `<Name>Game : GameModule` rendering that state.
- [ ] **Step 6:** Run tests; confirm green.
- [ ] **Step 7:** Commit `feat(<module>): add <module> with five levels`.

### The thirteen modules and their five levels

| Task | Module | L1 | L2 | L3 | L4 | L5 |
|---|---|---|---|---|---|---|
| 8 | `whatisit` | familiar items | + household, vehicles | + nature, clothes | + jobs, instruments | all sectors + find-the-one-I-name |
| 9 | `talktime` | word alone | word + picture named | two-word phrase | full sentence | sentence → tap matching picture |
| 10 | `matchshapes` | 3 shapes | 5 | 7 | 7 rotated | 7 rotated, similar |
| 11 | `countanimals` | to 5 | to 10 | match numeral | count two groups | addition to 10 |
| 12 | `colorsort` | 2 colours | 3 | 4 | colour + shape | colour + shape + size |
| 13 | `tracelines` | straight | curve | zigzag | shape outline | letter outline |
| 14 | `patterns` | AB | ABAB | AABB | ABC | ABC, colour + shape vary |
| 15 | `puzzleboard` | 4 pieces | 6 | 9 | 12 | 12, no outline |
| 16 | `memorypairs` | 3 pairs | 4 | 6 | 8 | 10 |
| 17 | `musicpad` (sandbox) | 4 pads | 6 | 8 | + 2nd instrument | record and replay |
| 18 | `carrace` | 2 lanes | 3 lanes | + obstacles | + ramps | + branching roads |
| 19 | `cardesign` (sandbox) | 4 colours | 8 colours | + wheels | + stickers | + body shapes |
| 20 | `carwash` (sandbox) | sponge | + hose | + dryer | + wax, polish | dirtier car, all tools |

**Sandbox modules** (`musicpad`, `cardesign`, `carwash`) never reach a completion
state. Their levels unlock tools rather than difficulty, and they call
`onFinished(Outcome.Completed)` after a few minutes of play so open play is
rewarded on the same schedule as goal-directed play.

**`carrace` requires specific care.** No crash, no lap timer, no opponent, no way
to lose. Obstacles slow the car with a soft bounce; the car cannot leave the road;
the drive continues until the child stops.

**`whatisit` and `talktime`** draw from `:core:vocab` and never bundle their own
images or recordings. Both show a muted-speaker indicator. Neither records audio.

### Per-game loop (runs around each task above)

```
build → review (kid-qa-reviewer, fresh context) → findings?
   ├─ yes → fix → re-review          (max 3 rounds, then park)
   └─ no  → unit tests → e2e on emulator (serialized) → done
```

The reviewer must never be the agent that wrote the module. On exhausting three
rounds the game is parked with its findings reported and the remaining games
continue; parked games are the only thing needing human attention.

---

## Phase 3 — Integration (Tasks 21–25)

### Task 21: Register all fourteen games

- [ ] Wire every `GameModule` into `GameRegistry` in `:app`. Verify each appears
      in the picker and launches.
- [ ] Run `./gradlew build`; confirm green.
- [ ] Commit `feat(app): register all fourteen games`.

### Task 22: Session orchestrator

- [ ] Write failing tests: unplayed games are weighted above played ones; the car
      cluster (`carrace`, `carwash`, `cardesign`) is offered together; a suggestion
      never interrupts active play.
- [ ] Implement, weighting toward breadth — breadth is what produces two hours at
      this age. Suggest, never force. Nudge once on idle, never on a loop.
- [ ] Commit `feat(shell): add session orchestrator`.

### Task 23: Parental gate

- [ ] Write failing tests: a three-second hold exits; rapid tapping does not.
- [ ] Implement as a corner press-and-hold. Friction, not security.
- [ ] Commit `feat(shell): add parental gate`.

### Task 24: Full-suite QA and repo-wide checks

- [ ] Add a Gradle check failing the build on any string resource or `Text`
      literal in `:games:*`, allowlisting `:games:talktime` alone.
- [ ] Add a check failing on `INTERNET` or `RECORD_AUDIO` in any manifest.
- [ ] Run `kid-qa-reviewer` across all fourteen modules. Fix confirmed findings.
- [ ] Commit `test: add repo-wide no-text and permission checks`.

### Task 25: Release APK

- [ ] Run the full suite: `./gradlew build`.
- [ ] Run `emulator-e2e-tester` over all fourteen games, one at a time.
- [ ] Assemble the release APK and verify sideload install.
- [ ] Confirm and state explicitly that the manifest declares no network and no
      microphone permission.
- [ ] Commit and tag.

---

## Notes on plan granularity

Phase 1 is specified step-by-step because thirteen workers depend on its exact
output, and an ambiguity there multiplies by thirteen. Phase 2 is specified as one
template plus a per-module level table, because the thirteen tasks are genuinely
the same task with different content — writing them out thirteen times would
duplicate rather than clarify, and each worker receives the full template with its
own row substituted.
