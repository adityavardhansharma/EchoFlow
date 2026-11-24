// Sketch only: not part of a buildable project yet. See ios/README.md.
//
// The chat composer from the design canvas, laid out like Messages: a glass + circle,
// a glass capsule field with dictation inside it, and a tinted voice-mode circle.
// When there is text, Send appears inside the field and the voice circle steps aside.
// The buttons inside the field are plain (no glass on glass).

import SwiftUI

struct ChatComposer: View {
    @Binding var text: String
    var onPlus: () -> Void
    var onDictate: () -> Void
    var onVoice: () -> Void
    var onSend: () -> Void

    @Namespace private var glass

    private var isEmpty: Bool {
        text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        GlassEffectContainer(spacing: 8) {
            HStack(alignment: .bottom, spacing: 8) {
                Button(action: onPlus) {
                    Image(systemName: "plus")
                        .fontWeight(.semibold)
                        .frame(width: 44, height: 44)
                }
                .glassEffect(.regular.interactive(), in: .circle)
                .accessibilityLabel("Add")

                HStack(alignment: .bottom, spacing: 6) {
                    TextField("Ask anything", text: $text, axis: .vertical)
                        .lineLimit(1...6)
                        .padding(.vertical, 11)

                    if isEmpty {
                        Button(action: onDictate) {
                            Image(systemName: "mic")
                                .frame(width: 34, height: 34)
                        }
                        .foregroundStyle(.secondary)
                        .accessibilityLabel("Dictate")
                    } else {
                        Button(action: onSend) {
                            Image(systemName: "arrow.up")
                                .fontWeight(.bold)
                                .frame(width: 34, height: 34)
                        }
                        .buttonStyle(.borderedProminent)
                        .buttonBorderShape(.circle)
                        .accessibilityLabel("Send")
                    }
                }
                .padding(.leading, 16)
                .padding(.trailing, 5)
                .padding(.bottom, 5)
                .glassEffect(.regular, in: .rect(cornerRadius: 22))
                .glassEffectID("field", in: glass)

                if isEmpty {
                    Button(action: onVoice) {
                        Image(systemName: "waveform")
                            .fontWeight(.semibold)
                            .frame(width: 44, height: 44)
                    }
                    .glassEffect(.regular.tint(.accentColor).interactive(), in: .circle)
                    .glassEffectID("voice", in: glass)
                    .accessibilityLabel("Start voice mode")
                }
            }
        }
        .padding(.horizontal, 12)
        .animation(.smooth, value: isEmpty)
    }
}

/// The app's root: a floating glass tab bar with Search as its own tab, and a bottom
/// accessory for a running Deep Research job. Settings opens from the Chats toolbar.
struct RootSketch: View {
    @State private var researchRunning = true

    var body: some View {
        TabView {
            Tab("Chats", systemImage: "bubble.left.fill") { ChatListSketch() }
            Tab("Imagine", systemImage: "wand.and.stars") { Text("Imagine") }
            Tab("Projects", systemImage: "folder") { Text("Projects") }
            Tab("Library", systemImage: "square.grid.2x2") { Text("Library") }
            Tab(role: .search) { Text("Search") }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        .tabViewBottomAccessory {
            if researchRunning {
                Label("Researching EV charger rebates", systemImage: "flask")
            }
        }
    }
}

struct ChatListSketch: View {
    var body: some View {
        NavigationStack {
            List {
                // Pinned chats as avatars, then Messages-style rows.
            }
            .listStyle(.plain)
            .navigationTitle("Chats")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Edit") {} }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button("Settings", systemImage: "gearshape") {}
                    Button("New chat", systemImage: "square.and.pencil") {}
                }
            }
        }
    }
}

/// A conversation: content scrolls under the composer and the system scroll-edge
/// effect keeps it legible. The title is a glass capsule that opens the model picker.
struct ChatScreenSketch: View {
    @State private var draft = ""

    var body: some View {
        ScrollView {
            // Messages are solid content, never glass.
        }
        .safeAreaBar(edge: .bottom) {
            ChatComposer(text: $draft, onPlus: {}, onDictate: {}, onVoice: {}, onSend: {})
        }
        .toolbar {
            ToolbarItem(placement: .principal) {
                Button {} label: {
                    Label("GPT-5.1", systemImage: "chevron.down")
                        .labelStyle(.titleAndIcon)
                }
                .buttonStyle(.glass)
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu("More", systemImage: "ellipsis") {}
            }
        }
        .toolbar(.hidden, for: .tabBar)
    }
}
