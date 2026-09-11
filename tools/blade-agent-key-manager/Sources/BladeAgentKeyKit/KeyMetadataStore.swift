import Foundation

public protocol AgentKeyMetadataStoring {
    func load() throws -> [StoredAgentKey]
    func save(_ keys: [StoredAgentKey]) throws
}

public final class KeyMetadataStore: AgentKeyMetadataStoring {
    public static let applicationSupportDirectoryName = "Blade Agent Key Manager"
    public static let metadataFileName = "keys-v1.json"

    private let fileURL: URL
    private let fileManager: FileManager

    public init(fileURL: URL? = nil, fileManager: FileManager = .default) {
        self.fileManager = fileManager
        if let fileURL {
            self.fileURL = fileURL
        } else {
            let root = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            self.fileURL = root
                .appendingPathComponent(Self.applicationSupportDirectoryName, isDirectory: true)
                .appendingPathComponent(Self.metadataFileName)
        }
    }

    public func load() throws -> [StoredAgentKey] {
        guard fileManager.fileExists(atPath: fileURL.path) else { return [] }
        let data = try Data(contentsOf: fileURL)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode([StoredAgentKey].self, from: data)
            .sorted { $0.createdAt > $1.createdAt }
    }

    public func save(_ keys: [StoredAgentKey]) throws {
        let directory = fileURL.deletingLastPathComponent()
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        try fileManager.setAttributes([.posixPermissions: 0o700], ofItemAtPath: directory.path)

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        let data = try encoder.encode(keys)
        try data.write(to: fileURL, options: [.atomic, .completeFileProtection])
        try fileManager.setAttributes([.posixPermissions: 0o600], ofItemAtPath: fileURL.path)
    }

    public var location: URL { fileURL }
}
