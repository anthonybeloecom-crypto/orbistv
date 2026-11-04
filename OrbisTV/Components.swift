import SwiftUI

enum Palette {
    static let bg = Color.black
    static let surface = Color(white: 0.06)
    static let surface2 = Color(white: 0.12)
    static let muted = Color(white: 0.62)
    static let light = Color(white: 0.957)
    static let ink = Color(red: 0.09, green: 0.10, blue: 0.125)
    static let brand = Color(red: 0.545, green: 0.424, blue: 1.0)
}

/// Logo Orbis : anneau blanc + satellite violet.
struct OrbisMark: View {
    var size: CGFloat = 24
    var body: some View {
        ZStack {
            Circle().stroke(Color.white, lineWidth: size * 0.11).padding(size * 0.16)
            Circle().fill(Palette.brand)
                .frame(width: size * 0.22, height: size * 0.22)
                .offset(x: size * 0.28, y: -size * 0.28)
        }
        .frame(width: size, height: size)
    }
}

struct Wordmark: View {
    var size: CGFloat = 17
    var body: some View {
        HStack(spacing: 10) {
            OrbisMark(size: size * 1.4)
            Text("ORBIS").font(.system(size: size, weight: .semibold)).tracking(size * 0.32)
        }
    }
}

struct PrimaryButton: View {
    let title: String
    var icon: String? = nil
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon { Image(systemName: icon) }
                Text(title).fontWeight(.medium)
            }
            .frame(maxWidth: .infinity, minHeight: 46)
            .foregroundStyle(Palette.ink)
            .background(Palette.light, in: RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }
}

struct SecondaryButton: View {
    let title: String
    var icon: String? = nil
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon { Image(systemName: icon) }
                Text(title).fontWeight(.medium)
            }
            .frame(maxWidth: .infinity, minHeight: 46)
            .foregroundStyle(.white)
            .background(Palette.surface2, in: RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }
}

struct Chip: View {
    let title: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(selected ? .semibold : .regular))
                .lineLimit(1)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .foregroundStyle(selected ? Color.black : Color.white)
                .background(selected ? Color.white : Palette.surface2, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Teinte sombre stable par chaîne (même nom = même couleur).
func tileTone(_ name: String) -> Color {
    let tones: [(Double, Double, Double)] = [
        (0.078, 0.149, 0.227), (0.114, 0.231, 0.165), (0.227, 0.114, 0.157), (0.180, 0.227, 0.102),
        (0.227, 0.192, 0.102), (0.165, 0.114, 0.227), (0.102, 0.180, 0.227), (0.227, 0.141, 0.102),
    ]
    let h = Int(stableHash(name).prefix(6), radix: 16) ?? 0
    let t = tones[h % tones.count]
    return Color(red: t.0, green: t.1, blue: t.2)
}

struct RemoteImage: View {
    let url: String?
    let name: String
    var fill = false
    var background: Color = Palette.surface2
    var initialsFont: Font = .caption.bold()

    var body: some View {
        ZStack {
            background
            Text(initials(name)).font(initialsFont).foregroundStyle(Color.white.opacity(0.8))
            if let u = url, let link = URL(string: u) {
                AsyncImage(url: link) { phase in
                    if let image = phase.image {
                        image.resizable().aspectRatio(contentMode: fill ? .fill : .fit)
                    }
                }
            }
        }
        .clipped()
    }

    private func initials(_ s: String) -> String {
        let parts = s.split(whereSeparator: { " -|:".contains($0) }).prefix(2)
        let r = parts.compactMap { $0.first.map { String($0).uppercased() } }.joined()
        return r.isEmpty ? "?" : r
    }
}

struct LoadingView: View {
    var text = "Chargement…"
    var body: some View {
        VStack(spacing: 14) {
            ProgressView().tint(.white)
            Text(text).font(.subheadline).foregroundStyle(Palette.muted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct ErrorView: View {
    let message: String
    let retry: () -> Void
    var body: some View {
        VStack(spacing: 14) {
            Text("Oups").font(.title2.weight(.semibold))
            Text(message).font(.subheadline).foregroundStyle(Palette.muted).multilineTextAlignment(.center)
            PrimaryButton(title: "Réessayer", icon: "arrow.clockwise", action: retry).frame(maxWidth: 240)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct EmptyStateView: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(Palette.muted)
            .multilineTextAlignment(.center)
            .padding(32)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct ProgressLine: View {
    let value: Double
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Rectangle().fill(Color.white.opacity(0.25))
                Rectangle().fill(Color.white).frame(width: geo.size.width * value)
            }
        }
        .frame(height: 3)
    }
}

// MARK: - Profils

let profileColors: [Color] = [
    Color(red: 0.93, green: 0.36, blue: 0.20),
    Color(red: 0.27, green: 0.47, blue: 0.96),
    Color(red: 0.15, green: 0.68, blue: 0.43),
    Color(red: 0.95, green: 0.66, blue: 0.13),
    Color(red: 0.55, green: 0.42, blue: 1.00),
    Color(red: 0.90, green: 0.27, blue: 0.47),
    Color(red: 0.13, green: 0.68, blue: 0.75),
]

struct Avatar: View {
    let profile: Profile
    var size: CGFloat = 64

    var body: some View {
        ZStack {
            Circle().fill(profileColors[profile.color % profileColors.count])
            Text(String(profile.name.prefix(1)).uppercased())
                .font(.system(size: size * 0.42, weight: .semibold))
                .foregroundStyle(.white)
        }
        .frame(width: size, height: size)
    }
}

/// Ligne « en ce moment » du guide TV sous le nom d'une chaîne (chargée à l'affichage).
struct EpgNowLine: View {
    let stream: Stream
    @State private var now: EpgEntry? = nil

    var body: some View {
        Group {
            if let now {
                VStack(alignment: .leading, spacing: 3) {
                    Text(now.title).font(.caption).foregroundStyle(Palette.muted).lineLimit(1)
                    ProgressLine(value: now.progress()).frame(width: 90).clipShape(Capsule())
                }
            }
        }
        .task(id: stream.key) {
            let list = await Repo.shared.epg(for: stream, limit: 2)
            now = list.first { $0.isNow } ?? list.first
        }
    }
}
