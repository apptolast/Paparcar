package com.rndeveloper.paparcar

import com.apptolast.baselogin.di.initLoginKoin
import com.rndeveloper.paparcar.data.sync.SyncOutboxDrainer
import com.rndeveloper.paparcar.detection.IosDetectionController
import com.rndeveloper.paparcar.di.dataModule
import com.rndeveloper.paparcar.di.domainModule
import com.rndeveloper.paparcar.di.iosDetectionModule
import com.rndeveloper.paparcar.di.iosPlatformModule
import com.rndeveloper.paparcar.di.paparcarLoginConfig
import com.rndeveloper.paparcar.di.presentationModule
import com.rndeveloper.paparcar.notification.IosNotificationActionHandler
import com.rndeveloper.paparcar.push.IosPushRegistry
import com.rndeveloper.paparcar.push.PushMessageHandler
import com.rndeveloper.paparcar.push.SharedCarMemberSync
import com.rndeveloper.paparcar.push.ZoneAlertSubscriptionSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The ONE bootstrap for the iOS process, callable from Swift — because a BACKGROUND relaunch
 * (SLC, region exit, BGTask) never builds the SwiftUI scene: `MainViewController()` does not run,
 * so anything only IT started (Koin, the detection orchestrator, the outbox drain) simply did not
 * exist on the wakes that matter most. `AppDelegate.didFinishLaunching` is the guaranteed hook;
 * this object is what it calls. Idempotent — the foreground path (MainViewController) calls it
 * too and whoever arrives second is a no-op. [IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001]
 */
object PaparcarIosBootstrap {

    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Held here because UNUserNotificationCenter.delegate is weak — letting this go out of scope
    // would silently disable the notification action routing.
    private var notificationDelegate: IosNotificationActionHandler? = null

    fun ensureStarted() {
        if (started) return
        started = true
        // Start Koin via the BaseLogin initializer so the library's auth modules are registered
        // alongside the app modules — mirrors PaparcarApp on Android. Google arrives with
        // IOS-SOCIAL-LOGIN-001. [AUTH-PROVIDERS-EXPLICIT-001]
        initLoginKoin(config = paparcarLoginConfig(googleWebClientId = null)) {
            modules(
                presentationModule,
                domainModule,
                dataModule,
                iosPlatformModule,
                iosDetectionModule,
            )
        }
        val koin = KoinPlatform.getKoin()
        val handler = IosNotificationActionHandler(
            coordinator = koin.get(),
            revertParkingUseCase = koin.get(),
            notificationPort = koin.get(),
            controller = koin.get(),
            vehicleRepository = koin.get(),
            carLinkDeclineStore = koin.get(),
        )
        notificationDelegate = handler
        UNUserNotificationCenter.currentNotificationCenter().setDelegate(handler)
        // [IOS-F1] The orchestrator: bus subscription before any delegate can fire + reconcile
        // + wake reconstruction + safety-net tick.
        koin.get<IosDetectionController>().start()
        // Las dos vigilancias que publican el token de este móvil. Sin ellas el reparto del
        // servidor descarta a este miembro por no tener dispositivos, y no lo dice: `targetsFor`
        // simplemente no lo cuenta. [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
        koin.get<ZoneAlertSubscriptionSync>().start()
        koin.get<SharedCarMemberSync>().start()
        // Anything the last process owed goes out now.
        scope.launch { koin.get<SyncOutboxDrainer>().drain() }
    }

    /**
     * El token de APNs/FCM que Swift acaba de recibir. [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
     *
     * ⛔ Resincroniza las DOS vigilancias, igual que `onNewToken` en Android: un token nuevo que
     * solo se apunta en una lista deja la otra apuntando a un dispositivo que ya no existe, y eso no
     * falla — se calla.
     */
    fun onPushToken(token: String) {
        ensureStarted()
        IosPushRegistry.onToken(token)
        val koin = KoinPlatform.getKoin()
        koin.get<ZoneAlertSubscriptionSync>().resyncNow()
        koin.get<SharedCarMemberSync>().resyncNow()
    }

    /**
     * Un mensaje de datos recién entregado por APNs.
     * [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
     *
     * ⛔ **`ensureStarted()` primero, y no por costumbre**: un push de fondo puede relanzar el
     * proceso muerto, y entonces Koin no existe todavía — la misma razón por la que este objeto
     * existe. [completion] se llama SIEMPRE, también si el reparto falla: iOS recorta el presupuesto
     * de fondo de las apps que no contestan.
     *
     * El orden de lo que pasa dentro —primero la valla, después la tarjeta— no se decide aquí: vive
     * en `PushMessageHandler`, que es el mismo en las dos plataformas.
     */
    fun onRemoteMessage(userInfo: Map<Any?, Any?>, completion: () -> Unit) {
        ensureStarted()
        val data = userInfo.entries.mapNotNull { (key, value) ->
            val name = key as? String ?: return@mapNotNull null
            val text = value as? String ?: return@mapNotNull null
            name to text
        }.toMap()
        scope.launch {
            runCatching { KoinPlatform.getKoin().get<PushMessageHandler>().handle(data) }
            completion()
        }
    }

    /** BGAppRefreshTask handler entry: mesh tick + outbox drain, then tell iOS we are done. */
    fun onBackgroundRefresh(completion: () -> Unit) {
        ensureStarted()
        val koin = KoinPlatform.getKoin()
        koin.get<IosDetectionController>().systemTick("bg-refresh")
        scope.launch {
            runCatching { koin.get<SyncOutboxDrainer>().drain() }
            completion()
        }
    }

    /** BGProcessingTask handler entry (network-required): drain only. */
    fun onBackgroundSync(completion: () -> Unit) {
        ensureStarted()
        scope.launch {
            runCatching { KoinPlatform.getKoin().get<SyncOutboxDrainer>().drain() }
            completion()
        }
    }
}
