package com.matteo.rosterenhancer.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.core.module.dsl.singleOf
import com.matteo.rosterenhancer.data.local.RosterDatabase
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.util.DataStoreManager
import org.koin.dsl.KoinAppDeclaration

expect val platformModule: Module

val commonModule = module {
    single { get<RosterDatabase>().employeeDao() }
    single { get<RosterDatabase>().shiftDao() }
    single { get<RosterDatabase>().monthRosterDao() }
    single { get<RosterDatabase>().shiftNoteDao() }
    single { get<RosterDatabase>().payslipDao() }
    
    singleOf(::DataStoreManager)
    singleOf(::RosterRepository)
    
    // UseCases
    org.koin.core.module.dsl.factoryOf(com.matteo.rosterenhancer.domain.usecase.FindSwapCandidatesUseCase::class)
    org.koin.core.module.dsl.factoryOf(com.matteo.rosterenhancer.domain.usecase.FindRestSwapCandidatesUseCase::class)
    org.koin.core.module.dsl.factoryOf(com.matteo.rosterenhancer.domain.usecase.GetMonthlyStatsUseCase::class)
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(commonModule, platformModule, viewModelModule)
}