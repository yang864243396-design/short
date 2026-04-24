import Foundation
import SwiftUI
import Combine

@MainActor
final class SessionStore: ObservableObject {
    private enum K {
        static let token = "hg_token"
        static let userId = "hg_user_id"
        static let username = "hg_username"
    }

    @Published private(set) var token: String
    @Published private(set) var userId: Int64
    @Published private(set) var username: String

    var isLoggedIn: Bool { !token.isEmpty }

    init() {
        let d = UserDefaults.standard
        self.token = d.string(forKey: K.token) ?? ""
        if d.object(forKey: K.userId) != nil {
            self.userId = Int64(d.integer(forKey: K.userId))
        } else {
            self.userId = 0
        }
        self.username = d.string(forKey: K.username) ?? ""
    }

    func setSession(token: String, userId: Int64, displayName: String?) {
        self.token = token
        self.userId = userId
        self.username = displayName ?? ""
        let d = UserDefaults.standard
        d.set(token, forKey: K.token)
        d.set(Int(userId), forKey: K.userId)
        d.set(username, forKey: K.username)
    }

    func logout() {
        token = ""
        userId = 0
        username = ""
        let d = UserDefaults.standard
        d.removeObject(forKey: K.token)
        d.removeObject(forKey: K.userId)
        d.removeObject(forKey: K.username)
    }
}
