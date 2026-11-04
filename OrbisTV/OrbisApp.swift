import SwiftUI
import AVFoundation

@main
struct OrbisApp: App {
    @StateObject private var store = Store.shared
    @StateObject private var playback = Playback()

    init() {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(playback)
                .preferredColorScheme(.dark)
                .tint(.white)
        }
    }
}

struct PlaySession: Identifiable {
    let id = UUID()
    let items: [PlayItem]
    let index: Int
}

@MainActor
final class Playback: ObservableObject {
    @Published var session: PlaySession? = nil

    func play(_ items: [PlayItem], at index: Int) {
        guard !items.isEmpty else { return }
        session = PlaySession(items: items, index: min(max(0, index), items.count - 1))
    }
}

struct RootView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var playback: Playback
    @State private var showSplash = true

    var body: some View {
        ZStack {
            Group {
                if store.account == nil {
                    LoginView()
                } else if !store.profileChosen {
                    ProfilePickerView()
                        .transition(.opacity)
                } else {
                    MainTabs()
                        .id(store.activeProfileID)
                }
            }
            .animation(.easeInOut(duration: 0.3), value: store.profileChosen)
            if showSplash {
                SplashView(playSound: store.introSound) {
                    withAnimation(.easeOut(duration: 0.45)) { showSplash = false }
                }
                .transition(.opacity)
                .zIndex(1)
            }
        }
        .fullScreenCover(item: $playback.session) { session in
            PlayerScreen(session: session)
                .environmentObject(store)
        }
    }
}

struct MainTabs: View {
    var body: some View {
        TabView {
            CatalogScreen(kind: .live)
                .tabItem { Label("Direct", systemImage: "dot.radiowaves.left.and.right") }
            GuideScreen()
                .tabItem { Label("Guide", systemImage: "list.bullet.rectangle") }
            CatalogScreen(kind: .movie)
                .tabItem { Label("Films", systemImage: "film") }
            CatalogScreen(kind: .series)
                .tabItem { Label("Séries", systemImage: "tv") }
            MeScreen()
                .tabItem { Label("Moi", systemImage: "person.crop.circle") }
        }
    }
}
