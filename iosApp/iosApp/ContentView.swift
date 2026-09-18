import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        #if DEBUG && PAPARCAR_SCREENSHOTS
        // App Store screenshot run: the mock graph, with the scenario the launch asked for.
        // Absent from Release on both sides — see ScreenshotMode.
        // [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
        if ScreenshotMode.isActive {
            return MockMainViewControllerKt.MockMainViewController(shot: ScreenshotMode.shot)
        }
        #endif
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}



