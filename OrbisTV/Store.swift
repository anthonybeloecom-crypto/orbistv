import Foundation

/// Stockage local : compte, profils, favoris et reprise par profil, réglages. Rien ne quitte l'iPhone.
@MainActor
final class Store: ObservableObject {
    static let shared = Store()

    private let d = UserDefaults.standard

    @Published private(set) var account: Account? = nil
    @Published private(set) var profiles: [Profile] = []
    @Published private(set) var activeProfileID: String = "default"
    /// Vrai une fois le profil choisi pour cette session (écran « Qui regarde ? »).
    @Published var profileChosen = false
    @Published private(set) var favorites: [Stream] = []
    /// Incrémenté quand la progression change (fermeture du lecteur) pour rafraîchir l'affichage.
    @Published private(set) var progressTick = 0

    @Published var hideAdult: Bool {
        didSet { d.set(hideAdult, forKey: "hide_adult") }
    }
    /// "m3u8" (HLS, recommandé sur iPhone) ou "ts".
    @Published var liveFormat: String {
        didSet { d.set(liveFormat, forKey: "live_format") }
    }
    @Published var introSound: Bool {
        didSet { d.set(introSound, forKey: "intro_sound") }
    }
    @Published var liveGrid: Bool {
        didSet { d.set(liveGrid, forKey: "live_grid") }
    }

    init() {
        introSound = d.object(forKey: "intro_sound") as? Bool ?? true
        liveGrid = d.object(forKey: "live_grid") as? Bool ?? true
        hideAdult = d.object(forKey: "hide_adult") as? Bool ?? true
        liveFormat = d.string(forKey: "live_format") ?? "m3u8"
        if let data = d.data(forKey: "account") {
            account = try? JSONDecoder().decode(Account.self, from: data)
        }
        if let data = d.data(forKey: "profiles"), let list = try? JSONDecoder().decode([Profile].self, from: data), !list.isEmpty {
            profiles = list
        } else {
            profiles = [Profile(id: "default", name: "Moi", color: 0)]
        }
        let saved = d.string(forKey: "active_profile") ?? "default"
        activeProfileID = profiles.contains { $0.id == saved } ? saved : profiles[0].id
        profileChosen = profiles.count < 2
        loadFavorites()
    }

    var activeProfile: Profile { profiles.first { $0.id == activeProfileID } ?? profiles[0] }

    /// Le profil d'origine garde les anciennes clés, les autres sont préfixés.
    private func prefix(_ id: String) -> String { id == "default" ? "" : "p_\(id)_" }
    private var p: String { prefix(activeProfileID) }

    // MARK: Compte

    func setAccount(_ a: Account) {
        account = a
        d.set(try? JSONEncoder().encode(a), forKey: "account")
    }

    func logout() {
        for key in d.dictionaryRepresentation().keys
        where key.contains("resume_") || key.contains("dur_") || key.hasSuffix("favorites") {
            d.removeObject(forKey: key)
        }
        d.removeObject(forKey: "account")
        favorites = []
        account = nil
    }

    // MARK: Profils

    func selectProfile(_ id: String) {
        activeProfileID = id
        d.set(id, forKey: "active_profile")
        loadFavorites()
        profileChosen = true
        progressTick += 1
    }

    @discardableResult
    func addProfile(name: String, color: Int) -> Profile {
        let clean = name.trimmingCharacters(in: .whitespaces)
        let profile = Profile(id: UUID().uuidString.prefix(8).lowercased(), name: clean.isEmpty ? "Profil" : clean, color: color)
        profiles.append(profile)
        saveProfiles()
        return profile
    }

    func updateProfile(_ id: String, name: String, color: Int) {
        guard let i = profiles.firstIndex(where: { $0.id == id }) else { return }
        let clean = name.trimmingCharacters(in: .whitespaces)
        if !clean.isEmpty { profiles[i].name = clean }
        profiles[i].color = color
        saveProfiles()
    }

    func deleteProfile(_ id: String) {
        guard profiles.count > 1 else { return }
        profiles.removeAll { $0.id == id }
        let pre = prefix(id)
        for key in d.dictionaryRepresentation().keys where !pre.isEmpty && key.hasPrefix(pre) {
            d.removeObject(forKey: key)
        }
        if id == "default" {
            d.removeObject(forKey: "favorites")
            for key in d.dictionaryRepresentation().keys where key.hasPrefix("resume_") || key.hasPrefix("dur_") {
                d.removeObject(forKey: key)
            }
        }
        saveProfiles()
        if activeProfileID == id { selectProfile(profiles[0].id) }
    }

    private func saveProfiles() {
        d.set(try? JSONEncoder().encode(profiles), forKey: "profiles")
    }

    // MARK: Favoris (par profil)

    private func loadFavorites() {
        if let data = d.data(forKey: p + "favorites") {
            favorites = (try? JSONDecoder().decode([Stream].self, from: data)) ?? []
        } else {
            favorites = []
        }
    }

    func isFavorite(_ s: Stream) -> Bool { favorites.contains { $0.key == s.key } }

    func toggleFavorite(_ s: Stream) {
        if let i = favorites.firstIndex(where: { $0.key == s.key }) {
            favorites.remove(at: i)
        } else {
            favorites.append(s)
        }
        d.set(try? JSONEncoder().encode(favorites), forKey: p + "favorites")
    }

    // MARK: Reprise (par profil)

    func resume(_ key: String) -> Double { d.double(forKey: p + "resume_\(key)") }
    func duration(_ key: String) -> Double { d.double(forKey: p + "dur_\(key)") }

    func progress(_ key: String) -> Double {
        _ = progressTick
        let total = duration(key)
        return total > 0 ? min(max(resume(key) / total, 0), 1) : 0
    }

    func saveProgress(_ key: String, position: Double, duration: Double) {
        d.set(position, forKey: p + "resume_\(key)")
        d.set(duration, forKey: p + "dur_\(key)")
    }

    func clearProgress(_ key: String) {
        d.removeObject(forKey: p + "resume_\(key)")
        d.removeObject(forKey: p + "dur_\(key)")
    }

    func bumpProgress() { progressTick += 1 }
}
