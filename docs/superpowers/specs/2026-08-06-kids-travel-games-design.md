# KidsGames — Offline Android Mini-Game Suite for Ages 3–5

**Date:** 2026-08-06
**Status:** Design approved, pending spec review
**Repo:** https://github.com/ravuripavan/KigsGames

## Problem

A 3–5 year old needs to stay engaged on an Android phone or tablet for roughly two
hours of travel time, with no network connection available.

At this age a child gives any single activity five to ten minutes. Depth does not
hold attention; variety does. Two hours therefore requires roughly ten short
activities plus a mechanism that moves the child from one to the next without an
adult intervening.

## Constraints

These are hard requirements. Every design decision below follows from them.

- **Pre-readers.** No text anywhere in the UI. All meaning is carried by picture,
  animation, colour, and sound.
- **Offline.** The app declares no `INTERNET` permission. Nothing loads from a
  network at any time.
- **No fail states.** Nothing is ever wrong. Incorrect input produces a neutral,
  gentle response and the activity continues. There are no scores, timers,
  lives, or losing conditions.
- **Personal use.** Sideloaded APK for family use. No Play Store release, no
  monetization, no ads, no analytics, no data collection of any kind.
- **One-handed portrait play.** The device is held in a car seat by a small child.
- **Audio is optional.** Sound enhances every activity but is never required to
  understand one, because headphones may be unavailable.
- **Low battery draw.** Animations pause when the activity is not visible.

## Stack

Kotlin with Jetpack Compose, targeting Android. No game engine.

Compose's animation APIs (`Animatable`, `graphicsLayer`, `AnimatedContent`) cover
tap, drag, scale, and fade — which is the full interaction vocabulary these games
need. Adding Godot or Unity would introduce an entire toolchain to support
capability the games do not use.

- Language: Kotlin
- UI: Jetpack Compose (Material 3 used only as a dependency baseline; the visual
  language is custom)
- Persistence: DataStore (Preferences)
- Async: Kotlin coroutines and `Flow`
- Build: Gradle with version catalogs, one module per mini-game
- Minimum SDK: 26. Target SDK: current.

## Architecture

The system is a host shell plus a set of independent game plugins.

```
:app                     Application entry point, wires everything together
:core:gameapi            The GameModule contract. Depends on nothing but Compose.
:core:designkit          Shared visual and audio language
:core:shell              Registry, session orchestrator, parental gate, progress store
:games:<name>            One module per mini-game. Depends on gameapi + designkit only.
```

The critical property is that `:games:*` modules never depend on each other and
never depend on `:core:shell`. Each is a leaf. This is what allows many of them to
be built concurrently without conflict, and what allows any one of them to be
deleted without touching anything else.

### `:core:gameapi` — the contract

This module is deliberately tiny and is frozen before any game is written. Every
game module and the shell both compile against it, so a change here forces a
rebuild of everything. It contains the interface, the outcome type, and nothing
else.

```kotlin
interface GameModule {
    val id: String              // stable identifier, used as the DataStore key
    val icon: Int               // drawable resource shown on the picker
    val ageBand: AgeBand        // THREE_TO_FOUR or FOUR_TO_FIVE
    val estimatedMinutes: Int   // used by the orchestrator to pace rotation

    @Composable
    fun Play(onFinished: (Outcome) -> Unit)
}

enum class AgeBand { THREE_TO_FOUR, FOUR_TO_FIVE }

sealed interface Outcome {
    data object Completed : Outcome   // child reached a natural end
    data object Abandoned : Outcome   // child backed out or went idle
}
```

A game signals completion by invoking `onFinished`. It does not navigate, does not
know the shell exists, and does not know what other games exist.

### `:core:designkit` — the shared language

Frozen alongside `gameapi`, because every game depends on it and a late change
ripples into all of them.

- **Tap targets.** A minimum of 64dp, with a `KidButton` composable enforcing it.
- **Palette.** High-saturation, high-contrast. Colour is never the sole carrier of
  meaning, since colour vision deficiency is undiagnosed at this age.
- **Celebration.** A single shared `Celebration()` composable — star burst,
  scale-pop, chime — used by every game, so success feels identical everywhere.
- **Audio.** A `SoundBank` wrapping `SoundPool` with a fixed set of cues: tap,
  success, gentle-retry, celebration. Games reference cues by name, never by file.
- **Haptics.** Light tick on tap, medium on success.
- **Reduced motion.** Honours the system animation scale; when animations are
  disabled system-wide, transitions become instant rather than absent.

### `:core:shell` — the host

- **Registry.** A list of `GameModule` instances, assembled in `:app`. Adding a
  game is one line here and one line in `settings.gradle.kts`.
- **Session orchestrator.** Tracks what has been played this session and how long
  ago. On `Completed` it offers the next game; on idle it nudges. It weights toward
  games not yet played this session, so the child covers breadth before repeating.
- **Picker.** A picture-only grid. Free choice is always available — the
  orchestrator suggests, it never forces.
- **Parental gate.** Exiting to Android requires a three-second press-and-hold on a
  corner target. This is a friction gate, not security; it exists so a child does
  not accidentally leave mid-journey.
- **Progress store.** DataStore, holding per-game play counts and last-played
  timestamps. Used only for rotation weighting. Never leaves the device.

### First wave of mini-games

Ten games, all buildable against the same contract. Each is a leaf module with no
knowledge of the others.

| Module | Activity |
|---|---|
| `:games:popballoons` | Tap floating balloons; each pops with a sound and colour burst |
| `:games:matchshapes` | Drag a shape to its matching hole |
| `:games:countanimals` | Tap animals one at a time; each tap speaks the next number |
| `:games:colorsort` | Drag items into matching colour bins |
| `:games:tracelines` | Trace a dotted path with a finger; the line fills in behind |
| `:games:animalsounds` | Tap an animal, hear its sound, watch it animate |
| `:games:puzzlefour` | Four-piece drag-and-drop jigsaw with generous snapping |
| `:games:memorypairs` | Six-card picture memory match |
| `:games:musicpad` | Six pads, each a note and an animation. Pure sandbox, no goal |
| `:games:carrace` | Endless lane-based drive. Tap or tilt to switch lanes and collect stars |
| `:games:cardesign` | Drag paint, wheels, and stickers onto a car. Pure sandbox, no goal |
| `:games:carwash` | Drag sponge, hose, and dryer over a dirty car until it shines |

Three of the twelve are open-ended sandboxes with no completion state, because
some children settle into open play and a suite of only goal-directed activities
pushes them out of the app.

The three car activities are grouped deliberately: a child who latches onto
vehicles can move race → wash → design and stay absorbed far longer than the
rotation would otherwise achieve. The orchestrator therefore treats them as a
loose affinity cluster when weighting suggestions.

`:games:carrace` needs particular care, because racing games are where fail states
creep back in. There is no crash, no lap timer, no opponent, and no way to lose.
Obstacles slow the car and produce a soft bounce rather than ending the run. The
car cannot leave the road, and the drive continues until the child stops.

"As many as fit" was the stated scope. Twelve is the first wave; the contract
imposes no ceiling, and later games are added without modifying any existing
module.

### Themed journey map

Deferred, and deliberately so. Because games are plugins addressed through the
registry, replacing the picker grid with a train-journey map is a change to one
screen in `:core:shell`. It is a reskin of the host, not an architectural change,
and it should not block the first playable build.

## Build sequence

**Phase 1 — sequential.** Freeze `:core:gameapi` and `:core:designkit`. Build
`:games:popballoons` as the reference implementation, proving the contract is
sufficient. Stand up a minimal `:core:shell` and `:app` so the reference game is
reachable from a launch icon. Nothing else starts until this phase is merged.

**Phase 2 — parallel.** All remaining eleven game modules, concurrently. Each
worker owns exactly one `:games:*` directory and touches no file outside it.
Registration is applied at integration rather than by the workers.

**Phase 3 — integration.** Session orchestrator tuning, parental gate, full-suite
QA pass, release APK.

The phase boundary is a genuine dependency, not a convention. Phase 2 workers all
compile against `gameapi` and `designkit`; if those move, every worker's output
breaks at once.

### Why this parallelizes

Concurrency comes from disjoint files, not from worker count. Two workers editing
one file serialize regardless of how many are running. The design guarantees
disjointness:

- Each game lives in its own module directory, so no two workers write the same
  file.
- Each game depends only on frozen modules, so no worker waits on another.
- Registration is two additive lines — one in `settings.gradle.kts`, one in the
  registry — applied at integration time rather than by each worker, which keeps
  the two shared files off the concurrent write path entirely.

## Agent roster

| Agent | Responsibility | Concurrency |
|---|---|---|
| `game-architect` | Defines and freezes `gameapi` and `designkit`; scaffolds modules | Phase 1 only, alone |
| `kid-ux-designer` | Visual and audio language, age-appropriateness review | Phase 1, then advisory |
| `minigame-builder` | Builds exactly one `:games:*` module | Phase 2, one instance per game |
| `android-integrator` | Shell, registry wiring, Gradle, APK assembly | Phases 1 and 3 |
| `kid-qa-reviewer` | Audits for fail states, tap-target size, dead ends, text leakage | Phase 3 |

`minigame-builder` is the agent that is cloned. The others are single instances.

## Testing

Each game splits its state machine from its Composable. The state machine is a
plain Kotlin class with no Android dependencies, unit-tested in isolation:
`:games:matchshapes` tests run without an emulator and without the shell.

This split is what makes concurrent output safe to merge. A game module arrives
with passing tests that depend on nothing outside itself, so integration is a
compile step rather than a review of behaviour.

Compose UI tests cover the shell: picker navigation, parental gate timing, and
orchestrator rotation.

A repo-wide check enforces the no-text rule by failing the build if any `:games:*`
module contains a string resource or a literal passed to `Text`.

## Error handling

There are no user-facing errors. A game that throws is caught at the shell
boundary, and the child is returned to the picker with a normal transition
animation and no message. The failure is written to a local log for the developer.

Missing or corrupt DataStore state falls back to defaults, because the store holds
only rotation hints and losing it costs nothing.

## Out of scope

- Play Store release, content rating, and privacy policy
- Any network capability, account, sync, or cloud save
- Ads, in-app purchase, and analytics
- Multiplayer and device-to-device play
- iOS and tablet-specific layouts beyond what responsive Compose gives for free
- Localization, which is moot in a no-text app
