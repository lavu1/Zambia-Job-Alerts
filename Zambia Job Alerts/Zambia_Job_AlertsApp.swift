//
//  Zambia_Job_AlertsApp.swift
//  Zambia Job Alerts
//
//  Created by Lavu Mweemba on 29/03/2026.
//

import SwiftUI
import CoreData

@main
struct Zambia_Job_AlertsApp: App {
    let persistenceController = PersistenceController.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(\.managedObjectContext, persistenceController.container.viewContext)
        }
    }
}
