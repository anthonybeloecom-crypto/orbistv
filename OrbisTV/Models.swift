import Foundation

let allID = "__all__"

/// Identifiant envoyé aux serveurs. Certains fournisseurs refusent les lecteurs qu'ils ne connaissent pas :
/// l'app essaie alors un identifiant de lecteur standard et retient celui qui fonctionne.
enum Net {
    static let candidates = [
        "OrbisTV/1.0 (iOS)",
        "VLC/3.0.20 LibVLC/3.0.20",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
    ]
    static var userAgent: String = UserDefaults.standard.string(forKey: "user_agent") ?? candidates[0] {
        didSet { UserDefaults.standard.set(userAgent, forKey: "user_agent") }
    }
}

enum Kind: String, Codable, CaseIterable {
    case live, movie, series
}

struct Category: Identifiable, Hashable {
    let id: String
    let name: String
}

struct Stream: Hashable, Codable {
    let id: String
    let name: String
    let logo: String?
    let categoryId: String
    let kind: Kind
    /// URL directe (M3U). nil pour Xtream : construite à la lecture.
    var url: String? = nil
    var ext: String? = nil
    var rating: String? = nil
    var plot: String? = nil
    /// Date d'ajout chez le fournisseur (timestamp), pour « Ajoutés récemment ».
    var added: Int? = nil

    var key: String { "\(kind.rawValue):\(id)" }
}

struct Catalog {
    let categories: [Category]
    let streams: [Stream]
    private let byCategory: [String: [Stream]]

    init(categories: [Category], streams: [Stream]) {
        self.categories = categories
        self.streams = streams
        self.byCategory = Dictionary(grouping: streams, by: { $0.categoryId })
    }

    func streams(in categoryId: String) -> [Stream] {
        categoryId == allID ? streams : (byCategory[categoryId] ?? [])
    }

    /// Les plus récents d'abord (si le fournisseur donne la date d'ajout).
    func recent(_ limit: Int) -> [Stream] {
        let dated = streams.filter { ($0.added ?? 0) > 0 }
        guard !dated.isEmpty else { return Array(streams.prefix(limit)) }
        return Array(dated.sorted { ($0.added ?? 0) > ($1.added ?? 0) }.prefix(limit))
    }
}

struct Episode: Hashable {
    let id: String
    let title: String
    let season: Int
    let number: Int
    let ext: String
    let image: String?
    let plot: String?
    var key: String { "ep:\(id)" }
}

struct SeriesInfo {
    let plot: String?
    let cover: String?
    let genre: String?
    let year: String?
    let backdrop: String?
    let seasons: [Int: [Episode]]

    var seasonNumbers: [Int] { seasons.keys.sorted() }
    var allEpisodes: [Episode] { seasonNumbers.flatMap { seasons[$0] ?? [] } }
}

struct VodInfo {
    var plot: String? = nil
    var genre: String? = nil
    var duration: String? = nil
    var year: String? = nil
    var cast: String? = nil
    var backdrop: String? = nil
}

/// Un programme du guide TV.
struct EpgEntry: Identifiable, Hashable {
    let id: String
    let title: String
    let desc: String?
    let start: Date
    let end: Date

    func progress(at now: Date = Date()) -> Double {
        let total = end.timeIntervalSince(start)
        guard total > 0 else { return 0 }
        return min(max(now.timeIntervalSince(start) / total, 0), 1)
    }

    var isNow: Bool { let n = Date(); return start <= n && n < end }

    var timeRange: String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return "\(f.string(from: start)) – \(f.string(from: end))"
    }
}

struct Profile: Codable, Identifiable, Hashable {
    let id: String
    var name: String
    var color: Int
}

enum Account: Codable, Equatable {
    case xtream(server: String, user: String, pass: String)
    case m3u(url: String)
}

func normalizeServer(_ raw: String) -> String {
    var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    while s.hasSuffix("/") { s.removeLast() }
    if !s.lowercased().hasPrefix("http://") && !s.lowercased().hasPrefix("https://") { s = "http://" + s }
    for suffix in ["/player_api.php", "/get.php"] where s.hasSuffix(suffix) {
        s.removeLast(suffix.count)
    }
    while s.hasSuffix("/") { s.removeLast() }
    return s
}

/// Un lien M3U de type http://serveur/get.php?username=…&password=… cache un compte Xtream Codes :
/// on le convertit pour utiliser l'API Xtream (plus fiable, et avec les séries).
func xtreamAccount(fromM3U link: String) -> Account? {
    guard let c = URLComponents(string: link.trimmingCharacters(in: .whitespacesAndNewlines)),
          c.path.lowercased().hasSuffix("get.php"),
          let host = c.host,
          let user = c.queryItems?.first(where: { $0.name == "username" })?.value, !user.isEmpty,
          let pass = c.queryItems?.first(where: { $0.name == "password" })?.value, !pass.isEmpty
    else { return nil }
    var server = "\(c.scheme ?? "http")://\(host)"
    if let port = c.port { server += ":\(port)" }
    return .xtream(server: server, user: user, pass: pass)
}

struct PlayItem {
    let key: String
    let title: String
    let url: String
    let isLive: Bool
    var subtitle: String? = nil
    /// nil = reprise auto, 0 = depuis le début, > 0 = position en secondes.
    var startAt: Double? = nil
}

struct OrbisError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

enum LoadState<T> {
    case loading
    case loaded(T)
    case failed(String)
}

/// Hash stable (FNV-1a) pour donner un identifiant fixe aux entrées M3U.
func stableHash(_ s: String) -> String {
    var h: UInt64 = 0xcbf29ce484222325
    for b in s.utf8 {
        h ^= UInt64(b)
        h = h &* 0x100000001b3
    }
    return String(h, radix: 16)
}

func formatTime(_ seconds: Double) -> String {
    let s = Int(seconds)
    let h = s / 3600, m = (s % 3600) / 60, sec = s % 60
    return h > 0 ? String(format: "%d:%02d:%02d", h, m, sec) : String(format: "%d:%02d", m, sec)
}
