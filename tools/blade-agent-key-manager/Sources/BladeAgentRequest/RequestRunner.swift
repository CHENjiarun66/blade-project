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

        let selectedKey: StoredAgentKey
        let grantKey = sessionGrantKey(agentName: request.agentName, scope: request.requiredScope)
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
        return try await client.execute(request, rawKey: rawKey)
    }

    private func sessionGrantKey(agentName: String, scope: AgentScope) -> String {
        "\(agentName.lowercased())|\(scope.rawValue)"
    }
}
