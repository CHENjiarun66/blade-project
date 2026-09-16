import Foundation

public struct AgentAPIRequest: Sendable {
    public var agentName: String
    public var method: String
    public var path: String
    public var body: Data?
    public var fileURL: URL?

    public init(agentName: String, method: String, path: String, body: Data? = nil, fileURL: URL? = nil) {
        self.agentName = agentName
        self.method = method
        self.path = path
        self.body = body
        self.fileURL = fileURL
    }
}

public struct ValidatedAgentRequest: Sendable {
    public let agentName: String
    public let method: String
    public let path: String
    public let requiredScope: AgentScope
    public let body: Data?
    public let fileURL: URL?
}

public enum AgentRequestPolicyError: LocalizedError, Equatable {
    case missingAgentName
    case invalidMethod
    case invalidPath
    case unsupportedEndpoint
    case bodyTooLarge
    case invalidUploadFile

    public var errorDescription: String? {
        switch self {
        case .missingAgentName:
            return "调用方必须声明 Agent 名称"
        case .invalidMethod:
            return "该本机代理只允许 GET 和受控 POST 请求"
        case .invalidPath:
            return "只能传入以 /api/agent/ 开头的相对路径"
        case .unsupportedEndpoint:
            return "该接口尚未加入本机授权代理的白名单"
        case .bodyTooLarge:
            return "请求体超过 10 MB 限制"
        case .invalidUploadFile:
            return "纸单原图必须是本机可读取且不超过 25 MB 的 JPG、PNG 或 WEBP 图片"
        }
    }
}

public enum AgentRequestPolicy {
    public static let maximumBodySize = 10 * 1024 * 1024
    public static let maximumUploadSize = 25 * 1024 * 1024
    private static let allowedImageExtensions = Set(["jpg", "jpeg", "png", "webp"])

    public static func validate(_ request: AgentAPIRequest) throws -> ValidatedAgentRequest {
        let agentName = request.agentName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !agentName.isEmpty else { throw AgentRequestPolicyError.missingAgentName }

        let method = request.method.uppercased()
        guard method == "GET" || method == "POST" else {
            throw AgentRequestPolicyError.invalidMethod
        }

        let path = request.path.trimmingCharacters(in: .whitespacesAndNewlines)
        guard path.hasPrefix("/api/agent/"),
              !path.contains("://"),
              !path.split(separator: "?").first!.split(separator: "/").contains("..") else {
            throw AgentRequestPolicyError.invalidPath
        }
        if let body = request.body, body.count > maximumBodySize {
            throw AgentRequestPolicyError.bodyTooLarge
        }

        let pathOnly = String(path.split(separator: "?", maxSplits: 1).first ?? "")
        let requiredScope: AgentScope
        switch (method, pathOnly) {
        case ("GET", "/api/agent/catalog/skus"):
            requiredScope = .catalogRead
        case ("GET", "/api/agent/products"):
            requiredScope = .productsRead
        case ("GET", "/api/agent/products/options"):
            requiredScope = .productsRead
        case ("GET", let value) where isNumericDetailPath(value, prefix: "/api/agent/products/"):
            requiredScope = .productsRead
        case ("POST", "/api/agent/products"):
            requiredScope = .productsCreate
        case ("GET", "/api/agent/orders"):
            requiredScope = .ordersRead
        case ("GET", let value) where isNumericDetailPath(value, prefix: "/api/agent/orders/"):
            requiredScope = .ordersRead
        case ("GET", "/api/agent/customers"):
            requiredScope = .customersRead
        case ("GET", let value) where isNumericDetailPath(value, prefix: "/api/agent/customers/"):
            requiredScope = .customersRead
        case ("POST", "/api/agent/customers"):
            requiredScope = .customersCreate
        case ("POST", "/api/agent/order-drafts/batch"):
            requiredScope = .ordersWrite
        case ("POST", "/api/agent/order-drafts/source-files"):
            requiredScope = .ordersWrite
        case ("GET", "/api/agent/analytics/style-trends"),
             ("GET", "/api/agent/analytics/sku-mix"):
            requiredScope = .analyticsRead
        default:
            throw AgentRequestPolicyError.unsupportedEndpoint
        }

        if pathOnly == "/api/agent/order-drafts/source-files" {
            guard request.body == nil,
                  let fileURL = request.fileURL,
                  fileURL.isFileURL,
                  allowedImageExtensions.contains(fileURL.pathExtension.lowercased()),
                  let values = try? fileURL.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey]),
                  values.isRegularFile == true,
                  let fileSize = values.fileSize,
                  fileSize > 0,
                  fileSize <= maximumUploadSize else {
                throw AgentRequestPolicyError.invalidUploadFile
            }
        } else if request.fileURL != nil {
            throw AgentRequestPolicyError.unsupportedEndpoint
        }

        return ValidatedAgentRequest(
            agentName: agentName,
            method: method,
            path: path,
            requiredScope: requiredScope,
            body: request.body,
            fileURL: request.fileURL
        )
    }

    private static func isNumericDetailPath(_ path: String, prefix: String) -> Bool {
        guard path.hasPrefix(prefix) else { return false }
        let suffix = path.dropFirst(prefix.count)
        return !suffix.isEmpty && suffix.allSatisfy(\.isNumber)
    }

    public static func eligibleKeys(
        for request: ValidatedAgentRequest,
        in keys: [StoredAgentKey],
        now: Date = Date()
    ) -> [StoredAgentKey] {
        keys.filter { !$0.isExpired(now: now) && $0.scopes.contains(request.requiredScope) }
    }
}

public struct AgentAPIResponse: Sendable {
    public let statusCode: Int
    public let data: Data

    public init(statusCode: Int, data: Data) {
        self.statusCode = statusCode
        self.data = data
    }
}

public enum AgentAPIClientError: LocalizedError {
    case invalidBaseURL
    case invalidRequestURL
    case invalidResponse
    case unsafeRedirect
    case unreadableUploadFile

    public var errorDescription: String? {
        switch self {
        case .invalidBaseURL:
            return "保存的 API 地址无效"
        case .invalidRequestURL:
            return "无法生成 Agent API 请求地址"
        case .invalidResponse:
            return "Agent API 没有返回有效的 HTTP 响应"
        case .unsafeRedirect:
            return "服务器尝试把请求重定向到其他主机，已阻止 Key 外泄"
        case .unreadableUploadFile:
            return "无法读取要上传的纸单原图"
        }
    }
}

public final class AgentAPIClient: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    private let expectedOrigin: URLComponents
    private lazy var session = URLSession(configuration: .ephemeral, delegate: self, delegateQueue: nil)

    public init(baseURL: String) throws {
        let normalized: String
        do {
            normalized = try KeyInputValidator.normalizedBaseURL(baseURL)
        } catch {
            throw AgentAPIClientError.invalidBaseURL
        }
        guard let components = URLComponents(string: normalized),
              components.scheme != nil,
              components.host != nil else {
            throw AgentAPIClientError.invalidBaseURL
        }
        self.expectedOrigin = components
        super.init()
    }

    public func execute(_ request: ValidatedAgentRequest, rawKey: String) async throws -> AgentAPIResponse {
        guard let base = expectedOrigin.url,
              let url = URL(string: request.path, relativeTo: base)?.absoluteURL else {
            throw AgentAPIClientError.invalidRequestURL
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = request.method
        urlRequest.setValue(rawKey, forHTTPHeaderField: "X-Agent-Key")
        urlRequest.setValue("BladeAgentKeyManager/1.0", forHTTPHeaderField: "User-Agent")
        if let fileURL = request.fileURL {
            guard let data = try? Data(contentsOf: fileURL, options: .mappedIfSafe) else {
                throw AgentAPIClientError.unreadableUploadFile
            }
            let boundary = "BladeAgentBoundary-\(UUID().uuidString)"
            urlRequest.httpBody = multipartBody(
                data: data,
                filename: safeFilename(fileURL.lastPathComponent),
                contentType: imageContentType(for: fileURL.pathExtension),
                boundary: boundary
            )
            urlRequest.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        } else if let body = request.body {
            urlRequest.httpBody = body
            urlRequest.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        }

        let (data, response) = try await session.data(for: urlRequest)
        guard let http = response as? HTTPURLResponse else {
            throw AgentAPIClientError.invalidResponse
        }
        return AgentAPIResponse(statusCode: http.statusCode, data: data)
    }

    private func multipartBody(data: Data, filename: String, contentType: String, boundary: String) -> Data {
        var body = Data()
        body.append(Data("--\(boundary)\r\n".utf8))
        body.append(Data("Content-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\r\n".utf8))
        body.append(Data("Content-Type: \(contentType)\r\n\r\n".utf8))
        body.append(data)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        return body
    }

    private func safeFilename(_ value: String) -> String {
        value.replacingOccurrences(of: "\"", with: "_")
            .replacingOccurrences(of: "\r", with: "_")
            .replacingOccurrences(of: "\n", with: "_")
    }

    private func imageContentType(for fileExtension: String) -> String {
        switch fileExtension.lowercased() {
        case "jpg", "jpeg": return "image/jpeg"
        case "png": return "image/png"
        case "webp": return "image/webp"
        default: return "application/octet-stream"
        }
    }

    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        guard let components = request.url.flatMap({ URLComponents(url: $0, resolvingAgainstBaseURL: false) }),
              components.scheme?.lowercased() == expectedOrigin.scheme?.lowercased(),
              components.host?.lowercased() == expectedOrigin.host?.lowercased(),
              components.port == expectedOrigin.port else {
            completionHandler(nil)
            return
        }
        completionHandler(request)
    }
}
