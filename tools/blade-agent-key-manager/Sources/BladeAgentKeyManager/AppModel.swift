import AppKit
import BladeAgentKeyKit
import Combine
import Foundation

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var keys: [StoredAgentKey] = []
    @Published var selectedID: StoredAgentKey.ID?
    @Published var isShowingAddSheet = false
    @Published var errorMessage: String?
    @Published private(set) var helperPath: String?

    private let metadataStore: AgentKeyMetadataStoring
    private let secretStore: AgentKeySecretStoring

    init(
        metadataStore: AgentKeyMetadataStoring = KeyMetadataStore(),
        secretStore: AgentKeySecretStoring = KeychainSecretStore()
    ) {
        self.metadataStore = metadataStore
        self.secretStore = secretStore
        reload()
        installBundledHelper()
    }

    var selectedKey: StoredAgentKey? {
        keys.first { $0.id == selectedID }
    }

    func reload() {
        do {
            keys = try metadataStore.load()
            if selectedID == nil || !keys.contains(where: { $0.id == selectedID }) {
                selectedID = keys.first?.id
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    @discardableResult
    func add(_ input: KeyInput) -> Bool {
        do {
            let validated = try KeyInputValidator.validate(input)
            guard !keys.contains(where: { $0.keyPrefix == validated.metadata.keyPrefix }) else {
                throw AppModelError.duplicatePrefix
            }

            try secretStore.save(
                validated.rawKey,
                for: validated.metadata.id,
                label: "Blade Agent Key — \(validated.metadata.name)"
            )
            do {
                try metadataStore.save([validated.metadata] + keys)
            } catch {
                try? secretStore.delete(for: validated.metadata.id)
                throw error
            }

            clearClipboardIfItContains(validated.rawKey)
            reload()
            selectedID = validated.metadata.id
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    func delete(_ key: StoredAgentKey) {
        do {
            try secretStore.delete(for: key.id)
            try metadataStore.save(keys.filter { $0.id != key.id })
            reload()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func copyConnectionGuide(agentName: String? = nil) {
        guard let helperPath else {
            errorMessage = "本机调用工具尚未安装，请从打包后的应用启动一次后重试"
            return
        }
        let resolvedAgent = (agentName?.isEmpty == false ? agentName! : "你的 Agent 名称")
        let text = """
        BladeProject 本机安全调用工具：
        \(helperPath)

        MCP 启动参数：
        \(helperPath) --mcp --agent "\(resolvedAgent)"

        安全约束：不要读取、索要或输出 Agent Key 原文。调用 BladeProject 时使用上述本机工具；工具会弹出 Key 选择和授权窗口，并只把 API 结果返回给 Agent。
        """
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
    }

    private func clearClipboardIfItContains(_ rawKey: String) {
        let pasteboard = NSPasteboard.general
        guard pasteboard.string(forType: .string)?.trimmingCharacters(in: .whitespacesAndNewlines) == rawKey else {
            return
        }
        pasteboard.clearContents()
    }

    private func installBundledHelper() {
        let fileManager = FileManager.default
        let source = Bundle.main.bundleURL
            .appendingPathComponent("Contents", isDirectory: true)
            .appendingPathComponent("Helpers", isDirectory: true)
            .appendingPathComponent("blade-agent-request")

        let supportRoot = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(KeyMetadataStore.applicationSupportDirectoryName, isDirectory: true)
        let destinationDirectory = supportRoot.appendingPathComponent("bin", isDirectory: true)
        let destination = destinationDirectory.appendingPathComponent("blade-agent-request")

        guard fileManager.fileExists(atPath: source.path) else {
            helperPath = fileManager.fileExists(atPath: destination.path) ? destination.path : nil
            return
        }

        do {
            try fileManager.createDirectory(at: destinationDirectory, withIntermediateDirectories: true)
            let temporary = destinationDirectory.appendingPathComponent("blade-agent-request.new")
            try? fileManager.removeItem(at: temporary)
            try fileManager.copyItem(at: source, to: temporary)
            if fileManager.fileExists(atPath: destination.path) {
                _ = try fileManager.replaceItemAt(destination, withItemAt: temporary)
            } else {
                try fileManager.moveItem(at: temporary, to: destination)
            }
            try fileManager.setAttributes([.posixPermissions: 0o700], ofItemAtPath: destination.path)
            helperPath = destination.path
        } catch {
            helperPath = nil
            errorMessage = "安装本机 Agent 调用工具失败：\(error.localizedDescription)"
        }
    }
}

private enum AppModelError: LocalizedError {
    case duplicatePrefix

    var errorDescription: String? {
        "这把 Key 已经保存过；如需更换，请先在系统中轮换，再录入新 Key"
    }
}
