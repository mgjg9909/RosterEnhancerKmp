package com.matteo.rosterenhancer.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import com.matteo.rosterenhancer.ui.screen.calendar.CalendarViewModel
import com.matteo.rosterenhancer.ui.screen.colleagues.ColleaguesViewModel
import com.matteo.rosterenhancer.ui.screen.dashboard.DashboardViewModel
import com.matteo.rosterenhancer.ui.screen.importscreen.ImportViewModel
import com.matteo.rosterenhancer.ui.screen.onboarding.OnboardingViewModel
import com.matteo.rosterenhancer.ui.screen.profile.ProfileViewModel
import com.matteo.rosterenhancer.ui.screen.salary.PayslipViewModel
import com.matteo.rosterenhancer.ui.screen.salary.SalaryViewModel
import com.matteo.rosterenhancer.ui.screen.settings.SettingsViewModel
import com.matteo.rosterenhancer.ui.screen.stats.StatsViewModel
import com.matteo.rosterenhancer.ui.screen.swaps.RestSwapViewModel
import com.matteo.rosterenhancer.ui.screen.swaps.ShiftSwapViewModel

val viewModelModule = module {
    viewModelOf(::CalendarViewModel)
    viewModelOf(::ColleaguesViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::ImportViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::PayslipViewModel)
    viewModelOf(::SalaryViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::StatsViewModel)
    viewModelOf(::RestSwapViewModel)
    viewModelOf(::ShiftSwapViewModel)
}



