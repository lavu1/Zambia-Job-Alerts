import SwiftUI

struct ContentView: View {
    @StateObject private var jobsStore = JobsStore()
    @StateObject private var savedJobsStore = SavedJobsStore()
    @StateObject private var servicesStore = ServicesStore()
    @StateObject private var notificationManager = NotificationManager()
    @StateObject private var adCoordinator = AdCoordinator()
    @StateObject private var networkMonitor = NetworkMonitor.shared
    @State private var selectedTab: AppTab = .home
    @State private var presentedJob: PresentedJob?
    @State private var routeMessage: String?
    @AppStorage("notifications.launchPromptAttempted") private var notificationLaunchPromptAttempted = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView(
                jobsStore: jobsStore,
                savedJobsStore: savedJobsStore,
                servicesStore: servicesStore,
                openJobsTab: { selectedTab = .jobs },
                openJob: openJob(_:)
            )
            .tabItem {
                Label("Home", systemImage: "house.fill")
            }
            .tag(AppTab.home)

            JobsView(
                jobsStore: jobsStore,
                savedJobsStore: savedJobsStore,
                adCoordinator: adCoordinator,
                openJob: openJob(_:)
            )
            .tabItem {
                Label("Jobs", systemImage: "briefcase.fill")
            }
            .tag(AppTab.jobs)

            SavedJobsView(
                savedJobsStore: savedJobsStore,
                openJob: openJob(_:)
            )
            .tabItem {
                Label("Saved", systemImage: "bookmark.fill")
            }
            .tag(AppTab.saved)

            ServicesHubView(servicesStore: servicesStore, adCoordinator: adCoordinator)
                .tabItem {
                    Label("Services", systemImage: "gift.fill")
                }
                .tag(AppTab.services)

            MoreView(notificationManager: notificationManager, adCoordinator: adCoordinator)
                .tabItem {
                    Label("More", systemImage: "ellipsis.circle.fill")
                }
                .tag(AppTab.more)
        }
        .tint(BrandPalette.orange)
        .task {
            networkMonitor.start()
            adCoordinator.start()

            let pendingRoute = NotificationRouteStore.consume()
            if let pendingRoute {
                handleNotificationRoute(pendingRoute)
            }

            Task {
                await performLaunchSetup(skipNotificationPrompt: pendingRoute != nil)
            }
        }
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 300_000_000_000)
                guard scenePhase == .active else {
                    continue
                }
                await jobsStore.refreshJobsIfNeeded()
            }
        }
        .sheet(item: $presentedJob, onDismiss: {
            presentedJob = nil
        }) { presentedJob in
            JobDetailSheet(
                summaryJob: presentedJob.job,
                initialErrorMessage: presentedJob.errorMessage,
                jobsStore: jobsStore,
                savedJobsStore: savedJobsStore,
                adCoordinator: adCoordinator
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
            .presentationBackground(.regularMaterial)
        }
        .alert("Status", isPresented: routeAlertPresented) {
            Button("OK", role: .cancel) {
                routeMessage = nil
            }
        } message: {
            Text(routeMessage ?? "")
        }
        .onOpenURL { url in
            Task {
                await handleIncomingURL(url)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .appRouteRequested)) { notification in
            guard let route = notification.object as? String else {
                return
            }
            handleNotificationRoute(route)
        }
        .onChange(of: savedJobsStore.savedJobs) { savedJobs in
            Task {
                await notificationManager.syncSavedJobsReminder(savedJobs: savedJobs)
            }
        }
        .onChange(of: scenePhase) { newPhase in
            if newPhase == .active {
                if let pendingRoute = NotificationRouteStore.consume() {
                    handleNotificationRoute(pendingRoute)
                    return
                }

                adCoordinator.appDidBecomeActive()
                Task {
                    await jobsStore.refreshJobsIfNeeded()
                    await notificationManager.syncSavedJobsReminder(savedJobs: savedJobsStore.savedJobs)
                }
            }
        }
    }

    private var routeAlertPresented: Binding<Bool> {
        Binding(
            get: { routeMessage != nil },
            set: { newValue in
                if !newValue {
                    routeMessage = nil
                }
            }
        )
    }

    private func openJob(_ job: JobListing) {
        print("[JobOpen] tap id=\(job.id) slug=\(job.slug) title=\(job.titleText)")
        adCoordinator.presentInterstitialIfNeeded {
            print("[JobOpen] callback after interstitial id=\(job.id)")
            Task { @MainActor in
                print("[JobOpen] scheduling sheet presentation id=\(job.id)")
                presentJobDetails(PresentedJob(job: job))
            }
        }
    }

    private func handleRoute(_ route: String) {
        if let url = URL(string: route) {
            Task {
                await handleIncomingURL(url, allowInterstitial: true)
            }
            return
        }

        switch route.lowercased() {
        case "home":
            selectedTab = .home
        case "jobs":
            selectedTab = .jobs
        case "saved":
            selectedTab = .saved
        case "services":
            selectedTab = .services
        case "more":
            selectedTab = .more
        default:
            routeMessage = "Unable to open route: \(route)"
        }
    }

    private func handleNotificationRoute(_ route: String) {
        if let url = URL(string: route) {
            Task {
                await handleIncomingURL(url, allowInterstitial: false)
            }
            return
        }

        handleRoute(route)
    }

    private func handleIncomingURL(_ url: URL, allowInterstitial: Bool = true) async {
        if let job = await jobsStore.resolveDeepLink(url: url) {
            await MainActor.run {
                print("[DeepLink] resolved job id=\(job.id) slug=\(job.slug)")
                let present = {
                    print("[DeepLink] scheduling sheet presentation id=\(job.id)")
                    presentJobDetails(PresentedJob(job: job))
                }

                if allowInterstitial {
                    adCoordinator.presentInterstitialIfAvailable {
                        print("[DeepLink] callback after interstitial id=\(job.id)")
                        Task { @MainActor in
                            present()
                        }
                    }
                } else {
                    print("[DeepLink] bypassing interstitial for notification route id=\(job.id)")
                    present()
                }
            }
            return
        }

        let host = url.host?.lowercased()
        if let host, host.contains("zambiajobalerts.com") {
            await MainActor.run {
                selectedTab = .jobs
                routeMessage = nil
            }
            return
        }

        if url.scheme == "zambiajobalerts" {
            await MainActor.run {
                switch host {
                case "home":
                    selectedTab = .home
                case "jobs":
                    selectedTab = .jobs
                case "saved":
                    selectedTab = .saved
                case "services":
                    selectedTab = .services
                case "more":
                    selectedTab = .more
                default:
                    routeMessage = "Unable to open that link in this build."
                }
            }
            return
        }

        await MainActor.run {
            routeMessage = "Unable to open that link in this build."
        }
    }

    private func performLaunchSetup(skipNotificationPrompt: Bool) async {
        await jobsStore.loadInitialJobsIfNeeded()
        await notificationManager.refreshAuthorizationStatus()
        if !skipNotificationPrompt, !notificationManager.isAuthorized, !notificationLaunchPromptAttempted {
            notificationLaunchPromptAttempted = true
            _ = await notificationManager.requestAuthorization()
        }
        await notificationManager.syncSavedJobsReminder(savedJobs: savedJobsStore.savedJobs)
    }

    @MainActor
    private func presentJobDetails(_ presentedJob: PresentedJob) {
        print("[JobOpen] presentJobDetails id=\(presentedJob.job.id) slug=\(presentedJob.job.slug) title=\(presentedJob.job.titleText)")
        self.presentedJob = nil
        Task { @MainActor in
            self.presentedJob = presentedJob
        }
    }
}

private struct PresentedJob: Identifiable {
    let id = UUID()
    let job: JobListing
    let errorMessage: String?

    init(job: JobListing, errorMessage: String? = nil) {
        self.job = job
        self.errorMessage = errorMessage
    }
}

enum AppTab: Hashable {
    case home
    case jobs
    case saved
    case services
    case more
}
