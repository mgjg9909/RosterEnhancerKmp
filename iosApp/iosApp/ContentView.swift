import SwiftUI
import ComposeApp

struct ContentView: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                ComposeView()
                    .ignoresSafeArea(.all)
                    .background(Color.red)
                
                VStack {
                    Text("SwiftUI Bounds: \(Int(geo.size.width)) x \(Int(geo.size.height))")
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.black.opacity(0.7))
                        .cornerRadius(8)
                    Spacer()
                }
                .padding(.top, 50)
            }
        }
        .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
