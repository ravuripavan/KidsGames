package com.kidsgames.shell

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProgressStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var store: ProgressStore

    @Before
    fun setUp() {
        // Intentionally NOT tied to runTest's TestScheduler: DataStore's own
        // internal coroutine must run independently, otherwise the
        // TestScheduler sees it as an uncompleted job and the test hangs.
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        store = ProgressStore(dataStore)
    }

    @Test
    fun `new game starts at level one`() = runTest {
        assertEquals(1, store.levelFor("popballoons"))
    }

    @Test
    fun `completing a level advances exactly one`() = runTest {
        store.recordCompletion("popballoons", 1)
        assertEquals(2, store.levelFor("popballoons"))
    }

    @Test
    fun `level never decreases`() = runTest {
        store.recordCompletion("popballoons", 4)
        store.recordCompletion("popballoons", 1)
        assertEquals(5, store.levelFor("popballoons"))
    }

    @Test
    fun `level five is the ceiling`() = runTest {
        repeat(10) { store.recordCompletion("popballoons", 5) }
        assertEquals(5, store.levelFor("popballoons"))
    }
}
