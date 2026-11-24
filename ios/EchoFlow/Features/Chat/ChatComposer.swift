// Sketch only: not part of a buildable project yet. See ios/README.md.
//
// The chat composer from the design canvas, with EchoFlow's own layout kept from Android:
// a glass model chip and a dictation button above a glass field that holds + and Send.
// Buttons inside the field are plain (no glass on glass); Send is the only tinted control.

import SwiftUI

struct ChatComposer: View {
    @Binding var text: String
    var modelName: String
    var onPickModel: () -> Void
    var onDictate: () -> Void
    var onPlus: () -> Void
    var onSend: () -> Void

    private var isEmpty: Bool {
        text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        GlassEffectContainer(spacing: 10) {
            VStack(spacing: 10) {
                HStack {
                    Button(action: onPickModel) {
                        Label(modelName, systemImage: "chevron.down")
                            .labelStyle(.titleAndIcon)
                    }
                    .buttonStyle(.glass)

                    Spacer()

                    Button(action: onDictate) {
                        Image(systemName: "mic")
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .accessibilityLabel("Dictate")
                }

                HStack(alignment: .bottom, spacing: 4) {
                    Button(action: onPlus) {
                        Image(systemName: "plus")
                            .font(.title3)
                            .frame(width: 44, height: 44)
                            .background(.fill.tertiary, in: .circle)
                    }
                    .accessibilityLabel("Add")

                    TextField("Ask anything", text: $text, axis: .vertical)
                        .lineLimit(1...6)
                        .padding(.vertical, 11)
                        .padding(.horizontal, 6)

                    Button(action: onSend) {
                        Image(systemName: "arrow.up")
                            .fontWeight(.bold)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.borderedProminent)
                    .buttonBorderShape(.circle)
                    .disabled(isEmpty)
                    .accessibilityLabel("Send")
                }
                .padding(6)
                .glassEffect(.regular, in: .capsule)
            }
        }
        .padding(.horizontal, 14)
    }
}

/// The chat screen keeps Android's top bar: drawer, the Chat / Imagine switch and new chat.
/// Content scrolls under the glass, and the system scroll-edge effect keeps it legible.
struct ChatScreenSketch: View {
    @State private var draft = ""
    @State private var mode = 0
    @State private var drawerOpen = false

    var body: some View {
        NavigationStack {
            ScrollView {
                // Messages are solid content, never glass.
            }
            .safeAreaBar(edge: .bottom) {
                ChatComposer(
                    text: $draft, modelName: "GPT-5.1",
                    onPickModel: {}, onDictate: {}, onPlus: {}, onSend: {}
                )
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Chats", systemImage: "line.3.horizontal") { drawerOpen = true }
                }
                ToolbarItem(placement: .principal) {
                    Picker("Mode", selection: $mode) {
                        Label("Chat", systemImage: "bubble.left.fill").tag(0)
                        Label("Imagine", systemImage: "wand.and.stars").tag(1)
                    }
                    .pickerStyle(.segmented)
                    .glassEffect(.regular, in: .capsule)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("New chat", systemImage: "square.and.pencil") {}
                }
            }
        }
    }
}
