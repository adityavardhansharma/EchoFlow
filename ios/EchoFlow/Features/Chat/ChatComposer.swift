// Sketch only: not part of a buildable project yet. See ios/README.md.
//
// The chat composer from the design canvas: one glass rounded rectangle pinned to the
// bottom safe area. Its buttons are plain inside it (no glass on glass); only the send
// button is tinted. When the field is empty, send becomes the voice-mode button.

import SwiftUI

struct ChatComposer: View {
    @Binding var text: String
    var modelName: String
    var onPlus: () -> Void
    var onPickModel: () -> Void
    var onDictate: () -> Void
    var onVoice: () -> Void
    var onSend: () -> Void

    private var isEmpty: Bool {
        text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            TextField("Ask anything…", text: $text, axis: .vertical)
                .lineLimit(1...6)
                .padding(.horizontal, 8)
                .padding(.top, 6)

            HStack(spacing: 8) {
                Button(action: onPlus) {
                    Image(systemName: "plus")
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.circle)
                .accessibilityLabel("Add")

                Button(action: onPickModel) {
                    Label(modelName, systemImage: "chevron.down")
                        .labelStyle(.titleAndIcon)
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)

                Spacer()

                Button(action: onDictate) {
                    Image(systemName: "mic")
                }
                .accessibilityLabel("Dictate")

                Button(action: isEmpty ? onVoice : onSend) {
                    Image(systemName: isEmpty ? "waveform" : "arrow.up")
                        .fontWeight(.semibold)
                        .contentTransition(.symbolEffect(.replace))
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.circle)
                .accessibilityLabel(isEmpty ? "Start voice mode" : "Send")
            }
        }
        .padding(10)
        .glassEffect(.regular, in: .rect(cornerRadius: 30))
        .padding(.horizontal, 12)
    }
}

/// How the chat screen hosts it: content scrolls underneath and the system
/// scroll-edge effect keeps the composer legible. No custom bar background.
struct ChatScreenSketch: View {
    @State private var draft = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                // Messages are solid content, never glass.
            }
            .safeAreaBar(edge: .bottom) {
                ChatComposer(
                    text: $draft,
                    modelName: "GPT-6 Luna",
                    onPlus: {}, onPickModel: {}, onDictate: {}, onVoice: {}, onSend: {}
                )
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Chats", systemImage: "line.3.horizontal") {}
                }
                ToolbarItem(placement: .principal) {
                    Picker("Mode", selection: .constant(0)) {
                        Label("Chat", systemImage: "bubble.left").tag(0)
                        Label("Imagine", systemImage: "wand.and.stars").tag(1)
                    }
                    .pickerStyle(.segmented)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("New chat", systemImage: "square.and.pencil") {}
                }
            }
        }
    }
}
