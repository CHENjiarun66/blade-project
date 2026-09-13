import BladeAgentKeyKit
import Foundation

final class MCPServer {
    private let agentName: String
    private let preferredKey: String?
    private let runner = RequestRunner()

    init(agentName: String, preferredKey: String?) {
        self.agentName = agentName
        self.preferredKey = preferredKey
    }

    func run() async {
        while let line = readLine(strippingNewline: true) {
            guard !line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { continue }
            do {
                guard let message = try JSONSerialization.jsonObject(with: Data(line.utf8)) as? [String: Any] else {
                    writeError(id: NSNull(), code: -32700, message: "Invalid JSON-RPC message")
                    continue
                }
                try await handle(message)
            } catch {
                writeError(id: NSNull(), code: -32603, message: error.localizedDescription)
            }
        }
    }

    private func handle(_ message: [String: Any]) async throws {
        let method = message["method"] as? String ?? ""
        let id = message["id"] ?? NSNull()

        switch method {
        case "initialize":
            let requestedVersion = ((message["params"] as? [String: Any])?["protocolVersion"] as? String)
                ?? "2025-06-18"
            writeResult(id: id, result: [
                "protocolVersion": requestedVersion,
                "capabilities": ["tools": ["listChanged": false]],
                "serverInfo": ["name": "blade-agent-local", "version": "1.0.0"]
            ])
        case "notifications/initialized":
            return
        case "ping":
            writeResult(id: id, result: [:])
        case "tools/list":
            writeResult(id: id, result: ["tools": toolDefinitions()])
        case "tools/call":
            let params = message["params"] as? [String: Any] ?? [:]
            let name = params["name"] as? String ?? ""
            let arguments = params["arguments"] as? [String: Any] ?? [:]
            await callTool(id: id, name: name, arguments: arguments)
        default:
            if !(method.hasPrefix("notifications/")) {
                writeError(id: id, code: -32601, message: "Method not found")
            }
        }
    }

    private func callTool(id: Any, name: String, arguments: [String: Any]) async {
        do {
            let request = try makeRequest(toolName: name, arguments: arguments)
            guard let response = try await runner.execute(request, preferredKey: preferredKey) else {
                writeToolResult(id: id, text: "用户拒绝了本次 BladeProject 调用。", isError: true)
                return
            }
            let text = String(data: response.data, encoding: .utf8) ?? "<non-UTF8 response>"
            writeToolResult(id: id, text: text, isError: !(200..<300).contains(response.statusCode))
        } catch {
            writeToolResult(id: id, text: error.localizedDescription, isError: true)
        }
    }

    private func makeRequest(toolName: String, arguments: [String: Any]) throws -> AgentAPIRequest {
        switch toolName {
        case "blade_catalog_search":
            var query: [String: Any] = [:]
            for key in ["keyword", "productCode", "colorName", "sizeCode", "limit"] {
                if let value = arguments[key] { query[key] = value }
            }
            return AgentAPIRequest(
                agentName: agentName,
                method: "GET",
                path: try path("/api/agent/catalog/skus", query: query)
            )
        case "blade_products_list":
            var query: [String: Any] = [:]
            for key in ["current", "size", "keyword", "categoryId", "status"] {
                if let value = arguments[key] { query[key] = value }
            }
            return AgentAPIRequest(agentName: agentName, method: "GET",
                                   path: try path("/api/agent/products", query: query))
        case "blade_product_get":
            let id = try positiveInteger(arguments["id"], name: "id")
            return AgentAPIRequest(agentName: agentName, method: "GET", path: "/api/agent/products/\(id)")
        case "blade_product_options":
            return AgentAPIRequest(agentName: agentName, method: "GET", path: "/api/agent/products/options")
        case "blade_product_create":
            guard let payload = arguments["payload"], JSONSerialization.isValidJSONObject(payload) else {
                throw MCPToolError.invalidArguments("payload 必须是符合商品新增接口的 JSON 对象")
            }
            return AgentAPIRequest(agentName: agentName, method: "POST", path: "/api/agent/products",
                                   body: try JSONSerialization.data(withJSONObject: payload))
        case "blade_orders_list":
            var query: [String: Any] = [:]
            for key in ["current", "size", "orderNo", "customerName", "fulfillmentStatus",
                        "collectionStatus", "orderType", "hasBalance", "startDate", "endDate"] {
                if let value = arguments[key] { query[key] = value }
            }
            return AgentAPIRequest(agentName: agentName, method: "GET",
                                   path: try path("/api/agent/orders", query: query))
        case "blade_order_get":
            let id = try positiveInteger(arguments["id"], name: "id")
            return AgentAPIRequest(agentName: agentName, method: "GET", path: "/api/agent/orders/\(id)")
        case "blade_order_drafts_create":
            guard let payload = arguments["payload"], JSONSerialization.isValidJSONObject(payload) else {
                throw MCPToolError.invalidArguments("payload 必须是符合订单草稿接口的 JSON 对象")
            }
            return AgentAPIRequest(
                agentName: agentName,
                method: "POST",
                path: "/api/agent/order-drafts/batch",
                body: try JSONSerialization.data(withJSONObject: payload)
            )
        case "blade_order_draft_source_upload":
            guard let filePath = arguments["filePath"] as? String, filePath.hasPrefix("/") else {
                throw MCPToolError.invalidArguments("filePath 必须是纸单图片的本机绝对路径")
            }
            return AgentAPIRequest(
                agentName: agentName,
                method: "POST",
                path: "/api/agent/order-drafts/source-files",
                fileURL: URL(fileURLWithPath: filePath)
            )
        case "blade_style_trends":
            var query: [String: Any] = [:]
            for key in ["periodType", "startDate", "endDate", "comparePeriods", "limit"] {
                if let value = arguments[key] { query[key] = value }
            }
            return AgentAPIRequest(
                agentName: agentName,
                method: "GET",
                path: try path("/api/agent/analytics/style-trends", query: query)
            )
        case "blade_sku_mix":
            guard let productName = arguments["productName"] as? String, !productName.isEmpty else {
                throw MCPToolError.invalidArguments("productName 不能为空")
            }
            var query: [String: Any] = ["productName": productName]
            for key in ["periodType", "startDate", "endDate", "limit"] {
                if let value = arguments[key] { query[key] = value }
            }
            return AgentAPIRequest(
                agentName: agentName,
                method: "GET",
                path: try path("/api/agent/analytics/sku-mix", query: query)
            )
        default:
            throw MCPToolError.unknownTool
        }
    }

    private func path(_ basePath: String, query: [String: Any]) throws -> String {
        var components = URLComponents()
        components.path = basePath
        components.queryItems = query
            .sorted { $0.key < $1.key }
            .map { URLQueryItem(name: $0.key, value: String(describing: $0.value)) }
        guard let value = components.string else { throw MCPToolError.invalidArguments("查询参数无法编码") }
        return value
    }

    private func positiveInteger(_ value: Any?, name: String) throws -> Int {
        let number: Int?
        if let int = value as? Int { number = int }
        else if let string = value as? String { number = Int(string) }
        else { number = nil }
        guard let number, number > 0 else {
            throw MCPToolError.invalidArguments("\(name) 必须是正整数")
        }
        return number
    }

    private func toolDefinitions() -> [[String: Any]] {
        [
            [
                "name": "blade_catalog_search",
                "description": "在 BladeProject 商品主档中按纸单款号、商品编码、颜色或尺码查询 SKU 候选。系统价格仅作参考，不得覆盖纸单售价。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "keyword": ["type": "string"],
                        "productCode": ["type": "string"],
                        "colorName": ["type": "string"],
                        "sizeCode": ["type": "string"],
                        "limit": ["type": "integer", "minimum": 1, "maximum": 100]
                    ],
                    "additionalProperties": false
                ]
            ],
            [
                "name": "blade_products_list",
                "description": "分页读取 BladeProject 商品主档、颜色尺码和 SKU。响应不含商品或 SKU 成本价。请按页读取，不要请求无上限全量数据。",
                "inputSchema": listSchema(properties: [
                    "keyword": ["type": "string"],
                    "categoryId": ["type": "integer", "minimum": 1],
                    "status": ["type": "integer", "enum": [0, 1]]
                ])
            ],
            [
                "name": "blade_product_get",
                "description": "按商品 ID 读取脱敏商品详情和 SKU。",
                "inputSchema": idSchema()
            ],
            [
                "name": "blade_product_options",
                "description": "读取新增商品可使用的分类、颜色和尺码选项；系统保留占位编码不会返回。",
                "inputSchema": ["type": "object", "properties": [:], "additionalProperties": false]
            ],
            [
                "name": "blade_product_create",
                "description": "新增一个商品并按已有颜色、尺码编码生成 SKU。相同商品编码只返回 DUPLICATE，不会覆盖；不写入库存，也不允许修改或删除。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "payload": ["type": "object", "description": "POST /api/agent/products 的完整 JSON 请求体"]
                    ],
                    "required": ["payload"],
                    "additionalProperties": false
                ]
            ],
            [
                "name": "blade_orders_list",
                "description": "分页读取正式订单与销售、收款和状态事实；不返回客户电话地址、成本或毛利。草稿不在此接口中。",
                "inputSchema": listSchema(properties: [
                    "orderNo": ["type": "string"],
                    "customerName": ["type": "string"],
                    "fulfillmentStatus": ["type": "string"],
                    "collectionStatus": ["type": "string"],
                    "orderType": ["type": "string"],
                    "hasBalance": ["type": "boolean"],
                    "startDate": ["type": "string", "description": "yyyy-MM-dd"],
                    "endDate": ["type": "string", "description": "yyyy-MM-dd"]
                ])
            ],
            [
                "name": "blade_order_get",
                "description": "按订单 ID 读取脱敏正式订单详情和商品明细。",
                "inputSchema": idSchema()
            ],
            [
                "name": "blade_order_draft_source_upload",
                "description": "上传一张纸质订单原图，返回 fileId。创建草稿时把同一订单的 fileId 按纸张顺序写入 sourceFileIds；最多 10 张。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "filePath": ["type": "string", "description": "JPG、PNG 或 WEBP 图片的本机绝对路径"]
                    ],
                    "required": ["filePath"],
                    "additionalProperties": false
                ]
            ],
            [
                "name": "blade_order_drafts_create",
                "description": "批量创建待人工复核的订单草稿。先上传原图，再把返回的 fileId 写入各订单 sourceFileIds。只能创建草稿，不能确认正式订单、收款或变更库存。externalRefNo 必须稳定并用于幂等。",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "payload": ["type": "object", "description": "POST /api/agent/order-drafts/batch 的完整 JSON 请求体"]
                    ],
                    "required": ["payload"],
                    "additionalProperties": false
                ]
            ],
            [
                "name": "blade_style_trends",
                "description": "读取 BladeProject 款式多周期趋势事实包。",
                "inputSchema": analyticsSchema(requiresProduct: false)
            ],
            [
                "name": "blade_sku_mix",
                "description": "读取某款商品的颜色、尺码和整款录入结构事实。",
                "inputSchema": analyticsSchema(requiresProduct: true)
            ]
        ]
    }

    private func listSchema(properties extra: [String: Any]) -> [String: Any] {
        var properties = extra
        properties["current"] = ["type": "integer", "minimum": 1]
        properties["size"] = ["type": "integer", "minimum": 1, "maximum": 100]
        return ["type": "object", "properties": properties, "additionalProperties": false]
    }

    private func idSchema() -> [String: Any] {
        [
            "type": "object",
            "properties": ["id": ["type": "integer", "minimum": 1]],
            "required": ["id"],
            "additionalProperties": false
        ]
    }

    private func analyticsSchema(requiresProduct: Bool) -> [String: Any] {
        var properties: [String: Any] = [
            "periodType": ["type": "string", "enum": ["TODAY", "WEEK", "MONTH", "QUARTER", "YEAR", "CUSTOM"]],
            "startDate": ["type": "string", "description": "yyyy-MM-dd"],
            "endDate": ["type": "string", "description": "yyyy-MM-dd"],
            "limit": ["type": "integer", "minimum": 1, "maximum": 100]
        ]
        if requiresProduct {
            properties["productName"] = ["type": "string"]
        } else {
            properties["comparePeriods"] = ["type": "integer", "minimum": 1, "maximum": 6]
        }
        return [
            "type": "object",
            "properties": properties,
            "required": requiresProduct ? ["productName"] : [],
            "additionalProperties": false
        ]
    }

    private func writeToolResult(id: Any, text: String, isError: Bool) {
        writeResult(id: id, result: [
            "content": [["type": "text", "text": text]],
            "isError": isError
        ])
    }

    private func writeResult(id: Any, result: Any) {
        writeJSON(["jsonrpc": "2.0", "id": id, "result": result])
    }

    private func writeError(id: Any, code: Int, message: String) {
        writeJSON([
            "jsonrpc": "2.0",
            "id": id,
            "error": ["code": code, "message": message]
        ])
    }

    private func writeJSON(_ object: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: object),
              var line = String(data: data, encoding: .utf8) else { return }
        line.append("\n")
        FileHandle.standardOutput.write(Data(line.utf8))
    }
}

private enum MCPToolError: LocalizedError {
    case unknownTool
    case invalidArguments(String)

    var errorDescription: String? {
        switch self {
        case .unknownTool:
            return "未知的 BladeProject 工具"
        case .invalidArguments(let message):
            return message
        }
    }
}
