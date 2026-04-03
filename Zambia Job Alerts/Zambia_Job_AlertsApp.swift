//
//  Zambia_Job_AlertsApp.swift
//  Zambia Job Alerts
//
//  Created by Lavu Mweemba on 29/03/2026.
//

import SwiftUI
import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

extension Notification.Name {
    static let appRouteRequested = Notification.Name("appRouteRequested")
    static let fcmTokenUpdated = Notification.Name("fcmTokenUpdated")
}

enum NotificationRouteStore {
    static let pendingRouteKey = "notifications.pending.route"

    static func save(_ route: String) {
        UserDefaults.standard.set(route, forKey: pendingRouteKey)
    }

    static func consume() -> String? {
        guard let route = UserDefaults.standard.string(forKey: pendingRouteKey), !route.isEmpty else {
            return nil
        }
        UserDefaults.standard.removeObject(forKey: pendingRouteKey)
        return route
    }

    static var hasPendingRoute: Bool {
        guard let route = UserDefaults.standard.string(forKey: pendingRouteKey) else {
            return false
        }
        return !route.isEmpty
    }
}

@main
struct Zambia_Job_AlertsApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseBootstrap.configureIfNeeded()
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        application.registerForRemoteNotifications()

        if let remoteNotification = launchOptions?[.remoteNotification] as? [AnyHashable: Any],
           let route = Self.route(from: remoteNotification) {
            NotificationRouteStore.save(route)
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("APNs registration failed: \(error.localizedDescription)")
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .list])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let route = Self.route(from: response.notification.request.content.userInfo) {
            NotificationRouteStore.save(route)
            NotificationCenter.default.post(name: .appRouteRequested, object: route)
        }
        completionHandler()
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else {
            return
        }
        UserDefaults.standard.set(fcmToken, forKey: NotificationManager.fcmTokenDefaultsKey)
        NotificationCenter.default.post(name: .fcmTokenUpdated, object: fcmToken)
        print("FCM token: \(fcmToken)")
    }

    private static func route(from userInfo: [AnyHashable: Any]) -> String? {
        if let route = userInfo["route"] as? String, !route.isEmpty {
            return route
        }
        if let route = userInfo["job_url"] as? String, !route.isEmpty {
            return route
        }
        if let route = userInfo["link"] as? String, !route.isEmpty {
            return route
        }
        if let route = userInfo["target_url"] as? String, !route.isEmpty {
            return route
        }
        return nil
    }
}

private enum FirebaseBootstrap {
    static func configureIfNeeded() {
        guard FirebaseApp.app() == nil else {
            return
        }

        let options = FirebaseOptions(
            googleAppID: "1:495738809523:ios:b87b6a8003ea02847014c6",
            gcmSenderID: "495738809523"
        )
        options.apiKey = "AIzaSyDpy92XIgoTz6coA5nYUglAPjCAdD1hTD4"
        options.projectID = "zambia-job-alerts"
        options.bundleID = Bundle.main.bundleIdentifier ?? "com.alphil.networks.Zambia-Job-Alerts"
        options.storageBucket = "zambia-job-alerts.firebasestorage.app"

        FirebaseApp.configure(options: options)
    }
}
