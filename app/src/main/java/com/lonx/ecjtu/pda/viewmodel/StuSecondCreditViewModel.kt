package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.service.SecondCreditData
import com.lonx.ecjtu.pda.service.StuSecondCreditService

class StuSecondCreditViewModel(
    private val service: StuSecondCreditService
) :BaseResultViewModel<SecondCreditData>(
    fetchData = { service.getSecondCredit() }
)