import Foundation
import Testing
@testable import Zambia_Job_Alerts

struct Zambia_Job_AlertsTests {
    @Test func savedJobSnapshotRetainsCoreFields() async throws {
        let job = JobListing(
            id: 10,
            date: "2026-03-29T12:00:00",
            slug: "ios-engineer",
            link: "https://zambiajobalerts.com/job/ios-engineer",
            title: RenderedText(rendered: "<b>iOS Engineer</b>"),
            excerpt: RenderedText(rendered: "<p>Build SwiftUI apps</p>"),
            content: nil,
            meta: [
                "_company_name": .string("Alpha"),
                "_job_location": .string("Lusaka"),
                "_application": .string("jobs@example.com")
            ],
            jobTypes: [6],
            embedded: nil,
            uagbExcerpt: nil
        )

        let snapshot = SavedJobSnapshot(job: job)

        #expect(snapshot.id == 10)
        #expect(snapshot.title == "iOS Engineer")
        #expect(snapshot.company == "Alpha")
        #expect(snapshot.location == "Lusaka")
    }
}
