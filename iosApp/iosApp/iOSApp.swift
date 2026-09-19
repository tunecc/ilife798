import SwiftUI
import shared

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .onChange(of: scenePhase) { newPhase in
            // iOS 15 兼容：使用单参数 onChange（两参数版本需 iOS 17+）。
            switch newPhase {
            case .active:
                AppLifecycle.shared.notifyResumed()
            case .background:
                AppLifecycle.shared.notifyStopped()
            default:
                break
            }
        }
    }
}
