import SwiftUI

struct HGDialog: Identifiable {
    let id = UUID()
    var title: String?
    var message: String
    var primaryTitle: String
    var secondaryTitle: String?
    var informStyle = false
    var onPrimary: () -> Void = {}
    var onSecondary: () -> Void = {}
}

struct HGDialogPresenter: ViewModifier {
    @Binding var dialog: HGDialog?

    func body(content: Content) -> some View {
        content.overlay {
            if let dialog {
                ZStack {
                    Color.black.opacity(0.55)
                        .ignoresSafeArea()
                        .onTapGesture {}
                    VStack(spacing: 0) {
                        if let title = dialog.title, !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            Text(title)
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(AppTheme.onSurface)
                                .multilineTextAlignment(.center)
                                .frame(maxWidth: .infinity)
                        }
                        Text(dialog.message)
                            .font(.system(size: 15))
                            .foregroundStyle(Color(red: 0.631, green: 0.631, blue: 0.631))
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                            .frame(maxWidth: .infinity)
                            .padding(.top, (dialog.title?.isEmpty ?? true) ? 0 : 14)
                        Button {
                            let action = dialog.onPrimary
                            self.dialog = nil
                            action()
                        } label: {
                            Text(dialog.primaryTitle)
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(dialog.informStyle ? .white : Color(red: 0.102, green: 0.102, blue: 0.102))
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .padding(.horizontal, 16)
                                .background(dialog.informStyle ? AppTheme.primary : Color(red: 1, green: 0.549, blue: 0.451))
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 24)
                        if let secondary = dialog.secondaryTitle {
                            Button {
                                let action = dialog.onSecondary
                                self.dialog = nil
                                action()
                            } label: {
                                Text(secondary)
                                    .font(.system(size: 15))
                                    .foregroundStyle(Color(red: 0.922, green: 0.922, blue: 0.961))
                                    .frame(maxWidth: .infinity, minHeight: 48)
                                    .padding(.horizontal, 16)
                                    .background(Color(red: 0.173, green: 0.173, blue: 0.18))
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                            .padding(.top, 12)
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, 28)
                    .padding(.bottom, 24)
                    .background(Color(red: 0.11, green: 0.11, blue: 0.118))
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                    .padding(.horizontal, 24)
                }
                .zIndex(1000)
            }
        }
    }
}

struct HGLoadingDialog: View {
    var message: String

    var body: some View {
        ZStack {
            Color.black.opacity(0.55).ignoresSafeArea()
            VStack(spacing: 16) {
                ProgressView()
                    .tint(AppTheme.primary)
                    .scaleEffect(1.2)
                Text(message)
                    .font(.system(size: 15))
                    .foregroundStyle(Color(red: 0.631, green: 0.631, blue: 0.631))
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 24)
            .padding(.top, 28)
            .padding(.bottom, 28)
            .background(Color(red: 0.11, green: 0.11, blue: 0.118))
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            .padding(.horizontal, 24)
        }
        .zIndex(1000)
    }
}

extension View {
    func hgDialog(_ dialog: Binding<HGDialog?>) -> some View {
        modifier(HGDialogPresenter(dialog: dialog))
    }
}
