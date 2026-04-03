import SwiftUI
import UniformTypeIdentifiers

struct ServicesHubView: View {
    @ObservedObject var servicesStore: ServicesStore
    let adCoordinator: AdCoordinator

    var body: some View {
        NavigationStack {
            List {
                Section {
                    BrandHeaderView(subtitle: "Watch ads for credits, then redeem the same services offered on the website.")
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)

                    HStack {
                        StatChip(title: "Credits", value: "\(servicesStore.credits)")
                        Spacer()
                        Button {
                            Task {
                                await servicesStore.watchAdForCredit()
                                adCoordinator.presentRewardedAd {
                                    servicesStore.completeAdReward()
                                } onFailure: { message in
                                    servicesStore.failAdReward(message: message)
                                }
                            }
                        } label: {
                            if servicesStore.isWatchingAd {
                                ProgressView()
                            } else {
                                Label("Watch Ad", systemImage: "play.rectangle.fill")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(BrandPalette.orange)
                    }
                    .padding(.vertical, 8)

                    if let statusMessage = servicesStore.statusMessage {
                        Text(statusMessage)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Redeem Services") {
                    ForEach(ServiceType.allCases) { service in
                        if servicesStore.canRedeem(service) {
                            NavigationLink {
                                ServiceFormView(service: service, servicesStore: servicesStore)
                            } label: {
                                ServiceAccessRow(
                                    service: service,
                                    credits: servicesStore.credits,
                                    isUnlocked: true
                                )
                            }
                            .buttonStyle(.plain)
                        } else {
                            Button {
                                servicesStore.statusMessage = "You need \(service.creditCost) credits to access \(service.title)."
                            } label: {
                                ServiceAccessRow(
                                    service: service,
                                    credits: servicesStore.credits,
                                    isUnlocked: false
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Services")
        }
    }
}

private struct ServiceAccessRow: View {
    let service: ServiceType
    let credits: Int
    let isUnlocked: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(service.title)
                    .font(.headline)
                    .foregroundStyle(BrandPalette.ink)
                Spacer()
                Text("\(service.creditCost) cr")
                    .font(.caption.bold())
                    .foregroundStyle(BrandPalette.orange)
            }
            Text(service.subtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Text(isUnlocked ? "Unlocked" : "Locked until \(service.creditCost) credits")
                .font(.caption)
                .foregroundStyle(isUnlocked ? .green : .secondary)

            if !isUnlocked {
                Text("Current credits: \(credits)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
        .opacity(isUnlocked ? 1 : 0.72)
    }
}

struct ServiceFormView: View {
    let service: ServiceType
    @ObservedObject var servicesStore: ServicesStore
    @StateObject private var viewModel: ServiceSubmissionViewModel
    @State private var isPickingFile = false

    init(service: ServiceType, servicesStore: ServicesStore) {
        self.service = service
        self.servicesStore = servicesStore
        _viewModel = StateObject(wrappedValue: ServiceSubmissionViewModel(service: service))
    }

    var body: some View {
        Form {
            Section {
                Text(service.subtitle)
                    .foregroundStyle(.secondary)
                Text("Credits required: \(service.creditCost)")
                    .font(.subheadline.bold())
            }

            Section("Your Details") {
                if service == .cvWrite || service == .careerCoaching {
                    TextField("Full Name", text: $viewModel.form.name)
                }

                if service != .phoneAlerts {
                    TextField("Email", text: $viewModel.form.email)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                }

                if service != .emailAlerts && service != .priorityApplication {
                    TextField("Phone", text: $viewModel.form.phone)
                        .keyboardType(.phonePad)
                }
            }

            if service == .emailAlerts || service == .phoneAlerts {
                Section("Alert Preferences") {
                    TextField("Job Category", text: $viewModel.form.jobCategory)
                }
            }

            if service == .cvWrite {
                Section("Background") {
                    TextField("Education", text: $viewModel.form.education, axis: .vertical)
                    TextField("Work Experience", text: $viewModel.form.workExperience, axis: .vertical)
                    TextField("Skills", text: $viewModel.form.skills, axis: .vertical)
                }
            }

            if service == .cvReview {
                Section("CV Upload") {
                    Button(viewModel.selectedFileName.isEmpty ? "Choose CV File" : viewModel.selectedFileName) {
                        isPickingFile = true
                    }
                }
            }

            if service == .priorityApplication || service == .cvReview || service == .cvWrite || service == .careerCoaching {
                Section("Notes") {
                    TextField("Additional Notes", text: $viewModel.form.additionalNotes, axis: .vertical)
                        .lineLimit(4...8)
                }
            }

            Section {
                Button {
                    Task {
                        await viewModel.submit(using: servicesStore)
                    }
                } label: {
                    if viewModel.isSubmitting {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text("Submit")
                            .frame(maxWidth: .infinity)
                    }
                }
                .disabled(viewModel.isSubmitting)
            }

            if let responseMessage = viewModel.responseMessage {
                Section {
                    Text(responseMessage)
                        .foregroundStyle(responseMessage.contains("successfully") ? .green : .secondary)
                }
            }
        }
        .navigationTitle(service.title)
        .fileImporter(
            isPresented: $isPickingFile,
            allowedContentTypes: [.pdf, .plainText, .rtf, .item],
            allowsMultipleSelection: false
        ) { result in
            guard case .success(let urls) = result, let url = urls.first else {
                return
            }
            viewModel.selectedFileURL = url
            viewModel.selectedFileName = url.lastPathComponent
        }
    }
}
