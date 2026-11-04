import SwiftUI

@MainActor
struct LoginView: View {
    @State private var mode = 0 // 0 = Xtream, 1 = M3U
    @State private var server = ""
    @State private var user = ""
    @State private var pass = ""
    @State private var m3u = ""
    @State private var loading = false
    @State private var error: String? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                Wordmark(size: 16)
                    .padding(.top, 24)

                VStack(alignment: .leading, spacing: 8) {
                    Text("Orbis TV")
                        .font(.system(size: 48, weight: .medium))
                        .tracking(-1)
                    Text("Le lecteur IPTV pour ta TV.")
                        .font(.title3.weight(.light))
                        .foregroundStyle(Palette.muted)
                }

                VStack(alignment: .leading, spacing: 14) {
                    Picker("Type", selection: $mode) {
                        Text("Xtream Codes").tag(0)
                        Text("Lien M3U").tag(1)
                    }
                    .pickerStyle(.segmented)

                    if mode == 0 {
                        Field(title: "Serveur (ex. http://exemple.com:8080)", text: $server, keyboard: .URL)
                        Field(title: "Identifiant", text: $user)
                        Field(title: "Mot de passe", text: $pass, secure: true)
                    } else {
                        Field(title: "Lien de la playlist M3U", text: $m3u, keyboard: .URL)
                    }

                    if let error {
                        Text(error).font(.footnote).foregroundStyle(Color(red: 1, green: 0.42, blue: 0.42))
                    }

                    Button(action: submit) {
                        HStack(spacing: 10) {
                            if loading { ProgressView().tint(Palette.ink) }
                            Text(loading ? "Connexion…" : "Se connecter").fontWeight(.medium)
                        }
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .foregroundStyle(Palette.ink)
                        .background(Palette.light, in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                    .disabled(loading)

                    Text("Tes identifiants restent sur cet iPhone. Orbis TV ne fournit aucune chaîne ni aucun contenu.")
                        .font(.caption)
                        .foregroundStyle(Palette.muted)
                }
                .padding(20)
                .background(Palette.surface, in: RoundedRectangle(cornerRadius: 14))
            }
            .padding(20)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
        }
        .background(Color.black.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
    }

    private func submit() {
        let account: Account = mode == 0
            ? .xtream(server: server.trimmingCharacters(in: .whitespaces), user: user.trimmingCharacters(in: .whitespaces), pass: pass)
            : .m3u(url: m3u.trimmingCharacters(in: .whitespaces))
        loading = true
        error = nil
        Task {
            do {
                try await Repo.shared.login(account)
            } catch {
                self.error = error.localizedDescription
            }
            loading = false
        }
    }
}

private struct Field: View {
    let title: String
    @Binding var text: String
    var keyboard: UIKeyboardType = .default
    var secure = false

    var body: some View {
        Group {
            if secure {
                SecureField(title, text: $text)
            } else {
                TextField(title, text: $text)
                    .keyboardType(keyboard)
            }
        }
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .padding(.horizontal, 14)
        .frame(minHeight: 48)
        .background(Palette.surface2, in: RoundedRectangle(cornerRadius: 8))
    }
}
