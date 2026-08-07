package com.kidsgames.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.gameapi.GameModule

/**
 * Picture-only game picker: a grid of icon buttons, no text anywhere. Tapping
 * a tile launches that game. This is free choice — always available alongside
 * whatever the session orchestrator suggests.
 */
@Composable
fun PickerScreen(
    registry: GameRegistry,
    onGameSelected: (GameModule) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        modifier = modifier.background(KidPalette.Background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(registry.games) { game ->
            KidButton(
                onClick = { onGameSelected(game) },
                modifier = Modifier.padding(8.dp),
            ) {
                Image(
                    painter = painterResource(id = game.icon),
                    contentDescription = null,
                )
            }
        }
    }
}
