//
//  HTMLView.swift
//  Zambia Job Alerts
//
//  Created by Lavu Mweemba on 10/04/2026.
//

import SwiftUI
import WebKit

struct HTMLView: UIViewRepresentable {
    let html: String
    @Binding var contentHeight: CGFloat

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.bounces = false
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        let isDarkMode = context.environment.colorScheme == .dark
        context.coordinator.isDarkMode = isDarkMode
        let textColor = isDarkMode ? "#FFFFFF" : "#111111"
        let wrappedHTML = """
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                html {
                    background: transparent !important;
                }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                    font-size: 16px;
                    line-height: 1.6;
                    color: \(textColor) !important;
                    margin: 0;
                    padding: 0;
                    background: transparent !important;
                }
                * {
                    color: #1C1C1E !important;
                    background: transparent !important;
                    box-shadow: none !important;
                }
                p, li, div {
                    margin-bottom: 0.9em;
                }
                ul, ol {
                    padding-left: 1.25rem;
                }
                h1, h2, h3, h4, h5, h6 {
                    color: #111111 !important;
                    line-height: 1.3;
                    margin-top: 1.1em;
                    margin-bottom: 0.5em;
                }
                img {
                    max-width: 100%;
                    height: auto;
                    border-radius: 12px;
                }
                a {
                    color: #0A84FF !important;
                    text-decoration: none;
                }
        
                img {
                    max-width: 100%;
                    height: auto;
                }

                @media (prefers-color-scheme: dark) {
                    body {
                        color: #FFFFFF;
                        background: transparent;
                    }

                    a {
                        color: #4DA3FF;
                    }
                }
        
            </style>
        </head>
        <body>
            \(html)
        </body>
        </html>
        """

        webView.loadHTMLString(wrappedHTML, baseURL: nil)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(contentHeight: $contentHeight)
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        @Binding private var contentHeight: CGFloat
        var isDarkMode: Bool

        init(contentHeight: Binding<CGFloat>) {
            _contentHeight = contentHeight
            self.isDarkMode = false
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            let textColor = isDarkMode ? "#FFFFFF" : "#111111"
            let normalizeScript = """
            (function() {
                document.documentElement.style.setProperty('background', 'transparent', 'important');
                document.body.style.setProperty('background', 'transparent', 'important');
                document.body.style.setProperty('color', '\(textColor)', 'important');

                var nodes = document.querySelectorAll('*');
                for (var i = 0; i < nodes.length; i++) {
                    var node = nodes[i];
                    node.style.setProperty('color', '\(textColor)', 'important');
                    node.style.setProperty('background', 'transparent', 'important');
                    node.style.setProperty('background-color', 'transparent', 'important');
                    node.style.setProperty('box-shadow', 'none', 'important');
                }

                var links = document.querySelectorAll('a');
                for (var j = 0; j < links.length; j++) {
                    links[j].style.setProperty('color', '#0A84FF', 'important');
                }

                return Math.max(
                    document.body.scrollHeight,
                    document.body.offsetHeight,
                    document.documentElement.clientHeight,
                    document.documentElement.scrollHeight,
                    document.documentElement.offsetHeight
                );
            })();
            """

            webView.evaluateJavaScript(normalizeScript) { result, _ in
                let measuredHeight: CGFloat
                if let heightNumber = result as? NSNumber {
                    measuredHeight = CGFloat(truncating: heightNumber)
                } else {
                    measuredHeight = webView.scrollView.contentSize.height
                }

                DispatchQueue.main.async {
                    self.contentHeight = max(measuredHeight, 300)
                }
            }
        }
    }
}
