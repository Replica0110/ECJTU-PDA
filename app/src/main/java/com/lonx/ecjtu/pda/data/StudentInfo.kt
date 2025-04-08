package com.lonx.ecjtu.pda.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class StuInfo(
    val key:String = "",
    val value:String = ""
): Parcelable

@Parcelize
data class StuInfoList(
    val type:String = "",
    val stuInfo: StuInfo? = null
): Parcelable