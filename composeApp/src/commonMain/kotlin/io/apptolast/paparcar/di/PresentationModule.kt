package io.apptolast.paparcar.di

import io.apptolast.paparcar.presentation.app.SplashViewModel
import io.apptolast.paparcar.presentation.app.AppViewModel
import io.apptolast.paparcar.presentation.history.HistoryViewModel
import io.apptolast.paparcar.presentation.home.HomeViewModel
import io.apptolast.paparcar.presentation.map.ParkingLocationViewModel
import io.apptolast.paparcar.presentation.permissions.PermissionsViewModel
import io.apptolast.paparcar.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::SplashViewModel)
    viewModelOf(::AppViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::ParkingLocationViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::PermissionsViewModel)
}
