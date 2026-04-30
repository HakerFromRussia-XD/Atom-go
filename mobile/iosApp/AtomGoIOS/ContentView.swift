import Foundation
import SwiftUI

private struct LoginResponse: Decodable {
    let role: String
    let accessToken: String

    enum CodingKeys: String, CodingKey {
        case role
        case accessToken = "access_token"
    }
}

private enum LoginRequestError: Error {
    case httpError(code: Int, body: String)
    case invalidPayload
    case invalidUrl
}

struct ContentView: View {
    @State private var login = ""
    @State private var password = ""
    @State private var statusText = "Статус: ожидание"
    @State private var isLoading = false

    private let backendBaseUrl = "http://127.0.0.1:8080/api/v1"

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Atom Go Login")
                .font(.system(size: 24, weight: .bold))
                .padding(.bottom, 4)

            TextField("Login", text: $login)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(10)

            SecureField("Password", text: $password)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .padding(12)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(10)

            Button(action: signInTapped) {
                Text("Sign in")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .disabled(isLoading)
            .padding(.top, 4)
            .padding(.bottom, 4)

            Text(statusText)
                .font(.system(size: 16))
                .foregroundStyle(.primary)

            Spacer(minLength: 0)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(.systemBackground))
    }

    private func signInTapped() {
        let normalizedLogin = login.trimmingCharacters(in: .whitespacesAndNewlines)
        if normalizedLogin.isEmpty || password.isEmpty {
            statusText = "Статус: введите логин и пароль"
            return
        }

        statusText = "Статус: выполняю вход..."
        isLoading = true

        Task {
            do {
                let result = try await doLogin(login: normalizedLogin, password: password)
                statusText = "Статус: вход выполнен, роль: \(result.role)\nToken: \(String(result.accessToken.prefix(12)))..."
            } catch {
                statusText = "Статус: ошибка входа: \(error.localizedDescription)"
            }
            isLoading = false
        }
    }

    private func doLogin(login: String, password: String) async throws -> LoginResponse {
        guard let url = URL(string: "\(backendBaseUrl)/auth/login") else {
            throw LoginRequestError.invalidUrl
        }

        let requestBody: [String: String] = [
            "login": login,
            "password": password
        ]

        guard let bodyData = try? JSONSerialization.data(withJSONObject: requestBody) else {
            throw LoginRequestError.invalidPayload
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 7
        request.httpBody = bodyData
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw LoginRequestError.invalidPayload
        }

        let rawBody = String(data: data, encoding: .utf8) ?? ""
        guard (200...299).contains(httpResponse.statusCode) else {
            throw LoginRequestError.httpError(code: httpResponse.statusCode, body: rawBody)
        }

        return try JSONDecoder().decode(LoginResponse.self, from: data)
    }
}

extension LoginRequestError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case let .httpError(code, body):
            return "HTTP \(code): \(body)"
        case .invalidPayload:
            return "Некорректный ответ backend"
        case .invalidUrl:
            return "Некорректный URL backend"
        }
    }
}

#Preview {
    ContentView()
}
