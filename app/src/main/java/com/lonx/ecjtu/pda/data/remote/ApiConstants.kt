package com.lonx.ecjtu.pda.data.remote

object ApiConstants {
    const val COURSE_SCHEDULE_URL ="https://jwxt.ecjtu.edu.cn/weixin/CalendarServlet"

    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/58.0.3029.110 Safari/537.3"
    const val ECJTU_DOMAIN = "ecjtu.edu.cn"
    const val PORTAL_ECJTU_DOMAIN = "http://portal.$ECJTU_DOMAIN/dcp/index.jsp"
    const val CAS_ECJTU_DOMAIN = "cas.$ECJTU_DOMAIN"
    const val JWXT_ECJTU_DOMAIN = "jwxt.$ECJTU_DOMAIN"
    const val PWD_ENC_URL = "http://$CAS_ECJTU_DOMAIN/cas/loginPasswdEnc" // 智慧交大密码加密地址
    const val ECJTU_LOGIN_URL = "http://$CAS_ECJTU_DOMAIN/cas/login"  // 智慧交大登录页
    const val ECJTU_LOGOUT_URL = "http://$CAS_ECJTU_DOMAIN/stuMag/LogOut_loginOut.action" // 智慧交大注销页
    const val JWXT_LOGIN_URL = "https://$JWXT_ECJTU_DOMAIN/stuMag/Login_dcpLogin.action"  // 教务系统登录页
    const val JWXT_LOGOUT_URL = "http://$CAS_ECJTU_DOMAIN/stuMag/LogOut_loginOut.action" //教务系统注销页
    const val ECJTU2JWXT_URL = "http://$CAS_ECJTU_DOMAIN/cas/login?service=https%3A%2F%2Fjwxt.ecjtu.edu.cn%2FstuMag%2FLogin_dcpLogin.action"  // 智慧交大登录教务系统
    const val GET_CLASSES_URL = "https://$JWXT_ECJTU_DOMAIN/Schedule/Weekcalendar_getStudentWeekcalendar.action?item=0208"  // 获取课程信息(post)
    const val GET_SCORE_URL = "https://$JWXT_ECJTU_DOMAIN/scoreQuery/stuScoreQue_getStuScore.action?item=0401"  // 获取成绩信息(get)
    const val WEIXIN_JWXT_URL = "https://$JWXT_ECJTU_DOMAIN/weixin/CalendarServlet"
    const val GET_ELECTIVE_COURSE_URL = "https://$JWXT_ECJTU_DOMAIN/infoQuery/XKStu_findTerm.action"  // 获取各学期课程详细信息(get)
    const val GET_STU_PROFILE_URL = "http://portal.$ECJTU_DOMAIN/dcp/profile/profile.action" // 获取个人信息
    const val DCP_URL = "http://portal.$ECJTU_DOMAIN/dcp/"
    const val GET_STU_INFO_URL= "https://$JWXT_ECJTU_DOMAIN/stuMag/UserInfoAction_findUserInfo.action" // 个人信息主页
    const val STU_AVATAR_L_URL= "http://portal.$ECJTU_DOMAIN/dcp/uploadfiles/avatar/large/" //获取头像
    const val DCP_SSO_URL = "http://portal.$ECJTU_DOMAIN/dcp/sso/sso.action"
    const val UPDATE_PASSWORD = "https://$JWXT_ECJTU_DOMAIN/stuMag/UserInfoAction_passwordUpdate.action" //修改密码
    const val COOKIE_CASTGC = "CASTGC"
    const val COOKIE_JSESSIONID = "JSESSIONID"
    const val GET_SECOND_CREDIT = "https://$JWXT_ECJTU_DOMAIN/scoreQuery/secondCreQue_findSecondCredit.action?item=0402" //素拓学分信息
    const val METHOD_GET_AVATAR = "getAvatar"
    const val METHOD_GET_YKT_NUM = "getYktNum"
    const val GET_SCHEDULE = "https://$JWXT_ECJTU_DOMAIN/Schedule/Schedule_getUserSchedume.action"  // 获取课表信息
    const val GET_EXPERIMENT = "https://$JWXT_ECJTU_DOMAIN/Experiment/StudentExperiment_getExperiment.action" //实验信息
    const val JWXT_LOGIN_PAGE_IDENTIFIER = "华东交通大学教务管理系统-登录"
}