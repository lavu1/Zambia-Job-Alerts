import Combine
import Foundation

@MainActor
final class SavedJobsStore: ObservableObject {
    @Published private(set) var savedJobs: [SavedJobSnapshot] = []

    private let defaults: UserDefaults
    private let storageKey = "saved.job.snapshots"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        load()
    }

    func contains(_ job: JobListing) -> Bool {
        savedJobs.contains(where: { $0.id == job.id })
    }

    func toggle(_ job: JobListing) {
        if contains(job) {
            savedJobs.removeAll { $0.id == job.id }
        } else {
            savedJobs.insert(SavedJobSnapshot(job: job), at: 0)
        }
        persist()
    }

    func remove(_ snapshot: SavedJobSnapshot) {
        savedJobs.removeAll { $0.id == snapshot.id }
        persist()
    }

    private func load() {
        guard let data = defaults.data(forKey: storageKey) else {
            return
        }
        guard let decoded = try? JSONDecoder().decode([SavedJobSnapshot].self, from: data) else {
            return
        }
        savedJobs = decoded.sorted { $0.savedAt > $1.savedAt }
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(savedJobs) else {
            return
        }
        defaults.set(data, forKey: storageKey)
    }
}
