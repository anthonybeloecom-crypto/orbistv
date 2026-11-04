import SwiftUI

/// Guide des programmes : chaque chaîne avec ce qui passe maintenant et ensuite.
@MainActor
struct GuideScreen: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var playback: Playback
    @State private var state: LoadState<Catalog> = .loading
    @State private var selected = allID

    var body: some View {
        NavigationStack {
            Group {
                if !Repo.shared.isXtream {
                    EmptyStateView(text: "Le guide TV est disponible avec un compte Xtream Codes.\nAvec un lien get.php, reconnecte-toi : l'app bascule toute seule en Xtream.")
                } else {
                    switch state {
                    case .loading:
                        LoadingView(text: "Chargement du guide…")
                    case .failed(let message):
                        ErrorView(message: message) { Task { await load() } }
                    case .loaded(let catalog):
                        content(catalog)
                    }
                }
            }
            .background(Color.black)
            .navigationTitle("Guide TV")
        }
        .task { if case .loading = state { await load() } }
    }

    @ViewBuilder
    private func content(_ catalog: Catalog) -> some View {
        let channels = catalog.streams(in: selected)
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

            List {
                ForEach(Array(channels.enumerated()), id: \.element.key) { i, s in
                    GuideRow(stream: s) {
                        playback.play(Repo.shared.liveQueue(channels), at: i)
                    }
                    .listRowBackground(Color.black)
                    .listRowSeparatorTint(Palette.surface2)
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 0))
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }

    private func load() async {
        do {
            state = .loaded(try await Repo.shared.catalog(.live))
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}

private struct GuideRow: View {
    let stream: Stream
    let onPlay: () -> Void
    @State private var entries: [EpgEntry] = []
    @State private var loaded = false

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onPlay) {
                VStack(spacing: 6) {
                    RemoteImage(url: stream.logo, name: stream.name, background: tileTone(stream.name))
                        .frame(width: 64, height: 40)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                    Text(stream.name).font(.caption2).lineLimit(1).frame(width: 72)
                }
            }
            .buttonStyle(.plain)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    if entries.isEmpty {
                        Text(loaded ? "Pas de programme disponible" : "…")
                            .font(.caption)
                            .foregroundStyle(Palette.muted)
                            .padding(.horizontal, 12)
                            .frame(height: 62)
                    }
                    ForEach(entries) { e in
                        Button(action: { if e.isNow { onPlay() } }) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(e.timeRange).font(.caption2).opacity(0.7)
                                Text(e.title).font(.footnote.weight(.semibold)).lineLimit(2)
                                if e.isNow {
                                    ProgressLine(value: e.progress()).clipShape(Capsule())
                                }
                            }
                            .padding(10)
                            .frame(width: blockWidth(e), height: 62, alignment: .topLeading)
                            .foregroundStyle(e.isNow ? Color.black : Color.white)
                            .background(e.isNow ? Color.white : Palette.surface2, in: RoundedRectangle(cornerRadius: 8))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.trailing, 16)
            }
        }
        .task(id: stream.key) {
            entries = await Repo.shared.epg(for: stream, limit: 6)
            loaded = true
        }
    }

    /// Largeur proportionnelle à la durée restante (2,6 pt par minute, bornée).
    private func blockWidth(_ e: EpgEntry) -> CGFloat {
        let from = max(e.start, Date())
        let minutes = e.end.timeIntervalSince(from) / 60
        return CGFloat(min(max(minutes * 2.6, 120), 320))
    }
}
