package com.rndeveloper.paparcar.fakes

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.usecase.user.SignOutUseCase
import com.rndeveloper.paparcar.fakes.data.repository.FakeNoopPushTokenProvider
import com.rndeveloper.paparcar.fakes.data.repository.FakeSharedCarRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeZoneAlertSubscriptionRepository

/**
 * El cierre de sesión real con fakes detrás, para los ViewModels que solo necesitan que exista.
 * [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001]
 *
 * Deliberadamente el caso de uso DE VERDAD y no un doble: lo que estos tests no pueden permitirse
 * es pasar con una salida que no retire nada, que es justo el fallo que el ticket arregla.
 */
fun testSignOutUseCase(auth: AuthRepository) = SignOutUseCase(
    authRepository = auth,
    zoneAlertSubscriptions = FakeZoneAlertSubscriptionRepository(),
    sharedCarRepository = FakeSharedCarRepository(),
    pushTokens = FakeNoopPushTokenProvider(),
)
