package com.kidsgames.countanimals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountAnimalsStateTest {

    @Test
    fun `level one starts with five animals in one group`() {
        val s = CountAnimalsState(level = 1)
        assertEquals(5, s.animals.size)
        assertTrue(s.animals.all { it.groupIndex == 0 })
    }

    @Test
    fun `level two starts with eight animals`() {
        assertEquals(8, CountAnimalsState(level = 2).animals.size)
    }

    @Test
    fun `level three starts with nine animals`() {
        assertEquals(9, CountAnimalsState(level = 3).animals.size)
    }

    @Test
    fun `level four starts with two groups totalling ten`() {
        val s = CountAnimalsState(level = 4)
        assertEquals(10, s.animals.size)
        assertEquals(setOf(0, 1), s.animals.map { it.groupIndex }.toSet())
    }

    @Test
    fun `level five starts with two groups of five`() {
        val s = CountAnimalsState(level = 5)
        assertEquals(10, s.animals.size)
        assertEquals(5, s.animals.count { it.groupIndex == 0 })
        assertEquals(5, s.animals.count { it.groupIndex == 1 })
    }

    @Test
    fun `count so far starts at zero and is always visible`() {
        val s = CountAnimalsState(level = 1)
        assertEquals(0, s.countSoFar)
        assertEquals(5, s.total)
    }

    @Test
    fun `tapping an animal increases count so far by exactly one`() {
        var s = CountAnimalsState(level = 1)
        val first = s.animals.first()
        s = s.tap(first.id)
        assertEquals(1, s.countSoFar)
        assertTrue(s.animals.first { it.id == first.id }.tapped)
    }

    @Test
    fun `tap does not mutate the state it was called on`() {
        val s = CountAnimalsState(level = 1)
        val target = s.animals.first()
        s.tap(target.id)
        assertFalse(s.animals.first { it.id == target.id }.tapped)
    }

    @Test
    fun `tapping the same animal twice does not increase the count`() {
        var s = CountAnimalsState(level = 1)
        val id = s.animals.first().id
        s = s.tap(id)
        val afterFirst = s.countSoFar
        s = s.tap(id)
        assertEquals(afterFirst, s.countSoFar)
    }

    @Test
    fun `tapping every animal at level one completes it without a numeral pick`() {
        var s = CountAnimalsState(level = 1)
        s.animals.map { it.id }.forEach { s = s.tap(it) }
        assertTrue(s.isComplete)
        assertFalse(s.needsNumeralPick)
        assertEquals(0, s.penalties)
    }

    @Test
    fun `level three is not complete until the numeral is picked`() {
        var s = CountAnimalsState(level = 3)
        s.animals.map { it.id }.forEach { s = s.tap(it) }
        assertTrue(s.allTapped)
        assertFalse(s.isComplete)
        assertTrue(s.awaitingNumeralPick)
    }

    @Test
    fun `level three numeral options include the correct total`() {
        val s = CountAnimalsState(level = 3)
        assertTrue(s.numeralOptions.contains(s.total))
    }

    @Test
    fun `picking the wrong numeral at level three is a no-op and never resets the count`() {
        var s = CountAnimalsState(level = 3)
        s.animals.map { it.id }.forEach { s = s.tap(it) }
        val countBefore = s.countSoFar
        val wrong = s.numeralOptions.first { it != s.total }

        val next = s.pickNumeral(wrong)

        assertFalse(next.isComplete)
        assertNull(next.selectedNumeral)
        assertEquals(countBefore, next.countSoFar)
        assertEquals(0, next.penalties)
        // every animal is still tapped -- a wrong pick never un-counts anything
        assertTrue(next.animals.all { it.tapped })
    }

    @Test
    fun `picking the correct numeral at level three completes it`() {
        var s = CountAnimalsState(level = 3)
        s.animals.map { it.id }.forEach { s = s.tap(it) }
        s = s.pickNumeral(s.total)
        assertTrue(s.isComplete)
        assertEquals(s.total, s.selectedNumeral)
    }

    @Test
    fun `picking a numeral before counting is finished is a no-op`() {
        val s = CountAnimalsState(level = 3)
        val next = s.pickNumeral(s.total)
        assertNull(next.selectedNumeral)
        assertFalse(next.isComplete)
    }

    @Test
    fun `level four completes once both groups are fully tapped`() {
        var s = CountAnimalsState(level = 4)
        s.animals.map { it.id }.forEach { s = s.tap(it) }
        assertTrue(s.isComplete)
        assertFalse(s.needsNumeralPick)
    }

    @Test
    fun `level five requires counting both groups then picking their sum`() {
        var s = CountAnimalsState(level = 5)
        s.animals.map { it.id }.forEach { s = s.tap(it) }
        assertTrue(s.allTapped)
        assertFalse(s.isComplete)

        val wrong = s.numeralOptions.first { it != s.total }
        val afterWrong = s.pickNumeral(wrong)
        assertFalse(afterWrong.isComplete)
        assertEquals(10, afterWrong.countSoFar) // still fully counted, nothing lost

        val afterCorrect = afterWrong.pickNumeral(s.total)
        assertTrue(afterCorrect.isComplete)
        assertEquals(10, afterCorrect.total)
    }

    @Test
    fun `tap never removes an animal or reorders the list`() {
        // CountAnimalsGame renders state.animals directly into items(...) in
        // list order. If tap() ever shrank the list or reordered it, every
        // animal after the tapped one would shift to a new grid cell.
        var s = CountAnimalsState(level = 2)
        val originalIds = s.animals.map { it.id }
        val originalSize = s.animals.size

        s.animals.map { it.id }.forEach { id ->
            s = s.tap(id)
            assertEquals(originalSize, s.animals.size)
            assertEquals(originalIds, s.animals.map { it.id })
        }
    }

    @Test
    fun `required work is non-decreasing across levels one through five`() {
        fun requiredActions(level: Int): Int {
            val s = CountAnimalsState(level)
            return s.animals.size + if (s.needsNumeralPick) 1 else 0
        }

        val requiredByLevel = (1..5).map { requiredActions(it) }
        for (i in 1 until requiredByLevel.size) {
            assertTrue(
                "required work dropped from level $i (${requiredByLevel[i - 1]}) " +
                    "to level ${i + 1} (${requiredByLevel[i]})",
                requiredByLevel[i] >= requiredByLevel[i - 1],
            )
        }
    }

    @Test
    fun `each level has the literal expected animal count and pick requirement`() {
        // Pinned to LITERAL values rather than derived from the implementation
        // (e.g. `s.animals.size`) -- a derived assertion is tautological and
        // would keep passing even if a level's content regressed to being a
        // copy of another level's, which is exactly what happened to L4/L5.
        val expected = mapOf(
            1 to (5 to false),
            2 to (8 to false),
            3 to (9 to true),
            4 to (10 to false),
            5 to (10 to true),
        )
        for ((level, spec) in expected) {
            val (animalCount, needsPick) = spec
            val s = CountAnimalsState(level)
            assertEquals("level $level animal count", animalCount, s.animals.size)
            assertEquals("level $level needsNumeralPick", needsPick, s.needsNumeralPick)
        }
    }

    @Test
    fun `level four renders two non-empty groups`() {
        val s = CountAnimalsState(level = 4)
        val group0 = s.animals.filter { it.groupIndex == 0 }
        val group1 = s.animals.filter { it.groupIndex == 1 }
        assertTrue("group 0 must be non-empty", group0.isNotEmpty())
        assertTrue("group 1 must be non-empty", group1.isNotEmpty())
        assertEquals(10, group0.size + group1.size)
    }

    @Test
    fun `level five renders two non-empty groups whose sizes sum to the total`() {
        val s = CountAnimalsState(level = 5)
        val group0 = s.animals.filter { it.groupIndex == 0 }
        val group1 = s.animals.filter { it.groupIndex == 1 }
        assertTrue("group 0 must be non-empty", group0.isNotEmpty())
        assertTrue("group 1 must be non-empty", group1.isNotEmpty())
        assertEquals(s.total, group0.size + group1.size)
    }

    @Test
    fun `level five sum is not exposed anywhere before the pick`() {
        // Round-2 defect: this test asserted `selectedNumeral == null` and
        // `awaitingNumeralPick == true`, which are true BY CONSTRUCTION at
        // any picking level and say nothing whatsoever about the sum. It
        // would still pass even if the combined total (the answer to L5's
        // addition) were rendered directly. Assert the actual claim: the
        // combined sum (group0.size + group1.size, i.e. `total`) is never
        // computable from anything CountAnimalsState exposes UNTIL the
        // child has finished tapping both groups -- specifically, the
        // per-group counts visible on screen throughout (each group's own
        // `tapped` count) never equal the combined sum while counting is
        // still in progress, so a child glancing at either group alone
        // before finishing can't read off the answer early.
        var s = CountAnimalsState(level = 5)
        val group0Ids = s.animals.filter { it.groupIndex == 0 }.map { it.id }
        val group1Ids = s.animals.filter { it.groupIndex == 1 }.map { it.id }
        val sum = group0Ids.size + group1Ids.size

        // Tap only group 0 fully -- counting is NOT finished yet.
        group0Ids.forEach { s = s.tap(it) }
        assertFalse(s.allTapped)
        assertFalse(s.awaitingNumeralPick)
        val group0CountSoFar = s.animals.count { it.groupIndex == 0 && it.tapped }
        val group1CountSoFar = s.animals.count { it.groupIndex == 1 && it.tapped }
        // Neither group's own (on-screen) count is the combined answer.
        assertTrue(group0CountSoFar != sum)
        assertTrue(group1CountSoFar != sum)
        // And the state-level total/selectedNumeral give no early answer
        // either: selectedNumeral is unset, and awaitingNumeralPick (the
        // ONLY signal the UI uses to decide whether to show the picker,
        // and therefore the sum) is false until every animal is tapped.
        assertNull(s.selectedNumeral)

        // Now finish group 1 too -- only now is the sum both computable AND
        // (per CountAnimalsGame) shown, via the numeral pick UI.
        group1Ids.forEach { s = s.tap(it) }
        assertTrue(s.allTapped)
        assertEquals(sum, s.total)
        assertNull(s.selectedNumeral)
        assertTrue(s.awaitingNumeralPick)
    }

    @Test
    fun `numeral options draw distractors from both sides of the total when possible`() {
        // L3 (total 9): before the fix, distractors were always [7, 8] --
        // both below the total -- so the answer was always the maximum
        // candidate. There must be at least one distractor above 9 now that
        // 10 is in range.
        val s3 = CountAnimalsState(level = 3)
        assertTrue(
            "level 3 options ${s3.numeralOptions} should include a distractor above the total",
            s3.numeralOptions.any { it > s3.total },
        )

        // L5 (total 10): nothing above 10 is representable (options are
        // clamped to 1..10), so the below-side distractor must still exist,
        // but the correct answer must not always land at a fixed slot.
        val s5 = CountAnimalsState(level = 5)
        assertTrue(s5.numeralOptions.contains(s5.total))
    }

    @Test
    fun `the correct numeral is not always the maximum option nor always the last index`() {
        // Regression test for the "always tap the rightmost button" defect,
        // and its round-2 mutation ("always tap the CENTRE button"): the
        // answer must not always be the maximum candidate.
        val isAlwaysMax = mutableListOf<Boolean>()
        for (level in listOf(3, 5)) {
            val s = CountAnimalsState(level)
            val options = s.numeralOptions
            val index = options.indexOf(s.total)
            assertTrue("total must appear in its own options", index >= 0)
            isAlwaysMax += (s.total == options.max())
        }
        assertFalse(
            "the correct answer must not always be the maximum option",
            isAlwaysMax.all { it },
        )
    }

    @Test
    fun `the correct numeral's index actually varies across successive questions at the same level`() {
        // The round-2 defect: `total` (and its distractor set) is fixed per
        // level, so a seed derived only from level+total is a compile-time
        // constant -- the answer sits at the SAME index every time a child
        // reaches that level, just a different fixed index than round 1's.
        // A real fix must vary with something that changes between
        // questions (CountAnimalsState.attempt) -- assert the index
        // actually changes across several distinct attempts, for BOTH
        // pick-requiring levels. A test that can pass via a disjunction
        // ("OR the answer merely isn't the max this time") certifies the
        // defect instead of catching it, so this asserts variance directly,
        // with no escape hatch.
        for (level in listOf(3, 5)) {
            val indices = (0 until 20).map { attempt ->
                val s = CountAnimalsState(level, attempt = attempt)
                s.numeralOptions.indexOf(s.total)
            }
            assertTrue(
                "level $level: every attempt's numeral options must contain the total",
                indices.all { it >= 0 },
            )
            assertTrue(
                "level $level: the answer's index never varied across 20 attempts " +
                    "(always index ${indices.first()}) -- the position is still fixed",
                indices.toSet().size > 1,
            )
        }
    }

    @Test
    fun `numeral options are stable across repeated reads for the same attempt`() {
        // The seed must be a pure function of (level, total, attempt) -- the
        // SAME question must never reshuffle the picker's buttons under the
        // child's finger across recompositions.
        for (level in listOf(3, 5)) {
            val s = CountAnimalsState(level, attempt = 42)
            val first = s.numeralOptions
            val second = s.numeralOptions
            assertEquals(first, second)
        }
    }

    @Test
    fun `a wrong numeral pick never reduces countSoFar at any level requiring one`() {
        for (level in listOf(3, 5)) {
            var s = CountAnimalsState(level)
            s.animals.map { it.id }.forEach { s = s.tap(it) }
            val before = s.countSoFar
            val wrong = s.numeralOptions.firstOrNull { it != s.total }
            if (wrong != null) {
                val after = s.pickNumeral(wrong)
                assertTrue(
                    "level $level: countSoFar dropped from $before to ${after.countSoFar}",
                    after.countSoFar >= before,
                )
            }
        }
    }
}
