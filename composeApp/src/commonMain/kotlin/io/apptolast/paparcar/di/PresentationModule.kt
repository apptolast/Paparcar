package io.apptolast.paparcar.di

import io.apptolast.paparcar.presentation.history.HistoryViewModel
import io.apptolast.paparcar.presentation.home.HomeViewModel
import io.apptolast.paparcar.presentation.map.MapViewModel
import io.apptolast.paparcar.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::MapViewModel)
    viewModelOf(::SettingsViewModel)
}
