import Combine
import SwiftUI

struct PostJobView: View {
    let adCoordinator: AdCoordinator
    @StateObject private var viewModel = PostJobViewModel()
    @State private var isPreparingAd = false

    var body: some View {
        Form {
            Section {
                Text("Post a job opening to Zambia Job Alerts from the iOS app.")
                    .foregroundStyle(.secondary)
            }

            Section("Job Details") {
                TextField("Job Title", text: $viewModel.form.jobTitle)
                TextField("Company Name", text: $viewModel.form.companyName)
                TextField("Location", text: $viewModel.form.location)
                TextField("Application URL or Email", text: $viewModel.form.applicationLink)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
            }

            Section("Category") {
                Menu {
                    ForEach(PostJobCategory.allCases) { category in
                        Button(category.title) {
                            viewModel.toggleCategory(category)
                        }
                    }
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Job Categories")
                            .foregroundStyle(.primary)
                        Text(viewModel.selectedCategorySummary)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                Picker("Job Type", selection: $viewModel.form.jobType) {
                    ForEach(PostJobType.allCases) { jobType in
                        Text(jobType.title).tag(jobType)
                    }
                }
            }

            Section("Description") {
                TextField("Job Description", text: $viewModel.form.description, axis: .vertical)
                    .lineLimit(8...14)
            }

            Section {
                Button {
                    submitWithAd()
                } label: {
                    if viewModel.isSubmitting || isPreparingAd {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text("Post Job")
                            .frame(maxWidth: .infinity)
                    }
                }
                .disabled(viewModel.isSubmitting || isPreparingAd)
            }

            if let statusMessage = viewModel.statusMessage {
                Section {
                    Text(statusMessage)
                        .foregroundStyle(statusMessage.localizedCaseInsensitiveContains("success") ? .green : .secondary)
                }
            }
        }
        .navigationTitle("Post a Job")
    }

    private func submitWithAd() {
        guard viewModel.validate() else {
            return
        }

        adCoordinator.presentPostJobInterstitial(
            after: {
                Task {
                    await viewModel.submit()
                }
            },
            onLoadingStateChange: { isLoading in
                isPreparingAd = isLoading
            }
        )
    }
}

enum PostJobCategory: String, CaseIterable, Identifiable {
    case accountant = "Accountant"
    case administrator = "Administrator"
    case agriculture = "Agriculture"
    case bankingFinance = "Banking/Finance"
    case development = "Development"
    case education = "Education"
    case engineerConstruction = "Engineer/Construction"
    case health = "Health"
    case hospitality = "Hospitality"
    case humanResources = "Human Resources"
    case itTelecoms = "IT/Telecoms"
    case legal = "Legal"
    case manufacturingFMCG = "Manufacturing/FMCG"
    case marketingPR = "Marketing/PR"
    case publicSector = "Public Sector"
    case retailSales = "Retail/Sales"
    case logisticsTransport = "Logistics/Transport"
    case other = "Other"

    var id: String { rawValue }
    var title: String { rawValue }

    var categoryID: Int {
        switch self {
        case .accountant: return 11
        case .administrator: return 12
        case .agriculture: return 13
        case .bankingFinance: return 14
        case .development: return 15
        case .education: return 16
        case .engineerConstruction: return 17
        case .health: return 18
        case .hospitality: return 19
        case .humanResources: return 20
        case .itTelecoms: return 21
        case .legal: return 22
        case .manufacturingFMCG: return 23
        case .marketingPR: return 24
        case .publicSector: return 26
        case .retailSales: return 27
        case .logisticsTransport: return 28
        case .other: return 25
        }
    }
}

enum PostJobType: String, CaseIterable, Identifiable {
    case fullTime = "Full Time"
    case partTime = "Part Time"
    case temporary = "Temporary"
    case freelance = "Freelance"
    case internship = "Internship"
    case consultancy = "Consultancy"
    case contract = "Contract"
    case tender = "Tender"

    var id: String { rawValue }
    var title: String { rawValue }

    var typeID: Int {
        switch self {
        case .fullTime: return 6
        case .partTime: return 7
        case .temporary: return 8
        case .freelance: return 9
        case .internship: return 10
        case .consultancy: return 30
        case .contract: return 31
        case .tender: return 32
        }
    }
}

struct PostJobForm {
    var jobTitle = ""
    var companyName = ""
    var location = ""
    var applicationLink = ""
    var description = ""
    var categories: Set<PostJobCategory> = []
    var jobType: PostJobType = .fullTime
}

@MainActor
final class PostJobViewModel: ObservableObject {
    @Published var form = PostJobForm()
    @Published private(set) var isSubmitting = false
    @Published private(set) var statusMessage: String?

    private let client = PostJobAPIClient()

    var selectedCategorySummary: String {
        let selected = form.categories.map(\.title).sorted()
        return selected.isEmpty ? "No category selected" : selected.joined(separator: ", ")
    }

    func toggleCategory(_ category: PostJobCategory) {
        if form.categories.contains(category) {
            form.categories.remove(category)
        } else {
            form.categories.insert(category)
        }
    }

    func submit() async {
        guard validate() else {
            return
        }

        isSubmitting = true
        statusMessage = nil

        do {
            try await client.postJob(form)
            statusMessage = "Job posted successfully!"
            form = PostJobForm()
        } catch {
            statusMessage = "Failed to post job."
        }

        isSubmitting = false
    }

    @discardableResult
    func validate() -> Bool {
        guard isValid else {
            statusMessage = "Please fill in all fields."
            return false
        }
        statusMessage = nil
        return true
    }

    private var isValid: Bool {
        !form.jobTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !form.companyName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !form.location.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !form.applicationLink.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !form.description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

struct PostJobAPIClient {
    private let endpoint = URL(string: "https://zambiajobalerts.com/wp-json/wp/v2/job-listings/")!
    private let username = "lavum27@gmail.com"
    private let password = "k1rE Jvud syGP cbmI y1HN hItI"
    private let webpushrEndpoint = URL(string: "https://api.webpushr.com/v1/notification/send/all")!
    private let webpushrKey = "82d976ee01019162668f6f92cec308fb"
    private let webpushrAuth = "88373"

    func postJob(_ form: PostJobForm) async throws {
        let payload = try makePayload(from: form)
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(basicAuthHeader, forHTTPHeaderField: "Authorization")
        request.httpBody = payload

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, 200 ..< 300 ~= httpResponse.statusCode else {
            throw URLError(.badServerResponse)
        }

        try await sendWebpushrNotification(using: data, form: form)
    }

    private func makePayload(from form: PostJobForm) throws -> Data {
        let body: [String: Any] = [
            "status": "publish",
            "title": form.jobTitle,
            "content": form.description,
            "meta": [
                "_job_location": form.location,
                "_company_name": form.companyName,
                "_application": form.applicationLink
            ],
            "job-categories": form.categories.isEmpty ? [PostJobCategory.other.categoryID] : form.categories.map(\.categoryID).sorted(),
            "job-types": [form.jobType.typeID]
        ]

        return try JSONSerialization.data(withJSONObject: body)
    }

    private var basicAuthHeader: String {
        let credentials = "\(username):\(password)"
        let encoded = Data(credentials.utf8).base64EncodedString()
        return "Basic \(encoded)"
    }

    private func sendWebpushrNotification(using responseData: Data, form: PostJobForm) async throws {
        let jobURL = try extractJobURL(from: responseData)
        let message = "\(form.jobTitle) Wanted for employment at \(form.companyName) for details click apply button"

        let payload: [String: Any] = [
            "title": form.jobTitle,
            "message": message,
            "target_url": jobURL.absoluteString,
            "action_buttons": [
                [
                    "title": "Apply",
                    "url": jobURL.absoluteString
                ]
            ]
        ]

        var request = URLRequest(url: webpushrEndpoint)
        request.httpMethod = "POST"
        request.setValue(webpushrKey, forHTTPHeaderField: "webpushrKey")
        request.setValue(webpushrAuth, forHTTPHeaderField: "webpushrAuthToken")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (_, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, 200 ..< 300 ~= httpResponse.statusCode else {
            throw URLError(.badServerResponse)
        }
    }

    private func extractJobURL(from responseData: Data) throws -> URL {
        if let object = try JSONSerialization.jsonObject(with: responseData) as? [String: Any],
           let link = object["link"] as? String,
           let url = URL(string: link) {
            return url
        }

        guard let fallbackURL = URL(string: "https://zambiajobalerts.com") else {
            throw URLError(.badURL)
        }
        return fallbackURL
    }
}
