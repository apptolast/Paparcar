#if DEBUG && PAPARCAR_SCREENSHOTS
import Foundation

/// The App Store screenshot run, and nothing else.
///
/// ⛔ The entire file sits behind `#if DEBUG && PAPARCAR_SCREENSHOTS`, and that is the point rather
/// than a style choice. App Review reads a demo path that exists in the shipped binary as guideline
/// 2.3.1 — hidden functionality — and has already rejected a sibling app of this team for it. A
/// runtime flag would not help: what is judged is the code inside the `.ipa`, not whether it runs.
///
/// Two conditions, not one, because they fail independently:
///   · `DEBUG` is defined only in the Debug configuration, so Release never compiles this.
///   · `PAPARCAR_SCREENSHOTS` is set by the `screenshots` lane alone, so an ordinary Debug build a
///     developer runs on their Mac does not compile it either — and could not link it if it did.
///
/// That second point is load-bearing: `MockMainViewController` lives in `:mockkit`, which produces
/// its OWN framework. A normal build embeds `:shared`'s, where the symbol does not exist. The lane
/// swaps the framework by setting `PAPARCAR_FRAMEWORK_PROJECT=mockkit`. So the Swift half and the
/// Kotlin half are each absent on their own terms, and neither can drag the other in.
/// [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
enum ScreenshotMode {

    /// Presence of this argument switches the app onto the mock graph.
    private static let flag = "-PaparcarScreenshots"

    /// `-PaparcarShot Parked` — the `ShotPreset` the frame needs. Same enum Android reads from
    /// `--es shot <key>`, so the two stores shoot the same eight scenes from one definition.
    private static let shotKey = "-PaparcarShot"

    static var isActive: Bool {
        ProcessInfo.processInfo.arguments.contains(flag)
    }

    static var shot: String? {
        let args = ProcessInfo.processInfo.arguments
        guard let index = args.firstIndex(of: shotKey), index + 1 < args.count else { return nil }
        return args[index + 1]
    }
}
#endif
