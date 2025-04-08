package com.lonx.ecjtu.pda.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.lonx.ecjtu.pda.network.MyOkHttpClient
import com.lonx.ecjtu.pda.network.WifiStatusMonitor
import com.lonx.ecjtu.pda.service.JwxtService
import com.lonx.ecjtu.pda.service.StuInfoService
import com.lonx.ecjtu.pda.service.StuScoreService
import com.lonx.ecjtu.pda.service.StuSecondCreditService
import com.lonx.ecjtu.pda.utils.LocationStatusMonitor
import com.lonx.ecjtu.pda.utils.PersistentCookieJar
import com.lonx.ecjtu.pda.utils.PreferencesManager
import com.lonx.ecjtu.pda.utils.SharedPrefsCookiePersistor
import com.lonx.ecjtu.pda.viewmodel.HomeViewModel
import com.lonx.ecjtu.pda.viewmodel.LoginViewModel
import com.lonx.ecjtu.pda.viewmodel.SettingViewModel
import com.lonx.ecjtu.pda.viewmodel.SplashViewModel
import com.lonx.ecjtu.pda.viewmodel.StuInfoViewModel
import com.lonx.ecjtu.pda.viewmodel.StuScoreViewModel
import com.lonx.ecjtu.pda.viewmodel.WifiViewModel
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
val appModule = module {

    // --- 核心依赖 ---
    single { PreferencesManager.getInstance(androidContext()) } // 可以省略显式类型

    // --- CookieJar 定义 ---
    single<PersistentCookieJar> {
        PersistentCookieJar(
            SetCookieCache(),
            SharedPrefsCookiePersistor(androidContext())
        )
    }
    // 2. 同时将其作为 CookieJar 接口提供 (别名)
    //    这样，需要 CookieJar 接口的地方也能获取到它
    single<CookieJar> { get<PersistentCookieJar>() }


    single(named("defaultTimeout")) { 30L }

    // --- 网络请求客户端 ---
    single<OkHttpClient> {
        MyOkHttpClient(
            cookieJar = get(),
            timeout = get(named("defaultTimeout"))
        ).createClient()
    }

    single<JwxtService> {
        JwxtService(
            prefs = get(),
            cookieJar = get(),
            client = get()
        )
    }
    single<StuScoreService> {
        StuScoreService(
            service = get()
        )
    }
    single<StuInfoService> {
        StuInfoService(
            service = get()
        )
    }
    single<StuSecondCreditService> {
        StuSecondCreditService(
            service = get()
        )
    }
    // --- 系统服务 ---
    single { androidContext().getSystemService(Context.WIFI_SERVICE) as WifiManager }
    single { androidContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    single { WifiStatusMonitor(get(), get(), androidContext()) }
    single { LocationStatusMonitor(androidContext()) }

    // ViewModels
    viewModel { WifiViewModel(
        prefs = get(), wifiStatusMonitor = get(), locationStatusMonitor = get(),
        applicationContext = androidContext()
    ) }
    viewModel { SplashViewModel(service = get(), prefs = get()) }
    viewModel { LoginViewModel(service = get(), prefs = get()) }
    viewModel { StuInfoViewModel(service = get(),prefs = get()) }
    viewModel { HomeViewModel(service = get(), prefs = get()) }
    viewModel { SettingViewModel(service = get(), prefs = get()) }
    viewModel {StuScoreViewModel(service = get(), prefs = get())}
}