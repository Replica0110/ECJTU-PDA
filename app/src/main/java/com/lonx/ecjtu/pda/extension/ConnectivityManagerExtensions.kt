package com.lonx.ecjtu.pda.extension

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal val ConnectivityManager.isWifiConnected: Boolean?
    get() = getNetworkCapabilities(activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
