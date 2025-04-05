package com.lonx.ecjtu.pda.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import top.yukonga.miuix.kmp.basic.PullToRefreshState
/**
 * Remembers a NestedScrollConnection configured to interact with an UpdatableScrollBehavior,
 * optionally considering a PullToRefreshState to disable interaction during refresh.
 *
 * @param scrollBehavior The UpdatableScrollBehavior instance that manages the TopAppBar state.
 * @param pullToRefreshState Optional PullToRefreshState. If provided and isRefreshing is true,
 *                           nested scroll events will not be forwarded to the scrollBehavior.
 * @return A remembered NestedScrollConnection instance.
 */
@Composable
fun rememberAppBarNestedScrollConnection(
    scrollBehavior: UpdatableScrollBehavior,
    pullToRefreshState: PullToRefreshState? = null
): NestedScrollConnection {
    return remember(scrollBehavior, pullToRefreshState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val isRefreshing = pullToRefreshState?.isRefreshing ?: false

                if (!isRefreshing && available.y < 0) {
                    val consumedY = scrollBehavior.updateHeightOffset(available.y)
                    if (consumedY != 0f) {
                        return Offset(0f, consumedY)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val isRefreshing = pullToRefreshState?.isRefreshing ?: false

                if (!isRefreshing && available.y != 0f) {
                    scrollBehavior.updateHeightOffset(available.y)
                }
                return Offset.Zero
            }

        }
    }
}