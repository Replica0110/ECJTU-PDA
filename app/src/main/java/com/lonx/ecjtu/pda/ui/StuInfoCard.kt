package com.lonx.ecjtu.pda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lonx.ecjtu.pda.service.StuInfoService
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme


@Composable
fun StuInfoCard(infoData: Map<String, Map<String, String>>) {
    val categoryOrder = listOf(StuInfoService.CATEGORY_BASIC_INFO, StuInfoService.CATEGORY_CONTACT_INFO)

    if (infoData.values.all { it.isEmpty() }) {
        Text("未能加载学生信息", modifier = Modifier.padding(16.dp))
        return
    }

        Column {
            categoryOrder.forEach { categoryTitle ->
                val categoryItems = infoData[categoryTitle] ?: emptyMap()

                if (categoryItems.isNotEmpty()) {
                    SmallTitle(text = categoryTitle)
                    val itemsList = categoryItems.entries.toList()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                    itemsList.forEachIndexed { index, entry ->
                        var (label, value) = entry
                        if (value.isEmpty()){
                            value = "暂无"
                        }
                        StuInfoItem(label = label, value = value)

                        if (index < itemsList.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
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
