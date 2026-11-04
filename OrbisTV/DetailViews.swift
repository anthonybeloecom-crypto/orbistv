import SwiftUI

// MARK: - Film

@MainActor
struct MovieDetailView: View {
    let stream: Stream
    @EnvironmentObject var store: Store
    @EnvironmentObject var playback: Playback
    @State private var info: VodInfo? = nil

    var body: some View {
        let resume = store.progress(stream.key) > 0 ? store.resume(stream.key) : 0
        let fav = store.isFavorite(stream)
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                RemoteImage(url: info?.backdrop ?? stream.logo, name: stream.name, fill: true)
                    .frame(height: 280)
                    .frame(maxWidth: .infinity)
                    .overlay(LinearGradient(colors: [.clear, .black], startPoint: .center, endPoint: .bottom))

                VStack(alignment: .leading, spacing: 14) {
                    Text(stream.name)
                        .font(.system(size: 30, weight: .medium))
                        .tracking(-0.4)
                    let meta = [info?.year, info?.genre, info?.duration, stream.rating.map { "★ \($0)" }].compactMap { $0 }
                    if !meta.isEmpty {
                        Text(meta.joined(separator: "  ·  ")).font(.subheadline).foregroundStyle(Palette.muted)
                    }
                    if resume > 0 {
                        ProgressLine(value: store.progress(stream.key)).clipShape(Capsule())
                        PrimaryButton(title: "Reprendre · \(formatTime(resume))", icon: "play.fill") { play(from: resume) }
                        SecondaryButton(title: "Depuis le début", icon: "arrow.counterclockwise") { play(from: 0) }
                    } else {
                        PrimaryButton(title: "Lecture", icon: "play.fill") { play(from: 0) }
                    }
                    SecondaryButton(title: fav ? "Retirer des favoris" : "Ajouter aux favoris", icon: fav ? "star.fill" : "star") {
                        store.toggleFavorite(stream)
                    }
                    if let plot = info?.plot ?? stream.plot {
                        Text(plot).font(.body).foregroundStyle(Color.white.opacity(0.85)).padding(.top, 4)
                    }
                    if let cast = info?.cast {
                        Text("Avec : \(cast)").font(.footnote).foregroundStyle(Palette.muted)
                    }
                }
                .padding(20)
            }
        }
        .background(Color.black)
        .ignoresSafeArea(edges: .top)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if stream.url == nil && info == nil {
                info = try? await Repo.shared.vodInfo(stream.id)
            }
        }
    }

    private func play(from start: Double) {
        guard let url = Repo.shared.playURL(stream) else { return }
        playback.play([PlayItem(key: stream.key, title: stream.name, url: url, isLive: false, startAt: start)], at: 0)
    }
}

// MARK: - Série

@MainActor
struct SeriesDetailView: View {
    let stream: Stream
    @EnvironmentObject var store: Store
    @EnvironmentObject var playback: Playback
    @State private var state: LoadState<SeriesInfo> = .loading
    @State private var season: Int? = nil

    var body: some View {
        Group {
            switch state {
            case .loading:
                LoadingView(text: "Chargement des épisodes…")
            case .failed(let message):
                ErrorView(message: message) { Task { await load() } }
            case .loaded(let info):
                content(info)
            }
        }
        .background(Color.black)
        .navigationTitle(stream.name)
        .navigationBarTitleDisplayMode(.inline)
        .task { if case .loading = state { await load() } }
    }

    @ViewBuilder
    private func content(_ info: SeriesInfo) -> some View {
        let current = season ?? info.seasonNumbers.first ?? 1
        let episodes = info.seasons[current] ?? []
        let fav = store.isFavorite(stream)
        List {
            Section {
                HStack(alignment: .top, spacing: 16) {
                    RemoteImage(url: stream.logo, name: stream.name, fill: true)
                        .frame(width: 110, height: 165)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    VStack(alignment: .leading, spacing: 8) {
                        Text(stream.name).font(.title2.weight(.medium))
                        let meta = [info.year, info.genre].compactMap { $0 }
                        if !meta.isEmpty {
                            Text(meta.joined(separator: "  ·  ")).font(.footnote).foregroundStyle(Palette.muted)
                        }
                        Button { store.toggleFavorite(stream) } label: {
                            Label(fav ? "Retirer des favoris" : "Ajouter aux favoris", systemImage: fav ? "star.fill" : "star")
                                .font(.subheadline)
                        }
                        .buttonStyle(.bordered)
                    }
                }
                if let plot = info.plot ?? stream.plot {
                    Text(plot).font(.subheadline).foregroundStyle(Color.white.opacity(0.85))
                }
                if info.seasonNumbers.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(info.seasonNumbers, id: \.self) { n in
                                Chip(title: "Saison \(n)", selected: n == current) { season = n }
                            }
                        }
                    }
                }
            }
            .listRowBackground(Color.black)
            .listRowSeparator(.hidden)

            Section {
                if episodes.isEmpty {
                    Text("Aucun épisode disponible").foregroundStyle(Palette.muted)
                }
                ForEach(episodes, id: \.key) { ep in
                    Button { play(ep, all: info.allEpisodes) } label: {
                        EpisodeRow(episode: ep, fallback: stream.logo, progress: store.progress(ep.key))
                    }
                    .buttonStyle(.plain)
                }
            }
            .listRowBackground(Color.black)
            .listRowSeparatorTint(Palette.surface2)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private func load() async {
        do {
            state = .loaded(try await Repo.shared.seriesInfo(stream.id))
        } catch {
            state = .failed(error.localizedDescription)
        }
    }

    private func play(_ ep: Episode, all: [Episode]) {
        let queue: [PlayItem] = all.compactMap { e in
            guard let url = Repo.shared.episodeURL(e) else { return nil }
            return PlayItem(key: e.key, title: "\(stream.name) · S\(e.season)E\(e.number) · \(e.title)", url: url, isLive: false)
        }
        playback.play(queue, at: all.firstIndex(of: ep) ?? 0)
    }
}

private struct EpisodeRow: View {
    let episode: Episode
    let fallback: String?
    let progress: Double

    var body: some View {
        HStack(spacing: 14) {
            RemoteImage(url: episode.image ?? fallback, name: episode.title, fill: true)
                .frame(width: 124, height: 70)
                .overlay(alignment: .bottom) { if progress > 0 { ProgressLine(value: progress) } }
                .clipShape(RoundedRectangle(cornerRadius: 6))
            VStack(alignment: .leading, spacing: 3) {
                Text("Épisode \(episode.number)").font(.caption.weight(.semibold)).foregroundStyle(Palette.muted)
                Text(episode.title).font(.subheadline.weight(.medium)).lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }
}

// MARK: - Moi : profil, favoris, diagnostic, réglages

@MainActor
struct MeScreen: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var playback: Playback

    var body: some View {
        let live = store.favorites.filter { $0.kind == .live }
        let vod = store.favorites.filter { $0.kind != .live }
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 16) {
                        Avatar(profile: store.activeProfile, size: 56)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(store.activeProfile.name).font(.title3.weight(.semibold))
                            Text("Profil actif").font(.caption).foregroundStyle(Palette.muted)
                        }
                        Spacer()
                        Button("Changer") { store.profileChosen = false }
                            .buttonStyle(.bordered)
                    }
                    .padding(.vertical, 6)
                }

                Section("Chaînes favorites") {
                    if live.isEmpty {
                        Text("Glisse une chaîne vers la gauche pour l'ajouter.").font(.footnote).foregroundStyle(Palette.muted)
                    }
                    ForEach(Array(live.enumerated()), id: \.element.key) { i, s in
                        Button { playback.play(Repo.shared.liveQueue(live), at: i) } label: {
                            ChannelRow(number: i + 1, stream: s, favorite: true)
                        }
                        .buttonStyle(.plain)
                    }
                    .onDelete { idx in idx.map { live[$0] }.forEach(store.toggleFavorite) }
                }

                Section("Films et séries favoris") {
                    if vod.isEmpty {
                        Text("Touche « Ajouter aux favoris » sur une fiche.").font(.footnote).foregroundStyle(Palette.muted)
                    }
                    ForEach(vod, id: \.key) { s in
                        NavigationLink(value: s) {
                            HStack(spacing: 14) {
                                RemoteImage(url: s.logo, name: s.name, fill: true)
                                    .frame(width: 44, height: 66)
                                    .clipShape(RoundedRectangle(cornerRadius: 6))
                                VStack(alignment: .leading) {
                                    Text(s.name).lineLimit(1)
                                    Text(s.kind == .series ? "Série" : "Film").font(.caption).foregroundStyle(Palette.muted)
                                }
                            }
                        }
                    }
                    .onDelete { idx in idx.map { vod[$0] }.forEach(store.toggleFavorite) }
                }

                Section {
                    NavigationLink {
                        DiagnosticView()
                    } label: {
                        Label("Wi-Fi ou fournisseur ?", systemImage: "wifi")
                    }
                    NavigationLink {
                        SettingsForm()
                    } label: {
                        Label("Réglages", systemImage: "gearshape")
                    }
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Color.black)
            .navigationTitle("Moi")
            .navigationDestination(for: Stream.self) { s in
                if s.kind == .series { SeriesDetailView(stream: s) } else { MovieDetailView(stream: s) }
            }
        }
    }
}

// MARK: - Réglages

@MainActor
struct SettingsForm: View {
    @EnvironmentObject var store: Store
    @State private var refreshed = false
    @State private var editor: EditorTarget? = nil

    var body: some View {
        Form {
            Section("Compte") {
                switch store.account {
                case let .xtream(server, user, _):
                    LabeledContent("Type", value: "Xtream Codes")
                    LabeledContent("Identifiant", value: user)
                    LabeledContent("Serveur", value: normalizeServer(server))
                case let .m3u(url):
                    LabeledContent("Type", value: "Playlist M3U")
                    Text(url).font(.footnote).foregroundStyle(Palette.muted).lineLimit(2)
                default:
                    Text("Non connecté")
                }
            }

            Section("Profils") {
                ForEach(store.profiles) { p in
                    Button { editor = .edit(p) } label: {
                        HStack(spacing: 12) {
                            Avatar(profile: p, size: 30)
                            Text(p.name)
                            Spacer()
                            if p.id == store.activeProfileID {
                                Image(systemName: "checkmark").foregroundStyle(Palette.muted)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
                Button { editor = .new } label: {
                    Label("Ajouter un profil", systemImage: "plus")
                }
            }

            Section {
                Toggle("Son d'ouverture", isOn: $store.introSound)
                Toggle("Masquer les catégories adultes", isOn: $store.hideAdult)
                if case .xtream = store.account {
                    Picker("Format du direct", selection: $store.liveFormat) {
                        Text("HLS (recommandé)").tag("m3u8")
                        Text("TS").tag("ts")
                    }
                }
                Button(refreshed ? "Listes vidées, elles seront rechargées" : "Recharger les listes") {
                    Repo.shared.clearAll()
                    refreshed = true
                }
            } footer: {
                Text("Sur iPhone, le direct passe par HLS. Les films en MKV ou AVI ne sont pas lus par le lecteur d'Apple.")
            }

            Section {
                Button("Se déconnecter", role: .destructive) {
                    Repo.shared.clearAll()
                    store.logout()
                }
            } footer: {
                Text("Supprime le compte, les favoris et l'historique de cet iPhone. Les profils sont conservés.")
            }

            Section {
                LabeledContent("Version", value: "1.1")
            } footer: {
                Text("Orbis TV est un lecteur uniquement : il ne fournit, n'héberge et ne vend aucune chaîne ni aucun contenu.")
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.black)
        .navigationTitle("Réglages")
        .sheet(item: $editor) { target in
            ProfileEditor(target: target).environmentObject(store)
        }
    }
}
