import Foundation

struct JobListing: Decodable, Identifiable, Hashable {
    let id: Int
    let date: String
    let slug: String
    let link: String
    let title: RenderedText
    let excerpt: RenderedText?
    let content: RenderedText?
    let meta: [String: MetaValue]?
    let jobTypes: [Int]?
    let embedded: EmbeddedPayload?
    let uagbExcerpt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case date
        case slug
        case link
        case title
        case excerpt
        case content
        case meta
        case embedded = "_embedded"
        case jobTypes = "job-types"
        case uagbExcerpt = "uagb_excerpt"
    }

    var titleText: String {
        title.rendered.htmlStripped
    }

    var excerptText: String {
        let html = uagbExcerpt ?? excerpt?.rendered ?? content?.rendered ?? ""
        return html.htmlStripped
    }

    var contentHTML: String {
        content?.rendered ?? excerpt?.rendered ?? ""
    }

    var company: String {
        meta?["_company_name"]?.stringValue?.condensedWhitespace ?? ""
    }

    var location: String {
        meta?["_job_location"]?.stringValue?.condensedWhitespace ?? ""
    }

    var application: String {
        meta?["_application"]?.stringValue?.condensedWhitespace ?? ""
    }

    var featuredImageURL: URL? {
        guard let sourceURL = embedded?.featuredMedia?.first?.sourceURL else {
            return nil
        }
        return URL(string: sourceURL)
    }

    var jobType: String {
        guard let firstType = jobTypes?.first else {
            return ""
        }
        return Self.jobTypeMapping[firstType] ?? ""
    }

    var formattedDate: String {
        String(date.prefix(10))
    }

    private static let jobTypeMapping: [Int: String] = [
        6: "Full Time",
        7: "Part Time",
        8: "Temporary",
        9: "Freelance",
        10: "Internship",
        30: "Consultancy",
        31: "Contract",
        32: "Tender"
    ]
}

struct CachedJobListing: Codable, Identifiable, Hashable {
    let id: Int
    let date: String
    let slug: String
    let link: String
    let title: String
    let excerpt: String
    let contentHTML: String
    let company: String
    let location: String
    let application: String
    let jobType: String
    let featuredImageURLString: String?
}

struct RenderedText: Decodable, Hashable {
    let rendered: String
}

struct EmbeddedPayload: Decodable, Hashable {
    let featuredMedia: [FeaturedMedia]?

    enum CodingKeys: String, CodingKey {
        case featuredMedia = "wp:featuredmedia"
    }
}

struct FeaturedMedia: Decodable, Hashable {
    let sourceURL: String

    enum CodingKeys: String, CodingKey {
        case sourceURL = "source_url"
    }
}

enum MetaValue: Decodable, Hashable {
    case string(String)
    case bool(Bool)
    case int(Int)
    case double(Double)
    case array([MetaValue])
    case object([String: MetaValue])
    case null

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let value = try? container.decode(String.self) {
            self = .string(value)
        } else if let value = try? container.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? container.decode(Int.self) {
            self = .int(value)
        } else if let value = try? container.decode(Double.self) {
            self = .double(value)
        } else if let value = try? container.decode([String: MetaValue].self) {
            self = .object(value)
        } else if let value = try? container.decode([MetaValue].self) {
            self = .array(value)
        } else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unsupported meta value."
            )
        }
    }

    var stringValue: String? {
        switch self {
        case .string(let value):
            return value
        case .bool(let value):
            return String(value)
        case .int(let value):
            return String(value)
        case .double(let value):
            return String(value)
        case .array, .object, .null:
            return nil
        }
    }
}

enum JobsFeedItem: Identifiable, Hashable {
    case job(JobListing)
    case nativeAdPlaceholder(Int)

    var id: String {
        switch self {
        case .job(let job):
            return "job-\(job.id)"
        case .nativeAdPlaceholder(let index):
            return "ad-\(index)"
        }
    }
}

struct SavedJobSnapshot: Codable, Identifiable, Hashable {
    let id: Int
    let slug: String
    let title: String
    let excerpt: String
    let company: String
    let location: String
    let date: String
    let link: String
    let application: String
    let jobType: String
    let featuredImageURLString: String?
    let savedAt: Date

    init(job: JobListing) {
        id = job.id
        slug = job.slug
        title = job.titleText
        excerpt = job.excerptText
        company = job.company
        location = job.location
        date = job.formattedDate
        link = job.link
        application = job.application
        jobType = job.jobType
        featuredImageURLString = job.featuredImageURL?.absoluteString
        savedAt = Date()
    }

    var asListing: JobListing {
        JobListing(
            id: id,
            date: date,
            slug: slug,
            link: link,
            title: RenderedText(rendered: title),
            excerpt: RenderedText(rendered: excerpt),
            content: RenderedText(rendered: excerpt),
            meta: [
                "_company_name": .string(company),
                "_job_location": .string(location),
                "_application": .string(application)
            ],
            jobTypes: nil,
            embedded: featuredImageURLString.map { EmbeddedPayload(featuredMedia: [FeaturedMedia(sourceURL: $0)]) },
            uagbExcerpt: excerpt
        )
    }
}

extension JobListing {
    var cachedValue: CachedJobListing {
        CachedJobListing(
            id: id,
            date: date,
            slug: slug,
            link: link,
            title: title.rendered,
            excerpt: excerpt?.rendered ?? uagbExcerpt ?? "",
            contentHTML: content?.rendered ?? excerpt?.rendered ?? "",
            company: company,
            location: location,
            application: application,
            jobType: jobType,
            featuredImageURLString: featuredImageURL?.absoluteString
        )
    }

    init(cachedValue: CachedJobListing) {
        self.init(
            id: cachedValue.id,
            date: cachedValue.date,
            slug: cachedValue.slug,
            link: cachedValue.link,
            title: RenderedText(rendered: cachedValue.title),
            excerpt: RenderedText(rendered: cachedValue.excerpt),
            content: RenderedText(rendered: cachedValue.contentHTML),
            meta: [
                "_company_name": .string(cachedValue.company),
                "_job_location": .string(cachedValue.location),
                "_application": .string(cachedValue.application)
            ],
            jobTypes: Self.jobTypeIDs(for: cachedValue.jobType),
            embedded: cachedValue.featuredImageURLString.map {
                EmbeddedPayload(featuredMedia: [FeaturedMedia(sourceURL: $0)])
            },
            uagbExcerpt: cachedValue.excerpt
        )
    }

    private static func jobTypeIDs(for jobType: String) -> [Int]? {
        guard let match = jobTypeMapping.first(where: { $0.value.caseInsensitiveCompare(jobType) == .orderedSame }) else {
            return nil
        }
        return [match.key]
    }

    var hasUsableDisplayContent: Bool {
        let content = (content?.rendered ?? excerpt?.rendered ?? "").htmlStripped
        let summary = (uagbExcerpt ?? excerpt?.rendered ?? "").htmlStripped
        return !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !summary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

extension String {
    var htmlStripped: String {
        replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&#038;", with: "&")
            .replacingOccurrences(of: "&hellip;", with: "...")
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var condensedWhitespace: String {
        replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
