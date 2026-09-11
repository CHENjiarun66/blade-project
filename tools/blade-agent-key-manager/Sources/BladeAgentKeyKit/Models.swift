import Foundation

public enum AgentScope: String, Codable, CaseIterable, Identifiable, Sendable {
    case catalogRead = "catalog:read"
    case ordersWrite = "orders:write"
    case analyticsRead = "analytics:read"
    case whatsappAnalyze = "whatsapp:analyze"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .catalogRead:
            return "查询商品候选"
        case .ordersWrite:
            return "创建订单草稿"
        case .analyticsRead:
            return "读取经营分析"
        case .whatsappAnalyze:
            return "WhatsApp 分析"
        }
    }

    public var detail: String {
        switch self {
        case .catalogRead:
            return "按款号、颜色和尺码匹配 SKU"
        case .ordersWrite:
            return "只允许创建待人工确认的订单草稿"
        case .analyticsRead:
            return "读取已授权的经营聚合数据"
        case .whatsappAnalyze:
            return "领取和回传 WhatsApp 分析任务"
        }
    }
}

public struct StoredAgentKey: Codable, Identifiable, Hashable, Sendable {
    public let id: UUID
    public var name: String
    public var agentName: String
    public var keyPrefix: String
    public var baseURL: String
    public var expiresAt: Date
    public var scopes: Set<AgentScope>
    public let createdAt: Date

    public init(
        id: UUID = UUID(),
        name: String,
        agentName: String,
        keyPrefix: String,
        baseURL: String,
        expiresAt: Date,
        scopes: Set<AgentScope>,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.name = name
        self.agentName = agentName
        self.keyPrefix = keyPrefix
        self.baseURL = baseURL
        self.expiresAt = expiresAt
        self.scopes = scopes
        self.createdAt = createdAt
    }

    public func remainingDays(now: Date = Date(), calendar: Calendar = .current) -> Int {
        let start = calendar.startOfDay(for: now)
        let end = calendar.startOfDay(for: expiresAt)
        return calendar.dateComponents([.day], from: start, to: end).day ?? 0
    }

    public func isExpired(now: Date = Date()) -> Bool {
        expiresAt <= now
    }

    public var scopeSummary: String {
        scopes
            .sorted { $0.rawValue < $1.rawValue }
            .map(\.displayName)
            .joined(separator: "、")
    }
}

public struct KeyInput: Sendable {
    public var name: String
    public var agentName: String
    public var rawKey: String
    public var baseURL: String
    public var expiresAt: Date
    public var scopes: Set<AgentScope>

    public init(
        name: String,
        agentName: String,
        rawKey: String,
        baseURL: String,
        expiresAt: Date,
        scopes: Set<AgentScope>
    ) {
        self.name = name
        self.agentName = agentName
        self.rawKey = rawKey
        self.baseURL = baseURL
        self.expiresAt = expiresAt
        self.scopes = scopes
    }
}

public struct ValidatedKeyInput: Sendable {
    public let metadata: StoredAgentKey
    public let rawKey: String
}

public enum KeyValidationError: LocalizedError, Equatable {
    case missingName
    case missingAgentName
    case invalidKey
    case invalidBaseURL
    case insecureRemoteURL
    case invalidExpiry
    case missingScopes

    public var errorDescription: String? {
        switch self {
        case .missingName:
            return "请输入便于识别的 Key 名称"
        case .missingAgentName:
            return "请输入使用这把 Key 的 Agent 名称"
        case .invalidKey:
            return "Agent Key 格式不正确，应为 prefix.secret"
        case .invalidBaseURL:
            return "API 地址只能包含协议、域名和端口，不能包含 /api 或其他路径"
        case .insecureRemoteURL:
            return "远程生产地址必须使用 HTTPS；HTTP 只允许 localhost"
        case .invalidExpiry:
            return "有效期必须晚于当前时间"
        case .missingScopes:
            return "请至少选择一个权限范围"
        }
    }
}

public enum KeyInputValidator {
    public static func validate(_ input: KeyInput, now: Date = Date()) throws -> ValidatedKeyInput {
        let name = input.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { throw KeyValidationError.missingName }

        let agentName = input.agentName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !agentName.isEmpty else { throw KeyValidationError.missingAgentName }

        let rawKey = input.rawKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let pieces = rawKey.split(separator: ".", maxSplits: 1, omittingEmptySubsequences: false)
        guard pieces.count == 2,
              pieces[0].count >= 4,
              pieces[1].count >= 8,
              !rawKey.contains(where: { $0.isWhitespace }) else {
            throw KeyValidationError.invalidKey
        }

        let baseURL = try normalizedBaseURL(input.baseURL)
        guard input.expiresAt > now else { throw KeyValidationError.invalidExpiry }
        guard !input.scopes.isEmpty else { throw KeyValidationError.missingScopes }

        return ValidatedKeyInput(
            metadata: StoredAgentKey(
                name: name,
                agentName: agentName,
                keyPrefix: String(pieces[0]),
                baseURL: baseURL,
                expiresAt: input.expiresAt,
                scopes: input.scopes
            ),
            rawKey: rawKey
        )
    }

    public static func normalizedBaseURL(_ rawValue: String) throws -> String {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"/+$"#, with: "", options: .regularExpression)
        guard var components = URLComponents(string: value),
              let scheme = components.scheme?.lowercased(),
              let host = components.host,
              !host.isEmpty,
              components.user == nil,
              components.password == nil,
              components.query == nil,
              components.fragment == nil,
              components.path.isEmpty else {
            throw KeyValidationError.invalidBaseURL
        }

        let localHosts = Set(["localhost", "127.0.0.1", "::1"])
        guard scheme == "https" || (scheme == "http" && localHosts.contains(host.lowercased())) else {
            throw KeyValidationError.insecureRemoteURL
        }

        components.scheme = scheme
        guard let normalized = components.string else { throw KeyValidationError.invalidBaseURL }
        return normalized
    }
}
