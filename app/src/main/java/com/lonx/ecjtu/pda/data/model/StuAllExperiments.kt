package com.lonx.ecjtu.pda.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ExperimentInfo(
    val courseName: String,
    val courseType: String,
    val experimentName: String,
    val experimentType: String,
    val batch: String,
    val time: String,
    val location: String,
    val teacher: String
) : Parcelable


@Parcelize
data class TermExperiments(
    val term: String,
    val termName: String,
    val experiments: List<ExperimentInfo>
): Parcelable

@Parcelize
data class StuAllExperiments(
    val experiments: List<TermExperiments>
): Parcelable