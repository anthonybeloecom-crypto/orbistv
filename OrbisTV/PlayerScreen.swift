import SwiftUI
import AVKit

@MainActor
struct PlayerScreen: View {
    @StateObject private var model: PlayerModel
    @Environment(\.dismiss) private var dismiss

    init(session: PlaySession) {
        _model = StateObject(wrappedValue: PlayerModel(items: session.items, index: session.index))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            SystemPlayer(player: model.player, gravity: model.gravity).ignoresSafeArea()

            VStack {
                HStack(spacing: 12) {
                    Button {
                        model.stop()
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.headline)
                            .frame(width: 44, height: 44)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                    .accessibilityLabel("Fermer")

                    if model.showInfo {
                        Text(model.infoText)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(2)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
                            .transition(.opacity)
                    }
                    Spacer()

                    Button { model.cycleGravity() } label: {
                        Image(systemName: "aspectratio").font(.headline).frame(width: 44, height: 44)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                    .accessibilityLabel("Format de l'image")

                    if model.current.isLive && model.items.count > 1 {
                        Button { model.zap(-1) } label: {
                            Image(systemName: "chevron.up").font(.headline).frame(width: 44, height: 44)
                                .background(.ultraThinMaterial, in: Circle())
                        }
                        .accessibilityLabel("Chaîne précédente")
                        Button { model.zap(1) } label: {
                            Image(systemName: "chevron.down").font(.headline).frame(width: 44, height: 44)
                                .background(.ultraThinMaterial, in: Circle())
                        }
                        .accessibilityLabel("Chaîne suivante")
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 56)

                Spacer()

                if let c = model.countdown {
                    HStack {
                        Spacer()
                        Button { model.playNext() } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Épisode suivant dans \(c) s").font(.caption).foregroundStyle(Palette.muted)
                                Text(model.nextTitle).font(.subheadline.weight(.semibold)).lineLimit(2)
                                Text("Touche pour lancer").font(.caption2).foregroundStyle(Palette.muted)
                            }
                            .padding(16)
                            .frame(maxWidth: 320, alignment: .leading)
                            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(24)
                    .padding(.bottom, 60)
                }
            }
        }
        .animation(.easeInOut(duration: 0.25), value: model.showInfo)
        .onAppear { model.start() }
        .onDisappear { model.stop() }
        .statusBarHidden()
    }
}

/// Lecteur natif d'Apple (contrôles, AirPlay, image dans l'image).
struct SystemPlayer: UIViewControllerRepresentable {
    let player: AVPlayer
    var gravity: AVLayerVideoGravity = .resizeAspect

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let vc = AVPlayerViewController()
        vc.player = player
        vc.allowsPictureInPicturePlayback = true
        vc.canStartPictureInPictureAutomaticallyFromInline = true
        vc.videoGravity = .resizeAspect
        return vc
    }

    func updateUIViewController(_ vc: AVPlayerViewController, context: Context) {
        if vc.player !== player { vc.player = player }
        if vc.videoGravity != gravity { vc.videoGravity = gravity }
    }
}

@MainActor
final class PlayerModel: ObservableObject {
    let player = AVPlayer()
    let items: [PlayItem]

    @Published var index: Int
    @Published var countdown: Int? = nil
    @Published var infoText = ""
    @Published var showInfo = false
    @Published var gravity: AVLayerVideoGravity = .resizeAspect

    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var statusObserver: NSKeyValueObservation?
    private var countdownTimer: Timer?
    private var hideTask: Task<Void, Never>?
    private var started = false
    private var retried = false

    var current: PlayItem { items[index] }
    var nextTitle: String { index + 1 < items.count ? items[index + 1].title : "" }

    init(items: [PlayItem], index: Int) {
        self.items = items
        self.index = index
    }

    func start() {
        guard !started else { return }
        started = true
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 10, preferredTimescale: 1), queue: .main) { [weak self] _ in
            Task { @MainActor in self?.saveProgress() }
        }
        playCurrent()
    }

    func stop() {
        guard started else { return }
        started = false
        saveProgress()
        player.pause()
        if let t = timeObserver { player.removeTimeObserver(t); timeObserver = nil }
        if let o = endObserver { NotificationCenter.default.removeObserver(o); endObserver = nil }
        statusObserver = nil
        cancelCountdown()
        hideTask?.cancel()
        Store.shared.bumpProgress()
    }

    func playCurrent() {
        cancelCountdown()
        retried = false
        let item = current
        guard let url = URL(string: item.url) else {
            flash("Adresse de lecture invalide", sticky: true)
            return
        }
        let asset = AVURLAsset(url: url, options: ["AVURLAssetHTTPHeaderFieldsKey": ["User-Agent": Net.userAgent]])
        let playerItem = AVPlayerItem(asset: asset)

        if let o = endObserver { NotificationCenter.default.removeObserver(o) }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: playerItem, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.ended() }
        }
        statusObserver = playerItem.observe(\.status, options: [.new]) { [weak self] obs, _ in
            guard obs.status == .failed else { return }
            Task { @MainActor in self?.failed() }
        }

        player.replaceCurrentItem(with: playerItem)
        let start = item.isLive ? 0 : (item.startAt ?? Store.shared.resume(item.key))
        if start > 0 {
            player.seek(to: CMTime(seconds: start, preferredTimescale: 600))
        }
        player.play()
        flash([item.subtitle, item.title].compactMap { $0 }.joined(separator: "  ·  "))
    }

    /// Adapter (toute l'image) → Zoom (remplit l'écran) → Étirer.
    func cycleGravity() {
        switch gravity {
        case .resizeAspect:
            gravity = .resizeAspectFill
            flash("Image : Zoom")
        case .resizeAspectFill:
            gravity = .resize
            flash("Image : Étirer")
        default:
            gravity = .resizeAspect
            flash("Image : Adapter")
        }
    }

    func zap(_ delta: Int) {
        guard items.count > 1 else { return }
        saveProgress()
        index = ((index + delta) % items.count + items.count) % items.count
        playCurrent()
    }

    func playNext() {
        guard index + 1 < items.count else { return }
        index += 1
        playCurrent()
    }

    private func ended() {
        let item = current
        if item.isLive {
            playCurrent()
            return
        }
        Store.shared.clearProgress(item.key)
        guard index + 1 < items.count else { return }
        countdown = 10
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
    }

    private func tick() {
        guard let c = countdown else { return }
        if c <= 1 {
            playNext()
        } else {
            countdown = c - 1
        }
    }

    private func failed() {
        if !retried {
            retried = true
            flash("Reconnexion…")
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                guard let self, self.started else { return }
                let keepRetried = true
                self.playCurrent()
                self.retried = keepRetried
            }
            return
        }
        flash("Lecture impossible. Essaie le format HLS dans Réglages, ou ce fichier n'est pas lisible sur iPhone (MKV, AVI).", sticky: true)
    }

    private func cancelCountdown() {
        countdownTimer?.invalidate()
        countdownTimer = nil
        countdown = nil
    }

    private func saveProgress() {
        guard !items.isEmpty else { return }
        let item = current
        guard !item.isLive, let duration = player.currentItem?.duration.seconds, duration.isFinite, duration > 0 else { return }
        let position = player.currentTime().seconds
        if position > duration * 0.95 {
            Store.shared.clearProgress(item.key)
        } else if position > 10 {
            Store.shared.saveProgress(item.key, position: position, duration: duration)
        }
    }

    private func flash(_ text: String, sticky: Bool = false) {
        infoText = text
        showInfo = true
        hideTask?.cancel()
        guard !sticky else { return }
        hideTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            guard !Task.isCancelled else { return }
            self?.showInfo = false
        }
    }
}
