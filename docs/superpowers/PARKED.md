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

## Cross-cutting issues found while reviewing (not module defects)

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
