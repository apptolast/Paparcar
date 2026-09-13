package com.rndeveloper.paparcar

import androidx.compose.ui.window.ComposeUIViewController

/**
 * The FOREGROUND entry point. All process bootstrap (Koin, the notification delegate, the
 * detection orchestrator, the outbox drain) lives in [PaparcarIosBootstrap] — because a
 * BACKGROUND relaunch never builds this view controller, and the bootstrap must not depend on
 * the UI existing. Idempotent from both doors. [IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001]
 */
fun MainViewController() = run {
    PaparcarIosBootstrap.ensureStarted()
    ComposeUIViewController { App() }
}
