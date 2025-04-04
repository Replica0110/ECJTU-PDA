package com.lonx.ecjtu.pda.utils

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBarState

// 定义一个新的接口，如果 ScrollBehavior 本身不允许修改的话
interface UpdatableScrollBehavior : ScrollBehavior {
    fun updateHeightOffset(delta: Float): Float // 返回消耗的 delta
    // 可以根据需要添加更多更新方法，例如 setContentOffset, 或者处理 fling 的方法
}

// 实现新的 Behavior
private class NavHostAwareScrollBehaviorImpl(
    override val state: TopAppBarState,
    // 保留动画参数，可能用于未来的 fling 处理
    override val snapAnimationSpec: AnimationSpec<Float>?,
    override val flingAnimationSpec: DecayAnimationSpec<Float>?
) : UpdatableScrollBehavior { // 实现新接口

    override val isPinned: Boolean = false // 通常 TopAppBar 不固定

    // 这个连接器不再是主要驱动力，子屏幕会使用自己的连接器
    override var nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            // 可以保留空实现或添加警告日志
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Log.w("NavHostAwareScrollBehavior", "Internal onPreScroll called unexpectedly")
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Log.w("NavHostAwareScrollBehavior", "Internal onPostScroll called unexpectedly")
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Log.w("NavHostAwareScrollBehavior", "Internal onPostFling called unexpectedly")
                // 如果需要处理 fling 后的 settle，逻辑会在这里，但需要被外部触发
                return Velocity.Zero
            }
        }

    // --- 关键：外部更新方法 ---
    override fun updateHeightOffset(delta: Float): Float {
        val oldHeightOffset = state.heightOffset
        // 直接修改 state，应用原始逻辑中的约束
        state.heightOffset = (oldHeightOffset + delta).coerceIn(state.heightOffsetLimit, 0f)
        // 返回实际被消耗的滚动量 (delta Y)
        return state.heightOffset - oldHeightOffset
    }

    // 你可以添加更多方法，例如:
    // fun updateContentOffset(delta: Float) { state.contentOffset += delta }
    // suspend fun settleAfterFling(velocityY: Float) { /* 使用 fling/snap spec */ }
}

// Composable 函数来创建和记住我们的新 Behavior 实例
@Composable
fun rememberNavHostAwareScrollBehavior(
    state: TopAppBarState = rememberTopAppBarState(), // 使用库提供的 rememberTopAppBarState
    snapAnimationSpec: AnimationSpec<Float>? = androidx.compose.animation.core.spring(stiffness = 3000f), // 标准 Spring
    flingAnimationSpec: DecayAnimationSpec<Float>? = androidx.compose.animation.rememberSplineBasedDecay() // 标准 Decay
): UpdatableScrollBehavior = // 返回新接口类型
    remember(state, snapAnimationSpec, flingAnimationSpec) {
        NavHostAwareScrollBehaviorImpl(
            state = state,
            snapAnimationSpec = snapAnimationSpec,
            flingAnimationSpec = flingAnimationSpec
        )
    }

// 兼容 Miuix 库原来的 rememberTopAppBarState (如果它存在且需要)
@Composable
fun rememberTopAppBarState(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
    initialHeightOffset: Float = 0f,
    initialContentOffset: Float = 0f
): TopAppBarState = remember {
    TopAppBarState(initialHeightOffsetLimit, initialHeightOffset, initialContentOffset)
}