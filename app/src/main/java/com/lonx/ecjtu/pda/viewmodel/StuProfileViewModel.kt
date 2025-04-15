package com.lonx.ecjtu.pda.viewmodel

import com.lonx.ecjtu.pda.base.BaseResultViewModel
import com.lonx.ecjtu.pda.service.StuProfileService


class StuProfileViewModel(
    private val service: StuProfileService
) : BaseResultViewModel<Map<String, Map<String, String>>>(
    fetchData = { service.getStudentProfile() }
)