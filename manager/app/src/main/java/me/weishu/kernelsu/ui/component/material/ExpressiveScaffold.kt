package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode

@Composable
fun ExpressiveScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = if (LocalUiMode.current == UiMode.Minimal) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    },
    contentColor: Color = if (containerColor == Color.Transparent) {
        // contentColorFor() returns Color.Unspecified for a transparent container, and
        // Scaffold forwards that to LocalContentColor, leaving Text and Icon without an
        // explicit colour unpainted. Name the theme ink instead.
        MaterialTheme.colorScheme.onBackground
    } else {
        contentColorFor(containerColor)
    },
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/**
 * Top app bar colours for the flat Minimal UI.
 *
 * The bar never paints a container (the page ground shows through), and when the
 * page scrolls under it the bar keeps that transparent container, so there is no
 * tonal band or elevation shift. Text and icons take their colour from the theme
 * instead of a hardcoded white, which is what makes light mode legible.
 */
@Composable
fun expressiveTopAppBarColors(
    containerColor: Color = if (LocalUiMode.current == UiMode.Minimal) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    },
    scrolledContainerColor: Color = containerColor,
): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = containerColor,
    scrolledContainerColor = scrolledContainerColor,
)

/**
 * Group label above a [me.weishu.kernelsu.ui.component.material.SegmentedColumn].
 *
 * Every settings-style page uses this one header so section titles, the accent
 * colour and the 16dp gutter match the list below them. The old code mixed
 * 24dp/16dp gutters and titleMedium/titleSmall, which is what put headings out
 * of line with their own content.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}
