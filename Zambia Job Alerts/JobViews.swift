import SwiftUI

struct HomeView: View {
    @ObservedObject var jobsStore: JobsStore
    @ObservedObject var savedJobsStore: SavedJobsStore
    @ObservedObject var servicesStore: ServicesStore
    let openJobsTab: () -> Void
    let openJob: (JobListing) -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    heroCard
                    AdaptiveBannerAdView()

                    VStack(alignment: .leading, spacing: 12) {
                        Text("Featured Jobs")
                            .font(.title2.bold())

                        if jobsStore.jobs.isEmpty && jobsStore.isLoading {
                            ProgressView("Loading jobs...")
                                .frame(maxWidth: .infinity, alignment: .leading)
                        } else {
                            ForEach(jobsStore.jobs.prefix(4)) { job in
                                JobCard(
                                    job: job,
                                    isSaved: savedJobsStore.contains(job),
                                    compact: false,
                                    openJob: { openJob(job) },
                                    toggleSave: { savedJobsStore.toggle(job) }
                                )
                            }
                        }
                    }
                }
                .padding(20)
                .padding(.top, 8)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Zambia Job Alerts")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var heroCard: some View {
        ZStack(alignment: .bottomLeading) {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [BrandPalette.blue, BrandPalette.blue.opacity(0.92), BrandPalette.orange],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(minHeight: 250)

            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    BrandMarkView(size: 56)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Zambia Job Alerts")
                            .font(.headline.bold())
                    }
                }
                .foregroundStyle(.white)

                Text("Find the next job faster")
                    .font(.largeTitle.bold())
                    .foregroundStyle(.white)

                Text("Browse fresh listings, save vacancies for later, and unlock premium services from the same app.")
                    .foregroundStyle(.white.opacity(0.9))
                    .fixedSize(horizontal: false, vertical: true)

                ViewThatFits(in: .horizontal) {
                    HStack {
                        StatChip(title: "Jobs", value: "\(jobsStore.jobs.count)")
                        StatChip(title: "Saved", value: "\(savedJobsStore.savedJobs.count)")
                        StatChip(title: "Credits", value: "\(servicesStore.credits)")
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            StatChip(title: "Jobs", value: "\(jobsStore.jobs.count)")
                            StatChip(title: "Saved", value: "\(savedJobsStore.savedJobs.count)")
                        }
                        StatChip(title: "Credits", value: "\(servicesStore.credits)")
                    }
                }

                Button("Browse Jobs") {
                    openJobsTab()
                }
                .buttonStyle(.borderedProminent)
                .tint(.white)
                .foregroundStyle(BrandPalette.orange)
            }
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity)
    }
}

struct JobsView: View {
    @ObservedObject var jobsStore: JobsStore
    @ObservedObject var savedJobsStore: SavedJobsStore
    let adCoordinator: AdCoordinator
    let openJob: (JobListing) -> Void

    var body: some View {
        NavigationStack {
            Group {
                if jobsStore.isLoading && jobsStore.feedItems.isEmpty {
                    ProgressView("Loading jobs...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 16) {
                            AdaptiveBannerAdView()

                            if let errorMessage = jobsStore.errorMessage {
                                Text(errorMessage)
                                    .font(.subheadline)
                                    .foregroundStyle(.red)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }

                            Text("Latest Jobs")
                                .font(.title3.bold())
                                .foregroundStyle(BrandPalette.ink)

                            if jobsStore.feedItems.isEmpty {
                                EmptyStateView(
                                    title: "No jobs found",
                                    message: "Try another search term or refresh the feed.",
                                    systemImage: "magnifyingglass"
                                )
                            } else {
                                ForEach(jobsStore.feedItems) { item in
                                    switch item {
                                    case .job(let job):
                                        JobCard(
                                            job: job,
                                            isSaved: savedJobsStore.contains(job),
                                            compact: true,
                                            openJob: { openJob(job) },
                                            toggleSave: { savedJobsStore.toggle(job) }
                                        )
                                        .task {
                                            await jobsStore.loadMoreIfNeeded(currentJobID: job.id)
                                        }
                                    case .nativeAdPlaceholder(let index):
                                        NativeAdCardView(slot: index)
                                    }
                                }
                            }

                            if jobsStore.isLoadingMore {
                                HStack {
                                    Spacer()
                                    ProgressView()
                                    Spacer()
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                    }
                    .searchable(text: $jobsStore.searchText, prompt: "Search jobs")
                    .onSubmit(of: .search) {
                        Task {
                            await jobsStore.submitSearch()
                        }
                    }
                    .refreshable {
                        await jobsStore.refreshJobs()
                    }
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Jobs")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    if !jobsStore.searchText.isEmpty {
                        Button("Clear") {
                            Task {
                                await jobsStore.clearSearch()
                            }
                        }
                    }
                }
            }
        }
    }
}

struct SavedJobsView: View {
    @ObservedObject var savedJobsStore: SavedJobsStore
    let openJob: (JobListing) -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    BrandHeaderView(subtitle: "Saved jobs stay available here even after you close the app.")
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }

                Section("Saved Jobs") {
                    if savedJobsStore.savedJobs.isEmpty {
                        EmptyStateView(
                            title: "No saved jobs",
                            message: "Save jobs from Home or Jobs to keep track of them here.",
                            systemImage: "bookmark.slash"
                        )
                    } else {
                        ForEach(savedJobsStore.savedJobs) { snapshot in
                            SavedJobCard(snapshot: snapshot) {
                                openJob(snapshot.asListing)
                            } remove: {
                                savedJobsStore.remove(snapshot)
                            }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Saved Jobs")
        }
    }
}

struct JobCard: View {
    let job: JobListing
    let isSaved: Bool
    let compact: Bool
    let openJob: () -> Void
    let toggleSave: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !compact, let imageURL = job.featuredImageURL {
                AsyncImage(url: imageURL) { image in
                    image
                        .resizable()
                        .scaledToFill()
                } placeholder: {
                    Rectangle()
                        .fill(BrandPalette.mist)
                        .overlay(ProgressView())
                }
                .frame(height: 160)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            }

            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(job.titleText)
                        .font(compact ? .headline : .title3.bold())
                        .foregroundStyle(.secondary)

                    if !job.company.isEmpty {
                        Label(job.company, systemImage: "building.2")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    HStack(spacing: 12) {
                        if !job.location.isEmpty {
                            Label(job.location, systemImage: "mappin.and.ellipse")
                        }
                        if !job.jobType.isEmpty {
                            Label(job.jobType, systemImage: "clock")
                        }
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }

                Spacer()

                Button {
                    toggleSave()
                } label: {
                    Image(systemName: isSaved ? "bookmark.fill" : "bookmark")
                        .font(.title3)
                        .foregroundStyle(isSaved ? BrandPalette.orange : .secondary)
                }
                .buttonStyle(.plain)
            }

            if !job.excerptText.isEmpty {
                Text(job.excerptText)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(compact ? 3 : 4)
            }

            HStack {
                Label(job.formattedDate, systemImage: "calendar")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Spacer()

                Button("View Job") {
                    openJob()
                }
                .buttonStyle(.borderedProminent)
                .tint(BrandPalette.blue)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
        .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
        .listRowBackground(Color.clear)
    }
}

struct SavedJobCard: View {
    let snapshot: SavedJobSnapshot
    let open: () -> Void
    let remove: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(snapshot.title)
                .font(.headline)
                .foregroundStyle(BrandPalette.ink)

            HStack(spacing: 12) {
                if !snapshot.company.isEmpty {
                    Label(snapshot.company, systemImage: "building.2")
                }
                if !snapshot.location.isEmpty {
                    Label(snapshot.location, systemImage: "mappin.and.ellipse")
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            Text(snapshot.excerpt)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(3)

            HStack {
                Button("Open") {
                    open()
                }
                .buttonStyle(.borderedProminent)
                .tint(BrandPalette.blue)

                Button("Remove", role: .destructive) {
                    remove()
                }
                .buttonStyle(.bordered)

                Spacer()

                Text(snapshot.date)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 6)
    }
}

struct AdCardView: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("Ad", systemImage: "megaphone.fill")
                    .font(.caption.bold())
                    .foregroundStyle(BrandPalette.orange)
                Spacer()
            }

            Text(title)
                .font(.headline)
                .foregroundStyle(BrandPalette.ink)
            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(BrandPalette.mist)
        )
    }
}

struct EmptyStateView: View {
    let title: String
    let message: String
    let systemImage: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(.secondary)
            Text(title)
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }
}
