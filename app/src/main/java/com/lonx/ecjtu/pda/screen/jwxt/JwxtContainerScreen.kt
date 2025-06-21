package com.lonx.ecjtu.pda.screen.jwxt

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lonx.ecjtu.pda.navigation.JwxtRoute
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LazyColumn
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperArrow

@Composable
fun JwxtContainerScreen(
    jwxtNavController: NavHostController,
//    scrollBehavior: UpdatableScrollBehavior,
) {

//     val nestedScrollConnection = rememberAppBarNestedScrollConnection(scrollBehavior)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
//            .nestedScroll(nestedScrollConnection)
    ) {
        item {
            SmallTitle("信息查询")
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SuperArrow(
                    title = JwxtRoute.ElectiveCourse.title,
                    summary = "查询选课小班序号",
                    onClick = {
                        jwxtNavController.navigate(JwxtRoute.ElectiveCourse.route)
                    }
                )
            }
        }
        item {
            SmallTitle("成绩查询")
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SuperArrow(
                    title = JwxtRoute.Score.title,
                    summary = "查询绩点及各科分数",
                    onClick = {
                        jwxtNavController.navigate(JwxtRoute.Score.route)
                    }
                )
                SuperArrow(
                    title = JwxtRoute.SecondCredit.title,
                    summary = "查询素质拓展学分",
                    onClick = {
                        jwxtNavController.navigate(JwxtRoute.SecondCredit.route)
                    }
                )
            }
        }
        item {
            SmallTitle("课表/考试")
            Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SuperArrow(
                    title = JwxtRoute.Schedules.title,
                    summary = "查看学期课程安排",
                    onClick = { jwxtNavController.navigate(JwxtRoute.Schedules.route) }
                )
                SuperArrow(
                    title = "班级课表",
                    summary = "查询全校班级课程安排",
                    onClick = {  },
                    enabled = false
                )
                SuperArrow(
                    title = "考试安排",
                    summary = "查询各学期考试安排",
                    onClick = {  },
                    enabled = false
                )
                SuperArrow(
                    title = "我的周历",
                    summary = "查询各学期周历",
                    onClick = {  },
                    enabled = false

                )
                SuperArrow(
                    title = "教师课表",
                    summary = "查询各学期教师课表",
                    onClick = {  },
                    enabled = false
                )
                SuperArrow(
                    title = "教室信息",
                    summary = "查询各学期周历",
                    onClick = {  },
                    enabled = false
                )
                SuperArrow(
                    title = "空教室",
                    summary = "查询空教室",
                    onClick = {  },
                    enabled = false
                )
            }
        }
        item {
            SmallTitle("教学评价")
            Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SuperArrow(
                    title = "学生评教",
                    summary = "对本学期教师进行评教，只在学期末开放",
                    onClick = { },
                    enabled = false
                )
            }
        }
        item {
            SmallTitle("实践教学")
            Card (modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)){
                SuperArrow(
                    title = JwxtRoute.ExperimentInfo.title,
                    summary = "查询各学期实验安排",
                    onClick = {
                        jwxtNavController.navigate(JwxtRoute.ExperimentInfo.route)
                    }
                )
            }
        }

    }
}