# KidsGames — Offline Android Mini-Game Suite for Ages 4–6

**Date:** 2026-08-06
**Status:** Design approved, pending spec review
**Repo:** https://github.com/ravuripavan/KidsGames

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
the older child nothing, and each game carries five difficulty levels so the same
activity meets both ends of the range.

## Constraints

These are hard requirements. Every design decision below follows from them.

- **No text.** No words anywhere in the UI. All meaning is carried by picture,
  animation, colour, and sound. Numerals are one exception: digits 1–5 may appear
  as level indicators, since they are legible to the older half of the range and
  decorative to the younger half. `:games:talktime` is the other, and the only
  module permitted to render words — see below.
- **Offline.** The app declares no `INTERNET` permission. Nothing loads from a
  network at any time.
- **No fail states.** Nothing is ever wrong. Incorrect input produces a neutral,
  gentle response and the activity continues. There are no scores, timers,
  lives, or losing conditions.
- **Personal use.** Sideloaded APK for family use. No Play Store release, no
  monetization, no ads, no analytics, no data collection of any kind.
- **One-handed portrait play.** The device is held in a car seat by a small child.
- **Audio is optional.** Sound enhances every activity but is never required to
  understand one, because headphones may be unavailable. `:games:talktime` and
  `:games:whatisit` are the only exemptions, since speech is their content.
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
:core:vocab              Bundled catalogue of pictures, names and recorded audio
:core:shell              Registry, session orchestrator, parental gate, progress store
:games:<name>            One module per mini-game. Depends on gameapi + designkit
                         (+ vocab, for the two language games) only.
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
    val levelCount: Int         // always 5 in the first wave

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

Keeping level state out of the game module matters for concurrency: thirteen agents
writing thirteen private persistence schemes would produce thirteen subtly different
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

### `:core:vocab` — the picture and word catalogue

Frozen in Phase 1 alongside `gameapi` and `designkit`, because two games depend on
it: `:games:whatisit` and `:games:talktime`.

It exists because both games need the same asset shape — a picture, a name, and a
recording of that name. Built separately they would produce two asset pipelines,
two audio formats, and the same banana recorded twice in two different voices. One
catalogue, one voice, one format.

```kotlin
data class VocabItem(
    val id: String,
    val image: Int,       // bundled drawable
    val audio: Int,       // bundled recording of the spoken name
    val word: String,     // written form; rendered only by talktime
    val sector: Sector,
    val tier: Int,        // 1..5, how familiar the item is to a 4-6 year old
)

enum class Sector {
    ANIMALS, FRUITS, VEGETABLES, VEHICLES, BODY, CLOTHES,
    HOUSEHOLD, FOOD, NATURE, JOBS, INSTRUMENTS, SPORTS
}
```

At least 100 items across the twelve sectors, with no sector below six items so no
level is dominated by one category. `tier` drives level selection: tier 1 holds
things a four year old already names (banana, dog, car), tier 5 holds things a six
year old is still acquiring (helicopter, thermometer, mechanic).

All audio is one voice, recorded at one level, bundled in the APK. No
text-to-speech at runtime: TTS quality and voice vary by device and would make the
suite sound different on each phone, and it is a dependency the app does not need
while offline.

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

Every game has five levels. Levels exist to encourage the child and to stretch
the activity across the 4–6 range, not to test them.

**Advancement is on completion, never on performance.** A child who needs forty
taps to finish level 1 advances exactly like a child who needs four. There is no
score threshold, no star rating, no minimum accuracy, and no time requirement.
Finishing is the only condition, and every level can be finished by every child in
the range given enough taps.

**Progression is never lost.** Once level 5 is reached in a game, it stays
reached. There is no demotion, no streak to break, and no decay over time. A child
returning after a week starts where they left off.

**All unlocked levels stay playable.** A child who prefers level 1 may replay it
indefinitely. The picker shows the highest level reached, and a press-and-hold on
a game reveals the unlocked levels as one to five dots so a child can drop
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

Reaching level 5 in a game triggers a larger, distinct celebration and adds a
permanent mark to that game's picker tile. This is the app's only long-term goal
and the main reason a child returns to a game rather than drifting away from it.

Sandbox games — `musicpad`, `cardesign`, `carwash` — have no completion state, so
their five levels unlock **tools rather than challenge**: more instruments, more
paint colours and stickers, more wash implements. A sandbox level advances after a
few minutes of play rather than on completion, so open play is rewarded on the same
schedule as goal-directed play.

### First wave of mini-games

Ten games, all buildable against the same contract. Each is a leaf module with no
knowledge of the others.

Levels 1 and 2 target a four year old, level 3 sits in the middle, and levels 4
and 5 stretch a six year old. The step from 1 to 5 is deliberately large, because
a single game must serve both ends of the range.

| Module | Activity | L1 | L2 | L3 | L4 | L5 |
|---|---|---|---|---|---|---|
| `:games:whatisit` | Shows a picture and asks what it is; tapping the speaker says the name aloud | familiar items | plus household and vehicles | plus nature and clothes | plus jobs and instruments | all sectors, plus find-the-one-I-name |
| `:games:talktime` | Daily word and sentence, spoken aloud with a picture, for the child to repeat | word alone | word plus its picture named | two-word phrase | short sentence | sentence, then pick its matching picture |
| `:games:popballoons` | Tap floating balloons; each pops with a colour burst and a spoken colour name | 5, still | 8, drifting | 12, varied sizes | 15, pop only one named colour | 15, pop in colour order |
| `:games:matchshapes` | Drag a shape to its matching hole | 3 shapes | 5 shapes | 7 shapes | 7 with rotation | 7 rotated, similar shapes |
| `:games:countanimals` | Tap animals one at a time; each tap speaks the next number | to 5 | to 10 | pick the matching numeral | count two groups | simple addition to 10 |
| `:games:colorsort` | Drag items into matching bins | 2 colours | 3 colours | 4 colours | colour and shape | colour, shape and size |
| `:games:tracelines` | Trace a dotted path with a finger; the line fills in behind | straight line | curve | zigzag | shape outline | letter outline |
| `:games:patterns` | Complete a repeating sequence by tapping the piece that comes next | AB | ABAB | AABB | ABC | ABC with colour and shape varying |
| `:games:puzzleboard` | Drag-and-drop jigsaw with generous snapping | 4 pieces | 6 | 9 | 12 | 12 with no background outline |
| `:games:memorypairs` | Picture memory match | 3 pairs | 4 | 6 | 8 | 10 |
| `:games:musicpad` | Pads that each play a note and animate. Sandbox | 4 pads | 6 pads | 8 pads | second instrument | record and replay a tune |
| `:games:carrace` | Endless lane-based drive. Tap or tilt to switch lanes and collect stars | 2 lanes | 3 lanes | plus obstacles | plus ramps | plus branching roads |
| `:games:cardesign` | Drag paint, wheels, and stickers onto a car. Sandbox | 4 colours | 8 colours | plus wheels | plus stickers | plus body shapes |
| `:games:carwash` | Drag sponge, hose, and dryer over a dirty car until it shines. Sandbox | sponge | plus hose | plus dryer | plus wax and polish | dirtier car, all tools |

Changes from the initial draft, following the shift to ages 4–6:

- **`animalsounds` removed.** Tapping an animal to hear a noise has no level-3 form
  that stretches a six year old, and its picture-plus-audio pairing is absorbed by
  `talktime`, which does the same thing with transferable vocabulary.
- **`patterns` added.** Sequence completion is the strongest single predictor of
  early maths readiness in this band, and it levels cleanly from ABAB to ABCABC.
- **`puzzlefour` renamed `puzzleboard`**, since the piece count is now a level
  rather than a fixed property of the game.
- **`countanimals` starts at five, not three**, and its upper levels introduce numeral
  recognition, which is appropriate at five to six and was not at three.
- **`tracelines` level 5 is letter outlines**, which prepares handwriting for
  the older half without requiring any reading.

### `:games:whatisit` — naming pictures

A picture fills the screen. The child is invited to say what it is. Tapping the
speaker button plays the recorded name — *banana* — and the picture animates in
response. A next arrow brings the following item.

The child's answer is never captured or checked. The pause before tapping the
speaker is the whole activity: the child names it privately, then hears whether
they matched. Nothing is scored, so a child who says nothing and a child who names
every item both proceed identically.

**Levels widen the catalogue rather than tightening the task.** Level 1 draws only
tier-1 items from the most familiar sectors; each level adds sectors and admits
higher tiers. Level 5 draws from all twelve sectors and adds a reverse mode: three
pictures are shown, a name is spoken, and the child taps the one that matches.
Reverse mode is receptive rather than productive recall, which is meaningfully
harder and is the right stretch for a six year old.

A wrong tap in reverse mode is not wrong. The tapped picture is named aloud —
*that's a carrot* — and the round continues until the child finds the match. The
child is told what they picked, never that they erred.

**Audio exemption.** Like `talktime`, this game needs sound: the spoken name is
its content. It shows a speaker indicator when the device is muted. It renders no
text, so it takes no text exemption — the word is heard, never written.

**No microphone.** The child's speech is never recorded or evaluated, for the same
reasons as `talktime`.

### `:games:talktime` — speech and sentences

A daily word and a daily sentence, spoken aloud alongside a picture, for the child
to hear and repeat. It is the only module in the suite that teaches language
directly, and it is the only one that needs exemptions from two hard constraints.

**Text exemption.** This module may render words on screen. Elsewhere text is
noise a pre-reader cannot use; here the written form is the point, and a five or
six year old connecting a heard word to its written shape is the entire
educational value. The word appears beneath its picture, large and uncrowded. A
four year old ignores it harmlessly.

This exemption is scoped to `:games:talktime` alone. The repo-wide no-text build
check allowlists this one module and continues to fail the build for the other
thirteen.

**Audio exemption.** Every other game is fully playable with the volume off. This
one is not, because speech is its content. It shows a visible speaker indicator so
a child who hears nothing understands why, and the shell deprioritizes it in
rotation when the device is muted.

**No microphone.** The app never records or evaluates the child's speech. This is
deliberate on three grounds: offline recognition handles four-year-old
articulation poorly, recording requires a permission this app otherwise avoids
entirely, and — decisively — any attempt to score pronunciation would introduce a
fail state into the app's most emotionally exposed activity. A child who is told
they said a word wrong stops saying words. Nothing is judged, so nothing can be
wrong.

**Daily rotation, offline.** The word and sentence pools are bundled assets with
pre-recorded audio. The pair for a given day is selected deterministically from
the day of the year, so it changes daily, is identical across restarts within a
day, and never touches the network. The pool holds a full year with no repeats;
after a year it cycles, which is invisible at this age.

Content is ordinary daily-use language — greetings, needs, courtesies, and the
objects of a child's day: *water*, *thank you*, *I am hungry*, *can I have more*,
*where are we going*. Travel-relevant phrasing is over-represented, since that is
the context of use.

**Levels.** Level 1 presents the day's word alone. Level 2 presents it inside a
two-word phrase. Level 3 presents the full sentence. Each level plays the audio,
shows the picture, pauses for the child to repeat, then celebrates
unconditionally — the pause is a turn-taking cue, not a test.

A "say it again" control replays the audio without limit, because repetition is
how the activity works and a child will ask for it many times.

Three of the fourteen are open-ended sandboxes with no completion state, because
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

"As many as fit" was the stated scope. Fourteen is the first wave; the contract
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

**Phase 2 — parallel.** All remaining thirteen game modules, concurrently. Each
worker owns exactly one `:games:*` directory and touches no file outside it.
Registration is applied at integration rather than by the workers.

**Phase 3 — integration.** Session orchestrator tuning, parental gate, full-suite
QA pass, release APK.

The phase boundary is a genuine dependency, not a convention. Phase 2 workers all
compile against `gameapi` and `designkit`; if those move, every worker's output
breaks at once.

### The per-game development loop

Each game module runs through an automated loop with no human in the middle. The
loop is per-game, so thirteen of them run concurrently in Phase 2.

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
roughly fourteen sequential runs, and it dominates the tail of Phase 3.

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
