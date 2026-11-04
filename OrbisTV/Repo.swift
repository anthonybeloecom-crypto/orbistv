import Foundation

/// Accès à l'abonnement de l'utilisateur (Xtream Codes ou M3U). Orbis TV ne fournit aucun contenu.
@MainActor
final class Repo {
    static let shared = Repo()

    private var cache: [Kind: Catalog] = [:]
    private var seriesCache: [String: SeriesInfo] = [:]
    private var epgCache: [String: (Date, [EpgEntry])] = [:]
    private var m3uEntries: [M3UEntry]? = nil

    private let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 30
        c.timeoutIntervalForResource = 120
        return URLSession(configuration: c)
    }()

    private var store: Store { Store.shared }

    var isXtream: Bool { if case .xtream = store.account { return true } else { return false } }

    func clearAll() {
        cache = [:]
        seriesCache = [:]
        epgCache = [:]
        m3uEntries = nil
    }

    func invalidate(_ kind: Kind) {
        cache[kind] = nil
        if case .m3u = store.account { m3uEntries = nil }
    }

    // MARK: Connexion

    func login(_ account: Account) async throws {
        clearAll()
        switch account {
        case let .xtream(server, user, pass):
            guard !server.trimmingCharacters(in: .whitespaces).isEmpty else { throw OrbisError("Indique l'adresse du serveur") }
            guard !user.isEmpty, !pass.isEmpty else { throw OrbisError("Identifiant et mot de passe requis") }
            let data = try await get(api(server: server, user: user, pass: pass))
            guard let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
                throw OrbisError("Ce serveur ne répond pas comme un serveur Xtream Codes")
            }
            guard let info = json["user_info"] as? [String: Any] else { throw OrbisError("Réponse inattendue du serveur") }
            guard str(info["auth"]) == "1" else { throw OrbisError("Identifiants refusés par le serveur") }
            if let status = str(info["status"]), status.lowercased() != "active" {
                throw OrbisError("Abonnement non actif (\(status))")
            }
            store.setAccount(account)

        case let .m3u(rawURL):
            let url = rawURL.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !url.isEmpty else { throw OrbisError("Colle le lien de ta playlist M3U") }
            // Lien get.php avec identifiants : on passe par l'API Xtream, le M3U reste en secours.
            var xtreamError: Error? = nil
            if let x = xtreamAccount(fromM3U: url) {
                do {
                    try await login(x)
                    return
                } catch {
                    xtreamError = error
                    clearAll()
                }
            }
            let data: Data
            do {
                data = try await get(url)
            } catch {
                throw xtreamError ?? error
            }
            let entries = await Task.detached { parseM3U(String(decoding: data, as: UTF8.self)) }.value
            guard !entries.isEmpty else { throw OrbisError("Aucune entrée trouvée dans cette playlist") }
            m3uEntries = entries
            store.setAccount(.m3u(url: url))
        }
    }

    // MARK: Catalogues

    func catalog(_ kind: Kind) async throws -> Catalog {
        if let c = cache[kind] { return c }
        guard let account = store.account else { throw OrbisError("Non connecté") }
        let raw: Catalog
        switch account {
        case let .xtream(server, user, pass):
            let actions: (String, String)
            switch kind {
            case .live: actions = ("get_live_categories", "get_live_streams")
            case .movie: actions = ("get_vod_categories", "get_vod_streams")
            case .series: actions = ("get_series_categories", "get_series")
            }
            let catData = try await get(api(server: server, user: user, pass: pass, ["action": actions.0]))
            let streamData = try await get(api(server: server, user: user, pass: pass, ["action": actions.1]))
            raw = await Task.detached { parseXtream(kind: kind, categories: catData, streams: streamData) }.value
        case let .m3u(url):
            let entries: [M3UEntry]
            if let e = m3uEntries {
                entries = e
            } else {
                let data = try await get(url)
                entries = await Task.detached { parseM3U(String(decoding: data, as: UTF8.self)) }.value
                m3uEntries = entries
            }
            raw = await Task.detached { m3uCatalog(entries, kind: kind) }.value
        }
        let hide = store.hideAdult
        let result = await Task.detached { finalize(raw, hideAdult: hide) }.value
        cache[kind] = result
        return result
    }

    func vodInfo(_ id: String) async throws -> VodInfo {
        guard case let .xtream(server, user, pass) = store.account else { return VodInfo() }
        let data = try await get(api(server: server, user: user, pass: pass, ["action": "get_vod_info", "vod_id": id]))
        guard let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let info = json["info"] as? [String: Any] else { return VodInfo() }
        return VodInfo(
            plot: str(info["plot"]) ?? str(info["description"]),
            genre: str(info["genre"]),
            duration: str(info["duration"]),
            year: (str(info["releasedate"]) ?? str(info["release_date"])).map { String($0.prefix(4)) },
            cast: str(info["cast"]) ?? str(info["actors"]),
            backdrop: (info["backdrop_path"] as? [Any])?.first.flatMap { str($0) }
        )
    }

    func seriesInfo(_ id: String) async throws -> SeriesInfo {
        if let s = seriesCache[id] { return s }
        guard case let .xtream(server, user, pass) = store.account else {
            throw OrbisError("Séries disponibles uniquement avec Xtream Codes")
        }
        let data = try await get(api(server: server, user: user, pass: pass, ["action": "get_series_info", "series_id": id]))
        let info = await Task.detached { parseSeriesInfo(data) }.value
        seriesCache[id] = info
        return info
    }

    // MARK: Guide TV

    /// Programmes en cours et à venir d'une chaîne (API Xtream). Vide pour les playlists M3U.
    func epg(for s: Stream, limit: Int = 6) async -> [EpgEntry] {
        guard s.kind == .live, case let .xtream(server, user, pass) = store.account else { return [] }
        if let cached = epgCache[s.id], Date().timeIntervalSince(cached.0) < 600 {
            return cached.1.filter { $0.end > Date() }
        }
        guard let data = try? await get(api(server: server, user: user, pass: pass,
                                             ["action": "get_short_epg", "stream_id": s.id, "limit": "\(limit)"]), fallback: false)
        else { return [] }
        let list = await Task.detached { parseEPG(data) }.value
        epgCache[s.id] = (Date(), list)
        return list.filter { $0.end > Date() }
    }

    // MARK: URLs de lecture

    func playURL(_ s: Stream, format: String? = nil) -> String? {
        if let u = s.url { return u }
        guard case let .xtream(server, user, pass) = store.account else { return nil }
        let base = normalizeServer(server)
        switch s.kind {
        case .live: return "\(base)/live/\(user)/\(pass)/\(s.id).\(format ?? store.liveFormat)"
        case .movie: return "\(base)/movie/\(user)/\(pass)/\(s.id).\(s.ext ?? "mp4")"
        case .series: return nil
        }
    }

    func episodeURL(_ e: Episode) -> String? {
        guard case let .xtream(server, user, pass) = store.account else { return nil }
        return "\(normalizeServer(server))/series/\(user)/\(pass)/\(e.id).\(e.ext)"
    }

    func liveQueue(_ channels: [Stream]) -> [PlayItem] {
        channels.enumerated().compactMap { i, s in
            guard let u = playURL(s) else { return nil }
            return PlayItem(key: s.key, title: s.name, url: u, isLive: true, subtitle: "\(i + 1)")
        }
    }

    // MARK: Diagnostic

    /// Temps de réponse d'une adresse, en millisecondes (nil si injoignable).
    func ping(_ urlString: String) async -> (ms: Int?, status: Int) {
        guard let url = URL(string: urlString) else { return (nil, 0) }
        var req = URLRequest(url: url, timeoutInterval: 8)
        req.setValue(Net.userAgent, forHTTPHeaderField: "User-Agent")
        let t0 = Date()
        do {
            let (_, resp) = try await session.data(for: req)
            let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
            return (Int(Date().timeIntervalSince(t0) * 1000), code)
        } catch {
            return (nil, 0)
        }
    }

    /// Adresse de l'API du fournisseur, pour le diagnostic.
    var serverTestURL: String? {
        switch store.account {
        case let .xtream(server, user, pass): return api(server: server, user: user, pass: pass)
        case let .m3u(url): return url
        default: return nil
        }
    }

    /// Débit réel sur un flux, en Mbit/s (télécharge quelques secondes puis s'arrête).
    func throughput(_ urlString: String, seconds: Double = 4) async -> Double? {
        guard let url = URL(string: urlString) else { return nil }
        return await SpeedProbe().run(url: url, seconds: seconds)
    }

    // MARK: Réseau

    private func api(server: String, user: String, pass: String, _ params: [String: String] = [:]) -> String {
        var c = URLComponents(string: normalizeServer(server) + "/player_api.php")
        var items = [URLQueryItem(name: "username", value: user), URLQueryItem(name: "password", value: pass)]
        for (k, v) in params.sorted(by: { $0.key < $1.key }) { items.append(URLQueryItem(name: k, value: v)) }
        c?.queryItems = items
        return c?.url?.absoluteString ?? ""
    }

    /// Télécharge une adresse. Si le serveur refuse l'identifiant de l'app (403, 884…),
    /// on réessaie avec un identifiant de lecteur standard et on retient celui qui marche.
    private func get(_ urlString: String, fallback: Bool = true) async throws -> Data {
        guard let url = URL(string: urlString), url.scheme != nil else { throw OrbisError("Adresse invalide") }
        let agents = fallback ? [Net.userAgent] + Net.candidates.filter { $0 != Net.userAgent } : [Net.userAgent]
        var lastStatus = 0
        for agent in agents {
            var req = URLRequest(url: url)
            req.setValue(agent, forHTTPHeaderField: "User-Agent")
            let (data, response) = try await fetch(req)
            let code = (response as? HTTPURLResponse)?.statusCode ?? 200
            if (200..<300).contains(code) {
                if agent != Net.userAgent { Net.userAgent = agent }
                return data
            }
            lastStatus = code
            // Une adresse introuvable ne dépend pas de l'identifiant : inutile de réessayer.
            if code == 404 { break }
        }
        throw OrbisError(httpMessage(lastStatus))
    }

    private func fetch(_ req: URLRequest) async throws -> (Data, URLResponse) {
        do {
            return try await session.data(for: req)
        } catch let e as URLError {
            switch e.code {
            case .cannotFindHost, .dnsLookupFailed: throw OrbisError("Serveur introuvable. Vérifie l'adresse.")
            case .timedOut: throw OrbisError("Le serveur met trop de temps à répondre.")
            case .notConnectedToInternet: throw OrbisError("Pas de connexion Internet.")
            case .cannotConnectToHost: throw OrbisError("Impossible de joindre le serveur. Vérifie l'adresse et le port.")
            default: throw OrbisError(e.localizedDescription)
            }
        }
    }

    private func httpMessage(_ code: Int) -> String {
        switch code {
        case 401, 403, 884:
            return "Le serveur refuse la connexion (code \(code)). Vérifie que ton abonnement est actif et qu'il n'est pas déjà utilisé sur un autre appareil."
        case 404:
            return "Adresse introuvable sur le serveur (404). Vérifie le lien."
        case 500...599:
            return "Le serveur de ton fournisseur a un problème (code \(code)). Réessaie dans quelques minutes."
        default:
            return "Le serveur a répondu avec le code \(code)."
        }
    }
}

/// Mesure de débit : compte les octets reçus pendant quelques secondes puis coupe.
final class SpeedProbe: NSObject, URLSessionDataDelegate {
    private var bytes = 0
    private var start = Date()
    private var limit: Double = 4
    private var continuation: CheckedContinuation<Double?, Never>?
    private var session: URLSession?
    private var finished = false

    func run(url: URL, seconds: Double) async -> Double? {
        limit = seconds
        return await withCheckedContinuation { cont in
            continuation = cont
            let s = URLSession(configuration: .ephemeral, delegate: self, delegateQueue: nil)
            session = s
            var req = URLRequest(url: url, timeoutInterval: seconds + 6)
            req.setValue(Net.userAgent, forHTTPHeaderField: "User-Agent")
            start = Date()
            s.dataTask(with: req).resume()
            DispatchQueue.global().asyncAfter(deadline: .now() + seconds + 5) { [weak self] in self?.finish() }
        }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        bytes += data.count
        if Date().timeIntervalSince(start) >= limit { finish() }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        finish()
    }

    private func finish() {
        objc_sync_enter(self)
        defer { objc_sync_exit(self) }
        guard !finished else { return }
        finished = true
        session?.invalidateAndCancel()
        let elapsed = max(Date().timeIntervalSince(start), 0.5)
        let mbps = bytes > 50_000 ? Double(bytes) * 8 / elapsed / 1_000_000 : nil
        continuation?.resume(returning: mbps)
        continuation = nil
    }
}

// MARK: - Parsing (hors thread principal)

struct M3UEntry {
    let name: String
    let group: String
    let url: String
    let logo: String?
}

func str(_ v: Any?) -> String? {
    switch v {
    case let s as String:
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        return (t.isEmpty || t == "null") ? nil : t
    case let n as NSNumber:
        return n.stringValue
    default:
        return nil
    }
}

private func jsonArray(_ data: Data) -> [[String: Any]] {
    (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] ?? []
}

func parseXtream(kind: Kind, categories: Data, streams: Data) -> Catalog {
    let cats: [Category] = jsonArray(categories).compactMap { o in
        guard let id = str(o["category_id"]) else { return nil }
        return Category(id: id, name: str(o["category_name"]) ?? "Sans nom")
    }
    let items: [Stream] = jsonArray(streams).compactMap { o in
        switch kind {
        case .live:
            guard let id = str(o["stream_id"]) else { return nil }
            return Stream(id: id, name: str(o["name"]) ?? "Sans nom", logo: str(o["stream_icon"]),
                          categoryId: str(o["category_id"]) ?? "", kind: .live)
        case .movie:
            guard let id = str(o["stream_id"]) else { return nil }
            return Stream(id: id, name: str(o["name"]) ?? "Sans nom", logo: str(o["stream_icon"]),
                          categoryId: str(o["category_id"]) ?? "", kind: .movie,
                          ext: str(o["container_extension"]), rating: str(o["rating"]).flatMap { $0 == "0" ? nil : $0 },
                          added: Int(str(o["added"]) ?? ""))
        case .series:
            guard let id = str(o["series_id"]) else { return nil }
            return Stream(id: id, name: str(o["name"]) ?? "Sans nom", logo: str(o["cover"]),
                          categoryId: str(o["category_id"]) ?? "", kind: .series,
                          rating: str(o["rating"]).flatMap { $0 == "0" ? nil : $0 }, plot: str(o["plot"]),
                          added: Int(str(o["last_modified"]) ?? ""))
        }
    }
    return Catalog(categories: cats, streams: items)
}

func parseEPG(_ data: Data) -> [EpgEntry] {
    guard let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
          let list = json["epg_listings"] as? [[String: Any]] else { return [] }
    func decode(_ v: Any?) -> String? {
        guard let s = str(v) else { return nil }
        if let d = Data(base64Encoded: s), let t = String(data: d, encoding: .utf8), !t.isEmpty { return t }
        return s
    }
    return list.compactMap { e in
        guard let start = Double(str(e["start_timestamp"]) ?? ""),
              let end = Double(str(e["stop_timestamp"]) ?? str(e["end_timestamp"]) ?? "") else { return nil }
        return EpgEntry(
            id: str(e["id"]) ?? "\(start)",
            title: decode(e["title"]) ?? "Programme",
            desc: decode(e["description"]),
            start: Date(timeIntervalSince1970: start),
            end: Date(timeIntervalSince1970: end)
        )
    }
    .sorted { $0.start < $1.start }
}

func parseM3U(_ text: String) -> [M3UEntry] {
    guard let regex = try? NSRegularExpression(pattern: "([\\w-]+)=\"([^\"]*)\"") else { return [] }
    var out: [M3UEntry] = []
    var name: String? = nil
    var logo: String? = nil
    var group: String? = nil
    for rawLine in text.split(whereSeparator: \.isNewline) {
        let line = rawLine.trimmingCharacters(in: .whitespaces)
        if line.uppercased().hasPrefix("#EXTINF") {
            var attrs: [String: String] = [:]
            let ns = line as NSString
            for m in regex.matches(in: line, range: NSRange(location: 0, length: ns.length)) {
                attrs[ns.substring(with: m.range(at: 1)).lowercased()] = ns.substring(with: m.range(at: 2))
            }
            let from = line.lastIndex(of: "\"") ?? line.startIndex
            var title = ""
            if let comma = line[from...].firstIndex(of: ",") {
                title = String(line[line.index(after: comma)...]).trimmingCharacters(in: .whitespaces)
            }
            name = title.isEmpty ? (attrs["tvg-name"] ?? "Sans nom") : title
            logo = attrs["tvg-logo"].flatMap { $0.isEmpty ? nil : $0 }
            group = attrs["group-title"].flatMap { $0.isEmpty ? nil : $0 }
        } else if line.isEmpty || line.hasPrefix("#") {
            continue
        } else {
            out.append(M3UEntry(name: name ?? (line as NSString).lastPathComponent,
                                group: group ?? "Sans catégorie", url: line, logo: logo))
            name = nil; logo = nil; group = nil
        }
    }
    return out
}

func m3uCatalog(_ entries: [M3UEntry], kind: Kind) -> Catalog {
    if kind == .series { return Catalog(categories: [], streams: []) }
    let wanted = entries.filter { e in
        let isVod = e.url.contains("/movie/") || e.url.contains("/series/")
        return kind == .movie ? isVod : !isVod
    }
    var seen = Set<String>()
    var cats: [Category] = []
    for e in wanted where seen.insert(e.group).inserted { cats.append(Category(id: e.group, name: e.group)) }
    let streams = wanted.map { e in
        Stream(id: stableHash(e.url), name: e.name, logo: e.logo, categoryId: e.group, kind: kind, url: e.url)
    }
    return Catalog(categories: cats, streams: streams)
}

func finalize(_ c: Catalog, hideAdult: Bool) -> Catalog {
    var seenCats = Set<String>()
    var cats = c.categories.filter { seenCats.insert($0.id).inserted }
    var seenStreams = Set<String>()
    var streams = c.streams.filter { seenStreams.insert($0.key).inserted }
    if hideAdult {
        let pattern = "(adult|xxx|porn|\\+18|18\\+)"
        let adult = Set(cats.filter { $0.name.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil }.map(\.id))
        cats = cats.filter { !adult.contains($0.id) }
        streams = streams.filter { !adult.contains($0.categoryId) }
    }
    return Catalog(categories: [Category(id: allID, name: "Toutes")] + cats, streams: streams)
}

func parseSeriesInfo(_ data: Data) -> SeriesInfo {
    let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
    let info = json["info"] as? [String: Any]
    var seasons: [Int: [Episode]] = [:]

    func add(_ arr: [[String: Any]], hint: Int?) {
        for e in arr {
            guard let id = str(e["id"]) else { continue }
            let season = Int(str(e["season"]) ?? "") ?? hint ?? 1
            let epInfo = e["info"] as? [String: Any]
            let ep = Episode(
                id: id,
                title: str(e["title"]) ?? "Épisode",
                season: season,
                number: Int(str(e["episode_num"]) ?? "") ?? 0,
                ext: str(e["container_extension"]) ?? "mp4",
                image: str(epInfo?["movie_image"]),
                plot: str(epInfo?["plot"])
            )
            seasons[season, default: []].append(ep)
        }
    }

    if let dict = json["episodes"] as? [String: Any] {
        for (k, v) in dict { if let arr = v as? [[String: Any]] { add(arr, hint: Int(k)) } }
    } else if let list = json["episodes"] as? [Any] {
        for v in list { if let arr = v as? [[String: Any]] { add(arr, hint: nil) } }
    }
    for k in seasons.keys { seasons[k]?.sort { $0.number < $1.number } }

    return SeriesInfo(
        plot: str(info?["plot"]),
        cover: str(info?["cover"]),
        genre: str(info?["genre"]),
        year: (str(info?["releaseDate"]) ?? str(info?["release_date"])).map { String($0.prefix(4)) },
        backdrop: (info?["backdrop_path"] as? [Any])?.first.flatMap { str($0) },
        seasons: seasons
    )
}
