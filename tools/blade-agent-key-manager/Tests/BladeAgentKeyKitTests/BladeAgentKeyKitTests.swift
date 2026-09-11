import XCTest
@testable import BladeAgentKeyKit

final class BladeAgentKeyKitTests: XCTestCase {
    func testValidInputNormalizesMetadataWithoutKeepingRawKey() throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let input = KeyInput(
            name: "  生产纸单  ",
            agentName: " DeepSeek ",
            rawKey: " agk_example.very-secret-value ",
            baseURL: "https://www.chenjianas.asia:33294/",
            expiresAt: now.addingTimeInterval(86_400),
            scopes: [.catalogRead, .ordersWrite]
        )

        let result = try KeyInputValidator.validate(input, now: now)

        XCTAssertEqual(result.metadata.name, "生产纸单")
        XCTAssertEqual(result.metadata.agentName, "DeepSeek")
        XCTAssertEqual(result.metadata.keyPrefix, "agk_example")
        XCTAssertEqual(result.metadata.baseURL, "https://www.chenjianas.asia:33294")
        XCTAssertEqual(result.rawKey, "agk_example.very-secret-value")
    }

    func testRemoteHTTPBaseURLIsRejected() {
        XCTAssertThrowsError(try KeyInputValidator.normalizedBaseURL("http://example.com")) { error in
            XCTAssertEqual(error as? KeyValidationError, .insecureRemoteURL)
        }
    }

    func testLocalHTTPBaseURLIsAllowed() throws {
        XCTAssertEqual(
            try KeyInputValidator.normalizedBaseURL("http://127.0.0.1:8080/"),
            "http://127.0.0.1:8080"
        )
    }

    func testMetadataRoundTripUsesOnlyNonSecretFields() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let file = directory.appendingPathComponent("keys.json")
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = KeyMetadataStore(fileURL: file)
        let key = StoredAgentKey(
            name: "分析",
            agentName: "Codex",
            keyPrefix: "agk_public",
            baseURL: "https://example.com",
            expiresAt: Date(timeIntervalSince1970: 1_900_000_000),
            scopes: [.analyticsRead],
            createdAt: Date(timeIntervalSince1970: 1_800_000_000)
        )

        try store.save([key])
        let loaded = try store.load()
        let rawJSON = try String(contentsOf: file)

        XCTAssertEqual(loaded, [key])
        XCTAssertFalse(rawJSON.contains("secret"))
        XCTAssertTrue(rawJSON.contains("agk_public"))
        let fileMode = try FileManager.default.attributesOfItem(atPath: file.path)[.posixPermissions] as? NSNumber
        let directoryMode = try FileManager.default.attributesOfItem(atPath: directory.path)[.posixPermissions] as? NSNumber
        XCTAssertEqual(fileMode?.intValue, 0o600)
        XCTAssertEqual(directoryMode?.intValue, 0o700)
    }

    func testRequestPolicyMapsOnlyAllowlistedEndpoints() throws {
        let catalog = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "DeepSeek",
            method: "GET",
            path: "/api/agent/catalog/skus?keyword=7000%23"
        ))
        XCTAssertEqual(catalog.requiredScope, .catalogRead)

        let drafts = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "ZCode",
            method: "POST",
            path: "/api/agent/order-drafts/batch",
            body: Data(#"{"orders":[]}"#.utf8)
        ))
        XCTAssertEqual(drafts.requiredScope, .ordersWrite)

        XCTAssertThrowsError(try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "DeepSeek",
            method: "POST",
            path: "/api/agent/action"
        ))) { error in
            XCTAssertEqual(error as? AgentRequestPolicyError, .unsupportedEndpoint)
        }
    }

    func testRequestPolicyAllowsPaperImageUploadOnlyForSafeLocalImages() throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".jpg")
        try Data([0xff, 0xd8, 0xff, 0xd9]).write(to: file)
        defer { try? FileManager.default.removeItem(at: file) }

        let upload = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "DeepSeek",
            method: "POST",
            path: "/api/agent/order-drafts/source-files",
            fileURL: file
        ))
        XCTAssertEqual(upload.requiredScope, .ordersWrite)
        XCTAssertEqual(upload.fileURL, file)

        let json = file.deletingPathExtension().appendingPathExtension("json")
        try Data("{}".utf8).write(to: json)
        defer { try? FileManager.default.removeItem(at: json) }
        XCTAssertThrowsError(try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "DeepSeek",
            method: "POST",
            path: "/api/agent/order-drafts/source-files",
            fileURL: json
        ))) { error in
            XCTAssertEqual(error as? AgentRequestPolicyError, .invalidUploadFile)
        }
    }

    func testRequestPolicyRejectsAbsoluteAndTraversalPaths() {
        for path in ["https://evil.example/api/agent/catalog/skus", "/api/agent/../system/users"] {
            XCTAssertThrowsError(try AgentRequestPolicy.validate(AgentAPIRequest(
                agentName: "DeepSeek",
                method: "GET",
                path: path
            )))
        }
    }

    func testEligibleKeysRequireScopeAndUnexpiredState() throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let request = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "DeepSeek",
            method: "GET",
            path: "/api/agent/catalog/skus"
        ))
        let eligible = StoredAgentKey(
            name: "catalog",
            agentName: "DeepSeek",
            keyPrefix: "agk_1",
            baseURL: "https://example.com",
            expiresAt: now.addingTimeInterval(3600),
            scopes: [.catalogRead]
        )
        let expired = StoredAgentKey(
            name: "expired",
            agentName: "DeepSeek",
            keyPrefix: "agk_2",
            baseURL: "https://example.com",
            expiresAt: now.addingTimeInterval(-1),
            scopes: [.catalogRead]
        )
        let wrongScope = StoredAgentKey(
            name: "analytics",
            agentName: "DeepSeek",
            keyPrefix: "agk_3",
            baseURL: "https://example.com",
            expiresAt: now.addingTimeInterval(3600),
            scopes: [.analyticsRead]
        )

        XCTAssertEqual(
            AgentRequestPolicy.eligibleKeys(for: request, in: [eligible, expired, wrongScope], now: now),
            [eligible]
        )
    }

    func testAPIClientRevalidatesStoredBaseURLBeforeSending() {
        XCTAssertThrowsError(try AgentAPIClient(baseURL: "http://evil.example")) { error in
            XCTAssertEqual(error.localizedDescription, AgentAPIClientError.invalidBaseURL.localizedDescription)
        }
    }
}
