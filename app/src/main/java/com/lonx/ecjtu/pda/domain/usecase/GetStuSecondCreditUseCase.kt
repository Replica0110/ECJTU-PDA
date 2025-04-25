package com.lonx.ecjtu.pda.domain.usecase

import com.lonx.ecjtu.pda.data.common.PDAResult
import com.lonx.ecjtu.pda.data.model.StuSecondCredits
import com.lonx.ecjtu.pda.domain.repository.SecondCreditRepository

class GetStuSecondCreditUseCase(
    private val secondCreditRepository: SecondCreditRepository
) {
    suspend operator fun invoke(): PDAResult<StuSecondCredits> {
        return secondCreditRepository.getSecondCredit()
    }
}