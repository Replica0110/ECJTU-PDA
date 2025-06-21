package com.lonx.ecjtu.pda.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun <T> GenericTabs(
    items: List<T>?,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    getLabel: (T) -> String
) {
    val safeItems = items ?: emptyList()
    val safeTabIndex = selectedIndex.coerceIn(0, safeItems.lastIndex)
    val colors = TabRowDefaults.tabRowColors()
    ScrollableTabRow(
            selectedTabIndex = safeTabIndex,
            edgePadding = 0.dp,
            containerColor = MiuixTheme.colorScheme.background,
            contentColor = MiuixTheme.colorScheme.primary,
            indicator = { },
            divider = { },
        ) {
            safeItems.forEachIndexed { index, item ->
                Tab(
                    modifier = Modifier.padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.backgroundColor(selectedIndex == index)),
                    selected = selectedIndex == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            text = getLabel(item),
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = colors.contentColor(selectedIndex == index)
                        )
                    },
                    selectedContentColor = MiuixTheme.colorScheme.onSurface,
                    unselectedContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

    }
}
object TabRowDefaults {

    /**
     * The default height of the [TabRow].
     */
    val TabRowHeight = 42.dp

    /**
     * The default corner radius of the [TabRow].
     */
    val TabRowCornerRadius = 8.dp

    /**
     * The default corner radius of the [TabRow] with contour style.
     */
    val TabRowWithContourCornerRadius = 10.dp

    /**
     * The default minimum width of the [TabRow].
     */
    val TabRowMinWidth = 76.dp

    /**
     * The default minimum width of the [TabRow] with contour style.
     */
    val TabRowWithContourMinWidth = 62.dp

    /**
     * The default colors for the [TabRow].
     */
    @Composable
    fun tabRowColors(
        backgroundColor: Color = MiuixTheme.colorScheme.background,
        contentColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        selectedBackgroundColor: Color = MiuixTheme.colorScheme.surface,
        selectedContentColor: Color = MiuixTheme.colorScheme.onSurface
    ): TabRowColors = TabRowColors(
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        selectedBackgroundColor = selectedBackgroundColor,
        selectedContentColor = selectedContentColor
    )
}

class TabRowColors(
    private val backgroundColor: Color,
    private val contentColor: Color,
    private val selectedBackgroundColor: Color,
    private val selectedContentColor: Color
) {
    @Stable
    fun backgroundColor(selected: Boolean): Color =
        if (selected) selectedBackgroundColor else backgroundColor

    @Stable
    fun contentColor(selected: Boolean): Color =
        if (selected) selectedContentColor else contentColor
}