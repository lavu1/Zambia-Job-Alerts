import Combine
import GoogleMobileAds
import Network
import SwiftUI
import UIKit

enum AdMobConfig {
    static let appID = "ca-app-pub-2168080105757285~8431622654"
    
    static let appOpenID = "ca-app-pub-2168080105757285/8837159177"
    static let adaptiveBannerID = "ca-app-pub-2168080105757285/3720563865"
    static let fixedBannerID = "ca-app-pub-2168080105757285/9099638813"
    static let interstitialID = "ca-app-pub-2168080105757285/3172002592"
    static let rewardedID = "ca-app-pub-2168080105757285/7171994767"
    static let rewardedInterstitialID = "ca-app-pub-2168080105757285/2215910501"
    static let nativeID = "ca-app-pub-2168080105757285/8691922587"
    static let nativeVideoID = "ca-app-pub-2168080105757285/8691922587"
}

@MainActor
final class AdCoordinator: NSObject, ObservableObject {
    @Published private(set) var isOnline = true

    private var hasStarted = false
    private var appOpenAd: AppOpenAd?
    private var interstitialAd: InterstitialAd?
    private var rewardedAd: RewardedAd?
    private var isMobileAdsStarted = false
    private var pendingInterstitialAction: (() -> Void)?
    private var jobOpenCount = 0
    private var refreshTimerCancellable: AnyCancellable?
    private var networkStateCancellable: AnyCancellable?
    private var lastAppOpenLoadDate: Date?
    private var lastInterstitialLoadDate: Date?
    private var lastRewardedLoadDate: Date?
    private var isLoadingAppOpenAd = false
    private var isLoadingInterstitialAd = false
    private var isLoadingRewardedAd = false
    private let refreshInterval: TimeInterval = 3600

    private var interstitialFallbackWorkItem: DispatchWorkItem?
    private let networkMonitor = NetworkMonitor.shared

    private var lastAppOpenPresentationDate: Date?
    private let appOpenCooldown: TimeInterval = 45 // seconds to avoid interrupting foreground flows

    func start() {
        guard !hasStarted else {
            return
        }

        hasStarted = true
        networkMonitor.start()
        isOnline = networkMonitor.isConnected
        Task { @MainActor [weak self] in
            guard let self else { return }
            _ = await MobileAds.shared.start()
            self.isMobileAdsStarted = true
            self.preloadAdsIfNeeded(force: true)
        }
        startRefreshTimer()
        observeNetworkChanges()
    }

    func appDidBecomeActive() {
        refreshExpiredAdsIfNeeded()

        // Throttle app-open ads to avoid interrupting foreground navigation frequently
        let now = Date()
        if let last = lastAppOpenPresentationDate, now.timeIntervalSince(last) < appOpenCooldown {
            // Too soon to present another app-open ad; ensure it's loaded for next time
            if appOpenAd == nil { loadAppOpenAd() }
            return
        }
        presentAppOpenAdIfAvailable()
    }

    func presentInterstitialIfNeeded(after action: @escaping () -> Void) {
        jobOpenCount += 1
        refreshExpiredAdsIfNeeded()
        print("[AdCoordinator] presentInterstitialIfNeeded count=\(jobOpenCount) hasAd=\(interstitialAd != nil)")

        // Only attempt every 3rd time
        guard jobOpenCount.isMultiple(of: 5), let interstitialAd else {
            print("[AdCoordinator] skipping interstitial and running action immediately")
            action() // proceed immediately when not due or no ad loaded
            return
        }

        print("[AdCoordinator] presenting interstitial on count=\(jobOpenCount)")
        presentInterstitial(interstitialAd, after: action)
    }

    func presentInterstitialIfAvailable(after action: @escaping () -> Void) {
        refreshExpiredAdsIfNeeded()
        print("[AdCoordinator] presentInterstitialIfAvailable hasAd=\(interstitialAd != nil)")

        guard let interstitialAd else {
            print("[AdCoordinator] no interstitial available, running action immediately")
            action()
            return
        }

        print("[AdCoordinator] presenting available interstitial")
        presentInterstitial(interstitialAd, after: action)
    }

    func presentPostJobInterstitial(
        after action: @escaping () -> Void,
        onLoadingStateChange: @escaping (Bool) -> Void
    ) {
        refreshExpiredAdsIfNeeded()

        if let interstitialAd {
            onLoadingStateChange(false)
            presentInterstitial(interstitialAd, after: action)
            return
        }

        onLoadingStateChange(true)
        loadInterstitialAd()

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            guard let self else { return }
            onLoadingStateChange(false)

            if let interstitialAd = self.interstitialAd {
                self.presentInterstitial(interstitialAd, after: action)
            } else {
                action()
            }
        }
    }

    private func presentInterstitial(_ interstitialAd: InterstitialAd, after action: @escaping () -> Void) {
        guard let rootViewController = UIApplication.shared.topViewController else {
            action() // no presenter available; do not block navigation
            return
        }

        // Cancel any previous safety fallback
        interstitialFallbackWorkItem?.cancel()

        // Defer the action until the ad is dismissed
        pendingInterstitialAction = action
        interstitialAd.fullScreenContentDelegate = self
        interstitialAd.present(from: rootViewController)
        // Clear the reference so equality checks don’t rely on identity later
        self.interstitialAd = nil

        // Safety fallback: if the SDK never calls back, proceed after a short delay
        let fallback = DispatchWorkItem { [weak self] in
            guard let self else { return }
            if let action = self.pendingInterstitialAction {
                self.pendingInterstitialAction = nil
                action()
            }
            self.loadInterstitialAd()
        }
        interstitialFallbackWorkItem = fallback
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0, execute: fallback)
    }

    func presentRewardedAd(onReward: @escaping () -> Void, onFailure: @escaping (String) -> Void) {
        refreshExpiredAdsIfNeeded()

        guard let rewardedAd else {
            loadRewardedAd()
            onFailure("The reward ad is still loading. Please try again in a moment.")
            return
        }

        guard let rootViewController = UIApplication.shared.topViewController else {
            onFailure("Unable to present the reward ad right now.")
            return
        }

        rewardedAd.fullScreenContentDelegate = self
        rewardedAd.present(from: rootViewController) {
            onReward()
        }
        self.rewardedAd = nil
    }

    private func presentAppOpenAdIfAvailable() {
        guard let appOpenAd, let rootViewController = UIApplication.shared.topViewController else {
            if appOpenAd == nil {
                loadAppOpenAd()
            }
            return
        }

        appOpenAd.fullScreenContentDelegate = self
        appOpenAd.present(from: rootViewController)
        self.appOpenAd = nil
        self.lastAppOpenPresentationDate = Date()
    }

    private func loadAppOpenAd() {
        guard !isLoadingAppOpenAd else {
            return
        }
        isLoadingAppOpenAd = true
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.appOpenAd = try? await AppOpenAd.load(
                with: AdMobConfig.appOpenID,
                request: Request()
            )
            self.lastAppOpenLoadDate = self.appOpenAd == nil ? nil : Date()
            self.isLoadingAppOpenAd = false
        }
    }

    private func loadInterstitialAd() {
        guard !isLoadingInterstitialAd else {
            return
        }
        isLoadingInterstitialAd = true
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.interstitialAd = try? await InterstitialAd.load(
                with: AdMobConfig.interstitialID,
                request: Request()
            )
            self.lastInterstitialLoadDate = self.interstitialAd == nil ? nil : Date()
            self.isLoadingInterstitialAd = false
        }
    }

    private func loadRewardedAd() {
        guard !isLoadingRewardedAd else {
            return
        }
        isLoadingRewardedAd = true
        Task { @MainActor [weak self] in
            guard let self else { return }
            self.rewardedAd = try? await RewardedAd.load(
                with: AdMobConfig.rewardedID,
                request: Request()
            )
            self.lastRewardedLoadDate = self.rewardedAd == nil ? nil : Date()
            self.isLoadingRewardedAd = false
        }
    }

    private func startRefreshTimer() {
        guard refreshTimerCancellable == nil else {
            return
        }

        refreshTimerCancellable = Timer.publish(every: 300, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                guard let self else { return }
                self.isOnline = self.networkMonitor.isConnected
                self.refreshExpiredAdsIfNeeded()
            }
    }

    private func observeNetworkChanges() {
        guard networkStateCancellable == nil else {
            return
        }

        networkStateCancellable = networkMonitor.$isConnected
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isConnected in
                guard let self else { return }
                self.isOnline = isConnected

                if isConnected {
                    self.preloadAdsIfNeeded(force: true)
                }
            }
    }

    private func preloadAdsIfNeeded(force: Bool = false) {
        guard networkMonitor.isConnected, isMobileAdsStarted else {
            return
        }

        if force || shouldReload(lastAppOpenLoadDate) || appOpenAd == nil {
            loadAppOpenAd()
        }
        if force || shouldReload(lastInterstitialLoadDate) || interstitialAd == nil {
            loadInterstitialAd()
        }
        if force || shouldReload(lastRewardedLoadDate) || rewardedAd == nil {
            loadRewardedAd()
        }
    }

    private func refreshExpiredAdsIfNeeded() {
        preloadAdsIfNeeded()
    }

    private func shouldReload(_ lastLoadDate: Date?) -> Bool {
        guard let lastLoadDate else {
            return true
        }
        return Date().timeIntervalSince(lastLoadDate) >= refreshInterval
    }
}

extension AdCoordinator: FullScreenContentDelegate {
    func adDidDismissFullScreenContent(_ ad: any FullScreenPresentingAd) {
        print("[AdCoordinator] adDidDismissFullScreenContent type=\(type(of: ad))")
        // Cancel any pending fallback since we received a callback
        interstitialFallbackWorkItem?.cancel()
        interstitialFallbackWorkItem = nil

        // If an interstitial was just dismissed, run the pending action
        performPendingInterstitialAction(after: 0.35)

        // Reload ads based on which type was dismissed
        switch ad {
        case is InterstitialAd:
            loadInterstitialAd()
        case is RewardedAd:
            loadRewardedAd()
        case is AppOpenAd:
            loadAppOpenAd()
        default:
            loadInterstitialAd()
            loadRewardedAd()
            loadAppOpenAd()
        }
    }

    func ad(_ ad: any FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: any Error) {
        print("[AdCoordinator] didFailToPresent type=\(type(of: ad)) error=\(error.localizedDescription)")
        // Cancel any pending fallback since we received a callback
        interstitialFallbackWorkItem?.cancel()
        interstitialFallbackWorkItem = nil

        // If an interstitial failed to present, proceed with the action immediately
        performPendingInterstitialAction(after: 0.1)

        switch ad {
        case is InterstitialAd:
            loadInterstitialAd()
        case is RewardedAd:
            loadRewardedAd()
        case is AppOpenAd:
            loadAppOpenAd()
        default:
            break
        }
    }

    private func performPendingInterstitialAction(after delay: TimeInterval) {
        guard let action = pendingInterstitialAction else {
            print("[AdCoordinator] no pending interstitial action to run")
            return
        }

        pendingInterstitialAction = nil
        print("[AdCoordinator] scheduling pending interstitial action after \(delay)s")
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
            print("[AdCoordinator] running pending interstitial action")
            action()
        }
    }
}

struct AdaptiveBannerAdView: View {
    @ObservedObject private var networkMonitor = NetworkMonitor.shared

    var body: some View {
        Group {
            if networkMonitor.isConnected {
                BannerAdRepresentable(adUnitID: AdMobConfig.adaptiveBannerID, usesAdaptiveSizing: true)
            } else {
                AdCardView(title: "Sponsored", subtitle: "Ads will appear when you are back online.")
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 60)
        .clipped()
        .contentShape(Rectangle())
    }
}

struct FixedBannerAdView: View {
    @ObservedObject private var networkMonitor = NetworkMonitor.shared

    var body: some View {
        Group {
            if networkMonitor.isConnected {
                BannerAdRepresentable(adUnitID: AdMobConfig.fixedBannerID, usesAdaptiveSizing: false)
            } else {
                AdCardView(title: "Sponsored", subtitle: "Ads will appear when you are back online.")
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 50)
        .clipped()
        .contentShape(Rectangle())
    }
}

private struct BannerAdRepresentable: UIViewRepresentable {
    let adUnitID: String
    let usesAdaptiveSizing: Bool

    func makeUIView(context: Context) -> BannerHostingView {
        let hostingView = BannerHostingView()
        hostingView.configure(adUnitID: adUnitID, usesAdaptiveSizing: usesAdaptiveSizing)
        return hostingView
    }

    func updateUIView(_ uiView: BannerHostingView, context: Context) {
        uiView.configure(adUnitID: adUnitID, usesAdaptiveSizing: usesAdaptiveSizing)
    }
}

private final class BannerHostingView: UIView {
    private let bannerView = BannerView()
    private var adUnitID: String?
    private var usesAdaptiveSizing = false
    private var lastConfiguredWidth: CGFloat = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        clipsToBounds = true
        backgroundColor = .clear
        bannerView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(bannerView)

        NSLayoutConstraint.activate([
            bannerView.centerXAnchor.constraint(equalTo: centerXAnchor),
            bannerView.topAnchor.constraint(equalTo: topAnchor),
            bannerView.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(adUnitID: String, usesAdaptiveSizing: Bool) {
        self.adUnitID = adUnitID
        self.usesAdaptiveSizing = usesAdaptiveSizing
        bannerView.adUnitID = adUnitID
        bannerView.rootViewController = UIApplication.shared.topViewController
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()

        guard let adUnitID, bounds.width > 1 else {
            return
        }

        let targetWidth = usesAdaptiveSizing ? max(bounds.width, 1) : min(bounds.width, 320)
        guard abs(lastConfiguredWidth - targetWidth) > 1 || bannerView.adUnitID != adUnitID else {
            return
        }

        lastConfiguredWidth = targetWidth
        bannerView.adSize = usesAdaptiveSizing
            ? currentOrientationAnchoredAdaptiveBanner(width: targetWidth)
            : AdSizeBanner
        bannerView.rootViewController = UIApplication.shared.topViewController
        bannerView.load(Request())
    }
}

struct NativeAdCardView: View {
    let slot: Int
    @ObservedObject private var networkMonitor = NetworkMonitor.shared
    @StateObject private var loader = NativeAdSlotLoader()

    var body: some View {
        Group {
            if !networkMonitor.isConnected {
                AdCardView(title: "Sponsored", subtitle: "Offline mode: ads are unavailable right now.")
            } else if let nativeAd = loader.nativeAd {
                NativeAdRepresentable(nativeAd: nativeAd)
                    .frame(height: 320)
            } else {
                AdCardView(title: "Sponsored", subtitle: "Loading ad...")
            }
        }
        .task(id: "\(slot)-\(networkMonitor.isConnected)") {
            guard networkMonitor.isConnected else {
                return
            }
            loader.loadIfNeeded()
        }
        .onChange(of: networkMonitor.isConnected) { isConnected in
            guard isConnected else {
                loader.reset()
                return
            }
            loader.loadIfNeeded()
        }
    }
}

@MainActor
final class NativeAdSlotLoader: NSObject, ObservableObject {
    @Published private(set) var nativeAd: NativeAd?

    private var adLoader: AdLoader?
    private var isLoading = false
    private var retryWorkItem: DispatchWorkItem?

    func loadIfNeeded() {
        guard !isLoading, nativeAd == nil else {
            return
        }

        isLoading = true
        print("[NativeAdLoader] load requested count=1")
        adLoader = AdLoader(
            adUnitID: AdMobConfig.nativeID,
            rootViewController: UIApplication.shared.topViewController,
            adTypes: [.native],
            options: nil
        )
        adLoader?.delegate = self
        adLoader?.load(Request())
    }

    func reset() {
        retryWorkItem?.cancel()
        retryWorkItem = nil
        isLoading = false
        nativeAd = nil
        adLoader = nil
    }

    private func scheduleRetry() {
        retryWorkItem?.cancel()
        let workItem = DispatchWorkItem { [weak self] in
            self?.loadIfNeeded()
        }
        retryWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0, execute: workItem)
    }
}

extension NativeAdSlotLoader: NativeAdLoaderDelegate {
    func adLoader(_ adLoader: AdLoader, didReceive nativeAd: NativeAd) {
        retryWorkItem?.cancel()
        retryWorkItem = nil
        isLoading = false
        self.nativeAd = nativeAd
        print("[NativeAdLoader] received native ad total=1")
    }
}

extension NativeAdSlotLoader: AdLoaderDelegate {
    func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: any Error) {
        print("[NativeAdLoader] failed error=\(error.localizedDescription)")
        isLoading = false
        scheduleRetry()
    }
}

private struct NativeAdRepresentable: UIViewRepresentable {
    let nativeAd: NativeAd

    func makeUIView(context: Context) -> NativeAdView {
        let nativeAdView = NativeAdView()
        let container = UIView()
        container.backgroundColor = UIColor(BrandPalette.mist)
        container.layer.cornerRadius = 22
        container.translatesAutoresizingMaskIntoConstraints = false

        let adBadge = UILabel()
        adBadge.text = "Ad"
        adBadge.font = .preferredFont(forTextStyle: .caption1).bold()
        adBadge.textColor = UIColor(BrandPalette.orange)
        adBadge.translatesAutoresizingMaskIntoConstraints = false

        let headlineLabel = UILabel()
        headlineLabel.font = .preferredFont(forTextStyle: .headline)
        headlineLabel.numberOfLines = 2
        headlineLabel.translatesAutoresizingMaskIntoConstraints = false

        let bodyLabel = UILabel()
        bodyLabel.font = .preferredFont(forTextStyle: .subheadline)
        bodyLabel.textColor = .secondaryLabel
        bodyLabel.numberOfLines = 3
        bodyLabel.translatesAutoresizingMaskIntoConstraints = false

        let callToActionButton = UIButton(type: .system)
        var callToActionConfiguration = UIButton.Configuration.plain()
        callToActionConfiguration.baseForegroundColor = .white
        callToActionConfiguration.background.backgroundColor = UIColor(BrandPalette.orange)
        callToActionConfiguration.background.cornerRadius = 10
        callToActionConfiguration.contentInsets = NSDirectionalEdgeInsets(
            top: 10,
            leading: 14,
            bottom: 10,
            trailing: 14
        )
        callToActionButton.configuration = callToActionConfiguration
        callToActionButton.translatesAutoresizingMaskIntoConstraints = false

        let mediaView = MediaView()
        mediaView.translatesAutoresizingMaskIntoConstraints = false

        nativeAdView.addSubview(container)
        container.addSubview(adBadge)
        container.addSubview(headlineLabel)
        container.addSubview(mediaView)
        container.addSubview(bodyLabel)
        container.addSubview(callToActionButton)

        NSLayoutConstraint.activate([
            container.topAnchor.constraint(equalTo: nativeAdView.topAnchor),
            container.leadingAnchor.constraint(equalTo: nativeAdView.leadingAnchor),
            container.trailingAnchor.constraint(equalTo: nativeAdView.trailingAnchor),
            container.bottomAnchor.constraint(equalTo: nativeAdView.bottomAnchor),

            adBadge.topAnchor.constraint(equalTo: container.topAnchor, constant: 16),
            adBadge.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 16),

            headlineLabel.topAnchor.constraint(equalTo: adBadge.bottomAnchor, constant: 8),
            headlineLabel.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 16),
            headlineLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -16),

            mediaView.topAnchor.constraint(equalTo: headlineLabel.bottomAnchor, constant: 12),
            mediaView.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 16),
            mediaView.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -16),
            mediaView.heightAnchor.constraint(equalToConstant: 160),

            bodyLabel.topAnchor.constraint(equalTo: mediaView.bottomAnchor, constant: 12),
            bodyLabel.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 16),
            bodyLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -16),

            callToActionButton.topAnchor.constraint(equalTo: bodyLabel.bottomAnchor, constant: 12),
            callToActionButton.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 16),
            callToActionButton.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -16)
        ])

        nativeAdView.headlineView = headlineLabel
        nativeAdView.bodyView = bodyLabel
        nativeAdView.callToActionView = callToActionButton
        nativeAdView.mediaView = mediaView

        return nativeAdView
    }

    func updateUIView(_ uiView: NativeAdView, context: Context) {
        (uiView.headlineView as? UILabel)?.text = nativeAd.headline
        (uiView.bodyView as? UILabel)?.text = nativeAd.body
        (uiView.callToActionView as? UIButton)?.setTitle(nativeAd.callToAction, for: .normal)
        uiView.mediaView?.mediaContent = nativeAd.mediaContent
        uiView.nativeAd = nativeAd
    }
}

private extension UIFont {
    func bold() -> UIFont {
        guard let descriptor = fontDescriptor.withSymbolicTraits(.traitBold) else {
            return self
        }
        return UIFont(descriptor: descriptor, size: pointSize)
    }
}

private extension UIApplication {
    var topViewController: UIViewController? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController?
            .topMostViewController
    }
}

private extension UIViewController {
    var topMostViewController: UIViewController {
        if let presentedViewController {
            return presentedViewController.topMostViewController
        }
        if let navigationController = self as? UINavigationController {
            return navigationController.visibleViewController?.topMostViewController ?? navigationController
        }
        if let tabBarController = self as? UITabBarController {
            return tabBarController.selectedViewController?.topMostViewController ?? tabBarController
        }
        return self
    }
}

@MainActor
final class NetworkMonitor: ObservableObject {
    static let shared = NetworkMonitor()

    @Published private(set) var isConnected = true

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "NetworkMonitor")
    private var hasStarted = false

    private init() {}

    func start() {
        guard !hasStarted else {
            return
        }

        hasStarted = true
        monitor.pathUpdateHandler = { [weak self] path in
            let isConnected = path.status == .satisfied
            DispatchQueue.main.async {
                self?.isConnected = isConnected
            }
        }
        monitor.start(queue: queue)
    }
}
