import AppKit
import CoreGraphics

let pid = pid_t(CommandLine.arguments[1])!
let identifier = CommandLine.arguments[2]
let deadline = Date().addingTimeInterval(12)
repeat {
    let windows = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID)
        as? [[String: Any]] ?? []
    let hasWindow = windows.contains {
        ($0[kCGWindowOwnerPID as String] as? pid_t) == pid &&
        ($0[kCGWindowLayer as String] as? Int) == 0
    }
    if hasWindow, let app = NSRunningApplication(processIdentifier: pid), app.bundleIdentifier == identifier {
        if app.terminate() {
            print("NATIVE_WINDOW_PASS bundle=\(identifier) normalQuitRequested=true")
            exit(0)
        }
        break
    }
    Thread.sleep(forTimeInterval: 0.1)
} while Date() < deadline
fputs("Could not observe and quit the app's native window\n", stderr)
exit(1)
