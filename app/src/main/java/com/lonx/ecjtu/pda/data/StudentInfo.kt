package com.lonx.ecjtu.pda.data


/**
 * 学生基本信息数据类
 */
data class StudentInfo(
    // 学号
    val studentId: String,

    // 在班编号
    val classInternalId: String,

    // 姓名
    val name: String,

    // 班级
    val className: String,

    // 性别
    val gender: String,

    // 民族
    val ethnicity: String,

    // 出生日期 (保持为String，如果需要Date对象，需自行解析)
    val dateOfBirth: String,

    // 身份证号
    val idCardNumber: String,

    // 政治面貌
    val politicalStatus: String,

    // 籍贯 (可能为空，使用可空类型 String?)
    val nativePlace: String?="未知",

    // 培养方案编号 (可能为空，使用可空类型 String?)
    val curriculumPlanId: String?="未知",

    // 英语分级级别 (可能为空，使用可空类型 String?)
    val englishLevel: String?,

    // 学籍状态
    val studentStatus: String,

    // 处分状态
    val disciplinaryStatus: String,

    // 高考考生号 (可能为空，使用可空类型 String?)
    val gaokaoExamId: String?,

    // 高考成绩 (保持为String以保留原始格式，如需数值计算需自行转换)
    val gaokaoScore: String?,

    // 生源地
    val placeOfOrigin: String
)
