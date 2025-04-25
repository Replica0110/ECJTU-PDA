package com.lonx.ecjtu.pda.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

val availableIsp = listOf(
    IspOption(1, "中国移动"),
    IspOption(2, "中国电信"),
    IspOption(3, "中国联通")
)

@Parcelize
data class IspOption(
    val id: Int,
    val name: String
) : Parcelable