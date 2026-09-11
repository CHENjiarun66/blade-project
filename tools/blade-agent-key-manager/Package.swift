// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "BladeAgentKeyManager",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .library(name: "BladeAgentKeyKit", targets: ["BladeAgentKeyKit"]),
        .executable(name: "BladeAgentKeyManager", targets: ["BladeAgentKeyManager"]),
        .executable(name: "blade-agent-request", targets: ["BladeAgentRequest"])
    ],
    targets: [
        .target(
            name: "BladeAgentKeyKit",
            linkerSettings: [
                .linkedFramework("Security"),
                .linkedFramework("LocalAuthentication")
            ]
        ),
        .executableTarget(
            name: "BladeAgentKeyManager",
            dependencies: ["BladeAgentKeyKit"]
        ),
        .executableTarget(
            name: "BladeAgentRequest",
            dependencies: ["BladeAgentKeyKit"]
        ),
        .testTarget(
            name: "BladeAgentKeyKitTests",
            dependencies: ["BladeAgentKeyKit"]
        )
    ]
)
