import SwiftUI

@main
struct BladeAgentKeyManagerApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            ContentView(model: model)
                .frame(minWidth: 880, minHeight: 580)
        }
        .windowStyle(.titleBar)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("新增 Agent Key") {
                    model.isShowingAddSheet = true
                }
                .keyboardShortcut("n")
            }
        }
    }
}
