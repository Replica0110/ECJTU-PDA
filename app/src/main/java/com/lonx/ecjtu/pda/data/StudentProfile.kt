package com.lonx.ecjtu.pda.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class StuProfile(
    val key:String = "",
    val value:String = ""
): Parcelable

@Parcelize
data class StuProfileList(
    val type:String = "",
    val stuProfile: StuProfile? = null
): Parcelable