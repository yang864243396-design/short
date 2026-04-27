import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss

    @State private var isLogin = true
    @State private var email = ""
    @State private var password = ""
    @State private var nickname = ""
    @State private var code = ""
    @State private var busy = false
    @State private var message: String?
    @State private var codeCooldown = 0
    @State private var hgDialog: HGDialog?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("邮箱", text: $email)
                        .textContentType(.emailAddress)
                        .textInputAutocapitalization(.never)
                    SecureField("密码", text: $password)
                    if !isLogin {
                        TextField("昵称（选填）", text: $nickname)
                        HStack {
                            TextField("邮箱验证码", text: $code)
                            Button(codeCooldown > 0 ? "\(codeCooldown)s" : "获取验证码") {
                                Task { await sendCode() }
                            }
                            .disabled(codeCooldown > 0 || busy)
                        }
                    }
                }
                Section {
                    Button(isLogin ? "登录" : "注册") { Task { await submit() } }
                        .disabled(busy)
                }
                Section {
                    Button(isLogin ? "没有账号？立即注册" : "已有账号？立即登录") {
                        isLogin.toggle()
                    }
                }
            }
            .onChange(of: message) { text in
                guard let text else { return }
                hgDialog = HGDialog(
                    title: "提示",
                    message: text,
                    primaryTitle: "确定",
                    informStyle: true,
                    onPrimary: { message = nil }
                )
            }
            .navigationTitle(isLogin ? "登录" : "注册")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .hgDialog($hgDialog)
    }

    private func sendCode() async {
        let e = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard e.contains("@") else {
            message = "请先填写正确邮箱"
            return
        }
        busy = true
        defer { busy = false }
        do {
            try await APIClient.shared.sendRegisterCode(email: e)
            message = "验证码已发送至邮箱"
            codeCooldown = 60
            Task {
                for s in (1 ... 60).reversed() {
                    codeCooldown = s
                    try? await Task.sleep(nanoseconds: 1_000_000_000)
                }
            }
        } catch { message = error.localizedDescription }
    }

    private func submit() async {
        let e = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let p = password.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !e.isEmpty, !p.isEmpty else {
            message = "请填写邮箱和密码"
            return
        }
        busy = true
        defer { busy = false }
        do {
            if isLogin {
                let a = try await APIClient.shared.login(email: e, password: p)
                session.setSession(
                    token: a.token,
                    userId: a.user?.id ?? 0,
                    displayName: a.user?.nickname
                )
            } else {
                guard p.count >= 6 else { message = "密码至少6位"; return }
                let c = code.trimmingCharacters(in: .whitespacesAndNewlines)
                guard c.count >= 4 else { message = "请输入邮箱验证码"; return }
                let a = try await APIClient.shared.register(
                    email: e,
                    password: p,
                    code: c,
                    nickname: nickname.isEmpty ? nil : nickname
                )
                session.setSession(
                    token: a.token,
                    userId: a.user?.id ?? 0,
                    displayName: a.user?.nickname
                )
            }
            dismiss()
        } catch { message = error.localizedDescription }
    }
}
