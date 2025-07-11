

package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.domain.repository.PreferencesRepository

class UpdatePrefsPasswordUseCase(
    private val preferencesRepository: PreferencesRepository
) {  //用于更新本地存储的密码数据
    operator fun invoke(password: String) {
        preferencesRepository.saveCredentials(studentPass = password)
    }
}