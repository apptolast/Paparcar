import AuthenticationServices
import CryptoKit
import FirebaseAuth
import Security
import UIKit
import ComposeApp

/// Native Sign in with Apple, wired to the Kotlin `AppleSignInProviderIOS` seams.
///
/// This is the reference integration: the library suspends its coroutine, this coordinator runs
/// AuthenticationServices, and the result travels back through the completion block.
///
/// It serves two requests, both starting with the same authorization:
///
/// - **sign in** — packs the identity token, the nonce and, on the first authorisation only, the
///   name, and hands the string to Kotlin.
/// - **revoke** — takes the fresh `authorizationCode` and calls
///   `Auth.auth().revokeToken(withAuthorizationCode:)`, which App Review guideline 5.1.1(v) requires
///   before deleting an account. The code must be **fresh and single-use**, which is exactly why
///   this asks Apple to authorise again instead of reusing the one from sign-in.
///
/// The two completions look identical and mean the opposite: for sign in, `nil` is failure; for
/// revoke, `nil` is success and a string is the reason it failed. That is the shape of the Kotlin
/// seams, so it is handled explicitly in `fail(_:)` rather than papered over.
///
/// Copied verbatim from BaseLogin 3.0.0 (`iosApp/iosApp/AppleSignInCoordinator.swift`): diff against
/// the library demo before editing. [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001]
///
/// Requires the **Sign in with Apple** capability (see `iosApp.entitlements`) and the Apple provider
/// enabled in the Firebase console for this bundle id.
final class AppleSignInCoordinator: NSObject {

    static let shared = AppleSignInCoordinator()

    private enum PendingRequest {
        case signIn((String?) -> Void)
        case revoke((String?) -> Void)
    }

    /// Non-nil exactly while a request is in flight.
    private var pending: PendingRequest?

    /// Raw (unhashed) nonce of the request in flight. Apple receives its SHA-256; Firebase receives
    /// this one and checks that both match, which is what makes a stolen identity token unusable.
    private var currentRawNonce: String?

    /// `ASAuthorizationController` is not retained by the system: without this reference it is
    /// deallocated before the delegate fires and the sheet never appears.
    private var controller: ASAuthorizationController?

    private override init() {
    }

    /// Installs both handlers. Call once at startup, before the first Composable renders.
    func register() {
        AppleSignInProviderIOS.shared.signInHandler = { [weak self] scopes, completion in
            self?.start(.signIn(discardingResult(completion)), scopes: Self.scopes(from: scopes))
        }

        AppleSignInProviderIOS.shared.revokeHandler = { [weak self] completion in
            self?.start(.revoke(discardingResult(completion)), scopes: Self.defaultScopes)
        }
    }

    private func start(_ request: PendingRequest, scopes: [ASAuthorization.Scope]) {
        // AuthenticationServices is main-thread only; the coroutine may resume anywhere.
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                Self.reject(request, reason: "The coordinator is gone.")
                return
            }
            guard self.pending == nil else {
                Self.reject(request, reason: "An Apple request is already in flight.")
                return
            }
            guard let rawNonce = Self.randomNonceString() else {
                Self.reject(request, reason: "Could not generate a secure nonce.")
                return
            }

            self.pending = request
            self.currentRawNonce = rawNonce

            let appleRequest = ASAuthorizationAppleIDProvider().createRequest()
            appleRequest.requestedScopes = scopes
            appleRequest.nonce = Self.sha256(rawNonce)

            let controller = ASAuthorizationController(authorizationRequests: [appleRequest])
            controller.delegate = self
            controller.presentationContextProvider = self
            self.controller = controller
            controller.performRequests()
        }
    }

    /// Turns down a request that never started, in the shape that request expects.
    private static func reject(_ request: PendingRequest, reason: String) {
        NSLog("%@", "[AppleSignIn] \(reason)")
        switch request {
        case .signIn(let completion): completion(nil)
        case .revoke(let completion): completion(reason)
        }
    }

    private func clearState() {
        pending = nil
        currentRawNonce = nil
        controller = nil
    }

    /// Sign-in exit point: `nil` means cancelled or failed.
    private func finishSignIn(_ token: String?) {
        guard case .signIn(let completion)? = pending else {
            clearState()
            return
        }
        clearState()
        completion(token)
    }

    /// Revocation exit point: `nil` means the token **was** revoked.
    private func finishRevoke(errorMessage: String?) {
        guard case .revoke(let completion)? = pending else {
            clearState()
            return
        }
        clearState()
        completion(errorMessage)
    }

    /// Fails whatever is in flight, in the shape that request expects.
    private func fail(_ reason: String) {
        NSLog("%@", "[AppleSignIn] \(reason)")
        switch pending {
        case .signIn: finishSignIn(nil)
        case .revoke: finishRevoke(errorMessage: reason)
        case .none: clearState()
        }
    }
}

// MARK: - ASAuthorizationControllerDelegate

extension AppleSignInCoordinator: ASAuthorizationControllerDelegate {

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            fail("Authorisation returned an unexpected credential type.")
            return
        }

        switch pending {
        case .signIn: completeSignIn(with: credential)
        case .revoke: completeRevoke(with: credential)
        case .none: clearState()
        }
    }

    private func completeSignIn(with credential: ASAuthorizationAppleIDCredential) {
        guard let tokenData = credential.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8)
        else {
            fail("Authorisation returned no usable identity token.")
            return
        }
        guard let rawNonce = currentRawNonce else {
            // Signing in without the nonce would drop replay protection: refuse instead.
            fail("No raw nonce for this request; refusing the token.")
            return
        }

        // Apple returns the full name **only on the very first authorisation** of each user. The
        // library persists it into the Firebase profile; if it is not sent now it is lost for good.
        var packed = "\(idToken)|||rawNonce|||\(rawNonce)"
        if let displayName = Self.displayName(from: credential.fullName) {
            packed += "|||displayName|||\(displayName)"
        }
        finishSignIn(packed)
    }

    private func completeRevoke(with credential: ASAuthorizationAppleIDCredential) {
        guard let codeData = credential.authorizationCode,
              let code = String(data: codeData, encoding: .utf8)
        else {
            fail("Authorisation returned no authorization code; cannot revoke.")
            return
        }

        Auth.auth().revokeToken(withAuthorizationCode: code) { [weak self] error in
            if let error {
                self?.fail("Revocation rejected by Firebase: \(error.localizedDescription)")
                return
            }
            self?.finishRevoke(errorMessage: nil)
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        if let authError = error as? ASAuthorizationError, authError.code == .canceled {
            fail("Cancelled by the user.")
        } else {
            fail("Failed: \(error.localizedDescription)")
        }
    }
}

// MARK: - ASAuthorizationControllerPresentationContextProviding

extension AppleSignInCoordinator: ASAuthorizationControllerPresentationContextProviding {

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap {
            $0 as? UIWindowScene
        }
        let scene = scenes.first {
            $0.activationState == .foregroundActive
        } ?? scenes.first
        return scene?.keyWindow ?? scene?.windows.first ?? UIWindow()
    }
}

// MARK: - Nonce

private extension AppleSignInCoordinator {

    /// What Apple is asked for when the host configures nothing, and what every host asked for
    /// before `AppleSignInConfig.scopes` reached this side.
    static let defaultScopes: [ASAuthorization.Scope] = [.fullName, .email]

    /// Translates the comma-separated scopes the library sends into Apple's own values.
    ///
    /// An empty or unrecognised list falls back to the default rather than requesting nothing:
    /// asking for no scopes means Apple never returns the name, and losing it is permanent.
    static func scopes(from packed: String?) -> [ASAuthorization.Scope] {
        let mapped = (packed ?? "")
            .split(separator: ",")
            .compactMap { part -> ASAuthorization.Scope? in
                switch part.trimmingCharacters(in: .whitespaces).lowercased() {
                case "name", "fullname": return .fullName
                case "email": return .email
                default: return nil
                }
            }
        return mapped.isEmpty ? defaultScopes : mapped
    }

    /// Cryptographically secure nonce. Returns `nil` rather than falling back to a predictable
    /// value: a guessable nonce is worse than no sign-in.
    static func randomNonceString(length: Int = 32) -> String? {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        result.reserveCapacity(length)

        while result.count < length {
            var randoms = [UInt8](repeating: 0, count: 16)
            guard SecRandomCopyBytes(kSecRandomDefault, randoms.count, &randoms) == errSecSuccess else {
                return nil
            }
            for random in randoms where result.count < length && random < charset.count {
                result.append(charset[Int(random)])
            }
        }
        return result
    }

    static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map {
            String(format: "%02x", $0)
        }
        .joined()
    }

    static func displayName(from components: PersonNameComponents?) -> String? {
        guard let components else {
            return nil
        }
        let formatter = PersonNameComponentsFormatter()
        formatter.style = .long
        let name = formatter.string(from: components).trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty ? nil : name
    }
}
