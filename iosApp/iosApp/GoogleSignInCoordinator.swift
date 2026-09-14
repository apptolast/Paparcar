import GoogleSignIn
import UIKit
import ComposeApp

/// Native Google Sign-In, wired to the Kotlin `GoogleSignInProviderIOS` seams.
///
/// The library passes the client id it was configured with (`CLIENT_ID` of GoogleService-Info.plist,
/// read in `GoogleServiceInfo.kt`) and suspends until the completion runs. Token format, shared with
/// Kotlin: `idToken|||accessToken|||<accessToken>`; `nil` means cancelled or failed.
/// [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001]
enum GoogleSignInCoordinator {

    /// Installs both handlers. Call once at startup, before the first Composable renders.
    static func register() {
        GoogleSignInProviderIOS.shared.signInHandler = { clientId, completion in
            let completion = discardingResult(completion)
            // GIDSignIn presents UI: main thread only, and the coroutine may resume anywhere.
            DispatchQueue.main.async {
                guard let clientId, let presenter = topViewController() else {
                    NSLog("%@", "[GoogleSignIn] No client id or no presenter; is GoogleService-Info.plist bundled?")
                    completion(nil)
                    return
                }
                GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientId)
                GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
                    guard let user = result?.user, let idToken = user.idToken?.tokenString, error == nil else {
                        completion(nil)
                        return
                    }
                    completion("\(idToken)|||accessToken|||\(user.accessToken.tokenString)")
                }
            }
        }

        // Firebase's signOut does not touch GIDSignIn's keychain session: without this the next
        // sign-in silently reuses the previous Google account.
        GoogleSignInProviderIOS.shared.signOutHandler = {
            GIDSignIn.sharedInstance.signOut()
        }
    }

    /// Hands the OAuth redirect back to GoogleSignIn. Wired from SwiftUI's `.onOpenURL`: in a
    /// scene-based app `application(_:open:options:)` is never called.
    static func handle(_ url: URL) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap {
            $0 as? UIWindowScene
        }
        let scene = scenes.first {
            $0.activationState == .foregroundActive
        } ?? scenes.first
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
