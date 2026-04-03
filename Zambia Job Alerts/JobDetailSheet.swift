import Combine
import SwiftUI

struct JobDetailSheet: View {
    let summaryJob: JobListing
    let initialErrorMessage: String?
    @ObservedObject var jobsStore: JobsStore
    @ObservedObject var savedJobsStore: SavedJobsStore
    let adCoordinator: AdCoordinator
    @StateObject private var loader: JobDetailLoader
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    init(summaryJob: JobListing, initialErrorMessage: String? = nil, jobsStore: JobsStore, savedJobsStore: SavedJobsStore, adCoordinator: AdCoordinator) {
        self.summaryJob = summaryJob
        self.initialErrorMessage = initialErrorMessage
        self.jobsStore = jobsStore
        self.savedJobsStore = savedJobsStore
        self.adCoordinator = adCoordinator
        _loader = StateObject(
            wrappedValue: JobDetailLoader(
                jobID: summaryJob.id,
                jobSlug: summaryJob.slug,
                fallbackJob: summaryJob,
                initialErrorMessage: initialErrorMessage
            )
        )
    }

    private var detailSections: [String] {
        let primaryText = loader.displayJob.contentHTML.htmlStripped
        let fallbackText = loader.displayJob.excerptText
        let sourceText = primaryText.isEmpty ? fallbackText : primaryText
        let cleanedText = sourceText.condensedWhitespace

        guard !cleanedText.isEmpty else {
            return []
        }

        let paragraphs = cleanedText
            .components(separatedBy: ". ")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }

        guard paragraphs.count > 3 else {
            return [cleanedText]
        }

        let chunkSize = max(1, Int(ceil(Double(paragraphs.count) / 3.0)))
        return stride(from: 0, to: paragraphs.count, by: chunkSize).map { startIndex in
            let endIndex = min(startIndex + chunkSize, paragraphs.count)
            let chunk = paragraphs[startIndex..<endIndex].joined(separator: ". ")
            return chunk.hasSuffix(".") ? chunk : chunk + "."
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    if let imageURL = loader.displayJob.featuredImageURL {
                        AsyncImage(url: imageURL) { image in
                            image
                                .resizable()
                                .scaledToFill()
                        } placeholder: {
                            Rectangle()
                                .fill(BrandPalette.mist)
                                .overlay(ProgressView())
                        }
                        .frame(height: 220)
                        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    }

                    Text(loader.displayJob.titleText)
                        .font(.largeTitle.bold())

                    DetailRow(title: "Company", value: loader.displayJob.company)
                    DetailRow(title: "Location", value: loader.displayJob.location)
                    DetailRow(title: "Type", value: loader.displayJob.jobType)
                    DetailRow(title: "Date Posted", value: loader.displayJob.formattedDate)

                    HStack {
                        Button(savedJobsStore.contains(loader.displayJob) ? "Saved" : "Save Job") {
                            savedJobsStore.toggle(loader.displayJob)
                        }
                        .buttonStyle(.bordered)

                        ShareLink(item: loader.displayJob.link) {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                        .buttonStyle(.bordered)
                    }

                    if !loader.displayJob.application.isEmpty {
                        ApplicationActionsView(job: loader.displayJob)
                    }

                    if let errorMessage = loader.errorMessage {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }

                    if loader.isLoading {
                        HStack(spacing: 10) {
                            ProgressView()
                            Text("Loading the latest job details...")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(BrandPalette.mist)
                        )
                    }

                    FixedBannerAdView()

                    if detailSections.isEmpty {
                        Text("Failed to load job details.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(Array(detailSections.enumerated()), id: \.offset) { index, section in
                            Text(section)
                                .font(.body)
                                .foregroundStyle(.primary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .fixedSize(horizontal: false, vertical: true)

                            if index == 0 {
                                FixedBannerAdView()
                            }
                        }

                        FixedBannerAdView()
                    }

                   /* if let link = URL(string: loader.displayJob.link) {
                        Button("Open Original Website Post") {
                            openURL(link)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(BrandPalette.orange)
                    }*/
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Job Details")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                print("[JobDetailSheet] appear id=\(summaryJob.id) slug=\(summaryJob.slug) title=\(summaryJob.titleText) initialError=\(initialErrorMessage ?? "nil")")
            }
            .task {
                await loader.load(using: jobsStore)
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

@MainActor
final class JobDetailLoader: ObservableObject {
    @Published private(set) var displayJob: JobListing
    @Published private(set) var errorMessage: String?
    @Published private(set) var isLoading = false

    private let jobID: Int
    private let jobSlug: String
    private var hasLoaded = false

    init(jobID: Int, jobSlug: String, fallbackJob: JobListing, initialErrorMessage: String? = nil) {
        self.jobID = jobID
        self.jobSlug = jobSlug
        displayJob = fallbackJob
        errorMessage = initialErrorMessage
    }

    func load(using jobsStore: JobsStore) async {
        guard !hasLoaded else {
            return
        }
        hasLoaded = true
        isLoading = true
        print("[JobDetailLoader] start id=\(jobID) slug=\(jobSlug) title=\(displayJob.titleText)")
        defer {
            isLoading = false
            print("[JobDetailLoader] end id=\(jobID) slug=\(jobSlug) finalTitle=\(displayJob.titleText) error=\(errorMessage ?? "nil")")
        }

        do {
            displayJob = try await jobsStore.fetchJob(id: jobID)
            print("[JobDetailLoader] fetch by id success id=\(displayJob.id) slug=\(displayJob.slug)")
        } catch {
            print("[JobDetailLoader] fetch by id failed id=\(jobID) error=\(error.localizedDescription)")
            if let fallbackDetail = try? await jobsStore.fetchJob(slug: jobSlug) {
                displayJob = fallbackDetail
                errorMessage = "Showing the latest available detail for this job."
                print("[JobDetailLoader] fetch by slug success id=\(fallbackDetail.id) slug=\(fallbackDetail.slug)")
            } else {
                errorMessage = "Showing the cached job summary because the full detail refresh failed."
                print("[JobDetailLoader] fetch by slug failed slug=\(jobSlug)")
            }
        }
    }
}

private struct DetailRow: View {
    let title: String
    let value: String

    var body: some View {
        if !value.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
                Text(value)
                    .font(.body)
            }
        }
    }
}

private struct ApplicationActionsView: View {
    let job: JobListing
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Apply")
                .font(.headline)

            HStack {
                Button("Apply Now") {
                    openApplicationTarget()
                }
                .buttonStyle(.borderedProminent)
                .tint(BrandPalette.blue)

//                if let link = URL(string: job.link) {
//                    Link("Website", destination: link)
//                        .buttonStyle(.bordered)
//                }
            }
        }
    }

    private func openApplicationTarget() {
        let application = job.application.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !application.isEmpty else {
            return
        }

        if application.contains("@"), let url = URL(string: "mailto:\(application)") {
            openURL(url)
            return
        }

        if let url = URL(string: application) {
            openURL(url)
        }
    }
}
