import XCTest
@testable import BladeAgentKeyKit

final class BladeAgentKeyKitTests: XCTestCase {
    func testValidInputNormalizesMetadataWithoutKeepingRawKey() throws {
        let input = KeyInput(
            name: "  生产纸单  ",
            agentName: " DeepSeek ",
            rawKey: " agk_example.very-secret-value ",
            baseURL: "https://www.chenjianas.asia:33294/"
        )

        let result = try KeyInputValidator.validate(input)

        XCTAssertEqual(result.name, "生产纸单")
        XCTAssertEqual(result.agentName, "DeepSeek")
        XCTAssertEqual(result.keyPrefix, "agk_example")
        XCTAssertEqual(result.baseURL, "https://www.chenjianas.asia:33294")
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
            lastSyncedAt: Date(timeIntervalSince1970: 1_850_000_000),
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

    func testMetadataWithoutLastSyncedAtRemainsReadableAfterUpgrade() throws {
        let data = Data(#"""
        [{
          "id": "00000000-0000-0000-0000-000000000001",
          "name": "旧版 Key",
          "agentName": "DeepSeek",
          "keyPrefix": "agk_legacy",
          "baseURL": "https://example.com",
          "expiresAt": "2030-03-17T17:46:40Z",
          "scopes": ["catalog:read"],
          "createdAt": "2027-01-15T08:00:00Z"
        }]
        """#.utf8)
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let file = directory.appendingPathComponent("keys.json")
        defer { try? FileManager.default.removeItem(at: directory) }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try data.write(to: file)

        let loaded = try KeyMetadataStore(fileURL: file).load()

        XCTAssertEqual(loaded.count, 1)
        XCTAssertEqual(loaded[0].keyPrefix, "agk_legacy")
        XCTAssertNil(loaded[0].lastSyncedAt)
    }

    func testCapabilitiesResponseDecodesServerScopesAndDates() throws {
        let data = Data(#"""
        {
          "code": 200,
          "message": "success",
          "data": {
            "keyPrefix": "agk_example",
            "name": "DeepSeek 生产",
            "scopes": ["catalog:read", "products:read", "orders:write", "products:cost:write"],
            "expiresAt": "2026-12-17T10:14:16Z",
            "serverTime": "2026-09-18T10:14:16.123Z"
          }
        }
        """#.utf8)

        let result = try AgentAPIClient.decodeCapabilities(data)

        XCTAssertEqual(result.keyPrefix, "agk_example")
        XCTAssertEqual(result.name, "DeepSeek 生产")
        XCTAssertEqual(result.scopes, [.catalogRead, .productsRead, .ordersWrite, .productsCostWrite])
        XCTAssertGreaterThan(result.expiresAt, result.serverTime)
    }

    func testCapabilitiesResponseRejectsUnknownServerScope() {
        let data = Data(#"""
        {
          "code": 200,
          "data": {
            "keyPrefix": "agk_example",
            "name": "future",
            "scopes": ["future:read"],
            "expiresAt": "2026-12-17T10:14:16Z",
            "serverTime": "2026-09-18T10:14:16Z"
          }
        }
        """#.utf8)

        XCTAssertThrowsError(try AgentAPIClient.decodeCapabilities(data)) { error in
            XCTAssertEqual(
                error.localizedDescription,
                AgentAPIClientError.unsupportedServerScopes(["future:read"]).localizedDescription
            )
        }
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

        let products = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "Codex", method: "GET", path: "/api/agent/products/42"
        ))
        XCTAssertEqual(products.requiredScope, .productsRead)

        let productCreate = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "Codex", method: "POST", path: "/api/agent/products", body: Data("{}".utf8)
        ))
        XCTAssertEqual(productCreate.requiredScope, .productsCreate)
        XCTAssertEqual(productCreate.requiredScopes, [.productsCreate])

        let productCreateWithCost = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "Codex",
            method: "POST",
            path: "/api/agent/products",
            body: Data(#"{"productCode":"7000#","costPrice":18.5}"#.utf8)
        ))
        XCTAssertEqual(productCreateWithCost.requiredScopes, [.productsCreate, .productsCostWrite])

        let draftsWithCost = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "Codex",
            method: "POST",
            path: "/api/agent/order-drafts/batch",
            body: Data(#"{"orders":[{"freightCost":2,"items":[{"costPrice":8}]}]}"#.utf8)
        ))
        XCTAssertEqual(draftsWithCost.requiredScopes, [.ordersWrite, .ordersCostWrite])

        let orders = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "Codex", method: "GET", path: "/api/agent/orders?current=1&size=100"
        ))
        XCTAssertEqual(orders.requiredScope, .ordersRead)

        let customers = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "Codex", method: "GET", path: "/api/agent/customers/42"
        ))
        XCTAssertEqual(customers.requiredScope, .customersRead)

        let customerCreate = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "Codex", method: "POST", path: "/api/agent/customers", body: Data("{}".utf8)
        ))
        XCTAssertEqual(customerCreate.requiredScope, .customersCreate)

        XCTAssertThrowsError(try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "Codex", method: "GET", path: "/api/agent/orders/not-a-number"
        )))

        XCTAssertThrowsError(try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "Codex", method: "GET", path: "/api/agent/customers/not-a-number"
        )))

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

    func testCostWritingRequiresBaseAndSensitiveScopesOnTheSameKey() throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let request = try AgentRequestPolicy.validate(AgentAPIRequest(
            agentName: "DeepSeek",
            method: "POST",
            path: "/api/agent/products",
            body: Data(#"{"productCode":"7000#","costPrice":18.5}"#.utf8)
        ))
        let createOnly = StoredAgentKey(
            name: "create only", agentName: "DeepSeek", keyPrefix: "agk_create",
            baseURL: "https://example.com", expiresAt: now.addingTimeInterval(3600),
            scopes: [.productsCreate]
        )
        let costOnly = StoredAgentKey(
            name: "cost only", agentName: "DeepSeek", keyPrefix: "agk_cost",
            baseURL: "https://example.com", expiresAt: now.addingTimeInterval(3600),
            scopes: [.productsCostWrite]
        )
        let both = StoredAgentKey(
            name: "both", agentName: "DeepSeek", keyPrefix: "agk_both",
            baseURL: "https://example.com", expiresAt: now.addingTimeInterval(3600),
            scopes: [.productsCreate, .productsCostWrite]
        )

        XCTAssertEqual(
            AgentRequestPolicy.eligibleKeys(for: request, in: [createOnly, costOnly, both], now: now),
            [both]
        )
    }

    func testAPIClientRevalidatesStoredBaseURLBeforeSending() {
        XCTAssertThrowsError(try AgentAPIClient(baseURL: "http://evil.example")) { error in
            XCTAssertEqual(error.localizedDescription, AgentAPIClientError.invalidBaseURL.localizedDescription)
        }
    }
}
