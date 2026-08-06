# KidsGames — Offline Android Mini-Game Suite for Ages 4–6

**Date:** 2026-08-06
**Status:** Design approved, pending spec review
**Repo:** https://github.com/ravuripavan/KigsGames

## Problem

A 4–6 year old needs to stay engaged on an Android phone or tablet for roughly two
hours of travel time, with no network connection available.

At this age a child gives any single activity five to ten minutes. Depth does not
hold attention; variety does. Two hours therefore requires roughly a dozen short
activities plus a mechanism that moves the child from one to the next without an
adult intervening.

The 4–6 range spans a literacy boundary. A four year old is a pure pre-reader; a
six year old reads sentences and is bored by activities pitched at a four year
old. The suite handles this in two ways: it stays entirely text-free, which costs
the older child nothing, and each game carries three difficulty levels so the same
activity meets both ends of the range.

## Constraints

These are hard requirements. Every design decision below follows from them.

- **No text.** No words anywhere in the UI. All meaning is carried by picture,
  animation, colour, and sound. Numerals are the single exception: digits 1–3 may
  appear as level indicators, since they are legible to the older half of the range
  and are decorative to the younger half.
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
    val ageBand: AgeBand        // FOUR_TO_FIVE or FIVE_TO_SIX
    val estimatedMinutes: Int   // used by the orchestrator to pace rotation
    val levelCount: Int         // always 3 in the first wave

    @Composable
    fun Play(level: Int, onFinished: (Outcome) -> Unit)
}

enum class AgeBand { FOUR_TO_FIVE, FIVE_TO_SIX }

sealed interface Outcome {
    data object Completed : Outcome   // child reached the end of this level
    data object Abandoned : Outcome   // child backed out or went idle
}
```

A game signals completion by invoking `onFinished`. It does not navigate, does not
know the shell exists, does not know what other games exist, and does not persist
its own level — the shell owns progression and passes `level` in.

Keeping level state out of the game module matters for concurrency: eleven agents
writing eleven private persistence schemes would produce eleven subtly different
progression behaviours. The game renders the difficulty it is told to render.

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
- **Progress store.** DataStore, holding per-game highest level reached, play
  counts, and last-played timestamps. It is the single owner of progression: game
  modules never persist level state themselves. Never leaves the device.

  Because the store is the sole owner of progression, a corrupt or missing store
  would silently reset every child's progress. It is therefore written
  additively — highest level reached is only ever raised, never lowered — so a
  partial write cannot demote a child.

### Levels and progression

Every game has three levels. Levels exist to encourage the child and to stretch
the activity across the 4–6 range, not to test them.

**Advancement is on completion, never on performance.** A child who needs forty
taps to finish level 1 advances exactly like a child who needs four. There is no
score threshold, no star rating, no minimum accuracy, and no time requirement.
Finishing is the only condition, and every level can be finished by every child in
the range given enough taps.

**Progression is never lost.** Once level 3 is reached in a game, it stays
reached. There is no demotion, no streak to break, and no decay over time. A child
returning after a week starts where they left off.

**All unlocked levels stay playable.** A child who prefers level 1 may replay it
indefinitely. The picker shows the highest level reached, and a press-and-hold on
a game reveals the unlocked levels as one, two, or three dots so a child can drop
back down without an adult.

**Levels are not locked behind failure — only behind absence.** Level 2 becomes
available because level 1 was finished, not because level 1 was finished *well*.
Nothing in the app can be permanently missed.

What "harder" means is per-game and is the game author's judgement, but it follows
one of three shapes:

- **More elements.** Four balloons become eight become twelve. Two cards become
  six become ten.
- **More discrimination.** Sorting by colour becomes sorting by shape becomes
  sorting by both at once.
- **More sequence.** One step becomes a two-step then a three-step sequence held
  in memory.

Difficulty never increases by adding time pressure, reducing tolerance, or
punishing error, because all three reintroduce fail states through the back door.

Reaching level 3 in a game triggers a larger, distinct celebration and adds a
permanent mark to that game's picker tile. This is the app's only long-term goal
and the main reason a child returns to a game rather than drifting away from it.

Sandbox games — `musicpad`, `cardesign`, `carwash` — have no completion state, so
their three levels unlock **tools rather than challenge**: more instruments, more
paint colours and stickers, more wash implements. A sandbox level advances after a
few minutes of play rather than on completion, so open play is rewarded on the same
schedule as goal-directed play.

### First wave of mini-games

Ten games, all buildable against the same contract. Each is a leaf module with no
knowledge of the others.

| Module | Activity | Levels 1 → 2 → 3 |
|---|---|---|
| `:games:popballoons` | Tap floating balloons; each pops with a sound and colour burst | 5 balloons → 10, drifting → 15, drifting and varied sizes |
| `:games:matchshapes` | Drag a shape to its matching hole | 3 shapes → 5 → 7 with rotation needed |
| `:games:countanimals` | Tap animals one at a time; each tap speaks the next number | count to 3 → to 5 → to 10 |
| `:games:colorsort` | Drag items into matching colour bins | 2 colours → 4 colours → sort by colour and shape together |
| `:games:tracelines` | Trace a dotted path with a finger; the line fills in behind | straight line → curve → shape outline |
| `:games:animalsounds` | Tap an animal, hear its sound, watch it animate | 4 animals → 8 → find-the-animal-by-its-sound |
| `:games:puzzlefour` | Drag-and-drop jigsaw with generous snapping | 4 pieces → 6 → 9 |
| `:games:memorypairs` | Picture memory match | 3 pairs → 5 → 8 |
| `:games:musicpad` | Pads that each play a note and animate. Sandbox | 4 pads → 8 pads → 8 pads with a second instrument |
| `:games:carrace` | Endless lane-based drive. Tap or tilt to switch lanes and collect stars | 2 lanes → 3 lanes with obstacles → 3 lanes, obstacles, ramps |
| `:games:cardesign` | Drag paint, wheels, and stickers onto a car. Sandbox | 4 colours → plus wheels and stickers → plus body shapes |
| `:games:carwash` | Drag sponge, hose, and dryer over a dirty car until it shines | sponge → plus hose and dryer → plus wax and a dirtier car |

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

### The per-game development loop

Each game module runs through an automated loop with no human in the middle. The
loop is per-game, so eleven of them run concurrently in Phase 2.

```
build  →  review  →  findings?  ──yes──→  fix  ──→  re-review ──┐
                         │                                       │
                         no                          (max 3 rounds)
                         ↓                                       │
                    unit tests  ←────────────────────────────────┘
                         │
                    pass? ──no──→ fix (counts against the same 3 rounds)
                         │
                        yes
                         ↓
              e2e on emulator (serialized)
                         │
                    pass? ──no──→ fix
                         │
                        yes
                         ↓
                       done
```

**Termination.** The loop is bounded at three review rounds per game. An unbounded
"loop until no findings" does not terminate when a reviewer emits a finding the
implementer cannot resolve — the two simply disagree forever. On exhausting three
rounds the game is parked with its outstanding findings reported, and the other
games continue. Parked games are the only thing requiring human attention.

**Review is fresh-context.** The reviewing agent must not be the agent that wrote
the code. An implementer reviewing its own module reliably confirms its own
assumptions, which is exactly the failure the loop exists to catch.

**Gates are ordered and cheap-first.** Review before unit tests, unit tests before
e2e. A game with a fail state in its state machine should be caught by a grep in
seconds, not by an emulator run several minutes later.

**e2e is serialized.** There is one AVD. Installing and driving two APKs on a
single emulator concurrently interleaves input and produces meaningless results.
Games queue for the emulator one at a time after passing their unit tests, so the
e2e stage is a drain rather than a fan-out. Total e2e wall-clock is therefore
roughly twelve sequential runs, and it dominates the tail of Phase 3.

If throughput becomes the constraint, the fix is more AVDs rather than more
agents. Nothing else in the loop is contended.

**What e2e actually verifies.** Launch the app, navigate the picker to the game,
play it to completion at each unlocked level, confirm `onFinished` fires, confirm
return to the picker, and confirm the level advanced. This catches the class of
defect unit tests structurally cannot: a state machine that is correct while the
Composable wired to it is not.

### Toolchain

- JDK 21, from the Android Studio bundled runtime
- Android SDK at `%LOCALAPPDATA%\Android\Sdk`
- `adb` from platform-tools
- Emulator target: `Pixel_9_Pro_API_36-ext19`

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
