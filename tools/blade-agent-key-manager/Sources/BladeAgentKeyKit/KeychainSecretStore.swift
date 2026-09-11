import Foundation
import LocalAuthentication
import Security

public protocol AgentKeySecretStoring {
    func save(_ secret: String, for id: UUID, label: String) throws
    func read(for id: UUID, operationPrompt: String?) throws -> String
    func delete(for id: UUID) throws
}

public enum KeychainStoreError: LocalizedError {
    case unexpectedStatus(OSStatus)
    case invalidData

    public var errorDescription: String? {
        switch self {
        case .unexpectedStatus(let status):
            let message = SecCopyErrorMessageString(status, nil) as String? ?? "未知错误"
            return "macOS 钥匙串操作失败：\(message)（\(status)）"
        case .invalidData:
            return "钥匙串中的 Agent Key 数据无法读取"
        }
    }
}

public final class KeychainSecretStore: AgentKeySecretStoring {
    public static let service = "com.chenjiarun.blade-agent-key-manager.agent-key"

    public init() {}

    public func save(_ secret: String, for id: UUID, label: String) throws {
        let account = id.uuidString
        let secretData = Data(secret.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: account
        ]
        let attributes: [String: Any] = [
            kSecValueData as String: secretData,
            kSecAttrLabel as String: label,
            kSecAttrDescription as String: "BladeProject Agent Key"
        ]

        let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecSuccess { return }
        guard updateStatus == errSecItemNotFound else {
            throw KeychainStoreError.unexpectedStatus(updateStatus)
        }

        var newItem = query
        attributes.forEach { newItem[$0.key] = $0.value }
        let addStatus = SecItemAdd(newItem as CFDictionary, nil)
        guard addStatus == errSecSuccess else {
            throw KeychainStoreError.unexpectedStatus(addStatus)
        }
    }

    public func read(for id: UUID, operationPrompt: String? = nil) throws -> String {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: id.uuidString,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnData as String: true
        ]
        if let operationPrompt, !operationPrompt.isEmpty {
            let context = LAContext()
            context.localizedReason = operationPrompt
            query[kSecUseAuthenticationContext as String] = context
        }

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess else {
            throw KeychainStoreError.unexpectedStatus(status)
        }
        guard let data = result as? Data, let secret = String(data: data, encoding: .utf8) else {
            throw KeychainStoreError.invalidData
        }
        return secret
    }

    public func delete(for id: UUID) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: id.uuidString
        ]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainStoreError.unexpectedStatus(status)
        }
    }
}
