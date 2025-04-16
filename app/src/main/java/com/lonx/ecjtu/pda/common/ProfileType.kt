package com.lonx.ecjtu.pda.common

enum class ProfileType {
    BASE,
    CONTACT
}
fun ProfileType.toName(): String {
    return when (this) {
        ProfileType.BASE -> "基本信息"
        ProfileType.CONTACT -> "联系方式"
    }
}