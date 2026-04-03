import Combine
import Foundation

@MainActor
final class JobsStore: ObservableObject {
    @Published private(set) var jobs: [JobListing] = []
    @Published private(set) var feedItems: [JobsFeedItem] = []
    @Published var searchText = ""
    @Published private(set) var isLoading = false
    @Published private(set) var isLoadingMore = false
    @Published private(set) var hasMorePages = true
    @Published var errorMessage: String?

    private let client = JobsAPIClient()
    private let defaults: UserDefaults
    private let cacheKey = "jobs.cache.entries"
    private let lastRefreshKey = "jobs.cache.lastRefreshDate"
    private var currentPage = 1
    private let pageSize = 10
    private let maxCachedJobs = 20
    private let detailPrefetchCount = 3
    private var currentQuery = ""
    private var hasLoadedOnce = false
    private var prefetchedJobIDs = Set<Int>()

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        loadCachedJobs()
    }

    func loadInitialJobsIfNeeded() async {
        guard !hasLoadedOnce else {
            return
        }
        hasLoadedOnce = true
        await refreshJobs()
    }

    func refreshJobs() async {
        currentPage = 1
        hasMorePages = true
        await loadPage(reset: true)
    }

    func refreshJobsIfNeeded(maxAge: TimeInterval = 3600) async {
        guard shouldRefresh(maxAge: maxAge) else {
            return
        }
        await refreshJobs()
    }

    func submitSearch() async {
        currentQuery = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        currentPage = 1
        hasMorePages = true
        await loadPage(reset: true)
    }

    func clearSearch() async {
        searchText = ""
        currentQuery = ""
        currentPage = 1
        hasMorePages = true
        await loadPage(reset: true)
    }

    func loadMoreIfNeeded(currentJobID: Int) async {
        guard hasMorePages, !isLoading, !isLoadingMore else {
            return
        }
        guard jobs.last?.id == currentJobID else {
            return
        }
        currentPage += 1
        await loadPage(reset: false)
    }

    func resolveDeepLink(url: URL) async -> JobListing? {
        if let existingJob = jobs.first(where: { $0.link == url.absoluteString || $0.slug == slug(from: url) }) {
            return existingJob
        }

        guard let slug = slug(from: url) else {
            return nil
        }

        do {
            return try await client.fetchJob(slug: slug)
        } catch {
            errorMessage = "Unable to open the linked job right now."
            return nil
        }
    }

    func fetchJob(id: Int) async throws -> JobListing {
        do {
            let job = try await client.fetchJob(id: id)
            upsertCachedJob(job)
            return job
        } catch {
            if let cachedJob = cachedJob(id: id) {
                errorMessage = "Offline mode: showing the last cached job details."
                return cachedJob
            }
            throw error
        }
    }

    func fetchJob(slug: String) async throws -> JobListing? {
        do {
            let job = try await client.fetchJob(slug: slug)
            if let job {
                upsertCachedJob(job)
            }
            return job
        } catch {
            if let cachedJob = cachedJob(slug: slug) {
                errorMessage = "Offline mode: showing the last cached job details."
                return cachedJob
            }
            throw error
        }
    }

    private func loadPage(reset: Bool) async {
        if reset {
            isLoading = true
            jobs = []
            rebuildFeed()
        } else {
            isLoadingMore = true
        }

        errorMessage = nil

        do {
            let page = try await client.fetchJobs(page: currentPage, perPage: pageSize, query: currentQuery)
            if reset {
                jobs = page
            } else {
                jobs += page
            }
            hasMorePages = page.count == pageSize
            persistJobs()
            defaults.set(Date(), forKey: lastRefreshKey)
            rebuildFeed()
            Task {
                await self.prefetchTopJobDetailsIfNeeded()
            }
        } catch {
            if !reset {
                currentPage -= 1
            }
            if reset {
                if jobs.isEmpty {
                    loadCachedJobs()
                }
                rebuildFeed()
            }
            errorMessage = jobs.isEmpty
                ? "Offline: no cached jobs are available yet."
                : "Offline mode: showing cached jobs. Pull to refresh when you are back online."
        }

        isLoading = false
        isLoadingMore = false
    }

    private func prefetchTopJobDetailsIfNeeded() async {
        guard currentQuery.isEmpty else {
            return
        }

        let candidates = jobs.prefix(detailPrefetchCount).filter { job in
            !prefetchedJobIDs.contains(job.id) && cachedJob(id: job.id) == nil
        }

        guard !candidates.isEmpty else {
            return
        }

        await withTaskGroup(of: JobListing?.self) { group in
            for job in candidates {
                let id = job.id
                let slug = job.slug
                group.addTask {
                    if let detailedJob = try? await self.client.fetchJob(id: id) {
                        return detailedJob
                    }
                    return try? await self.client.fetchJob(slug: slug)
                }
            }

            for await prefetchedJob in group {
                guard let prefetchedJob else {
                    continue
                }
                upsertCachedJob(prefetchedJob)
                prefetchedJobIDs.insert(prefetchedJob.id)
            }
        }
    }

    private func rebuildFeed() {
        var items: [JobsFeedItem] = []
        var adIndex = 1

        for (offset, job) in jobs.enumerated() {
            items.append(.job(job))
            if (offset + 1).isMultiple(of: 5) {
                items.append(.nativeAdPlaceholder(adIndex))
                adIndex += 1
            }
        }

        feedItems = items
    }

    private func persistJobs() {
        let cachedJobs = Array(jobs.prefix(maxCachedJobs)).map(\.cachedValue)
        guard let data = try? JSONEncoder().encode(cachedJobs) else {
            return
        }
        defaults.set(data, forKey: cacheKey)
    }

    private func loadCachedJobs() {
        guard let data = defaults.data(forKey: cacheKey),
              let cachedJobs = try? JSONDecoder().decode([CachedJobListing].self, from: data) else {
            return
        }

        jobs = cachedJobs.map(JobListing.init(cachedValue:))
        rebuildFeed()
    }

    private func upsertCachedJob(_ job: JobListing) {
        var currentCache = cachedJobs()
        currentCache.removeAll { $0.id == job.id || $0.slug == job.slug }
        currentCache.insert(job.cachedValue, at: 0)
        currentCache = Array(currentCache.prefix(maxCachedJobs))

        guard let data = try? JSONEncoder().encode(currentCache) else {
            return
        }
        defaults.set(data, forKey: cacheKey)
    }

    private func cachedJob(id: Int) -> JobListing? {
        cachedJobs()
            .first(where: { $0.id == id })
            .map(JobListing.init(cachedValue:))
    }

    private func cachedJob(slug: String) -> JobListing? {
        cachedJobs()
            .first(where: { $0.slug == slug })
            .map(JobListing.init(cachedValue:))
    }

    private func cachedJobs() -> [CachedJobListing] {
        guard let data = defaults.data(forKey: cacheKey),
              let cachedJobs = try? JSONDecoder().decode([CachedJobListing].self, from: data) else {
            return []
        }
        return cachedJobs
    }

    private func slug(from url: URL) -> String? {
        let parts = url.pathComponents.filter { $0 != "/" }
        if url.scheme == "zambiajobalerts" {
            return parts.last
        }
        guard parts.count >= 2, parts[0] == "job" else {
            return nil
        }
        return parts[1]
    }

    private func shouldRefresh(maxAge: TimeInterval) -> Bool {
        guard currentQuery.isEmpty else {
            return false
        }
        guard let lastRefresh = defaults.object(forKey: lastRefreshKey) as? Date else {
            return true
        }
        return Date().timeIntervalSince(lastRefresh) >= maxAge
    }
}

struct JobsAPIClient {
    private let decoder = JSONDecoder()

    func fetchJobs(page: Int, perPage: Int, query: String) async throws -> [JobListing] {
        var components = URLComponents(string: "https://zambiajobalerts.com/wp-json/wp/v2/job-listings")!
        var queryItems = [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "per_page", value: String(perPage)),
            URLQueryItem(name: "_embed", value: "1")
        ]

        if !query.isEmpty {
            queryItems.append(URLQueryItem(name: "search", value: query))
        }

        components.queryItems = queryItems
        let request = URLRequest(url: components.url!, cachePolicy: .reloadIgnoringLocalCacheData)
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response: response)
        return try decoder.decode([JobListing].self, from: data)
    }

    func fetchJob(slug: String) async throws -> JobListing? {
        var components = URLComponents(string: "https://zambiajobalerts.com/wp-json/wp/v2/job-listings")!
        components.queryItems = [
            URLQueryItem(name: "slug", value: slug),
            URLQueryItem(name: "_embed", value: "1")
        ]

        let request = URLRequest(url: components.url!, cachePolicy: .reloadIgnoringLocalCacheData)
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response: response)
        return try decoder.decode([JobListing].self, from: data).first
    }

    func fetchJob(id: Int) async throws -> JobListing {
        var components = URLComponents(string: "https://zambiajobalerts.com/wp-json/wp/v2/job-listings/\(id)")!
        components.queryItems = [
            URLQueryItem(name: "_embed", value: "1")
        ]

        let request = URLRequest(url: components.url!, cachePolicy: .reloadIgnoringLocalCacheData)
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response: response)
        return try decoder.decode(JobListing.self, from: data)
    }

    private func validate(response: URLResponse) throws {
        guard let httpResponse = response as? HTTPURLResponse, 200 ..< 300 ~= httpResponse.statusCode else {
            throw URLError(.badServerResponse)
        }
    }
}
