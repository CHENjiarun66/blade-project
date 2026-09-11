import AppKit
import BladeAgentKeyKit
import Foundation

struct AuthorizationDecision {
    let key: StoredAgentKey
    let rememberForTenMinutes: Bool
}

@MainActor
enum AuthorizationPrompt {
    static func chooseKey(
        for request: ValidatedAgentRequest,
        candidates: [StoredAgentKey],
        preferredKey: String?
    ) -> AuthorizationDecision? {
        guard !candidates.isEmpty else {
            showInformation(
                title: "没有可用的 Agent Key",
                message: "没有未过期且包含“\(request.requiredScope.displayName)”权限的 Key。请先打开 Blade Agent Key Manager 录入。"
            )
            return nil
        }

        let app = NSApplication.shared
        app.setActivationPolicy(.accessory)
        app.activate(ignoringOtherApps: true)

        let sorted = candidates.sorted { lhs, rhs in
            let lhsPreferred = matchesPreferred(lhs, value: preferredKey)
            let rhsPreferred = matchesPreferred(rhs, value: preferredKey)
            if lhsPreferred != rhsPreferred { return lhsPreferred }
            let lhsAgentMatch = lhs.agentName.localizedCaseInsensitiveCompare(request.agentName) == .orderedSame
            let rhsAgentMatch = rhs.agentName.localizedCaseInsensitiveCompare(request.agentName) == .orderedSame
            if lhsAgentMatch != rhsAgentMatch { return lhsAgentMatch }
            return lhs.name.localizedCompare(rhs.name) == .orderedAscending
        }

        let alert = NSAlert()
        alert.alertStyle = .warning
        alert.icon = NSImage(systemSymbolName: "person.badge.key.fill", accessibilityDescription: nil)
        alert.messageText = "\(request.agentName) 请求使用 BladeProject"
        alert.informativeText = "接口：\(request.method) \(request.path)\n需要权限：\(request.requiredScope.displayName)\n\n请选择 Key 并确认。Agent 只能获得接口结果，不会看到 Key 原文。"
        alert.addButton(withTitle: "允许")
        alert.addButton(withTitle: "拒绝")

        let accessory = NSStackView()
        accessory.orientation = .vertical
        accessory.alignment = .leading
        accessory.spacing = 10
        accessory.translatesAutoresizingMaskIntoConstraints = false

        let label = NSTextField(labelWithString: "使用哪一把 Key")
        label.font = .systemFont(ofSize: 12, weight: .semibold)
        accessory.addArrangedSubview(label)

        let popup = NSPopUpButton(frame: NSRect(x: 0, y: 0, width: 430, height: 28), pullsDown: false)
        for key in sorted {
            popup.addItem(withTitle: "\(key.name) · \(key.agentName) · 剩余 \(max(0, key.remainingDays())) 天")
        }
        popup.selectItem(at: 0)
        accessory.addArrangedSubview(popup)

        let remember = NSButton(checkboxWithTitle: "同一 Agent 和权限在接下来的 10 分钟内不再询问", target: nil, action: nil)
        remember.state = .off
        accessory.addArrangedSubview(remember)

        let warning = NSTextField(wrappingLabelWithString: "提示：这里显示的 Agent 名称来自本机工具配置，首版不对调用进程做密码学身份校验。只给你主动启动并信任的本机 Agent 授权。")
        warning.textColor = .secondaryLabelColor
        warning.font = .systemFont(ofSize: 11)
        warning.maximumNumberOfLines = 3
        warning.preferredMaxLayoutWidth = 430
        accessory.addArrangedSubview(warning)

        alert.accessoryView = accessory
        let response = alert.runModal()
        guard response == .alertFirstButtonReturn else { return nil }

        let selectedIndex = max(0, popup.indexOfSelectedItem)
        return AuthorizationDecision(
            key: sorted[selectedIndex],
            rememberForTenMinutes: remember.state == .on
        )
    }

    static func showInformation(title: String, message: String) {
        let app = NSApplication.shared
        app.setActivationPolicy(.accessory)
        app.activate(ignoringOtherApps: true)
        let alert = NSAlert()
        alert.alertStyle = .informational
        alert.messageText = title
        alert.informativeText = message
        alert.addButton(withTitle: "知道了")
        alert.runModal()
    }

    private static func matchesPreferred(_ key: StoredAgentKey, value: String?) -> Bool {
        guard let value, !value.isEmpty else { return false }
        return key.id.uuidString.caseInsensitiveCompare(value) == .orderedSame ||
            key.name.localizedCaseInsensitiveCompare(value) == .orderedSame ||
            key.keyPrefix.localizedCaseInsensitiveCompare(value) == .orderedSame
    }
}
