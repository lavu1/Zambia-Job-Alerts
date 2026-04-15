import StoreKit
import SwiftUI

struct MoreView: View {
    @ObservedObject var notificationManager: NotificationManager
    let adCoordinator: AdCoordinator
    @Environment(\.requestReview) private var requestReview
    @State private var statusMessage: String?
    private let appShareURL = URL(string: "https://apps.apple.com/app/id6761562142")!
    private let appShareMessage = "Check out Zambia Job Alerts for the latest jobs and career opportunities: https://apps.apple.com/app/id6761562142"

    var body: some View {
        NavigationStack {
            List {
                Section {
                    BrandHeaderView(subtitle: "About, support, and notification settings for the iOS app.")
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }

                Section("App") {
                    NavigationLink("About Zambia Job Alerts") {
                        AboutView()
                    }
                    NavigationLink("Terms & Conditions") {
                        TermsAndConditionsView(adCoordinator: adCoordinator)
                    }
                    NavigationLink("Post a Job") {
                        PostJobView(adCoordinator: adCoordinator)
                    }
                    ShareLink(
                        item: appShareURL,
                        subject: Text("Zambia Job Alerts"),
                        message: Text(appShareMessage)
                    ) {
                        Label("Share App", systemImage: "square.and.arrow.up")
                    }
                    Button {
                        requestReview()
                    } label: {
                        Label("Rate App", systemImage: "star.bubble")
                    }
//                    Link("Visit Website", destination: URL(string: "https://zambiajobalerts.com")!)
                    Link("Contact Support", destination: URL(string: "mailto:contact@zambiajobalerts.com")!)
                }

                Section("Notifications") {
                    HStack {
                        Text("Status")
                        Spacer()
                        Text(notificationManager.isAuthorized ? "Enabled" : "Disabled")
                            .foregroundStyle(notificationManager.isAuthorized ? .green : .secondary)
                    }

                    if let fcmToken = notificationManager.fcmToken, !fcmToken.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("FCM Token Status")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(fcmToken)
                                .font(.footnote.monospaced())
                                .textSelection(.enabled)
                        }
                        .task {
                            await uploadToken(fcmToken)
                        }
                    }


                    Button(notificationManager.isAuthorized ? "Refresh Permission State" : "Enable Notifications") {
                        Task {
                            if notificationManager.isAuthorized {
                                await notificationManager.refreshAuthorizationStatus()
                                notificationManager.refreshFCMToken()
                                statusMessage = "Notification status refreshed."
                            } else {
                                let granted = await notificationManager.requestAuthorization()
                                notificationManager.refreshFCMToken()
                                statusMessage = granted ? "Notifications enabled." : "Notifications were not granted."
                            }
                        }
                    }
                }

                if let statusMessage {
                    Section {
                        Text(statusMessage)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("More")
            .task {
                await notificationManager.refreshAuthorizationStatus()
                notificationManager.refreshFCMToken()
            }
            .onReceive(NotificationCenter.default.publisher(for: .fcmTokenUpdated)) { notification in
                if let token = notification.object as? String {
                    notificationManager.refreshFCMToken()
                    statusMessage = "FCM token updated."
                    print("FCM token available for Postman: \(token)")
                }
            }
        }
    }

    private func uploadToken(_ token: String) async {
        guard let encodedToken = token.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://zambiajobalerts.com/savetoken.php?token=\(encodedToken)") else {
            return
        }

        do {
            let (_, response) = try await URLSession.shared.data(from: url)
            if let httpResponse = response as? HTTPURLResponse {
                print("Server responded with code: \(httpResponse.statusCode)")
            }
        } catch {
            print("Failed to send token: \(error.localizedDescription)")
        }
    }
}

private struct AboutView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                BrandHeaderView(subtitle: "Fresh jobs, saved vacancies, and premium career services built for Zambia.")
                    .foregroundStyle(.primary)

                Text("Zambia Job Alerts shares live job opportunities from companies around Zambia and beyong and presents them in a faster mobile workflow for iPhone and iPad.")
                    .foregroundStyle(.secondary)

                Text("Core features")
                    .font(.headline)

                VStack(alignment: .leading, spacing: 10) {
                    Label("Latest jobs from hiring companies accross the country", systemImage: "network")
                    Label("Persistent saved jobs", systemImage: "bookmark.fill")
                    Label("Premium services with credit redemption", systemImage: "gift.fill")
//                    Label("Deep-link ready job detail view", systemImage: "link")
                }
                .foregroundStyle(BrandPalette.ink)
            }
            .padding(20)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("About")
    }

}

private struct TermsAndConditionsView: View {
    let adCoordinator: AdCoordinator

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                BrandHeaderView(subtitle: "The terms that govern use of Zambia Job Alerts and its services.")
                    .foregroundStyle(.primary)

                FixedBannerAdView()

                termsSection(
                    title: "Welcome",
                    body: "Welcome to our website – www.zambiajobalerts.com. If you continue to browse and use this website, you agree to comply with and be bound by the following terms and conditions of use, which together with our privacy policy govern Zambia Job Alerts’ relationship with you in relation to this website. If you disagree with any part of these terms and conditions, please do not use our website."
                )

                termsSection(
                    title: "1. Information on This Site",
                    body: "The content of the pages of this website is for general information and use only. It is subject to change without notice.\n\nNeither we nor any third parties provide any warranty or guarantee as to the accuracy, timeliness, performance, completeness, or suitability of the information and materials found or offered on this website for any particular purpose. You acknowledge that such information and materials may contain inaccuracies or errors, and we expressly exclude liability for any such inaccuracies or errors to the fullest extent permitted by law.\n\nYour use of any information or materials on this website is entirely at your own risk, for which we shall not be liable. It shall be your own responsibility to ensure that any products, services, or information available through this website meet your specific requirements."
                )

                termsSection(
                    title: "2. Links",
                    body: "This website may include links to other websites. These links are provided for your convenience to provide further information. We have no responsibility for the content of the linked website(s). Using links to gain access to such sites is entirely at your own risk."
                )

                termsSection(
                    title: "3. Content Rights",
                    body: "This website contains material which is owned by or licensed to us. This material includes, but is not limited to, the design, layout, look, appearance, and graphics. Reproduction is prohibited other than in accordance with the copyright notice, which forms part of these terms and conditions.\n\nAll trademarks reproduced in this website, which are not the property of or licensed to the operator, are acknowledged on the website.\n\nUnauthorized use of this website may give rise to a claim for damages and/or be a criminal offense."
                )

                termsSection(
                    title: "4. Copyright Notice",
                    body: "This website and its content are copyright of Zambia Job Alerts – © Zambia Job Alerts 2024. All rights reserved. Any redistribution or reproduction of part or all of the contents in any form is prohibited.\n\nYou may not, except with our express written permission, distribute or commercially exploit the content. Nor may you transmit it or store it in any other website or other form of electronic retrieval system."
                )

                termsSection(
                    title: "5. Website Disclaimer",
                    body: "The information contained in this website is for general information purposes only. The information is provided by Zambia Job Alerts and our users, and we make no representations or warranties of any kind, express or implied, about the completeness, accuracy, reliability, suitability, or availability with respect to the website or the information, products, services, or related graphics contained on the website for any purpose. Any reliance you place on such information is therefore strictly at your own risk.\n\nIn no event will we be liable for any loss or damage including without limitation, indirect or consequential loss or damage, or any loss or damage whatsoever arising from loss of data or profits arising out of, or in connection with, the use of this website.\n\nThrough this website, you may be able to link to other websites which are not under the control of Zambia Job Alerts. We have no control over the nature, content, and availability of those sites.\n\nEvery effort is made to keep the website up and running smoothly. However, Zambia Job Alerts takes no responsibility for, and will not be liable for, the website being temporarily unavailable due to technical issues beyond our control."
                )

                FixedBannerAdView()

                termsSection(
                    title: "6. Law & Jurisdiction",
                    body: "Recruiters, job seekers, and all other users will comply with all applicable employment, data, and equality laws.\n\nUse of this website and any dispute arising out of such use of the website is subject to the laws of Zambia."
                )

                termsSection(
                    title: "7. Job Seekers",
                    body: "You acknowledge that our jobs board operates as a venue only and does not generally introduce or supply job seekers to recruiters or recruiters to job seekers.\n\nWe accept no responsibility or liability for the content of advertisements placed on our site and expect candidates to carry out such verification procedures as are customary and prudent in the circumstances.\n\nIf you provide information on this site, then you are solely responsible for the information submitted. You are responsible for ensuring that all information supplied by you is true and accurate and that it is not discriminatory, obscene, offensive, defamatory, or otherwise illegal.\n\nFollowing registration, you will be required to sign in by submitting a username and password. You are solely responsible for the security and proper use of your password, which should be kept confidential at all times.\n\nWe may terminate your registration and/or deny you access to your account without any explanation or notification."
                )

                termsSection(
                    title: "8. Employers & Recruiters",
                    body: "Employers and recruiters acknowledge that our jobs board operates as a venue only and does not generally introduce or supply job seekers to recruiters or recruiters to job seekers.\n\nWe do not guarantee any response to advertisements or that responses will be from individuals suitable for the job advertised.\n\nAll and any subsequent dealings between the employer/recruiter and any job seeker in connection with the job seeker’s response to a job posting are the responsibility of the employer/recruiter, and Zambia Job Alerts accepts no liability whatsoever therewith.\n\nEmployers and recruiters agree to deal fairly and professionally with individuals who may respond to an advertisement you have posted.\n\nAdvertisements that discriminate on grounds of sex, race, or disability are illegal. If we believe an advertisement may be discriminatory, we may at our discretion amend or remove the advertisement from our website without liability.\n\nSearching or browsing our database of registered job seekers can only be used for recruitment purposes. Using this service for marketing or direct selling to candidates is prohibited."
                )

                termsSection(
                    title: "9. Forum & Communication",
                    body: "Any posting of information in these forums is the opinion of the person posting only and in no way reflects the opinions or attitudes of Zambia Job Alerts.\n\nWe encourage debate and the sharing of information between users. However, your use of our forums and communication systems must be lawful.\n\nWe reserve the right to remove any content that violates our terms, including offensive or misleading information.\n\nFor any concerns or questions, please contact us at support@zambiajobalerts.com."
                )

                FixedBannerAdView()
            }
            .padding(20)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Terms")
    }

    @ViewBuilder
    private func termsSection(title: String, body: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
                .foregroundStyle(BrandPalette.blue)
            Text(body)
                .foregroundStyle(.secondary)
        }
    }
}
