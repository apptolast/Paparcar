package com.rndeveloper.paparcar.presentation.settings

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberInviteFriendAction(userId: String?): (() -> Unit)?
