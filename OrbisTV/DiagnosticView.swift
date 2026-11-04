import SwiftUI
import Network

/// « Wi-Fi ou fournisseur ? » : teste la connexion, Internet, le serveur et le débit d'un flux,
/// puis dit d'où vient le problème.
@MainActor
struct DiagnosticView: View {
    enum Status { case waiting, running, ok, warn, fail }

    struct Step: Identifiable {
        let id: Int
        let title: String
        var status: Status = .waiting
        var detail = ""
    }

    @State private var steps: [Step] = [
        Step(id: 0, title: "Connexion de l'iPhone"),
        Step(id: 1, title: "Internet"),
        Step(id: 2, title: "Serveur du fournisseur"),
        Step(id: 3, title: "Débit du flux vidéo"),
    ]
    @State private var verdict: (title: String, text: String)? = nil
    @State private var running = false

    var body: some View {
        List {
            Section {
                ForEach(steps) { step in
                    HStack(spacing: 14) {
                        icon(step.status).frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(step.title).font(.body)
                            if !step.detail.isEmpty {
                                Text(step.detail).font(.caption).foregroundStyle(Palette.muted)
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
            } footer: {
                Text("Le test télécharge quelques secondes d'une chaîne pour mesurer le débit réel.")
            }

            if let verdict {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(verdict.title).font(.headline)
                        Text(verdict.text).font(.subheadline).foregroundStyle(Color.white.opacity(0.85))
                    }
                    .padding(.vertical, 6)
                }
            }

            Section {
                Button(running ? "Test en cours…" : "Relancer le test") { Task { await run() } }
                    .disabled(running)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.black)
        .navigationTitle("Wi-Fi ou fournisseur ?")
        .navigationBarTitleDisplayMode(.inline)
        .task { await run() }
    }

    @ViewBuilder
    private func icon(_ s: Status) -> some View {
        switch s {
        case .waiting: Image(systemName: "circle").foregroundStyle(Palette.muted)
        case .running: ProgressView().tint(.white)
        case .ok: Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.green)
        case .warn: Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Color.orange)
        case .fail: Image(systemName: "xmark.circle.fill").foregroundStyle(Color.red)
        }
    }

    private func set(_ i: Int, _ status: Status, _ detail: String) {
        steps[i].status = status
        steps[i].detail = detail
    }

    private func run() async {
        guard !running else { return }
        running = true
        verdict = nil
        for i in steps.indices { set(i, .waiting, "") }

        // 1. Type de connexion
        set(0, .running, "")
        let link = await currentLink()
        set(0, link == nil ? .fail : .ok, link ?? "Aucune connexion réseau")
        guard link != nil else {
            verdict = ("Pas de réseau", "L'iPhone n'est connecté à rien. Active le Wi-Fi ou les données mobiles.")
            running = false
            return
        }

        // 2. Internet
        set(1, .running, "")
        let net = await Repo.shared.ping("https://captive.apple.com/hotspot-detect.html")
        if let ms = net.ms {
            set(1, ms > 400 ? .warn : .ok, "Réponse en \(ms) ms")
        } else {
            set(1, .fail, "Injoignable")
            verdict = ("Problème de Wi-Fi ou de box", "Ton appareil est connecté au réseau mais Internet ne répond pas. Redémarre ta box ou rapproche-toi du Wi-Fi.")
            running = false
            return
        }

        // 3. Serveur du fournisseur
        set(2, .running, "")
        guard let serverURL = Repo.shared.serverTestURL else {
            set(2, .fail, "Aucun abonnement connecté")
            running = false
            return
        }
        let srv = await Repo.shared.ping(serverURL)
        if let ms = srv.ms, (200..<300).contains(srv.status) {
            set(2, ms > 1500 ? .warn : .ok, "Réponse en \(ms) ms")
        } else {
            set(2, .fail, srv.ms == nil ? "Injoignable" : "Refus du serveur (code \(srv.status))")
            verdict = ("Problème côté fournisseur", "Internet fonctionne, mais le serveur de ton fournisseur ne répond pas correctement. Ce n'est pas ton Wi-Fi : contacte ton fournisseur ou réessaie plus tard.")
            running = false
            return
        }

        // 4. Débit d'un flux
        set(3, .running, "Mesure sur une chaîne…")
        var mbps: Double? = nil
        if let live = try? await Repo.shared.catalog(.live), let first = live.streams.first,
           let url = Repo.shared.playURL(first, format: "ts") {
            mbps = await Repo.shared.throughput(url)
        }
        if let mbps {
            let txt = String(format: "%.1f Mbit/s", mbps)
            if mbps < 3 {
                set(3, .warn, txt)
                verdict = ("Débit trop faible", "Le flux arrive à \(txt). Il faut environ 5 Mbit/s pour la HD. Si ton Internet est rapide, le fournisseur est saturé ; sinon rapproche-toi du Wi-Fi.")
            } else {
                set(3, .ok, txt)
                verdict = ("Tout est bon", "Ta connexion et le serveur de ton fournisseur fonctionnent. Si une chaîne coupe, c'est cette chaîne en particulier qui pose problème chez le fournisseur.")
            }
        } else {
            set(3, .fail, "Le flux ne démarre pas")
            verdict = ("Flux refusé par le fournisseur", "Le serveur répond mais n'envoie pas de vidéo. L'abonnement est peut-être déjà utilisé sur un autre appareil, ou la chaîne testée est hors service.")
        }
        running = false
    }

    private func currentLink() async -> String? {
        await withCheckedContinuation { cont in
            let monitor = NWPathMonitor()
            let once = OnceFlag()
            monitor.pathUpdateHandler = { path in
                guard once.claim() else { return }
                monitor.cancel()
                guard path.status == .satisfied else { cont.resume(returning: nil); return }
                if path.usesInterfaceType(.wifi) { cont.resume(returning: "Wi-Fi") }
                else if path.usesInterfaceType(.cellular) { cont.resume(returning: "Données mobiles") }
                else if path.usesInterfaceType(.wiredEthernet) { cont.resume(returning: "Câble Ethernet") }
                else { cont.resume(returning: "Connecté") }
            }
            monitor.start(queue: DispatchQueue(label: "orbis.diag"))
        }
    }
}

/// Garantit qu'une continuation n'est reprise qu'une seule fois.
final class OnceFlag {
    private var done = false
    private let lock = NSLock()
    func claim() -> Bool {
        lock.lock(); defer { lock.unlock() }
        if done { return false }
        done = true
        return true
    }
}
