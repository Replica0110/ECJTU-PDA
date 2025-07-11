package com.lonx.ecjtu.pda.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.google.gson.Gson
import com.lonx.ecjtu.pda.common.monitor.LocationStatusMonitor
import com.lonx.ecjtu.pda.common.monitor.WifiStatusMonitor
import com.lonx.ecjtu.pda.data.local.cookies.PersistentCookieJar
import com.lonx.ecjtu.pda.data.local.cookies.SharedPrefsCookiePersistor
import com.lonx.ecjtu.pda.data.local.prefs.PreferencesManager
import com.lonx.ecjtu.pda.domain.repository.AuthRepository
import com.lonx.ecjtu.pda.domain.repository.CourseRepository
import com.lonx.ecjtu.pda.domain.repository.ElectiveRepository
import com.lonx.ecjtu.pda.domain.repository.ExperimentRepository
import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository
import com.lonx.ecjtu.pda.domain.repository.ProfileRepository
import com.lonx.ecjtu.pda.domain.repository.SchedulesRepository
import com.lonx.ecjtu.pda.domain.repository.ScoreRepository
import com.lonx.ecjtu.pda.domain.repository.SecondCreditRepository
import com.lonx.ecjtu.pda.domain.repository.WifiRepository
import com.lonx.ecjtu.pda.domain.source.JwxtApiClient
import com.lonx.ecjtu.pda.domain.usecase.CampusNetLoginUseCase
import com.lonx.ecjtu.pda.domain.usecase.CampusNetLogoutUseCase
import com.lonx.ecjtu.pda.domain.usecase.CheckCredentialsExistUseCase
import com.lonx.ecjtu.pda.domain.usecase.CheckSessionValidityUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetBooleanPrefsUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetCampusNetStatusUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetNetworkTypeUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetStuCourseUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetStuCredentialsUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetStuElectiveUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetStuExperimentsUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetStuProfileUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetStuSchedulesUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetStuScoreUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetStuSecondCreditUseCase
import com.lonx.ecjtu.pda.domain.usecase.GetWeiXinIDUseCase
import com.lonx.ecjtu.pda.domain.usecase.LoginManuallyUseCase
import com.lonx.ecjtu.pda.domain.usecase.LoginUseCase
import com.lonx.ecjtu.pda.domain.usecase.LogoutUseCase
import com.lonx.ecjtu.pda.domain.usecase.UpdateBooleanPrefsUseCase
import com.lonx.ecjtu.pda.domain.usecase.UpdatePasswordUseCase
import com.lonx.ecjtu.pda.domain.usecase.UpdatePrefsIspUseCase
import com.lonx.ecjtu.pda.domain.usecase.UpdatePrefsPasswordUseCase
import com.lonx.ecjtu.pda.domain.usecase.UpdatePrefsStuIdUseCase
import com.lonx.ecjtu.pda.domain.usecase.UpdateStuCredentialsUseCase
import com.lonx.ecjtu.pda.domain.usecase.UpdateWeiXinIDUseCase
import com.lonx.ecjtu.pda.network.OkHttpConfig
import com.lonx.ecjtu.pda.network.PDAOkHttpClient
import com.lonx.ecjtu.pda.repository.AuthRepositoryImpl
import com.lonx.ecjtu.pda.repository.JwxtCourseRepositoryImpl
import com.lonx.ecjtu.pda.repository.JwxtElectiveRepositoryImpl
import com.lonx.ecjtu.pda.repository.JwxtExperimentRepositoryImpl
import com.lonx.ecjtu.pda.repository.JwxtProfileRepositoryImpl
import com.lonx.ecjtu.pda.repository.JwxtSchedulesRepositoryImpl
import com.lonx.ecjtu.pda.repository.JwxtScoreRepositoryImpl
import com.lonx.ecjtu.pda.repository.JwxtSecondCreditRepositoryImpl
import com.lonx.ecjtu.pda.repository.PreferencesRepositoryImpl
import com.lonx.ecjtu.pda.repository.WifiRepositoryImpl
import com.lonx.ecjtu.pda.repository.source.JwxtApiClientImpl
import com.lonx.ecjtu.pda.viewmodel.HomeViewModel
import com.lonx.ecjtu.pda.viewmodel.LoginViewModel
import com.lonx.ecjtu.pda.viewmodel.SettingViewModel
import com.lonx.ecjtu.pda.viewmodel.SplashViewModel
import com.lonx.ecjtu.pda.viewmodel.StuElectiveViewModel
import com.lonx.ecjtu.pda.viewmodel.StuExperimentViewModel
import com.lonx.ecjtu.pda.viewmodel.StuProfileViewModel
import com.lonx.ecjtu.pda.viewmodel.StuScheduleViewModel
import com.lonx.ecjtu.pda.viewmodel.StuScoreViewModel
import com.lonx.ecjtu.pda.viewmodel.StuSecondCreditViewModel
import com.lonx.ecjtu.pda.viewmodel.WifiViewModel
import okhttp3.CookieJar
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
val appModule = module {

    // --- 核心依赖 ---
    single { PreferencesManager.getInstance() } // 可以省略显式类型

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
    single { PDAOkHttpClient() }
    // --- 网络请求客户端 ---
//    single<OkHttpClient> {
//        MyOkHttpClient(
//            cookieJar = get(),
//            timeout = get(named("defaultTimeout"))
//        ).createClient()
//    }
    // 用于教务系统的客户端
    single(named("jwxtClient")) {
        get<PDAOkHttpClient>().createClient(
            OkHttpConfig(timeoutSeconds = get(named("defaultTimeout")), cookieJar = get(), useUnsafeSSL = true)
        )
    }
    // 用于校园网登录的客户端
    single(named("campusNetClient")) {
        get<PDAOkHttpClient>().createClient(
            OkHttpConfig(timeoutSeconds = 2, followRedirects = false, useUnsafeSSL = true)
        )
    }
    single<Gson> { Gson() }

    single<AuthRepository> { AuthRepositoryImpl(prefs = get(), cookieJar = get(), client = get(named("jwxtClient")), gson = get()) }
    single<JwxtApiClient> { JwxtApiClientImpl(client = get(named("jwxtClient")), prefs = get(), gson = get()) }
    single<WifiRepository> { WifiRepositoryImpl(prefs = get(), client = get(named("campusNetClient"))) }
    single<ProfileRepository> { JwxtProfileRepositoryImpl(apiClient = get(), authRepository = get()) }

    single<CourseRepository> { JwxtCourseRepositoryImpl(apiClient = get(), authRepository = get()) }
    single<ElectiveRepository> { JwxtElectiveRepositoryImpl(apiClient = get(), authRepository = get()) }
    single<ScoreRepository> { JwxtScoreRepositoryImpl(apiClient = get(), authRepository = get()) }
    single<SchedulesRepository> { JwxtSchedulesRepositoryImpl(apiClient = get(), authRepository = get()) }
    single<ExperimentRepository> { JwxtExperimentRepositoryImpl(apiClient = get(), authRepository = get()) }
    single<SecondCreditRepository> { JwxtSecondCreditRepositoryImpl(apiClient = get(), authRepository = get()) }
    single<PreferencesRepository> { PreferencesRepositoryImpl(prefs = get()) }

    single<LoginManuallyUseCase> { LoginManuallyUseCase(authRepository = get()) }
    single<LoginUseCase> { LoginUseCase(authRepository = get()) }
    single<LogoutUseCase> { LogoutUseCase(authRepository = get()) }

    single<CheckCredentialsExistUseCase> { CheckCredentialsExistUseCase(preferencesRepository = get()) }
    single<CheckSessionValidityUseCase> { CheckSessionValidityUseCase(authRepository = get()) }

    single<GetStuProfileUseCase> { GetStuProfileUseCase(profileRepository = get()) }
    single<GetStuElectiveUseCase> { GetStuElectiveUseCase(electiveRepository = get()) }
    single<GetStuCourseUseCase> { GetStuCourseUseCase(courseRepository = get()) }
    single<GetStuScoreUseCase> { GetStuScoreUseCase(scoreRepository = get()) }
    single<GetStuExperimentsUseCase> { GetStuExperimentsUseCase(experimentRepository = get()) }
    single<GetStuSecondCreditUseCase> { GetStuSecondCreditUseCase(secondCreditRepository = get()) }
    single<GetStuSchedulesUseCase> { GetStuSchedulesUseCase(schedulesRepository = get()) }
    single<GetCampusNetStatusUseCase> { GetCampusNetStatusUseCase(wifiRepository = get()) }
    single<GetNetworkTypeUseCase> { GetNetworkTypeUseCase(wifiRepository = get()) }
    single<CampusNetLoginUseCase> { CampusNetLoginUseCase(wifiRepository = get()) }
    single<CampusNetLogoutUseCase> { CampusNetLogoutUseCase(wifiRepository = get()) }

    single<GetStuCredentialsUseCase> { GetStuCredentialsUseCase(preferencesRepository = get()) }
    single<GetWeiXinIDUseCase> { GetWeiXinIDUseCase(preferencesRepository = get()) }
    single<UpdateStuCredentialsUseCase> { UpdateStuCredentialsUseCase(preferencesRepository = get()) }
    single<UpdateWeiXinIDUseCase> { UpdateWeiXinIDUseCase(preferencesRepository = get()) }
    single<UpdatePasswordUseCase> { UpdatePasswordUseCase(authRepository = get()) }
    single<UpdatePrefsStuIdUseCase> { UpdatePrefsStuIdUseCase(preferencesRepository = get()) }
    single<UpdatePrefsPasswordUseCase> { UpdatePrefsPasswordUseCase(preferencesRepository = get()) }
    single<UpdatePrefsIspUseCase> { UpdatePrefsIspUseCase(preferencesRepository = get()) }

    single<GetBooleanPrefsUseCase> { GetBooleanPrefsUseCase(preferencesRepository = get()) }
    single<UpdateBooleanPrefsUseCase> {  UpdateBooleanPrefsUseCase(preferencesRepository = get()) }

    // --- 系统服务 ---
    single { androidContext().getSystemService(Context.WIFI_SERVICE) as WifiManager }
    single { androidContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    single { WifiStatusMonitor(get(), get(), androidContext()) }
    single { LocationStatusMonitor(androidContext()) }

    // ViewModels
    viewModel { WifiViewModel(
        wifiStatusMonitor = get(),
        locationStatusMonitor = get(),
        applicationContext = androidContext(),
        campusNetLoginUseCase = get(),
        campusNetLogoutUseCase = get(),
        checkCredentialsExistUseCase = get()
    ) }
    viewModel { SplashViewModel(checkSessionValidityUseCase = get(), checkCredentialsExistUseCase = get(), loginUseCase = get()) }
    viewModel { LoginViewModel(loginManuallyUseCase = get()) }
    viewModel { StuProfileViewModel(getStuProfileUseCase = get()) }
    viewModel { HomeViewModel() }
    viewModel { SettingViewModel(
        updatePasswordUseCase = get(),
        logoutUseCase = get(),
        updateWeiXinIDUseCase = get(),
        getStuCredentialsUseCase = get(),
        getWeiXinIDUseCase = get(),
        updateStuCredentialsUseCase = get(),
        getBooleanPrefsUseCase = get(),
        updateBooleanPrefsUseCase = get(),
        updatePrefsStuIdUseCase = get(),
        updatePrefsPasswordUseCase = get(),
        updatePrefsIspUseCase = get()
    ) }
    viewModel { StuScoreViewModel(getStuScoreUseCase = get()) }
    viewModel { StuSecondCreditViewModel(getStuSecondCreditUseCase = get()) }
    viewModel { StuScheduleViewModel(
        getSchedulesUseCase = get()
    ) }
    viewModel { StuElectiveViewModel(getStuElectiveUseCase = get()) }
    viewModel { StuExperimentViewModel(getStuExperimentsUseCase = get()) }
}