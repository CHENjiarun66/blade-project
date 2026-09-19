import BladeAgentKeyKit
import Foundation

struct AuthorizationDecision {
    let key: StoredAgentKey
    let grantDuration: TimeInterval?
}

enum AuthorizationPrompt {
    static func chooseKey(
        for request: ValidatedAgentRequest,
        candidates: [StoredAgentKey],
        preferredKey: String?
    ) -> AuthorizationDecision? {
        guard !candidates.isEmpty else {
            showNoEligibleKey(for: request, storedKeys: [])
            return nil
        }

        let sorted = candidates.sorted { lhs, rhs in
            let lhsPreferred = matchesPreferred(lhs, value: preferredKey)
            let rhsPreferred = matchesPreferred(rhs, value: preferredKey)
            if lhsPreferred != rhsPreferred { return lhsPreferred }
            let lhsAgentMatch = lhs.agentName.localizedCaseInsensitiveCompare(request.agentName) == .orderedSame
            let rhsAgentMatch = rhs.agentName.localizedCaseInsensitiveCompare(request.agentName) == .orderedSame
            if lhsAgentMatch != rhsAgentMatch { return lhsAgentMatch }
            return lhs.name.localizedCompare(rhs.name) == .orderedAscending
        }

        let labels = sorted.enumerated().map { index, key in
            "\(index + 1). \(singleLine(key.name)) · \(singleLine(key.agentName)) · 剩余 \(max(0, key.remainingDays())) 天"
        }
        let title = "\(request.agentName) 请求使用 BladeProject"
        let prompt = "接口：\(request.method) \(request.path)\n需要权限：\(request.requiredScopeSummary)\n\n请选择 Key。这里显示的 Agent 名称来自本机工具配置，只授权你主动启动并信任的 Agent。"
        guard let output = runAppleScript(
            authorizationScript,
            arguments: [title, prompt] + labels
        ) else {
            return nil
        }

        let parts = output.split(separator: "\t", maxSplits: 1).map(String.init)
        guard parts.count == 2,
              let oneBasedIndex = Int(parts[0]),
              sorted.indices.contains(oneBasedIndex - 1) else {
            return nil
        }

        return AuthorizationDecision(
            key: sorted[oneBasedIndex - 1],
            grantDuration: parts[1] == "允许 1 小时" ? 60 * 60 : nil
        )
    }

    static func showInformation(title: String, message: String) {
        _ = runAppleScript(informationScript, arguments: [title, message])
    }

    static func showNoEligibleKey(
        for request: ValidatedAgentRequest,
        storedKeys: [StoredAgentKey]
    ) {
        let activeKeys = storedKeys.filter { !$0.isExpired() }
        if activeKeys.isEmpty {
            showInformation(
                title: "没有可用的 Agent Key",
                message: "本机没有未过期的 Agent Key。请打开 Blade Agent Key Manager 录入或更新 Key。"
            )
        } else {
            showInformation(
                title: "本机权限记录未同步",
                message: "本机保存的 Key 都没有完整登记“\(request.requiredScopeSummary)”权限。这不代表服务器一定没有授权。\n\n请打开 Blade Agent Key Manager，选择对应 Key，点击“同步服务器权限”后重试。"
            )
        }
    }

    private static func runAppleScript(_ source: String, arguments: [String]) -> String? {
        let process = Process()
        let standardOutput = Pipe()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
        process.arguments = ["-e", source, "--"] + arguments
        process.standardOutput = standardOutput
        process.standardError = FileHandle.nullDevice

        do {
            try process.run()
            process.waitUntilExit()
        } catch {
            return nil
        }

        let outputData: Data
        do {
            outputData = try standardOutput.fileHandleForReading.readToEnd() ?? Data()
        } catch {
            return nil
        }

        guard process.terminationStatus == 0,
              let value = String(data: outputData, encoding: .utf8)?
                .trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty,
              value != "DENY" else {
            return nil
        }
        return value
    }

    private static func matchesPreferred(_ key: StoredAgentKey, value: String?) -> Bool {
        guard let value, !value.isEmpty else { return false }
        return key.id.uuidString.caseInsensitiveCompare(value) == .orderedSame ||
            key.name.localizedCaseInsensitiveCompare(value) == .orderedSame ||
            key.keyPrefix.localizedCaseInsensitiveCompare(value) == .orderedSame
    }

    private static func singleLine(_ value: String) -> String {
        value.replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\r", with: " ")
            .replacingOccurrences(of: "\t", with: " ")
    }

    private static let authorizationScript = """
    on run argv
        if (count argv) < 3 then return "DENY"
        set dialogTitle to item 1 of argv
        set dialogPrompt to item 2 of argv
        set choices to items 3 thru -1 of argv
        tell application "System Events"
            activate
            set picked to choose from list choices with title dialogTitle with prompt dialogPrompt OK button name "选择" cancel button name "拒绝"
            if picked is false then return "DENY"
            set selectedLabel to item 1 of picked
            set confirmation to display dialog ("使用：" & selectedLabel & return & return & "Agent 只能获得接口结果，不会看到 Key 原文。") with title dialogTitle buttons {"拒绝", "允许一次", "允许 1 小时"} default button "允许一次" with icon caution
            set actionName to button returned of confirmation
            if actionName is "拒绝" then return "DENY"
            set selectedIndex to 0
            repeat with i from 1 to count choices
                if item i of choices is selectedLabel then
                    set selectedIndex to i
                    exit repeat
                end if
            end repeat
            return (selectedIndex as text) & tab & actionName
        end tell
    end run
    """

    private static let informationScript = """
    on run argv
        if (count argv) < 2 then return
        tell application "System Events"
            activate
            display dialog (item 2 of argv) with title (item 1 of argv) buttons {"知道了"} default button "知道了" with icon note
        end tell
    end run
    """
}
