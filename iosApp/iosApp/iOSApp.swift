import SwiftUI
import BackgroundTasks
import FirebaseCore
import FirebaseCrashlytics
import ComposeApp

/// Forwards the shared module's crash facade into the Firebase iOS SDK — the implementation the
/// `CrashReporterBridge` contract has documented since IOS-RESURRECT-001, finally installed.
/// Kotlin non-fatals travel as NSError (no dSYM needed); native-crash symbolication is the
/// release lane's business. [IOS-CRASH-A-BRIDGE-NOBODY-INSTALLS-001]
class SwiftCrashReporterBridge: CrashReporterBridge {
    func recordNonFatal(tag: String, message: String, error: String) {
        let err = NSError(
            domain: tag,
            code: 0,
            userInfo: [
                "tag": tag,
                "message": message,
                NSLocalizedDescriptionKey: error,
            ]
        )
        Crashlytics.crashlytics().record(error: err)
    }

    func setUserId(userId: String?) {
        Crashlytics.crashlytics().setUserID(userId ?? "")
    }
}

/// BGTaskScheduler stays entirely in Swift ON PURPOSE: the Kotlin/Native CoreLocation klib
/// already proved it can lack symbols (`startMonitoringVisits`), and a background-task
/// registration that silently fails to compile-or-run is a mesh with no heartbeat. Kotlin gets
/// called; it never touches the scheduler. [IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001]
enum PaparcarBackgroundTasks {
    static let refreshId = "com.rndeveloper.paparcar.refresh"
    static let syncId = "com.rndeveloper.paparcar.sync"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshId, using: nil) { task in
            scheduleRefresh() // chain the next one first — iOS forgets non-resubmitted requests
            PaparcarIosBootstrap.shared.onBackgroundRefresh {
                task.setTaskCompleted(success: true)
            }
        }
        BGTaskScheduler.shared.register(forTaskWithIdentifier: syncId, using: nil) { task in
            schedulePendingSync()
            PaparcarIosBootstrap.shared.onBackgroundSync {
                task.setTaskCompleted(success: true)
            }
        }
    }

    /// The mesh's opportunistic tick. iOS decides the real cadence; the request is a wish.
    static func scheduleRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    /// The outbox's backstop: network-required, so a queued write survives long offline stretches.
    static func schedulePendingSync() {
        let request = BGProcessingTaskRequest(identifier: syncId)
        request.requiresNetworkConnectivity = true
        request.earliestBeginDate = Date(timeIntervalSinceNow: 30 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        FirebaseApp.configure()
        // Until this line runs every recordNonFatal/setUserId from Kotlin is a silent no-op —
        // install right after Firebase is up so nothing reported at launch is lost.
        CrashReporter.shared.bridge = SwiftCrashReporterBridge()
        // A BACKGROUND relaunch (SLC, region exit, BGTask) never builds the SwiftUI scene, so
        // MainViewController does not run — this is the guaranteed hook, and the bootstrap is
        // idempotent from both doors. [IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001]
        PaparcarIosBootstrap.shared.ensureStarted()
        // Registration must finish before launch ends — Apple's contract for BGTaskScheduler.
        PaparcarBackgroundTasks.register()
        return true
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                PaparcarBackgroundTasks.scheduleRefresh()
                PaparcarBackgroundTasks.schedulePendingSync()
            }
        }
    }
}
