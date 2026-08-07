# Parked modules

A module is parked when it has exhausted the three review rounds the plan allows
without reaching a playable state. Parking is not failure — it is the loop
terminating honestly instead of spending unbounded effort. Each entry below needs
a human decision before more work is spent on it.

---

## `:games:puzzleboard` — parked 2026-08-06

Three review rounds, three fix rounds. Builds green, 20 unit tests pass, and the
game is still not reliably playable at level 5.

**What works.** The state machine is correct and pure. The drag overlay draws at
provably the same coordinates the drop is judged against. The wobble is a real
keyed animation. The `(shape, pipCount)` identity scheme is bijective over all
twelve piece ids. The vertical budget genuinely measures available space via
`BoxWithConstraints` rather than assuming a number. Drops cannot resolve to a
neighbouring cell. No fail states, no text, no permissions.

**Why it is parked.**

**B1 — pips are invisible at level 5 on circle and diamond slots.** Board-slot
glyphs draw pips in opaque `KidPalette.Background` (`#FFFDF7`) while an unplaced
level-5 slot fills with `KidPalette.Surface` (`#FFFFFF`) — a contrast ratio of
about 1.01:1. The pips are only legible where they overlap the shape's 0.4-alpha
ink, and the 8dp pip floor introduced in round 3 is what pushes them outside it: a
three-pip row is 28dp wide, while a diamond is 12dp wide at that height. The three
diamond slots therefore present as a single centre pip.

Level 5 removes the colour tint, so `(shape, pipCount)` is the only disambiguator.
A child holding three diamond pieces sees three identical hints and can only
brute-force. This is the round-1 identity defect surviving in rendering form.

The round-2 pip size (3.4dp) produced an 11.4dp row that stayed inside every
shape but was too small to count. The round-3 size (8dp) is countable but falls
off the shape. Neither value works, which suggests the pip-row-inside-the-glyph
approach is the wrong shape for the problem rather than a value needing tuning.

**H2 — the tray clips below 318dp of effective width.** `PIECES_PER_TRAY_ROW` is a
hard 4 with fixed sizes and no wrap or shrink fallback. `4x64 + 3x10 + 2x16 =
318dp` fits a 360dp phone with room, but a 320dp device leaves 2dp, and a 320dp
device at a larger display-size accessibility setting drops below the threshold. A
clipped fourth piece cannot be dragged, so that level cannot be completed.

**H3 — leaks a `SoundPool` on every entry.** Uses `remember { SoundBank(context) }`
with no disposal, while designkit now provides `rememberSoundBank()`.

**Suggested direction, if resumed.** Replace the pip row with a second channel that
does not depend on fitting inside the shape's silhouette — a border treatment,
notch, or fill pattern — since two attempts at sizing pips inside the glyph have
now failed from opposite directions. Give the tray a wrapping or shrinking
fallback rather than a hard column count.

---

## `:games:colorsort` — parked 2026-08-06

Three review rounds, three fix rounds. Builds green, 15 unit tests pass, levels 1
to 4 are playable. Level 5 is not completable on a 360x640 device.

**What works.** Bins are real uncoerced 64dp targets. The drag overlay draws at
provably the same coordinates the drop is judged against. The bin hit-test resolves
to the nearest centre, so an accurate drop can no longer be credited to a
neighbour. Every item carries a dot-count badge independent of colour, with a
pairwise test proving any two differently-coloured items differ by something other
than hue. Haptics, `SoundBank` disposal, literal level-content tests, immutable
pure state, no fail states.

**Why it is parked — the content spec is the problem, not the code.**

Level 5 asks for 24 items across 8 bins. At the 64dp touch-target floor an item
slot is 72dp, so 24 items in four columns is six rows: `6x72 + 5x8 + 16 = 488dp`
of items, plus a 172dp bin block, against roughly 396dp of item viewport on a
360x640 device. It does not fit, and no arrangement fixes it: fewer columns makes
the grid taller, and smaller items break the 64dp floor.

Round 3 responded honestly — it declined to assert another false budget and made
the item area scroll. But that reproduces round 1's rejected finding with a smaller
target. Every surface not covered by a 72dp draggable item is 16dp or narrower,
`detectDragGestures` consumes any pointer that touches an item, and the one
full-height 16dp stripe sits inside the system gesture-navigation inset, where a
horizontal swipe triggers Back and leaves the game. A 4-6 year old's finger contact
patch is 7-10mm; the scroll target is about 2.5mm. The scroll is also
undiscoverable: no scrollbar, no edge fade, no hint, and a non-reader has no signal
that four more shapes exist below the fold.

Separately, the 96dp exit-button clearance is scrollable content rather than
reserved screen space, so at every scroll offset except maximum the leftmost item
column sits under `GameHost`'s exit button — a child reaching for the bottom-left
shape leaves the game mid-level.

**Why this needs a human decision rather than a fourth round.** The three rounds
were spent fixing implementation, and the implementation is now largely correct.
The finding is that the level's designed content does not fit the device. Changing
it changes what the game contains, which is a product decision, not a bug fix.

`ITEMS_PER_BIN` is a module-private constant — the spec only says level 5 sorts by
"colour, shape and size" and never mandates 24 items — so the change does not
require a spec amendment.

**Recommended, from the reviewer.** Split level 5 into two sequential sub-rounds of
12: first the four LARGE bins, then the four SMALL bins. Per sub-round the layout
is `96dp` of bins plus `248dp` of items plus a fixed 96dp bottom inset = 440dp,
comfortably inside 568dp on the smallest device, with no scrolling at any level and
`verticalScroll` deleted entirely. Size still discriminates, because the child sees
both sizes of the same colour and shape across the two sub-rounds.

Alternative: `ITEMS_PER_BIN = 2` at level 5 only, giving 16 items in four rows.
Simpler, but still needs padding trimmed to clear the exit inset on a 640dp device,
and updates the level-content test to `6, 9, 12, 12, 16`.

Either way, the exit clearance must become fixed bottom padding on the outer
column, outside any scrollable region, so it holds unconditionally.

---

## `:games:whatisit` — parked 2026-08-07

Two review rounds, two fix rounds. Builds green, 20 tests pass, and every constraint
in the spec is honoured except the one that matters most: the child cannot answer
the question the game asks.

**What works.** No text, no microphone, no permissions, no network. No fail states —
`tapReverseOption` returns unchanged on a non-match, and a wrong tap now names what
the child picked rather than buzzing at them, exactly as the spec asks. No dead
ends: both round types have an always-available exit, and skipping still advances
level accounting correctly. The volume poll is lifecycle-gated. `playRaw` targets
the right item at all three call sites. The variant hash is deterministic across
runtimes, confirmed by an independent reimplementation. Layout is measured rather
than asserted.

**Why it is parked — this one needs artwork, not code.**

`ItemGlyph` draws one silhouette per *sector*. There are twelve sectors and 144
catalogue items. Round 2 added `ItemVariant`, which decorates that silhouette with
proportion, dot count, a corner mark, mirroring and an accent colour — so items now
render *differently*. They still do not render *recognisably*.

Level 1 asks the child to name dog, bird, orange, hand, eye. What they see: a paw
with one dot, the same paw squashed with three dots, a circle, a stick figure, the
same stick figure. A four year old shown a paw with three dots cannot say "bird".
The game's only question has no derivable answer for any item whose sector glyph is
not itself the item.

The distinction that took two rounds to surface: **distinctness is not
identifiability**. The uniqueness tests check that no two items share a descriptor,
which is a pairwise property. The child sees ONE picture with nothing to compare it
against, so pairwise difference is not the property the task needs. Two rounds were
spent making a per-sector glyph carry per-item identity, and the second fixed the
measurable half while leaving the child's actual task exactly where it was.

No amount of procedural decoration on twelve silhouettes makes "what is it?"
answerable. This module is the one most exposed to the missing-artwork gap, and the
gap is now the blocker.

**A bounded code fix worth doing whenever art lands.** The round-2 uniqueness tests
are about 36x weaker than they read, and should be tightened at the same time:

- `ItemVariant.swatchIndex` spans 0..255 in the descriptor but renders
  `% KidPalette.Swatch.size` (7), so the descriptor distinguishes 256 states where
  the screen shows 7. Fold the modulo into the descriptor.
- `mirrored` changes the descriptor but is a visual no-op in 9 of the 12 sectors,
  whose glyphs are horizontally symmetric. Only mug, cap and ball are asymmetric.
- Accent colour counts as a differentiator even when `featureCount == 0` and
  `accentKey == 0`, i.e. when nothing accent-coloured is drawn at all.

With those folded in, two catalogue pairs currently render pixel-identically while
passing every test — `clothes_dress`/`clothes_raincoat` and
`house_broom`/`house_thermometer`, both reachable from level 3 up. Expect the
tightened tests to fail and surface more.

Also worth knowing: level 5's reverse mode is blind guessing today, because it
identifies its target only by spoken audio and no audio plays anywhere in the app.
It always exits, so it is not a dead end — but an e2e run showing "child completed
level 5" is not evidence that the mode works.

---

## `:games:musicpad` — parked 2026-08-07

Three review rounds, three fix rounds. Builds green, 26 tests pass, and levels 1 to
4 are correct at every device configuration checked. Level 5 is not.

**What works, and it is nearly everything.** Replay follows the recorded instrument
and reproduces the child's actual rhythm, because taps are stamped from
`SystemClock.elapsedRealtime()` and the replay loop waits the real gaps. An
accidental press of record no longer erases the previous tune until the first new
tap lands. Replay can be stopped, restores the instrument the child was on, and
caps any single gap so a long pause cannot lock the grid. Completion fires and
cannot fire with the screen off. No text, no microphone, no permissions, no
network; `TapEvent` stores only which pad, which instrument, and when — recording a
tune never captures audio. Literal per-level counts, bounded tests,
`rememberSoundBank()`, and a `padResId` KDoc that states honestly that note audio
is unwired rather than claiming it will self-heal.

**Why it is parked — the test certifies against a box the game never receives.**

Round 3 did the right thing structurally: it extracted the layout decision into a
pure `chooseColumns(count, maxColumns, maxWidth, maxHeight)` checking both axes,
and added `MusicPadGameLayoutTest` to run that exact function against four device
configurations. The composable is correct — it owns a real `BoxWithConstraints`,
passes the measured constraints straight in, honours the returned column count, and
the weighted rows split exactly the height that was measured.

The test's model of available space is what is wrong. `contentSize()` subtracts the
96dp exit zone and the module's 12dp padding, and nothing else. But `KidsApp`
applies `WindowInsets.safeDrawing` ABOVE `GameHost`, so the height reaching the game
is already short by the status bar plus the navigation bar — the repo's own estimate,
written in `PickerScreen`, is about 80dp.

With that included, level 5 at a 1.3x Display size lays out pads of 72.3 x **42.8dp**
against a 64dp floor, via a `bestByWidthOnly` fallback branch that no test has ever
executed. Level 5 at 1.15x clears the floor by 0.2dp. The break-even is roughly 16dp
of inset, which is less than a status bar on its own.

What a child gets at a raised Display size: level 5's eight pads are a 3-4mm strip
against a 7-10mm finger, 12dp apart, so aiming at one pad routinely fires its
neighbour — and record-and-replay, the whole point of the level, echoes a tune they
did not play.

**The lesson worth keeping.** Extracting layout into a pure function genuinely does
make a spatial property testable, and it is the best answer this project found to
its dominant defect class. But the test is only as good as its model of the
available space, and here the model omitted a reduction the shell applies two layers
up. A layout test should derive its content box from the same chain the runtime
uses, not from a hand-written subtraction.

**Why this needs a decision rather than a fourth round.** The mechanical part is
small — subtract a `safeDrawing` allowance in `contentSize()`, watch level 5 fail at
1.3x and 1.15x. What comes after is a content question: on a short screen, level 5
has to give something up. Six pads instead of eight, a scrolling grid (which this
project has rejected elsewhere), or control rows that share a single row. That is
the same shape of decision as `:games:colorsort`'s level 5, and it is not the
implementer's to make.

**Separately, and independent of the above.** This is a music toy that makes no
sound, and its silence is worse than the repo-wide gap: `padResId` returns integers
in 1000-1107, which can never be Android resource ids, so wiring `resourceFor` alone
would not help. A human must write an `R.raw.*` lookup and record the notes.

Today the visuals carry it — eight distinct shapes, a bounce and a rising fading
trace per tap, and a rhythm echo that genuinely reads as the child's own timing.
That is charming for a minute or two. The honest ceiling is about three minutes
against the spec's five, and the module's own timer agrees at 180 seconds. The
level-4 "second instrument" is meaningless without audio: it changes a badge and
draws a ring, and a child cannot discover why they would want it.

---

## `:games:countanimals` — parked 2026-08-07

Three review rounds, three fix rounds, plus one integrator fix that turned out to
cause a fourth blocker. Builds green, 26 tests pass, the state machine is clean, and
levels 1 to 4 work. Level 5 does not survive a raised Display size.

**What works.** The numeral picker's answer position genuinely varies between
attempts — an earlier version seeded the shuffle on `level` and `total`, both fixed,
so the answer sat at the same index forever and a child could clear it by tapping
the same place. Level 4's two groups are real stacked containers with their own
counts, and level 5 shows an addition without ever displaying the sum before the
child answers. Digits are drawn as seven-segment glyphs, all ten verified correct,
so the module needs no text exemption. Group colours are off the protan confusion
axis and carry a circle-vs-square marker as well. Per-animal wobble is reachable on
blocked taps. Literal difficulty map, bounded tests, `rememberSoundBank()`, no
permissions, no network, no microphone, no fail states.

**Why it is parked — two blockers, one root cause, and one of them is mine.**

There is no `BoxWithConstraints` anywhere in this module. The layout budget lives in
a KDoc comment, and both blockers are what happens when an asserted budget meets a
device it was not written against.

**B1, width.** The level-5 numeral picker needs 284dp of options, but the Row sits
inside 24dp of its own horizontal padding and another 12dp from the group container,
so it needs 356dp of screen. That is 4dp of margin on a stock 360dp phone and
negative on anything narrower — at a 1.1x Display size the third button is squeezed
to about 55dp with its digit and pips overlapping its neighbour. Because the answer
index genuinely varies, roughly one attempt in three puts the answer in that slot
and the level cannot be finished on that attempt.

**B2, height.** Level 5's vertical total is 454dp against roughly 464dp available.
The outer `Column` is unscrollable and unweighted, so the picker — the last child —
absorbs any shortfall as zero height. At a 1.2x Display size on a 360x640 device,
and at "largest" on a 360x800 device, the child taps all ten animals, reaches the
end of level 5, and there is nothing on screen to tap next.

**The integrator's part in this, recorded plainly.** Round 3's blocker was that ten
12dp pips in a single row made the widest option 186dp, so three options needed
536dp and the outer digits clipped off both edges. That enlargement was itself an
integrator instruction — the pips had been 8dp and read as a dashed bar rather than
countable things. The integrator then fixed the clipping directly rather than
spending a fourth round, by wrapping the pips at five per row.

The wrap was validated against "288dp of screen", which is the pre-padding width at
exactly 360dp, so the check did not generalise — B1. And stacking the pips made each
option 6dp taller, which cut the vertical margin from 26dp to 10dp — B2. Both
blockers are downstream of a fix made outside the round bound, on the reasoning that
correcting one's own regression is not a fourth round. That reasoning still holds,
but the fix repeated the very mistake it was correcting: a number checked against
one device rather than derived from a measurement.

**What it needs.** Measure both axes with a real `BoxWithConstraints`, following
`games/musicpad`'s pure-function shape — but deriving the content box from the same
chain the runtime uses rather than hand-subtracting it, which is the mistake that
parked `musicpad` itself. Then decide what level 5 gives up on a short screen: fewer
numeral options, a narrower picker, or groups that share vertical space differently.
That last part is a content decision, which is why this parks rather than taking
another pass.

---

## `:games:cardesign` — parked 2026-08-07

Three review rounds, three fix rounds. Builds green, 34 tests pass, the state machine
is clean, and the module gets a great deal right. Level 5 does not work on a stock
phone, and levels 4 and 5 lose their headline content at any raised Display size.

**What works, and it is substantial.** Tap-to-equip then tap-to-apply, chosen
deliberately over drag-and-drop by citing the two modules parked for drag defects —
the first time a lesson in this project prevented a defect rather than being
rediscovered. A sticker tap with a non-sticker tool both removes the sticker AND
applies the tool, so no tap is ever a pure loss. Marker contrast is computed against
the actual alpha-blended-over-white colour in both selection states, and every one
of eleven cases clears 3:1 — verified independently. The contrast test now matches
the real palette byte-for-byte. Completion fires and provably cannot fire
backgrounded. `currentStateFlow` is hoisted, so this module is out of the observer
leak. No text, permissions, network, or microphone; no fail states; literal tool
counts.

**Why it is parked.**

**B1 — the tool tray scrolls with no discoverable cue.** Round 3 protected the car
by reserving its floor first and letting the tray scroll into the remainder. There is
no scrollbar, no edge fade, no arrow, no hint. At a 1.3x Display size on level 5, 19
tools become 7 rows of 3 in a 216.3dp tray, and row 4's top edge falls at 218dp —
exactly zero pixels of it render. The child sees eight paint colours and one wheel;
two wheel styles, all four stickers and all four body shapes are below the fold. At
1.15x it is worse in kind: row 4 renders 62.5 of its 64dp, so the tray LOOKS complete
while row 5 is entirely invisible.

This is `:games:colorsort`'s parking condition, with one aggravating difference.
colorsort scrolled *content*; this scrolls *controls*. The rule this project settled
on is that content may scroll and controls may not, and a tray of paint, wheels,
stickers and body shapes is the control surface.

The round-3 change traded a measurable defect (a 0dp canvas) for an unmeasurable one
(unreachable controls), and the new layout test asserts only the car floor — never
that the tray is fully visible. Its own four passing cases include configurations
where the tray is clipped, and it says nothing about them.

**B2 — the car is a stripe at the default configuration.** `MIN_CAR_CANVAS_HEIGHT`
is 100dp, but the module applies 16dp of its own vertical padding INSIDE the weighted
box, so the drawing canvas at the floor is 84dp. Nothing bounds the aspect ratio —
the floor constrains height only, and width is whatever the screen gives. At
360x640, level 5, default Display size, the car is a body 288dp long and 32dp tall
(9:1) with two 16dp wheels: a coloured bar with two dots.

The consequences follow the levels. Level 3 exists to choose a wheel style, and the
spokes draw at 1.4dp inside a 7.9dp radius, so switching styles changes nothing
visible. Level 5 exists to choose a body shape, and sedan, van, bug and pickup are
all keyed off fractions of an 84dp height with an 18dp roof band.

The KDoc defending the 100dp figure states a 36dp body and 18dp wheels. The code
beneath it produces 30dp and 15dp, because the calculation omits the module's own
padding — the same shape of error that parked `musicpad` and `countanimals`: a stated
derivation that does not match the code it defends.

**What it needs.** Two product decisions, which is why it parks rather than taking a
fourth round. What does level 5 give up on a short screen — fewer tools on offer at
once, a paged tray with a visible affordance, or a smaller tool footprint? And how
wide may the car be — an aspect-ratio bound would fix B2, but it costs either width
or the tray's space, so it interacts with the first question.

**One finding worth carrying regardless.** Selection state in the tray is carried by
opacity alone (1.0 vs 0.6 for paint, 0.55 vs 0.33 for wheels and body shapes). There
is no ring, border, scale-pop or checkmark. For wheels and body shapes the difference
is two pale greys, which makes alpha the sole carrier of a meaningful state — the one
thing the palette rule says never to do.

---

## `:games:talktime` — parked 2026-08-07

Three review rounds, three fix rounds, plus two integrator fixes after the rounds
were spent. Builds green, 23 tests pass, and almost everything about it is right.
It is not registered in the app.

**What works.** The text exemption is used honestly: exactly one `Text` call,
rendering only the taught word, phrase or sentence, with no instruction, label or
feedback string anywhere — and it is the sole entry in the build gate's allowlist,
so nothing else can hide behind it. No microphone, no manifest, no permissions, no
network. No fail states: a wrong tap returns the state byte-for-byte unchanged,
`acknowledge()` is unconditional, replays are unlimited. `Sentence.image` drives the
level-5 target on all 365 days with a collision guard making two identical-looking
options unreachable. Every control sits outside the scroll region and fits at 1.15x
and 1.3x Display size — the footer is measured before the weighted scroll, so it
cannot be squeezed until roughly 1.74x density, which is unreachable. Determinism is
real and tested across a full year.

**Why it is parked.**

**B1 — it renders "a astronaut".** The level-3 phrase rule gives `Sector.JOBS`
entries `"a $word"` with no `a`/`an` selection. `astronaut` is the only vowel-initial
JOBS entry, so on day 122 and day 266 a five or six year old is shown, at 32sp, the
phrase they are being taught to read and say: *a astronaut*. In the one module
allowed to render text, whose exemption exists because the written form is the
educational point.

**B2 — level 3's audio is not level 3's text.** Level 3 displays `phraseText`, built
from the word pool, while `playRaw` plays `sentence.audio` from the independent
sentence pool. The module's own KDoc states those two pools share no key. So the
child sees "my spoon" and hears a sentence about something else. This is the module
that took the audio exemption because speech IS its content, and level 3 is the one
level where what is heard and what is read are about different things.

This cannot be fixed by choosing a different id: `phraseText` is synthesised at
runtime, so no recording of it can exist. `word.audio` is the closest honest match,
but level 3 then plays a single word while showing a two-word phrase. Which of those
is right is a content decision.

**H1 — eight NATURE entries take a possessive that is not possessable.** `my sun`,
`my moon`, `my rain`, `my snow`, `my mountain`, `my river`, `my rainbow`,
`my volcano` — sixteen days a year. The fix's own stated principle is "a child does
not own a person"; the same reasoning applies to the weather and the sky and was not
carried there.

**The integrator's part, recorded plainly, because it is the point.** This module's
phrase logic has now been fixed three times, and each fix covered exactly the cases
that had been reported to it:

- Round 3 special-cased three gerunds by name. Ten more of the same class survived.
- The integrator then replaced the blocklist with a rule keyed on the item's
  grammatical class — activities take the verb English uses with them, jobs take an
  article — and rewrote the test to enumerate all 144 entries rather than sampling.
  That fixed the reported classes and introduced B1, because the article rule was
  written for the consonant-initial jobs that had been named.
- The strengthened test cannot catch B1 either. Both of its semantic assertions sit
  inside `if (phrase.startsWith("my "))`, so `a astronaut` passes, as would
  `go doctor` or `the swimming`. It enumerates the whole catalogue but only checks
  the half of the output space where the previous two bugs happened to live.

The lesson is not that anyone was careless. It is that a fix validated against a
report inherits the report's blind spot, and a test written alongside such a fix
tends to inherit it too — the test asserts the shape of the bug that was just fixed,
not the property that was wanted. The property here is "every phrase is grammatical
English", and nothing in the module asserts it.

B1 is a one-line change. It is left undone deliberately: two integrator interventions
on this module produced two new defects, and a third attempt against the same blind
spot is not a good bet. What this needs is someone reading all 144 phrases once, and
a decision on B2.

---

## Found by the emulator e2e pass, after every module had cleared review

The e2e sweep ran the seven registered games on a real device at zero volume, at
default density and at raised Display sizes. It found three defects that fourteen
rounds of code review had missed, plus several shell-level gaps. The three game
defects are being fixed. What follows is what needs a decision or belongs to the
shell.

**Two spec features were never built into the picker.** The design doc says: "The
picker shows the highest level reached, and a press-and-hold on a game reveals the
unlocked levels as one to five dots so a child can drop back down without an adult."
Neither exists — there is no level indicator anywhere in `PickerScreen` and no
long-press handler.

The consequence is a direct contradiction of the spec's own progression rule. "All
unlocked levels stay playable" and "a child who prefers level 1 may replay it
indefinitely" are both false today: once a child reaches level 5 they cannot get
back to level 1 without an adult clearing app data. This is the largest gap between
the shipped app and the design.

**System back from the picker exits the app, bypassing the parental gate.** The gate
itself works — it resisted 25 rapid taps and needed a genuine 3.6 second hold. But
back-from-picker quitting to the launcher is wired in `KidsApp`, so a child who
discovers the back gesture leaves in one swipe. Whether that is acceptable is a
product call: the gate exists so a child does not accidentally leave mid-journey, and
right now the most common accidental gesture on Android bypasses it entirely.

**No `android:configChanges`**, so changing the system Display size recreates the
Activity and drops in-progress level state back to the start. Minor, but worth
knowing before someone tests accessibility settings and reports lost progress.

**`:games:popballoons` draws its target swatch on top of the balloons.** The
`TargetColorSwatch` is a `Box`-aligned overlay at `TopCenter` with no reserved
space, so it lands on row-1 balloons. It is a 56dp coloured circle with a white ring
— visually a balloon. It does not consume touch, so the balloons under it still
work, but a child sees an eighth balloon wedged between two others that cannot be
popped. This is the same overlay-on-content pattern the shell deliberately removed
for the exit button and the parental gate by reserving strips; the game never got the
same treatment.

**`:games:carwash` runs out of game before it runs out of timer.** At level 1 the car
is fully clean after roughly 60 seconds, and the sandbox timer runs 180. There is no
shine, no progress signal, and nothing to do for the remaining two minutes.
`:games:carrace` has the same shape: 132 seconds per level with no progress
indicator of any kind. Between them that is over five minutes of blank waiting per
car game per level.

**`:games:carwash` spreads its dirt over the whole screen.** The dirt grid maps
across the entire canvas width rather than the car's bounding region, so blobs are
clipped at both edges and float well outside the body. It reads as a dirty screen
rather than a dirty car.

**Picker tile icons render at their intrinsic ~24dp inside a ~98dp tile**, so each
picture is a small mark floating in a large white square. `PickerScreen` passes no
size modifier to `Image`.

**`:games:memorypairs`'s fallback is unusable in a box that a real phone can reach.**
The e2e blocker is fixed — `chooseGrid` now measures both axes and level 5 lays out
4x5 at the configuration that was clipping. But when no column count clears the 64dp
floor on both axes it falls back to `columns = cardCount, rows = 1`: all twenty cards
in a single row, roughly 14dp each. The reasoning in the code is that a single column
would scroll and be worse; a single row is worse still.

The fix's author judged that box unreachable, on the grounds that it implies an
effective width under 320dp, "below what Android guarantees". That guarantee is about
a device's default width. A stock 360x640 phone at the largest system Display size
lands at roughly 277dp wide, which is the failing box — so the fallback is reachable
on an ordinary phone with an accessibility setting a parent might well have on.

Underneath it is the same decision that parked four other modules: at some box size,
twenty cards cannot coexist with a 64dp touch floor, and something must give. Fewer
pairs at level 5 on a small box, a smaller floor, or an explicit "this level needs a
bigger screen" state are all defensible; a 14dp card is not. Left as-is because
choosing is a product call, and because the shipped path at every configuration the
device pass actually exercised is correct.

**Note on e2e coverage, so nobody reads more into it than is there.** Only
`:games:popballoons` was played through all five levels by hand. For the other six,
level 1 was played, the store was confirmed to advance, and the progress file was
then seeded to level 5 — which was played to a real completion. **Levels 2 to 4 of
six games have never been exercised on a device.**

## Cross-cutting issues found while reviewing (not module defects)

**Nothing in the app makes a sound, and nothing ever has.** `SoundBank.resourceFor`
is never assigned anywhere in the repo. It defaults to `{ null }`, so every cue in
every module — tap, success, gentle-retry, celebration — is a silent no-op, as is
every `playRaw` call for catalogue words. Fourteen modules have been designed
around audio feedback that has never once played.

This is larger than "recordings are missing". The wiring point exists and nothing
is plugged into it, so even if audio files were added tomorrow they would not play
until something assigns `resourceFor`. Two consequences worth holding onto:

- Every "verified playable at zero volume" finding in these reviews is really a
  statement about how the app behaves *today, for everyone*, not about an edge
  case. That is fortunate — the constraint forced visible carriers everywhere.
- Audio-dependent behaviour has never been exercised. `:games:whatisit` and
  `:games:talktime` both rest on spoken words, and `:games:whatisit`'s level-5
  reverse mode is currently blind guessing for every child, because the name it
  asks them to recognise is never spoken.

Wiring `resourceFor` belongs in Phase 3, alongside the first real cue assets.

**The picture catalogue has no pictures, and one module cannot work without them.**
`:games:whatisit` renders a drawing per *sector* rather than per *item*, so 144
catalogue entries become 12 pictures — level 1 shows the same paw for "dog" and
"bird". Under review it is being changed to derive a distinct procedural glyph per
item, which makes the task answerable, but the game only becomes genuinely good
with real artwork. It is the module most exposed to the missing-assets gap.

**`Lifecycle.currentStateFlow` leaks an observer on every call, in four modules.**
It is a getter, not a cached property: each call allocates a fresh
`MutableStateFlow`, creates a `LifecycleEventObserver`, calls `addObserver`, and
never removes it. `:games:carwash` calls it once per 500ms tick, so 361 observers
accumulate per level session and live until the Activity is destroyed.

No child-visible effect — roughly 70KB per session and a few hundred no-op
callbacks per lifecycle event — but it is in `:games:carrace`, `:games:musicpad`
and `:games:cardesign` as well, because the correct lifecycle-gating pattern was
copied from module to module along with this flaw.

One-line fix in each: hoist to
`val states = remember(lifecycleOwner) { lifecycleOwner.lifecycle.currentStateFlow }`
and collect from that. Worth a single Phase 3 sweep rather than a round per module.

**`KidButton`'s 64dp floor is not structural under `weight`.** The guarantee is
`Modifier.defaultMinSize`, which by definition only applies when the incoming
constraint is unbounded. `Modifier.weight(1f)` supplies an EXACT width, so the
floor silently disappears — `:games:musicpad` shipped 62dp pads at 320dp width
this way.

This is a different trap from the earlier one. That was caller modifiers landing
on the inner visual node, fixed by adding `layoutModifier`; `layoutModifier` does
not help here, because the problem is the constraint type rather than which node
receives it. Any weighted `KidButton` can go under the floor.

The durable fix is for a caller to measure and choose its own column count rather
than dividing available space by weight and hoping. Three modules now do that
correctly. If the floor is meant to be a real guarantee under weight, `KidButton`
would need `sizeIn(minWidth, minHeight)` rather than `defaultMinSize` — worth
considering, but it would change layout behaviour for every existing caller, so it
is not a safe mid-flight change.

**`KidPalette.Swatch` has only seven entries** and has now constrained three
modules: `:games:puzzleboard` (12 pieces, 5 colour collisions), `:games:colorsort`,
and `:games:cardesign` (needed 8 colours at level 2 and defined a local teal).
Each solved it locally with shape or pip-count markers, which is the right answer
for accessibility anyway — but a shared swatch that carries distinct non-colour
markers would stop each module reinventing it.

**`:core:vocab`'s daily content does not last a year.** The spec promises a pool
that "holds a full year with no repeats". Two ways it falls short, both found while
reviewing `:games:talktime` and both outside any game module's boundary:

- `VocabCatalogue.items` holds 144 entries and `DailyPicker` indexes
  `(dayOfYear - 1) % items.size`, so day 145 serves the same word as day 1 and the
  word repeats roughly two and a half times a year.
- `DailyPicker` appends 51 hand-written phrases and then generates the rest from
  two templates in order, so days 52-206 are all "I see a ___" and days 207-361 are
  all "look at the ___". Five straight months of a single sentence frame is not the
  varied daily language the spec describes.

Neither blocks a module today — `:games:talktime`'s level 4 and 5 now key off
`Sentence.image`, which is backed by the larger sentence pool — but both undercut
the daily-rotation promise that justifies the feature. Fixing means authoring more
catalogue content, so it belongs with the artwork and audio decision rather than
with a code fix.

**Stale hand-reserved exit zones.** Modules built before the shell reserved the
exit strip structurally may still pad for it themselves. `:games:memorypairs`
carried 104dp of bottom padding duplicating `GameHost`'s 96dp, which cost it a
quarter of its vertical budget and contributed to a blocking overflow.
`:games:puzzleboard` reserved 80dp by hand. Worth a sweep across all fourteen.

**Edge-to-edge with no insets applied.** `MainActivity` calls `enableEdgeToEdge()`
and nothing anywhere applies `safeDrawing` or `systemBars` insets. Every game's top
content draws under the status bar. This affects all fourteen modules and belongs
in the shell, not in any game.

**`SoundBank` leak is near-universal.** Seven of eight built modules still use
`remember { SoundBank(context) }` with no disposal: carrace, colorsort,
countanimals, memorypairs, patterns, popballoons, puzzleboard. Only matchshapes
has adopted `rememberSoundBank()`. Worth a single sweep rather than per-module
fixes.

**The exit control competes with game content.** `GameHost` places it at
BottomStart, and three separate modules have had blocking or high findings where
game content sat under it. A reserved exclusion zone enforced by the shell would
remove the class of defect rather than relying on each game to remember.
