import SwiftUI
import ComposeApp

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .background(Color.red)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
