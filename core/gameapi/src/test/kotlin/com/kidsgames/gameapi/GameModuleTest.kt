package com.kidsgames.gameapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GameModuleTest {
    @Test
    fun `outcome types are distinct singletons`() {
        assertNotEquals(Outcome.Completed as Outcome, Outcome.Abandoned as Outcome)
    }

    @Test
    fun `age bands cover the four to six range`() {
        assertEquals(2, AgeBand.entries.size)
    }
}
