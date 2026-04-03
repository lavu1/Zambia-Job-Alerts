import Combine
import Foundation
import UIKit
import UserNotifications

@MainActor
final class NotificationManager: ObservableObject {
    static let fcmTokenDefaultsKey = "notifications.fcm.token"

    @Published private(set) var isAuthorized = false
    @Published private(set) var fcmToken: String?
    private let savedJobsReminderIdentifier = "saved.jobs.reminder"
    private let savedJobsReminderInterval: TimeInterval = 6 * 60 * 60

    init() {
        fcmToken = UserDefaults.standard.string(forKey: Self.fcmTokenDefaultsKey)
    }

    func refreshAuthorizationStatus() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        isAuthorized = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
        if isAuthorized {
            await MainActor.run {
                UIApplication.shared.registerForRemoteNotifications()
            }
        }
    }

    func requestAuthorization() async -> Bool {
        do {
            let granted = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])
            isAuthorized = granted
            if granted {
                await MainActor.run {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
            return granted
        } catch {
            isAuthorized = false
            return false
        }
    }

    func syncSavedJobsReminder(savedJobs: [SavedJobSnapshot]) async {
        await refreshAuthorizationStatus()

        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [savedJobsReminderIdentifier])

        guard isAuthorized, !savedJobs.isEmpty else {
            return
        }

        let content = UNMutableNotificationContent()
        content.title = "Saved jobs are waiting"
        content.body = reminderBody(for: savedJobs.count)
        content.sound = .default
        content.userInfo = ["route": "saved"]

        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: savedJobsReminderInterval,
            repeats: true
        )
        let request = UNNotificationRequest(
            identifier: savedJobsReminderIdentifier,
            content: content,
            trigger: trigger
        )

        try? await center.add(request)
    }

    private func reminderBody(for savedJobsCount: Int) -> String {
        if savedJobsCount == 1 {
            return "You have 1 saved job. Apply soon before the deadline passes."
        }
        return "You have \(savedJobsCount) saved jobs. Review them and apply soon."
    }

    func refreshFCMToken() {
        fcmToken = UserDefaults.standard.string(forKey: Self.fcmTokenDefaultsKey)
    }
}
