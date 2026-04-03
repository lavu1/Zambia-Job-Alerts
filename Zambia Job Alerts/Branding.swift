import SwiftUI

enum BrandPalette {
    static let blue = Color(red: 20 / 255, green: 48 / 255, blue: 121 / 255)
    static let orange = Color(red: 1.0, green: 137 / 255, blue: 28 / 255)
    static let mist = Color(red: 244 / 255, green: 247 / 255, blue: 252 / 255)
    static let ink = Color.primary
    static let cardText = Color(red: 28 / 255, green: 32 / 255, blue: 43 / 255)
    static let cardSecondaryText = Color(red: 96 / 255, green: 104 / 255, blue: 122 / 255)
}

struct BrandMarkView: View {
    var size: CGFloat = 68

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [BrandPalette.blue, BrandPalette.blue.opacity(0.9)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            Text("Z")
                .font(.system(size: size * 0.58, weight: .black, design: .rounded))
                .foregroundStyle(.white)

            RoundedRectangle(cornerRadius: size * 0.07, style: .continuous)
                .fill(BrandPalette.orange)
                .frame(width: size * 0.16, height: size * 0.86)
                .rotationEffect(.degrees(34))
                .offset(x: size * 0.02, y: -size * 0.02)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct BrandHeaderView: View {
    let subtitle: String

    var body: some View {
        HStack(spacing: 14) {
            BrandMarkView(size: 56)

            VStack(alignment: .leading, spacing: 4) {
                Text("Zambia Job Alerts")
                    .font(.title3.bold())
                    .foregroundStyle(BrandPalette.blue)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(BrandPalette.cardSecondaryText)
            }

            Spacer()
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(BrandPalette.mist)
        )
    }
}

struct StatChip: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.headline.bold())
                .foregroundStyle(BrandPalette.cardText)
            Text(title)
                .font(.caption)
                .foregroundStyle(BrandPalette.cardSecondaryText)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            Capsule()
                .fill(Color.white.opacity(0.9))
        )
    }
}
