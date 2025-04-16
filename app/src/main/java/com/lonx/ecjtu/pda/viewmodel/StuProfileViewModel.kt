package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.domain.usecase.GetStuProfileUseCase


class StuProfileViewModel(
    private val getStuProfileUseCase: GetStuProfileUseCase
) : BaseResultViewModel<Map<String, Map<String, String>>>(
    fetchData = { getStuProfileUseCase() }
)