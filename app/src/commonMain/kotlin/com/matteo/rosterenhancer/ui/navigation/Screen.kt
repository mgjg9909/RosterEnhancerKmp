package com.matteo.rosterenhancer.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding  : Screen("onboarding")
    object Main        : Screen("main")
    object Dashboard   : Screen("dashboard")
    object Calendar    : Screen("calendar")
    object Stats       : Screen("stats")
    object Settings    : Screen("settings")
    object Import      : Screen("import")
    object Swaps       : Screen("swaps")
    object Salary      : Screen("salary")
    object Profile     : Screen("profile")
    object RestSwap    : Screen("restswap") 
    object Payslips    : Screen("payslips")
    object PayslipViewer : Screen("payslip_viewer/{payslipId}") {
        fun createRoute(id: Long) = "payslip_viewer/$id"
    }
}
