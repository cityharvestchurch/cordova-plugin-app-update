import Foundation

/// Apple has no Play-Core equivalent, so this checks the app's published
/// version against Apple's public, unauthenticated iTunes Lookup API and
/// deep-links to the App Store. No app-owned backend is involved.
@objc(AppUpdateChecker)
class AppUpdateChecker: CDVPlugin {

    private let lookupBaseURL = "https://itunes.apple.com/lookup"

    @objc(checkForUpdate:)
    func checkForUpdate(command: CDVInvokedUrlCommand) {
        guard let bundleId = Bundle.main.bundleIdentifier else {
            send(command, error: "Could not resolve bundle identifier")
            return
        }
        guard let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String else {
            send(command, error: "Could not resolve current app version")
            return
        }
        guard var components = URLComponents(string: lookupBaseURL) else {
            send(command, error: "Invalid lookup URL")
            return
        }
        components.queryItems = [
            URLQueryItem(name: "bundleId", value: bundleId),
            // Cache-bust so a just-published update isn't masked by a stale CDN hit.
            URLQueryItem(name: "t", value: String(Date().timeIntervalSince1970))
        ]

        guard let url = components.url else {
            send(command, error: "Could not build lookup URL")
            return
        }

        let task = URLSession.shared.dataTask(with: url) { [weak self] data, _, error in
            guard let self = self else { return }
            if let error = error {
                self.send(command, error: "Lookup request failed: \(error.localizedDescription)")
                return
            }
            guard let data = data else {
                self.send(command, error: "Empty lookup response")
                return
            }
            do {
                guard
                    let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                    let results = json["results"] as? [[String: Any]],
                    let app = results.first,
                    let latestVersion = app["version"] as? String,
                    let storeUrl = app["trackViewUrl"] as? String
                else {
                    self.send(command, error: "App not found for bundle id \(bundleId)")
                    return
                }

                let updateAvailable = latestVersion.compare(currentVersion, options: .numeric) == .orderedDescending

                let payload: [String: Any] = [
                    "updateAvailable": updateAvailable,
                    "platform": "ios",
                    "currentVersion": currentVersion,
                    "latestVersion": latestVersion,
                    "storeUrl": storeUrl
                ]
                self.send(command, payload: payload)
            } catch {
                self.send(command, error: "Could not parse lookup response: \(error.localizedDescription)")
            }
        }
        task.resume()
    }

    @objc(openStoreListing:)
    func openStoreListing(command: CDVInvokedUrlCommand) {
        guard let bundleId = Bundle.main.bundleIdentifier else {
            send(command, error: "Could not resolve bundle identifier")
            return
        }
        var components = URLComponents(string: lookupBaseURL)
        components?.queryItems = [URLQueryItem(name: "bundleId", value: bundleId)]

        guard let url = components?.url else {
            send(command, error: "Could not build lookup URL")
            return
        }

        URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
            guard let self = self else { return }
            guard
                let data = data,
                let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                let results = json["results"] as? [[String: Any]],
                let app = results.first,
                let storeUrlString = app["trackViewUrl"] as? String,
                let storeUrl = URL(string: storeUrlString)
            else {
                self.send(command, error: "Could not resolve App Store URL")
                return
            }

            DispatchQueue.main.async {
                UIApplication.shared.open(storeUrl, options: [:]) { success in
                    if success {
                        self.send(command, payload: ["opened": true])
                    } else {
                        self.send(command, error: "Failed to open App Store")
                    }
                }
            }
        }.resume()
    }

    // MARK: - Android-only actions: fail gracefully instead of silently no-op-ing

    @objc(startImmediateUpdate:)
    func startImmediateUpdate(command: CDVInvokedUrlCommand) {
        send(command, error: "Immediate/flexible in-app updates are an Android-only Play Core feature. On iOS, use checkForUpdate + openStoreListing.")
    }

    @objc(startFlexibleUpdate:)
    func startFlexibleUpdate(command: CDVInvokedUrlCommand) {
        startImmediateUpdate(command: command)
    }

    @objc(completeFlexibleUpdate:)
    func completeFlexibleUpdate(command: CDVInvokedUrlCommand) {
        startImmediateUpdate(command: command)
    }

    @objc(getFlexibleUpdateStatus:)
    func getFlexibleUpdateStatus(command: CDVInvokedUrlCommand) {
        startImmediateUpdate(command: command)
    }

    // MARK: - Helpers

    private func send(_ command: CDVInvokedUrlCommand, payload: [String: Any]) {
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: payload)
        commandDelegate.send(result, callbackId: command.callbackId)
    }

    private func send(_ command: CDVInvokedUrlCommand, error: String) {
        let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error)
        commandDelegate.send(result, callbackId: command.callbackId)
    }
}
