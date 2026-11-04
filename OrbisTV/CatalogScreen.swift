import SwiftUI

/// Écran Direct / Films / Séries : catégories en bandeau, recherche, liste, mosaïque ou accueil « en grand ».
@MainActor
struct CatalogScreen: View {
    let kind: Kind
    @EnvironmentObject var store: Store
    @EnvironmentObject var playback: Playback

    @State private var state: LoadState<Catalog> = .loading
    @State private var selected = allID
    @State private var query = ""

    private var title: String {
        switch kind {
        case .live: return "TV en direct"
        case .movie: return "Films"
        case .series: return "Séries"
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                switch state {
                case .loading:
                    LoadingView()
                case .failed(let message):
                    ErrorView(message: message) { Task { await load(force: true) } }
                case .loaded(let catalog):
                    content(catalog)
                }
            }
            .background(Color.black)
            .navigationTitle(title)
            .toolbar {
                if kind == .live {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button { store.liveGrid.toggle() } label: {
                            Image(systemName: store.liveGrid ? "list.bullet" : "square.grid.2x2")
                        }
                        .accessibilityLabel(store.liveGrid ? "Afficher en liste" : "Afficher en mosaïque")
                    }
                }
            }
            .navigationDestination(for: Stream.self) { s in
                if s.kind == .series {
                    SeriesDetailView(stream: s)
                } else {
                    MovieDetailView(stream: s)
                }
            }
        }
        .task { await load(force: false) }
        .onChange(of: store.hideAdult) { _ in state = .loading; Task { await load(force: true) } }
    }

    @ViewBuilder
    private func content(_ catalog: Catalog) -> some View {
        let items = filtered(catalog)
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 8) {
                    ForEach(catalog.categories) { c in
                        Chip(title: c.name, selected: c.id == selected) { selected = c.id }
                    }
                }
                .padding(.horizontal, 16)
            }
            .frame(height: 48)

            if items.isEmpty {
                EmptyStateView(text: query.isEmpty
                    ? (kind == .series && catalog.streams.isEmpty ? "Aucune série (une playlist M3U n'a pas de fiches séries)." : "Rien dans cette catégorie.")
                    : "Aucun résultat pour « \(query) ».")
            } else if kind != .live && selected == allID && query.isEmpty {
                VodHome(catalog: catalog)
            } else if kind == .live && store.liveGrid {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 12)], spacing: 16) {
                        ForEach(Array(items.enumerated()), id: \.element.key) { i, s in
                            let fav = store.isFavorite(s)
                            Button {
                                playback.play(Repo.shared.liveQueue(items), at: i)
                            } label: {
                                ChannelTile(number: i + 1, stream: s, favorite: fav)
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button { store.toggleFavorite(s) } label: {
                                    Label(fav ? "Retirer des favoris" : "Ajouter aux favoris", systemImage: fav ? "star.slash" : "star")
                                }
                            }
                        }
                    }
                    .padding(16)
                }
            } else if kind == .live {
                List {
                    ForEach(Array(items.enumerated()), id: \.element.key) { i, s in
                        let fav = store.isFavorite(s)
                        Button {
                            playback.play(Repo.shared.liveQueue(items), at: i)
                        } label: {
                            ChannelRow(number: i + 1, stream: s, favorite: fav)
                        }
                        .buttonStyle(.plain)
                        .listRowBackground(Color.black)
                        .listRowSeparatorTint(Palette.surface2)
                        .swipeActions {
                            Button { store.toggleFavorite(s) } label: {
                                Label(fav ? "Retirer" : "Favori", systemImage: fav ? "star.slash" : "star")
                            }
                            .tint(fav ? .gray : Palette.brand)
                        }
                        .contextMenu {
                            Button { store.toggleFavorite(s) } label: {
                                Label(fav ? "Retirer des favoris" : "Ajouter aux favoris", systemImage: fav ? "star.slash" : "star")
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 108), spacing: 12)], spacing: 18) {
                        ForEach(items, id: \.key) { s in
                            NavigationLink(value: s) { PosterCell(stream: s) }
                                .buttonStyle(.plain)
                        }
                    }
                    .padding(16)
                }
            }
        }
        .searchable(text: $query, prompt: "Rechercher")
        .refreshable { await load(force: true) }
    }

    private func filtered(_ catalog: Catalog) -> [Stream] {
        let q = query.trimmingCharacters(in: .whitespaces)
        if q.isEmpty { return catalog.streams(in: selected) }
        return catalog.streams.filter { $0.name.range(of: q, options: [.caseInsensitive, .diacriticInsensitive]) != nil }
    }

    private func load(force: Bool) async {
        if !force, case .loaded = state { return }
        if force { Repo.shared.invalidate(kind) }
        do {
            state = .loaded(try await Repo.shared.catalog(kind))
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}

// MARK: - Accueil Films / Séries « en grand »

private struct CategoryRow: Identifiable {
    let id: String
    let name: String
    let items: [Stream]
}

@MainActor
private struct VodHome: View {
    let catalog: Catalog

    var body: some View {
        let recent = catalog.recent(20)
        let rows: [CategoryRow] = catalog.categories.filter { $0.id != allID }.prefix(16).compactMap { c in
            let list = Array(catalog.streams(in: c.id).prefix(20))
            return list.isEmpty ? nil : CategoryRow(id: c.id, name: c.name, items: list)
        }
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 26) {
                if let featured = recent.first {
                    VodHero(stream: featured)
                }
                PosterRow(title: "Ajoutés récemment", items: recent)
                ForEach(rows) { row in
                    PosterRow(title: row.name, items: row.items)
                }
            }
            .padding(.bottom, 24)
        }
    }
}

@MainActor
private struct VodHero: View {
    let stream: Stream
    @State private var backdrop: String? = nil
    @State private var plot: String? = nil

    var body: some View {
        NavigationLink(value: stream) {
            ZStack(alignment: .bottomLeading) {
                RemoteImage(url: backdrop ?? stream.logo, name: stream.name, fill: true)
                    .frame(height: 360)
                    .frame(maxWidth: .infinity)
                    .blur(radius: backdrop == nil ? 18 : 0)
                    .overlay(LinearGradient(colors: [.clear, .black.opacity(0.6), .black], startPoint: .top, endPoint: .bottom))
                HStack(alignment: .bottom, spacing: 16) {
                    RemoteImage(url: stream.logo, name: stream.name, fill: true)
                        .frame(width: 104, height: 156)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .shadow(radius: 12)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("À LA UNE").font(.caption2.weight(.semibold)).tracking(2).foregroundStyle(Palette.muted)
                        Text(stream.name).font(.system(size: 26, weight: .medium)).lineLimit(2)
                        if let plot {
                            Text(plot).font(.caption).foregroundStyle(Color.white.opacity(0.8)).lineLimit(3)
                        }
                        HStack(spacing: 6) {
                            Image(systemName: "play.fill")
                            Text(stream.kind == .series ? "Voir les épisodes" : "Voir le film")
                        }
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Palette.ink)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 9)
                        .background(Palette.light, in: RoundedRectangle(cornerRadius: 8))
                    }
                }
                .padding(16)
            }
            .clipped()
        }
        .buttonStyle(.plain)
        .task(id: stream.key) {
            if stream.kind == .movie, stream.url == nil, let info = try? await Repo.shared.vodInfo(stream.id) {
                backdrop = info.backdrop
                plot = info.plot
            } else if stream.kind == .series, let info = try? await Repo.shared.seriesInfo(stream.id) {
                backdrop = info.backdrop
                plot = info.plot ?? stream.plot
            }
        }
    }
}

private struct PosterRow: View {
    let title: String
    let items: [Stream]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.headline).padding(.horizontal, 16)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) {
                    ForEach(items, id: \.key) { s in
                        NavigationLink(value: s) { PosterCell(stream: s).frame(width: 112) }
                            .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

// MARK: - Cellules

struct ChannelRow: View {
    let number: Int
    let stream: Stream
    let favorite: Bool

    var body: some View {
        HStack(spacing: 14) {
            Text("\(number)")
                .font(.footnote.monospacedDigit())
                .foregroundStyle(Palette.muted)
                .frame(width: 34, alignment: .leading)
            RemoteImage(url: stream.logo, name: stream.name, background: tileTone(stream.name))
                .frame(width: 60, height: 36)
                .clipShape(RoundedRectangle(cornerRadius: 6))
            VStack(alignment: .leading, spacing: 3) {
                Text(stream.name).font(.body).lineLimit(1)
                EpgNowLine(stream: stream)
            }
            Spacer(minLength: 8)
            if favorite {
                Image(systemName: "star.fill").font(.caption).foregroundStyle(Palette.brand)
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }
}

/// Tuile de chaîne : logo en grand sur une teinte propre à la chaîne.
struct ChannelTile: View {
    let number: Int
    let stream: Stream
    let favorite: Bool

    var body: some View {
        let tone = tileTone(stream.name)
        VStack(alignment: .leading, spacing: 6) {
            ZStack {
                RadialGradient(colors: [tone.opacity(0.75), tone], center: .center, startRadius: 0, endRadius: 140)
                RemoteImage(url: stream.logo, name: stream.name, background: .clear, initialsFont: .title2.weight(.semibold))
                    .padding(.horizontal, 20)
                    .padding(.vertical, 14)
            }
            .aspectRatio(16.0 / 10.0, contentMode: .fit)
            .overlay(alignment: .topLeading) {
                Text("\(number)").font(.caption2).foregroundStyle(Color.white.opacity(0.6)).padding(7)
            }
            .overlay(alignment: .topTrailing) {
                if favorite {
                    Image(systemName: "star.fill").font(.caption2).foregroundStyle(Palette.brand).padding(7)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 10))
            Text(stream.name).font(.caption).lineLimit(1)
            EpgNowLine(stream: stream)
        }
        .contentShape(Rectangle())
    }
}

struct PosterCell: View {
    let stream: Stream
    @EnvironmentObject var store: Store

    var body: some View {
        let progress = store.progress(stream.key)
        VStack(alignment: .leading, spacing: 6) {
            RemoteImage(url: stream.logo, name: stream.name, fill: true)
                .aspectRatio(2.0 / 3.0, contentMode: .fit)
                .overlay(alignment: .bottom) {
                    if progress > 0 { ProgressLine(value: progress) }
                }
                .clipShape(RoundedRectangle(cornerRadius: 8))
            Text(stream.name)
                .font(.caption)
                .lineLimit(2, reservesSpace: true)
        }
    }
}
