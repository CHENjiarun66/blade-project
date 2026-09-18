import AppKit
import BladeAgentKeyKit
import SwiftUI

struct ContentView: View {
    @ObservedObject var model: AppModel
    @State private var pendingDelete: StoredAgentKey?

    var body: some View {
        NavigationSplitView {
            VStack(spacing: 0) {
                List(model.keys, selection: $model.selectedID) { key in
                    KeyRow(key: key)
                        .tag(key.id)
                        .contextMenu {
                            Button("删除本机 Key…", role: .destructive) {
                                pendingDelete = key
                            }
                        }
                }
                .listStyle(.sidebar)

                HStack {
                    Text("共 \(model.keys.count) 把 Key")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Button {
                        model.isShowingAddSheet = true
                    } label: {
                        Label("新增", systemImage: "plus")
                    }
                    .buttonStyle(.borderless)
                }
                .padding(12)
                .background(.bar)
            }
            .navigationTitle("Agent Key")
            .frame(minWidth: 270)
        } detail: {
            if let key = model.selectedKey {
                KeyDetailView(key: key, helperPath: model.helperPath) {
                    model.copyConnectionGuide(agentName: key.agentName)
                } syncAction: {
                    Task { await model.syncCapabilities(for: key) }
                } deleteAction: {
                    pendingDelete = key
                } isSyncing: {
                    model.isSyncing(key)
                }
            } else {
                EmptyStateView {
                    model.isShowingAddSheet = true
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    model.isShowingAddSheet = true
                } label: {
                    Label("录入 Key", systemImage: "plus")
                }
            }
        }
        .sheet(isPresented: $model.isShowingAddSheet) {
            AddKeyView(model: model)
        }
        .alert(
            "操作失败",
            isPresented: Binding(
                get: { model.errorMessage != nil },
                set: { if !$0 { model.errorMessage = nil } }
            )
        ) {
            Button("知道了") { model.errorMessage = nil }
        } message: {
            Text(model.errorMessage ?? "未知错误")
        }
        .alert(
            "同步完成",
            isPresented: Binding(
                get: { model.noticeMessage != nil },
                set: { if !$0 { model.noticeMessage = nil } }
            )
        ) {
            Button("知道了") { model.noticeMessage = nil }
        } message: {
            Text(model.noticeMessage ?? "")
        }
        .confirmationDialog(
            "从本机删除 \(pendingDelete?.name ?? "")？",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            )
        ) {
            Button("删除本机 Key", role: .destructive) {
                if let pendingDelete { model.delete(pendingDelete) }
                pendingDelete = nil
            }
            Button("取消", role: .cancel) { pendingDelete = nil }
        } message: {
            Text("这只会清除本机钥匙串和本机记录，不会停用服务器上的 Key。若要彻底撤销访问，还需要到 BladeProject 系统管理中停用它。")
        }
    }
}

private struct KeyRow: View {
    let key: StoredAgentKey

    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(statusColor)
                .frame(width: 9, height: 9)
            VStack(alignment: .leading, spacing: 3) {
                Text(key.name)
                    .font(.headline)
                    .lineLimit(1)
                Text("\(key.agentName) · \(remainingText)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, 5)
    }

    private var statusColor: Color {
        if key.isExpired() { return .red }
        if key.remainingDays() <= 14 { return .orange }
        return .green
    }

    private var remainingText: String {
        let days = key.remainingDays()
        if key.isExpired() { return "已过期" }
        if days == 0 { return "今天到期" }
        return "剩余 \(days) 天"
    }
}

private struct EmptyStateView: View {
    let addAction: () -> Void

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "key.horizontal")
                .font(.system(size: 52, weight: .light))
                .foregroundStyle(.blue)
            Text("还没有保存 Agent Key")
                .font(.title2.bold())
            Text("从 BladeProject 系统管理复制完整 Key，\n在这里录入后会安全保存到 macOS 钥匙串。")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            Button("录入第一把 Key", action: addAction)
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(nsColor: .windowBackgroundColor))
    }
}

private struct KeyDetailView: View {
    let key: StoredAgentKey
    let helperPath: String?
    let copyGuideAction: () -> Void
    let syncAction: () -> Void
    let deleteAction: () -> Void
    let isSyncing: () -> Bool
    @State private var didCopy = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                HStack(alignment: .top, spacing: 16) {
                    Image(systemName: "key.horizontal.fill")
                        .font(.system(size: 28))
                        .foregroundStyle(.white)
                        .frame(width: 58, height: 58)
                        .background(statusColor.gradient, in: RoundedRectangle(cornerRadius: 15))

                    VStack(alignment: .leading, spacing: 5) {
                        Text(key.name)
                            .font(.largeTitle.bold())
                        Text("供 \(key.agentName) 使用")
                            .font(.title3)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    StatusBadge(key: key)
                }

                GroupBox {
                    VStack(spacing: 0) {
                        DetailLine(label: "Key 前缀", value: key.keyPrefix, monospaced: true)
                        Divider()
                        DetailLine(label: "API 地址", value: key.baseURL, monospaced: true)
                        Divider()
                        DetailLine(label: "到期日期", value: key.expiresAt.formatted(date: .long, time: .shortened))
                        Divider()
                        DetailLine(label: "剩余时间", value: remainingText)
                        Divider()
                        DetailLine(label: "录入时间", value: key.createdAt.formatted(date: .long, time: .shortened))
                        if let lastSyncedAt = key.lastSyncedAt {
                            Divider()
                            DetailLine(label: "权限同步", value: lastSyncedAt.formatted(date: .long, time: .shortened))
                        }
                    }
                    .padding(4)
                } label: {
                    Label("基本信息", systemImage: "info.circle")
                        .font(.headline)
                }

                GroupBox {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(key.scopes.sorted(by: { $0.rawValue < $1.rawValue })) { scope in
                            HStack(alignment: .top, spacing: 10) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(.green)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(scope.displayName).fontWeight(.semibold)
                                    Text(scope.detail)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        Divider()
                        HStack {
                            Text("权限与有效期以服务器记录为准。调整或轮换 Key 后请重新同步。")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Spacer()
                            Button(action: syncAction) {
                                if isSyncing() {
                                    ProgressView()
                                        .controlSize(.small)
                                } else {
                                    Label("同步服务器权限", systemImage: "arrow.triangle.2.circlepath")
                                }
                            }
                            .disabled(isSyncing())
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(4)
                } label: {
                    Label("权限范围", systemImage: "checkmark.shield")
                        .font(.headline)
                }

                GroupBox {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("其他 Agent 不会读取 Key 原文。它们通过本机调用工具发起请求，你会先看到 Agent 名称、接口和可选 Key，再决定是否授权。")
                            .foregroundStyle(.secondary)
                        if let helperPath {
                            Text(helperPath)
                                .font(.system(.caption, design: .monospaced))
                                .textSelection(.enabled)
                                .padding(10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                        } else {
                            Label("调用工具尚未安装，请使用打包后的应用", systemImage: "exclamationmark.triangle.fill")
                                .foregroundStyle(.orange)
                        }
                        Button {
                            copyGuideAction()
                            didCopy = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2) { didCopy = false }
                        } label: {
                            Label(didCopy ? "接入说明已复制" : "复制 Agent 接入说明", systemImage: didCopy ? "checkmark" : "doc.on.doc")
                        }
                        .disabled(helperPath == nil)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(4)
                } label: {
                    Label("安全授权", systemImage: "person.badge.key")
                        .font(.headline)
                }

                HStack {
                    Spacer()
                    Button("删除本机 Key…", role: .destructive, action: deleteAction)
                }
            }
            .padding(32)
            .frame(maxWidth: 760)
            .frame(maxWidth: .infinity)
        }
        .navigationTitle(key.name)
        .background(Color(nsColor: .windowBackgroundColor))
    }

    private var statusColor: Color {
        if key.isExpired() { return .red }
        if key.remainingDays() <= 14 { return .orange }
        return .blue
    }

    private var remainingText: String {
        let days = key.remainingDays()
        if key.isExpired() { return "已过期 \(abs(days)) 天" }
        if days == 0 { return "今天到期" }
        return "\(days) 天"
    }
}

private struct StatusBadge: View {
    let key: StoredAgentKey

    var body: some View {
        Text(label)
            .font(.callout.bold())
            .foregroundStyle(color)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(color.opacity(0.12), in: Capsule())
    }

    private var label: String {
        if key.isExpired() { return "已过期" }
        if key.remainingDays() <= 14 { return "即将到期" }
        return "可使用"
    }

    private var color: Color {
        if key.isExpired() { return .red }
        if key.remainingDays() <= 14 { return .orange }
        return .green
    }
}

private struct DetailLine: View {
    let label: String
    let value: String
    var monospaced = false

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .foregroundStyle(.secondary)
                .frame(width: 90, alignment: .leading)
            Text(value)
                .font(monospaced ? .system(.body, design: .monospaced) : .body)
                .textSelection(.enabled)
            Spacer()
        }
        .padding(.vertical, 11)
    }
}

private struct AddKeyView: View {
    @ObservedObject var model: AppModel
    @Environment(\.dismiss) private var dismiss

    @State private var name = "生产环境纸单录入"
    @State private var agentName = ""
    @State private var rawKey = ""
    @State private var baseURL = "https://www.chenjianas.asia:33294"

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("录入 Agent Key")
                        .font(.title2.bold())
                    Text("完整 Key 只写入 macOS 钥匙串，列表仅保存非敏感信息。")
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button("取消") { dismiss() }
                    .keyboardShortcut(.cancelAction)
            }
            .padding(24)

            Divider()

            Form {
                Section("识别信息") {
                    TextField("Key 名称", text: $name, prompt: Text("例如：生产环境纸单录入"))
                    TextField("使用 Agent", text: $agentName, prompt: Text("例如：DeepSeek、ZCode、Codex"))
                }

                Section("连接信息") {
                    HStack {
                        SecureField("完整 Agent Key", text: $rawKey)
                        Button("从剪贴板粘贴") {
                            rawKey = NSPasteboard.general.string(forType: .string) ?? ""
                        }
                    }
                    TextField("API 地址", text: $baseURL)
                        .font(.system(.body, design: .monospaced))
                }

                Section("服务器验证") {
                    Label("保存前会使用这把 Key 查询服务器，并自动填充真实权限和到期时间。无需再次手动选择权限。", systemImage: "checkmark.shield")
                        .foregroundStyle(.secondary)
                    Text("验证请求只访问 /api/agent/capabilities，不读取业务数据；完整 Key 仍只保存到 macOS 钥匙串。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .formStyle(.grouped)

            Divider()

            HStack {
                Label("保存后，如剪贴板仍是这把 Key，会自动清空。", systemImage: "lock.shield")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Button {
                    let input = KeyInput(name: name, agentName: agentName, rawKey: rawKey, baseURL: baseURL)
                    Task {
                        if await model.add(input) { dismiss() }
                    }
                } label: {
                    if model.isAddingKey {
                        HStack {
                            ProgressView().controlSize(.small)
                            Text("正在验证…")
                        }
                    } else {
                        Text("验证并保存到钥匙串")
                    }
                }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut(.defaultAction)
                .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                          agentName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                          rawKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                          baseURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                          model.isAddingKey)
            }
            .padding(20)
        }
        .frame(width: 640, height: 560)
    }
}
