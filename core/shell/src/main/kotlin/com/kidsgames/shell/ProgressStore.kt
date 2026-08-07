package com.kidsgames.shell

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

private const val MAX_LEVEL = 5
private const val START_LEVEL = 1

/**
 * Tracks each game's highest unlocked level, persisted with DataStore.
 *
 * Written ADDITIVELY: [recordCompletion] only ever raises the stored level,
 * never lowers it, and clamps at [MAX_LEVEL]. This means a partial or
 * out-of-order write can never demote a child's progress — the worst case is a
 * write that has no effect.
 */
class ProgressStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.progressDataStore)

    private fun keyFor(id: String) = intPreferencesKey("level_$id")

    suspend fun levelFor(id: String): Int {
        val prefs = dataStore.data.first()
        return prefs[keyFor(id)] ?: START_LEVEL
    }

    suspend fun recordCompletion(id: String, level: Int) {
        val nextLevel = (level + 1).coerceAtMost(MAX_LEVEL)
        dataStore.edit { prefs ->
            val key = keyFor(id)
            val current = prefs[key] ?: START_LEVEL
            if (nextLevel > current) {
                prefs[key] = nextLevel
            }
        }
    }
}
