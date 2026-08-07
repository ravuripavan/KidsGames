package com.kidsgames.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kidsgames.shell.GameRegistry
import com.kidsgames.shell.KidsApp
import com.kidsgames.shell.ProgressStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val registry = GameRegistry(games = AppGames.all)
        val progressStore = ProgressStore(applicationContext)

        setContent {
            KidsApp(registry = registry, progressStore = progressStore, onExitApp = { finish() })
        }
    }
}
