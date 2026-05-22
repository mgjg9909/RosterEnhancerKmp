import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        KoinHelper.shared.initKoinIOS()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.keyboard)
        }
    }
}