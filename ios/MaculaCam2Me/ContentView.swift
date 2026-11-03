import SwiftUI

/// Skeleton entry point. Proves the FFI wiring end to end -- generating a
/// macula 12 node identity (pq_hybrid) via `FfiNodeKey` and rendering its
/// node_id -- without yet touching the mesh. The key is not kept between
/// launches and nothing connects yet (#5). Generating a pq_hybrid key and
/// solving the admission puzzle takes a few seconds, so it runs off the
/// main thread.
struct ContentView: View {
    @State private var nodeIdHex = "generating a node identity..."

    var body: some View {
        VStack(spacing: 12) {
            Text("Macula Cam2Me")
                .font(.title)
            Text("macula-rust-ffi is alive.")
            Text("node_id: \(nodeIdHex)")
                .font(.system(.footnote, design: .monospaced))
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .padding()
        .task {
            nodeIdHex = await Task.detached(priority: .userInitiated) { Self.generateNodeIdHex() }.value
        }
    }

    private static func generateNodeIdHex() -> String {
        do {
            let identity = try FfiNodeKey.generate(profile: .pqHybrid)
            return identity.nodeId().map { String(format: "%02x", $0) }.joined()
        } catch {
            return "could not generate a node identity: \(error)"
        }
    }
}

#Preview {
    ContentView()
}
