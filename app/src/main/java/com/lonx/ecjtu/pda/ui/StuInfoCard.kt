package com.lonx.ecjtu.pda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lonx.ecjtu.pda.data.StudentInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme


@Composable
fun StuInfoCard(studentInfo: StudentInfo) {
    val infoItems = remember(studentInfo) {
        listOfNotNull(
            "学号" to studentInfo.studentId,
            "在班编号" to studentInfo.classInternalId,
            "姓名" to studentInfo.name,
            "班级" to studentInfo.className,
            "性别" to studentInfo.gender,
            "民族" to studentInfo.ethnicity,
            "出生日期" to studentInfo.dateOfBirth,
            "身份证号" to studentInfo.idCardNumber,
            "政治面貌" to studentInfo.politicalStatus,
            studentInfo.nativePlace?.takeIf { it.isNotBlank() }?.let { "籍贯" to it },
            studentInfo.curriculumPlanId?.takeIf { it.isNotBlank() }?.let { "培养方案编号" to it },
            studentInfo.englishLevel?.takeIf { it.isNotBlank() }?.let { "英语分级级别" to it },
            "学籍状态" to studentInfo.studentStatus,
            "处分状态" to studentInfo.disciplinaryStatus,
            studentInfo.gaokaoExamId?.takeIf { it.isNotBlank() }?.let { "高考考生号" to it },
            studentInfo.gaokaoScore?.takeIf { it.isNotBlank() }?.let { "高考成绩" to it },
            "生源地" to studentInfo.placeOfOrigin
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column {
            infoItems.forEachIndexed { index, (label, value) ->
                StuInfoItem(label = label, value = value)

                if (index < infoItems.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
@Composable
fun StuInfoItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.main,
            textAlign = TextAlign.Start
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.subtitle,
            textAlign = TextAlign.End
        )
    }
}
@Composable
@Preview
fun StuInfoCardPreview() {
    MiuixTheme {
        StuInfoCard(
            studentInfo = StudentInfo(
                studentId = "20190000001",
                classInternalId = "20190000001",
                name = "张三",
                className = "计算机科学与技术",
                gender = "男",
                ethnicity = "汉族",
                dateOfBirth = "2000-01-01",
                idCardNumber = "123456789123456789",
                politicalStatus = "群众",
                nativePlace = "北京市",
                curriculumPlanId = "4894894",
                englishLevel = "B",
                studentStatus = "正常|有学籍",
                disciplinaryStatus = "正常",
                gaokaoExamId = "4894981",
                gaokaoScore = "666.66",
                placeOfOrigin = "江西省",
            )
        )
    }
}