package me.weishu.kernelsu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Flat shape scale for the Minimal UI.
 *
 * One modest radius family replaces the mix of 16/18/20/22/24/25/28dp literals
 * that used to be sprinkled across screens, so cards, list groups, dialogs and
 * sheets all read as one system. Corners stay small on purpose: flat design gets
 * its hierarchy from colour and spacing, not from large rounding.
 */
val FlatShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
