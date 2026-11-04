import SwiftUI

/// Écran « Qui regarde ? » : chaque profil a ses favoris et sa reprise de lecture.
@MainActor
struct ProfilePickerView: View {
    @EnvironmentObject var store: Store
    @State private var editing = false
    @State private var editor: EditorTarget? = nil

    var body: some View {
        VStack(spacing: 36) {
            Spacer()
            Text("Qui regarde ?")
                .font(.system(size: 30, weight: .medium))
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 24)], spacing: 28) {
                ForEach(store.profiles) { p in
                    Button {
                        if editing { editor = .edit(p) } else { store.selectProfile(p.id) }
                    } label: {
                        VStack(spacing: 10) {
                            ZStack(alignment: .topTrailing) {
                                Avatar(profile: p, size: 84)
                                if editing {
                                    Image(systemName: "pencil.circle.fill")
                                        .font(.title3)
                                        .foregroundStyle(.white, Palette.ink)
                                }
                            }
                            Text(p.name).font(.subheadline).lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                }
                Button { editor = .new } label: {
                    VStack(spacing: 10) {
                        ZStack {
                            Circle().stroke(Palette.surface2, lineWidth: 2)
                            Image(systemName: "plus").font(.title2).foregroundStyle(Palette.muted)
                        }
                        .frame(width: 84, height: 84)
                        Text("Ajouter").font(.subheadline).foregroundStyle(Palette.muted)
                    }
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 32)
            .frame(maxWidth: 560)

            Button(editing ? "Terminé" : "Modifier les profils") { editing.toggle() }
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Palette.muted)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .sheet(item: $editor) { target in
            ProfileEditor(target: target).environmentObject(store)
        }
    }
}

enum EditorTarget: Identifiable {
    case new
    case edit(Profile)
    var id: String {
        switch self {
        case .new: return "new"
        case .edit(let p): return p.id
        }
    }
}

@MainActor
struct ProfileEditor: View {
    let target: EditorTarget
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var color = 0

    private var existing: Profile? {
        if case .edit(let p) = target { return p }
        return nil
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 28) {
                Avatar(profile: Profile(id: "preview", name: name.isEmpty ? "?" : name, color: color), size: 96)
                    .padding(.top, 24)
                TextField("Prénom", text: $name)
                    .textInputAutocapitalization(.words)
                    .padding(.horizontal, 14)
                    .frame(minHeight: 48)
                    .background(Palette.surface2, in: RoundedRectangle(cornerRadius: 8))
                HStack(spacing: 14) {
                    ForEach(profileColors.indices, id: \.self) { i in
                        Button { color = i } label: {
                            Circle()
                                .fill(profileColors[i])
                                .frame(width: 34, height: 34)
                                .overlay(Circle().stroke(Color.white, lineWidth: color == i ? 3 : 0))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Couleur \(i + 1)")
                    }
                }
                PrimaryButton(title: existing == nil ? "Créer le profil" : "Enregistrer") {
                    if let p = existing {
                        store.updateProfile(p.id, name: name, color: color)
                    } else {
                        let p = store.addProfile(name: name, color: color)
                        store.selectProfile(p.id)
                    }
                    dismiss()
                }
                if let p = existing, store.profiles.count > 1 {
                    Button("Supprimer ce profil", role: .destructive) {
                        store.deleteProfile(p.id)
                        dismiss()
                    }
                    .font(.subheadline)
                }
                Spacer()
            }
            .padding(24)
            .background(Color.black.ignoresSafeArea())
            .navigationTitle(existing == nil ? "Nouveau profil" : "Modifier le profil")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
            }
        }
        .onAppear {
            if let p = existing {
                name = p.name
                color = p.color
            } else {
                color = store.profiles.count % profileColors.count
            }
        }
    }
}
