import Combine
import SwiftUI

struct JobDetailSheet: View {
    let summaryJob: JobListing
    let initialErrorMessage: String?
    @ObservedObject var jobsStore: JobsStore
    @ObservedObject var savedJobsStore: SavedJobsStore
    let adCoordinator: AdCoordinator
    @StateObject private var loader: JobDetailLoader
    @State private var htmlSectionHeights: [Int: CGFloat] = [:]
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

    private var htmlSections: [String] {
        let html = loader.displayJob.contentHTML.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !html.isEmpty else {
            return []
        }

        let blocks = html.htmlBlocks
        if blocks.count >= 2 {
            return blocks.splitIntoBalancedSections()
        }

        if html.htmlStripped.count >= 700 {
            return html.splitNearMiddle().map(\.self)
        }

        guard blocks.count >= 1 else {
            return [html]
        }

        return [html]
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

                    /*if !loader.displayJob.application.isEmpty {
                        ApplicationActionsView(job: loader.displayJob)
                    }*/
                    FixedBannerAdView()

                    if loader.displayJob.contentHTML.isEmpty {
                        Text(loader.displayJob.excerptText.isEmpty ? "Failed to load job details." : loader.displayJob.excerptText)
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(Array(htmlSections.enumerated()), id: \.offset) { index, section in
                            HTMLView(
                                html: section,
                                contentHeight: Binding(
                                    get: { htmlSectionHeights[index, default: 300] },
                                    set: { htmlSectionHeights[index] = $0 }
                                )
                            )
                            .frame(height: htmlSectionHeights[index, default: 300])

                            if index == 0, htmlSections.count > 1 {
                                FixedBannerAdView()
                            }
                        }

                        FixedBannerAdView()

                        DescriptionActionRow(job: loader.displayJob, savedJobsStore: savedJobsStore)
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

                    /*FixedBannerAdView()

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
                    }*/

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

private extension String {
    var htmlBlocks: [String] {
        let pattern = #"(?is).*?(</p>|</ul>|</ol>|</li>|</div>|</section>|</article>|</blockquote>|</h[1-6]>|<br\s*/?>)"#

        guard let regex = try? NSRegularExpression(pattern: pattern) else {
            return [self]
        }

        let fullRange = NSRange(startIndex..., in: self)
        let matches = regex.matches(in: self, range: fullRange)

        guard !matches.isEmpty else {
            return [self]
        }

        var blocks: [String] = []
        var currentLocation = startIndex

        for match in matches {
            guard let range = Range(match.range, in: self) else {
                continue
            }

            blocks.append(String(self[currentLocation..<range.upperBound]))
            currentLocation = range.upperBound
        }

        let trailing = self[currentLocation...].trimmingCharacters(in: .whitespacesAndNewlines)
        if !trailing.isEmpty {
            blocks.append(String(self[currentLocation...]))
        }

        return blocks.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    func splitNearMiddle() -> [String] {
        let midpoint = index(startIndex, offsetBy: count / 2)
        let candidateOffsets = [0, 40, -40, 80, -80, 120, -120]

        for offset in candidateOffsets {
            guard let splitIndex = index(midpoint, offsetBy: offset, limitedBy: endIndex) else {
                continue
            }

            let suffix = self[splitIndex...]
            if let range = suffix.range(of: #"</p>|<br\s*/?>|</div>|</li>"#, options: .regularExpression) {
                let actualIndex = range.upperBound
                let firstHalf = String(self[..<actualIndex]).trimmingCharacters(in: .whitespacesAndNewlines)
                let secondHalf = String(self[actualIndex...]).trimmingCharacters(in: .whitespacesAndNewlines)

                if !firstHalf.isEmpty, !secondHalf.isEmpty {
                    return [firstHalf, secondHalf]
                }
            }
        }

        let firstHalf = String(self[..<midpoint]).trimmingCharacters(in: .whitespacesAndNewlines)
        let secondHalf = String(self[midpoint...]).trimmingCharacters(in: .whitespacesAndNewlines)
        return [firstHalf, secondHalf].filter { !$0.isEmpty }
    }
}

private extension Array where Element == String {
    func splitIntoBalancedSections() -> [String] {
        guard count >= 2 else {
            return self
        }

        let midpoint = count / 2
        let firstHalf = self[..<midpoint].joined()
        let secondHalf = self[midpoint...].joined()

        return [firstHalf, secondHalf].filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
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

private struct DescriptionActionRow: View {
    let job: JobListing
    @ObservedObject var savedJobsStore: SavedJobsStore
    @Environment(\.openURL) private var openURL

    var body: some View {
        HStack {
            Button(savedJobsStore.contains(job) ? "Saved" : "Save Job") {
                savedJobsStore.toggle(job)
            }
            .buttonStyle(.bordered)

            ShareLink(item: job.link) {
                Label("Share", systemImage: "square.and.arrow.up")
            }
            .buttonStyle(.bordered)

            if !job.application.isEmpty {
                Button("Apply Now") {
                    openApplicationTarget()
                }
                .buttonStyle(.borderedProminent)
                .tint(BrandPalette.orange)
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
