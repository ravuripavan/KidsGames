package com.kidsgames.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
 *
 * Tile sizing is deliberately structural rather than left to whatever an
 * icon drawable's intrinsic size happens to be: [PICKER_TILE_MIN_SIZE] sets
 * the grid's column width, and `aspectRatio(1f)` on each tile's real
 * touch-target node forces its height to match, so a tile's footprint is
 * always exactly the grid cell — never smaller (icon assets can't shrink it
 * below the tap-target floor) and never larger (icon assets can't blow the
 * row height and break the budget below).
 *
 * The math this is budgeted against, at 14 games on a 360x640dp portrait
 * viewport:
 * - Grid width available to columns: 360dp - 16dp - 16dp (contentPadding) = 328dp.
 * - `GridCells.Adaptive(minSize = 80.dp)`: floor(328 / 80) = 4 columns
 *   (4 x 80 = 320 <= 328; 5 x 80 = 400 > 328 would not fit).
 * - Actual cell width at 4 columns: 328 / 4 = 82dp — above the 64dp floor
 *   with margin, and every tile is exactly this size via `aspectRatio(1f)`.
 * - Rows needed for 14 games at 4 columns: ceil(14 / 4) = 4 rows.
 * - Grid content height: 4 rows x 82dp + 16dp + 16dp (contentPadding) = 360dp.
 * - Available height after `safeDrawing` insets on a 640dp device (status
 *   bar + gesture/nav bar, conservatively ~80dp combined): ~560dp.
 * - 360dp of content against ~560dp available: fits with no scrolling at
 *   fourteen games, so there is no undiscoverable scroll to find.
 */
private val PICKER_TILE_MIN_SIZE = 80.dp

@Composable
fun PickerScreen(
    registry: GameRegistry,
    onGameSelected: (GameModule) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = PICKER_TILE_MIN_SIZE),
        modifier = modifier.fillMaxSize().background(KidPalette.Background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(registry.games) { game ->
            KidButton(
                onClick = { onGameSelected(game) },
                modifier = Modifier.padding(8.dp),
                layoutModifier = Modifier.aspectRatio(1f),
                testTag = "game_tile_${game.id}",
            ) {
                Image(
                    painter = painterResource(id = game.icon),
                    contentDescription = null,
                )
            }
        }
    }
}
