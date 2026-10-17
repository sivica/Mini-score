// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "MiniscoreCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "MiniscoreCore", targets: ["MiniscoreCore"])],
    targets: [
        .target(name: "MiniscoreCore"),
        .testTarget(name: "MiniscoreCoreTests", dependencies: ["MiniscoreCore"],
                    resources: [.copy("Fixtures")])
    ]
)
