import SwiftUI

struct MoreView: View {
    @ObservedObject var notificationManager: NotificationManager
    let adCoordinator: AdCoordinator
    @State private var statusMessage: String?
    private let appShareURL = URL(string: "https://apps.apple.com/app/id6761562142")!
    private let appShareMessage = "Check out Zambia Job Alerts for the latest jobs and career opportunities: https://apps.apple.com/app/id6761562142"

    var body: some View {
        NavigationStack {
            List {
                Section {
                    BrandHeaderView(subtitle: "About, support, and notification settings for the iOS app.")
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }

                Section("App") {
                    NavigationLink("About Zambia Job Alerts") {
                        AboutView()
                    }
                    NavigationLink("Post a Job") {
                        PostJobView(adCoordinator: adCoordinator)
                    }
                    ShareLink(
                        item: appShareURL,
                        subject: Text("Zambia Job Alerts"),
                        message: Text(appShareMessage)
                    ) {
                        Label("Share App", systemImage: "square.and.arrow.up")
                    }
//                    Link("Visit Website", destination: URL(string: "https://zambiajobalerts.com")!)
                    Link("Contact Support", destination: URL(string: "mailto:contact@zambiajobalerts.com")!)
                }

                Section("Notifications") {
                    HStack {
                        Text("Status")
                        Spacer()
                        Text(notificationManager.isAuthorized ? "Enabled" : "Disabled")
                            .foregroundStyle(notificationManager.isAuthorized ? .green : .secondary)
                    }

                 /*
                  if let fcmToken = notificationManager.fcmToken, !fcmToken.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
//                            Text("FCM Token")
//                                .font(.caption)
//                                .foregroundStyle(.secondary)
//                            Text(fcmToken)
//                                .font(.footnote.monospaced())
//                                .textSelection(.enabled)
                        }
                    }*/

                    Button(notificationManager.isAuthorized ? "Refresh Permission State" : "Enable Notifications") {
                        Task {
                            if notificationManager.isAuthorized {
                                await notificationManager.refreshAuthorizationStatus()
                                notificationManager.refreshFCMToken()
                                statusMessage = "Notification status refreshed."
                            } else {
                                let granted = await notificationManager.requestAuthorization()
                                notificationManager.refreshFCMToken()
                                statusMessage = granted ? "Notifications enabled." : "Notifications were not granted."
                            }
                        }
                    }
                }

                if let statusMessage {
                    Section {
                        Text(statusMessage)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("More")
            .task {
                await notificationManager.refreshAuthorizationStatus()
                notificationManager.refreshFCMToken()
            }
            .onReceive(NotificationCenter.default.publisher(for: .fcmTokenUpdated)) { notification in
                if let token = notification.object as? String {
                    notificationManager.refreshFCMToken()
                    statusMessage = "FCM token updated."
                    print("FCM token available for Postman: \(token)")
                }
            }
        }
    }
}

private struct AboutView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                BrandHeaderView(subtitle: "Fresh jobs, saved vacancies, and premium career services built for Zambia.")
                    .foregroundStyle(.primary)

                Text("Zambia Job Alerts shares live job opportunities from companies around Zambia and beyong and presents them in a faster mobile workflow for iPhone and iPad.")
                    .foregroundStyle(.secondary)

                Text("Core features")
                    .font(.headline)

                VStack(alignment: .leading, spacing: 10) {
                    Label("Latest jobs from hiring companies accross the country", systemImage: "network")
                    Label("Persistent saved jobs", systemImage: "bookmark.fill")
                    Label("Premium services with credit redemption", systemImage: "gift.fill")
//                    Label("Deep-link ready job detail view", systemImage: "link")
                }
                .foregroundStyle(BrandPalette.ink)
            }
            .padding(20)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("About")
    }
}
