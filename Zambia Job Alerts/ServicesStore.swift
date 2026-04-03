import Combine
import Foundation
import UIKit

@MainActor
final class ServicesStore: ObservableObject {
    @Published private(set) var credits: Int
    @Published var isWatchingAd = false
    @Published var statusMessage: String?

    private let defaults: UserDefaults
    private let storageKey = "services.credits"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        credits = defaults.integer(forKey: storageKey)
    }

    func watchAdForCredit() async {
        guard !isWatchingAd else {
            return
        }

        isWatchingAd = true
        statusMessage = nil
    }

    func completeAdReward() {
        credits += 1
        defaults.set(credits, forKey: storageKey)
        statusMessage = "Reward earned. Credits available: \(credits)"
        isWatchingAd = false
    }

    func failAdReward(message: String) {
        statusMessage = message
        isWatchingAd = false
    }

    func canRedeem(_ service: ServiceType) -> Bool {
        credits >= service.creditCost
    }

    @discardableResult
    func deductCredits(for service: ServiceType) -> Bool {
        guard canRedeem(service) else {
            statusMessage = "You need \(service.creditCost) credits to access \(service.title)."
            return false
        }

        credits -= service.creditCost
        defaults.set(credits, forKey: storageKey)
        statusMessage = "\(service.title) unlocked. Credits left: \(credits)"
        return true
    }
}

enum ServiceType: String, CaseIterable, Identifiable {
    case emailAlerts
    case phoneAlerts
    case priorityApplication
    case cvReview
    case cvWrite
    case careerCoaching

    var id: String { rawValue }

    var title: String {
        switch self {
        case .emailAlerts:
            return "Email Alerts"
        case .phoneAlerts:
            return "Phone Alerts"
        case .priorityApplication:
            return "Priority Application"
        case .cvReview:
            return "CV Review"
        case .cvWrite:
            return "CV Writing"
        case .careerCoaching:
            return "Career Coaching"
        }
    }

    var subtitle: String {
        switch self {
        case .emailAlerts:
            return "Get job alerts by email"
        case .phoneAlerts:
            return "Receive alerts by phone"
        case .priorityApplication:
            return "Move your application forward faster"
        case .cvReview:
            return "Upload a CV for expert review"
        case .cvWrite:
            return "Request a CV written from scratch"
        case .careerCoaching:
            return "Get one-on-one career support"
        }
    }

    var creditCost: Int {
        switch self {
        case .emailAlerts, .phoneAlerts:
            return 1
        case .priorityApplication:
            return 3
        case .cvReview, .cvWrite:
            return 5
        case .careerCoaching:
            return 7
        }
    }

    var requestType: String {
        switch self {
        case .emailAlerts, .phoneAlerts:
            return "Share me Jobs"
        case .priorityApplication:
            return "Priority Job Application"
        case .cvReview:
            return "cv_review"
        case .cvWrite:
            return "Write CV"
        case .careerCoaching:
            return "Career Coaching"
        }
    }

    var dayValue: String {
        switch self {
        case .emailAlerts:
            return "1"
        case .phoneAlerts:
            return "2"
        case .priorityApplication, .careerCoaching:
            return "0"
        case .cvReview, .cvWrite:
            return ""
        }
    }
}

struct ServiceFormData {
    var name = ""
    var email = ""
    var phone = ""
    var jobCategory = ""
    var education = ""
    var workExperience = ""
    var skills = ""
    var additionalNotes = ""
}

@MainActor
final class ServiceSubmissionViewModel: ObservableObject {
    @Published var form = ServiceFormData()
    @Published var isSubmitting = false
    @Published var selectedFileURL: URL?
    @Published var selectedFileName = ""
    @Published var responseMessage: String?

    private let client = ServiceAPIClient()
    let service: ServiceType

    init(service: ServiceType) {
        self.service = service
    }

    func submit(using servicesStore: ServicesStore) async {
        guard validate() else {
            responseMessage = "Fill all required fields before submitting."
            return
        }

        guard servicesStore.canRedeem(service) else {
            responseMessage = "Not enough credits for \(service.title)."
            return
        }

        isSubmitting = true
        responseMessage = nil

        do {
            if service == .cvReview {
                guard let fileURL = selectedFileURL else {
                    responseMessage = "Select a CV file before submitting."
                    isSubmitting = false
                    return
                }
                try await client.uploadCVReview(form: form, fileURL: fileURL, fileName: selectedFileName)
            } else {
                try await client.submitService(service: service, form: form)
            }

            if servicesStore.deductCredits(for: service) {
                responseMessage = "\(service.title) submitted successfully."
                form = ServiceFormData()
                selectedFileURL = nil
                selectedFileName = ""
            } else {
                responseMessage = "Not enough credits for \(service.title)."
            }
        } catch {
            responseMessage = "Failed to submit \(service.title.lowercased())."
        }

        isSubmitting = false
    }

    private func validate() -> Bool {
        switch service {
        case .emailAlerts:
            return !form.email.isEmpty
        case .phoneAlerts:
            return !form.phone.isEmpty
        case .priorityApplication:
            return !form.email.isEmpty
        case .cvReview:
            return !form.email.isEmpty && !form.phone.isEmpty && selectedFileURL != nil
        case .cvWrite:
            return !form.name.isEmpty && !form.email.isEmpty && !form.phone.isEmpty
        case .careerCoaching:
            return !form.name.isEmpty && !form.email.isEmpty
        }
    }
}

struct ServiceAPIClient {
    private let baseURL = URL(string: "https://zambiajobalerts.com/system/api/services")!
    private let deviceID = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString

    func submitService(service: ServiceType, form: ServiceFormData) async throws {
        let payload: [String: String] = [
            "type": service.requestType,
            "days": service.dayValue,
            "name": form.name,
            "email": form.email,
            "phone": form.phone,
            "education_background": form.education,
            "work_experience": form.workExperience,
            "skills": form.skills,
            "additional_notes": noteValue(for: service, form: form),
            "status": "Pending",
            "device_id": deviceID
        ]

        var request = URLRequest(url: baseURL)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = formEncodedString(payload).data(using: .utf8)

        let (_, response) = try await URLSession.shared.data(for: request)
        try validate(response: response)
    }

    func uploadCVReview(form: ServiceFormData, fileURL: URL, fileName: String) async throws {
        let boundary = UUID().uuidString
        var request = URLRequest(url: baseURL)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        let fileData = try Data(contentsOf: fileURL)
        var body = Data()
        appendFormField("type", value: "cv_review", to: &body, boundary: boundary)
        appendFormField("email", value: form.email, to: &body, boundary: boundary)
        appendFormField("phone", value: form.phone, to: &body, boundary: boundary)
        appendFormField("additional_notes", value: form.additionalNotes, to: &body, boundary: boundary)
        appendFormField("device_id", value: deviceID, to: &body, boundary: boundary)
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"cv_file_path\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: application/octet-stream\r\n\r\n".data(using: .utf8)!)
        body.append(fileData)
        body.append("\r\n".data(using: .utf8)!)
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body

        let (_, response) = try await URLSession.shared.data(for: request)
        try validate(response: response)
    }

    private func validate(response: URLResponse) throws {
        guard let httpResponse = response as? HTTPURLResponse, 200 ..< 300 ~= httpResponse.statusCode else {
            throw URLError(.badServerResponse)
        }
    }

    private func noteValue(for service: ServiceType, form: ServiceFormData) -> String {
        switch service {
        case .emailAlerts, .phoneAlerts:
            return form.jobCategory
        case .priorityApplication:
            return form.additionalNotes
        case .cvReview, .cvWrite, .careerCoaching:
            return form.additionalNotes
        }
    }

    private func formEncodedString(_ payload: [String: String]) -> String {
        payload
            .map { key, value in
                let encodedKey = key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? key
                let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
                return "\(encodedKey)=\(encodedValue)"
            }
            .joined(separator: "&")
    }

    private func appendFormField(_ name: String, value: String, to body: inout Data, boundary: String) {
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8)!)
        body.append("\(value)\r\n".data(using: .utf8)!)
    }
}
