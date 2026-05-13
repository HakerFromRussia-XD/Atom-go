import SwiftUI
import PhotosUI
import UIKit

struct PlaceholderBikeAvatar: View {
    let cornerRadius: CGFloat

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            ZStack {
                Color(red: 227 / 255, green: 230 / 255, blue: 235 / 255)

                Path { path in
                    path.move(to: CGPoint(x: 0, y: 0))
                    path.addLine(to: CGPoint(x: size.width, y: size.height))
                    path.move(to: CGPoint(x: size.width, y: 0))
                    path.addLine(to: CGPoint(x: 0, y: size.height))
                }
                .stroke(Color(red: 156 / 255, green: 166 / 255, blue: 179 / 255).opacity(0.45), lineWidth: 1)

                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(
                        Color(red: 156 / 255, green: 166 / 255, blue: 179 / 255),
                        style: StrokeStyle(lineWidth: 1, dash: [4, 4])
                    )
            }
        }
    }
}

struct BikePhotoView<Placeholder: View>: View {
    let source: String?
    @ViewBuilder let placeholder: () -> Placeholder

    var body: some View {
        if let decodedImage {
            Image(uiImage: decodedImage)
                .resizable()
                .scaledToFill()
        } else if let remoteURL {
            AsyncImage(url: remoteURL) { phase in
                switch phase {
                case let .success(image):
                    image.resizable().scaledToFill()
                default:
                    placeholder()
                }
            }
        } else {
            placeholder()
        }
    }

    private var normalizedSource: String? {
        let value = source?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? nil : value
    }

    private var remoteURL: URL? {
        guard let normalizedSource, !normalizedSource.lowercased().hasPrefix("data:image") else {
            return nil
        }
        return URL(string: normalizedSource)
    }

    private var decodedImage: UIImage? {
        guard let normalizedSource, normalizedSource.lowercased().hasPrefix("data:image") else {
            return nil
        }
        guard
            let commaIndex = normalizedSource.firstIndex(of: ","),
            normalizedSource[..<commaIndex].lowercased().contains(";base64")
        else {
            return nil
        }
        let encoded = String(normalizedSource[normalizedSource.index(after: commaIndex)...])
        guard let data = Data(base64Encoded: encoded) else {
            return nil
        }
        return UIImage(data: data)
    }
}

extension String {
    var trimmedToOptional: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}


extension DateFormatter {
    static let apiDate: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}
