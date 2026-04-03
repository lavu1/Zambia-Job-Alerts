import SwiftUI
import UIKit

struct HTMLTextView: View {
    let html: String

    var body: some View {
        Text(attributedContent)
            .font(.body)
            .foregroundStyle(.primary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .textSelection(.enabled)
    }

    private var attributedContent: AttributedString {
        AttributedString(html.sanitizedHTML.attributedHTML)
    }
}

private extension String {
    var sanitizedHTML: String {
        let withoutUnsupportedMedia = replacingOccurrences(
            of: "<(figure|img|iframe|script|style)[\\s\\S]*?</\\1>|<(img|br)\\b[^>]*?/?>",
            with: "",
            options: .regularExpression
        )

        return """
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
        <style>
        body {
            margin: 0;
            padding: 0;
            font: -apple-system-body;
            color: \(UIColor.label.hexString);
            background-color: transparent;
            overflow-wrap: break-word;
            word-wrap: break-word;
        }
        p, div, li, ul, ol, h1, h2, h3, h4, h5, h6 {
            margin-top: 0;
            margin-left: 0;
            margin-right: 0;
            margin-bottom: 12px;
            padding: 0;
        }
        </style>
        </head>
        <body>\(withoutUnsupportedMedia)</body>
        </html>
        """
    }

    var attributedHTML: NSAttributedString {
        guard let data = data(using: .utf8) else {
            return NSAttributedString(string: self)
        }

        let options: [NSAttributedString.DocumentReadingOptionKey: Any] = [
            .documentType: NSAttributedString.DocumentType.html,
            .characterEncoding: String.Encoding.utf8.rawValue
        ]

        guard let attributed = try? NSMutableAttributedString(
            data: data,
            options: options,
            documentAttributes: nil
        ) else {
            return NSAttributedString(string: self.htmlStripped)
        }

        let range = NSRange(location: 0, length: attributed.length)
        attributed.addAttributes(
            [
                .foregroundColor: UIColor.label,
                .font: UIFont.preferredFont(forTextStyle: .body)
            ],
            range: range
        )

        return attributed
    }
}

private extension UIColor {
    var hexString: String {
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return String(
            format: "#%02X%02X%02X",
            Int(red * 255),
            Int(green * 255),
            Int(blue * 255)
        )
    }
}
