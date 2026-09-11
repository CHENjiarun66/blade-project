import BladeAgentKeyKit
import Darwin
import Foundation

@main
struct BladeAgentRequestMain {
    static func main() async {
        do {
            let options = try CommandLineOptions(arguments: Array(CommandLine.arguments.dropFirst()))
            if options.mcpMode {
                await MCPServer(agentName: options.agentName, preferredKey: options.preferredKey).run()
                return
            }

            let body: Data?
            if let bodyFile = options.bodyFile {
                body = try Data(contentsOf: URL(fileURLWithPath: bodyFile))
            } else {
                body = nil
            }
            let request = AgentAPIRequest(
                agentName: options.agentName,
                method: options.method,
                path: options.path,
                body: body,
                fileURL: options.filePath.map { URL(fileURLWithPath: $0) }
            )
            let runner = RequestRunner()
            guard let response = try await runner.execute(request, preferredKey: options.preferredKey) else {
                writeError("用户拒绝了本次 BladeProject 调用。")
                exit(77)
            }
            FileHandle.standardOutput.write(response.data)
            if !response.data.isEmpty { FileHandle.standardOutput.write(Data("\n".utf8)) }
            if !(200..<300).contains(response.statusCode) { exit(Int32(response.statusCode == 401 ? 78 : 1)) }
        } catch {
            writeError(error.localizedDescription)
            exit(64)
        }
    }

    private static func writeError(_ value: String) {
        FileHandle.standardError.write(Data("blade-agent-request: \(value)\n".utf8))
    }
}

private struct CommandLineOptions {
    let mcpMode: Bool
    let agentName: String
    let preferredKey: String?
    let method: String
    let path: String
    let bodyFile: String?
    let filePath: String?

    init(arguments: [String]) throws {
        var values: [String: String] = [:]
        var flags: Set<String> = []
        var index = 0
        while index < arguments.count {
            let token = arguments[index]
            if token == "--mcp" {
                flags.insert(token)
                index += 1
                continue
            }
            guard token.hasPrefix("--"), index + 1 < arguments.count else {
                throw CommandLineError.invalidUsage
            }
            values[token] = arguments[index + 1]
            index += 2
        }

        guard let agentName = values["--agent"]?.trimmingCharacters(in: .whitespacesAndNewlines),
              !agentName.isEmpty else {
            throw CommandLineError.missingAgent
        }
        self.agentName = agentName
        self.preferredKey = values["--key"]
        self.mcpMode = flags.contains("--mcp")
        self.method = values["--method"] ?? "GET"
        self.path = values["--path"] ?? ""
        self.bodyFile = values["--body-file"]
        self.filePath = values["--file"]

        if !mcpMode && (path.isEmpty
            || (bodyFile != nil && filePath != nil)
            || (filePath != nil && filePath?.hasPrefix("/") != true)) {
            throw CommandLineError.invalidUsage
        }
    }
}

private enum CommandLineError: LocalizedError {
    case missingAgent
    case invalidUsage

    var errorDescription: String? {
        switch self {
        case .missingAgent:
            return "必须通过 --agent 声明调用方，例如 --agent DeepSeek"
        case .invalidUsage:
            return "用法：blade-agent-request --agent <名称> --method GET|POST --path </api/agent/...> [--body-file <json> | --file <图片>] [--key <名称或前缀>]，或使用 --mcp 模式"
        }
    }
}
