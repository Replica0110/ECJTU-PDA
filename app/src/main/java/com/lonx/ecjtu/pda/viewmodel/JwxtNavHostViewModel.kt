package com.lonx.ecjtu.pda.viewmodel

import androidx.lifecycle.ViewModel

class JwxtNavHostViewModel(): ViewModel() {
// 不包含任何内容，仅用于创建导航器
// ViewModel Scope: 使用viewModel(viewModelStoreOwner = navBackStackEntry) 创建或获取一个 JwxtNavigationViewModel 实例。
// 这个实例的生命周期与 internalNavController 中 AppRoutes.JWXT 这个导航目的地的 NavBackStackEntry 绑定。
// 只要 AppRoutes.JWXT 还在 internalNavController 的返回栈上，即使 JwxtScreen Composable 被销毁，这个 ViewModel 实例会 保持存活。
// rememberNavController() Integration: 当 rememberNavController() 在 composable(AppRoutes.JWXT) lambda 内部被调用时，它会查找当前的 LifecycleOwner, ViewModelStoreOwner, 和 SavedStateRegistryOwner。
// 由于我们在这个 lambda 中，并且通过 navBackStackEntry 获取了 ViewModel，rememberNavController 会利用这些与 AppRoutes.JWXT 目标关联的 owner。
// State Preservation: rememberNavController 使用 SavedStateRegistryOwner 来保存和恢复导航状态（包括返回栈）。
// 因为 SavedStateRegistryOwner 的生命周期现在由与 ViewModel 相同的 NavBackStackEntry 控制，所以当 JwxtScreen 被重新组合时（例如从 WIFI 导航回来），rememberNavController 会从与存活的 ViewModel 相关联的 SavedStateRegistry 中恢复之前的状态，包括 jwxtNavController 的返回栈。
//No Crash: 因为 rememberNavController 负责了状态的保存和恢复，并且与正确的生命周期所有者关联，所以当新的 NavHost 在 JwxtScreen 中创建时，它会获得一个正确恢复了状态的 NavController 实例，避免了 IllegalStateException。

}