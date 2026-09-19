import BladeAgentKeyKit
import Foundation

final class RequestRunner {
    private struct SessionGrant {
        let keyID: UUID
        let expiresAt: Date
    }

    private let metadataStore: AgentKeyMetadataStoring
    private let secretStore: AgentKeySecretStoring
    private var sessionGrants: [String: SessionGrant] = [:]

    init(
        metadataStore: AgentKeyMetadataStoring = KeyMetadataStore(),
        secretStore: AgentKeySecretStoring = KeychainSecretStore()
    ) {
        self.metadataStore = metadataStore
        self.secretStore = secretStore
    }

    func execute(_ rawRequest: AgentAPIRequest, preferredKey: String? = nil) async throws -> AgentAPIResponse? {
        let request = try AgentRequestPolicy.validate(rawRequest)
        let keys = try metadataStore.load()
        let candidates = AgentRequestPolicy.eligibleKeys(for: request, in: keys)
        guard !candidates.isEmpty else {
            AuthorizationPrompt.showNoEligibleKey(for: request, storedKeys: keys)
            return nil
        }

        let selectedKey: StoredAgentKey
        let grantKey = sessionGrantKey(agentName: request.agentName, scopes: request.requiredScopes)
        if let grant = sessionGrants[grantKey],
           grant.expiresAt > Date(),
           let grantedKey = candidates.first(where: { $0.id == grant.keyID }) {
            selectedKey = grantedKey
        } else {
            sessionGrants.removeValue(forKey: grantKey)
            guard let decision = AuthorizationPrompt.chooseKey(
                for: request,
                candidates: candidates,
                preferredKey: preferredKey
            ) else {
                return nil
            }
            selectedKey = decision.key
            if let grantDuration = decision.grantDuration {
                sessionGrants[grantKey] = SessionGrant(
                    keyID: selectedKey.id,
                    expiresAt: Date().addingTimeInterval(grantDuration)
                )
            }
        }

        let rawKey = try secretStore.read(
            for: selectedKey.id,
            operationPrompt: "允许 \(request.agentName) 使用 \(selectedKey.name)"
        )
        let client = try AgentAPIClient(baseURL: selectedKey.baseURL)
        let response = try await client.execute(request, rawKey: rawKey)
        if response.statusCode == 403 {
            AuthorizationPrompt.showInformation(
                title: "服务器拒绝权限",
                message: "本机已选择“\(selectedKey.name)”，但服务器拒绝了“\(request.requiredScopeSummary)”中的一项或多项权限。\n\n\(response.serverMessage ?? "请在生产环境检查该 Key 的真实权限，然后回到 Key Manager 同步服务器权限。")"
            )
        } else if response.statusCode == 401 {
            AuthorizationPrompt.showInformation(
                title: "Agent Key 已被服务器拒绝",
                message: "这把 Key 可能已停用、过期或被轮换。请在 BladeProject 检查状态，并在 Key Manager 更新本机 Key。"
            )
        }
        return response
    }

    private func sessionGrantKey(agentName: String, scopes: Set<AgentScope>) -> String {
        let scopeKey = scopes.map(\.rawValue).sorted().joined(separator: ",")
        return "\(agentName.lowercased())|\(scopeKey)"
    }
}
