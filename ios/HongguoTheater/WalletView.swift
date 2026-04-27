import SwiftUI
import UIKit

struct WalletView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.scenePhase) private var scenePhase
    @State private var balance: WalletBalance?
    @State private var env: RechargePackagesEnvelope?
    @State private var path = NavigationPath()
    @State private var message: String?
    @State private var payQueryMch: String?
    @State private var payQueryPayId: String?
    @State private var showPayChannel = false
    @State private var payChannelPkg: RechargePackageItem?
    @State private var payChannelEnv: RechargePackagesEnvelope?
    @State private var confirmPackage: RechargePackageItem?
    @State private var confirmEnvelope: RechargePackagesEnvelope?
    @State private var recharging = false
    @State private var payChecking = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                walletHeader
                rechargeGrid
                supportCard
            }
            .padding()
        }
        .background(AppTheme.background)
        .navigationTitle("钱包")
        .task { await reload() }
        .refreshable { await reload() }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                Task { await queryPendingRechargeIfNeeded() }
            }
        }
        .overlay {
            if recharging || payChecking {
                ZStack {
                    Color.black.opacity(0.35).ignoresSafeArea()
                    VStack(spacing: 12) {
                        ProgressView().tint(AppTheme.primary)
                        Text(recharging ? "订单提交中，请稍候" : "正在检测支付结果，请稍候")
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.onSurface)
                    }
                    .padding(20)
                    .hgCard(fill: AppTheme.surfaceHigh)
                }
            }
        }
        .confirmationDialog("充值套餐", isPresented: Binding(
            get: { confirmPackage != nil && confirmEnvelope != nil },
            set: { if !$0 { confirmPackage = nil; confirmEnvelope = nil } }
        ), titleVisibility: .visible) {
            Button("确定") {
                guard let pkg = confirmPackage, let e = confirmEnvelope else { return }
                confirmPackage = nil
                confirmEnvelope = nil
                Task { await buyConfirmed(pkg, e) }
            }
            Button("取消", role: .cancel) {
                confirmPackage = nil
                confirmEnvelope = nil
            }
        } message: {
            if let pkg = confirmPackage {
                Text(rechargeConfirmMessage(pkg))
            }
        }
        .confirmationDialog("选择支付方式", isPresented: $showPayChannel, titleVisibility: .visible) {
            if let e = payChannelEnv {
                ForEach(e.payOptions.filter(\.enabled), id: \.id) { opt in
                    Button("\(opt.name) (\(opt.productId))") {
                        guard let pkg = payChannelPkg else { return }
                        Task { await executeRechargeOrder(package: pkg, envelope: e, productId: opt.productId) }
                    }
                }
            }
            Button("取消", role: .cancel) {
                payChannelPkg = nil
                payChannelEnv = nil
            }
        } message: {
            Text("请选择聚合支付对应的产品")
        }
        .navigationDestination(for: WalletTxDest.self) { _ in
            WalletTransactionsView()
        }
        .alert("提示", isPresented: Binding(
            get: { message != nil },
            set: { if !$0 { message = nil } }
        )) {
            Button("确定", role: .cancel) { message = nil }
        } message: {
            Text(message ?? "")
        }
    }

    @ViewBuilder
    private var walletHeader: some View {
        if let b = balance {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(Color(red: 0.239, green: 0.18, blue: 0.165))
                    Image(systemName: "creditcard.fill")
                        .font(.title3)
                        .foregroundStyle(Color(red: 1, green: 0.557, blue: 0.451))
                }
                .frame(width: 44, height: 44)
                Text("我的钱包")
                    .font(.headline)
                    .foregroundStyle(AppTheme.onSurface)
                Spacer()
                VStack(alignment: .trailing, spacing: 8) {
                    Text("\(b.coins) \(b.currencyName ?? "金币")")
                        .font(.headline.weight(.bold))
                        .foregroundStyle(Color(red: 1, green: 0.557, blue: 0.451))
                    Button("余额流水 ›") { path.append(WalletTxDest()) }
                        .font(.caption)
                        .foregroundStyle(AppTheme.onSurface)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(AppTheme.surfaceHighest)
                        .clipShape(Capsule())
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
            .background(Color(red: 0.118, green: 0.118, blue: 0.133))
            .clipShape(Capsule())
        }
    }

    @ViewBuilder
    private var rechargeGrid: some View {
        if let e = env {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("充值套餐")
                        .font(.headline)
                        .foregroundStyle(AppTheme.onSurface)
                    Spacer()
                    if let b = balance, b.coinsPerYuan ?? 0 > 0 {
                        Text("1 金币 ≈ ¥\(String(format: "%.2f", 1.0 / Double(b.coinsPerYuan ?? 100)))")
                            .font(.caption)
                            .foregroundStyle(AppTheme.onSurfaceVariant)
                    }
                }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                    ForEach(e.list, id: \.id) { pkg in
                        Button {
                            confirmPackage = pkg
                            confirmEnvelope = e
                        } label: {
                            VStack(spacing: 6) {
                                Text(pkg.name)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(AppTheme.onSurface)
                                    .lineLimit(1)
                                Text("\(pkg.coins + pkg.bonusCoins) 币")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(AppTheme.primary)
                                Text("¥ \(String(format: "%.2f", pkg.priceYuan))")
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.onSurfaceVariant)
                            }
                            .frame(maxWidth: .infinity, minHeight: 92)
                            .padding(8)
                            .hgCard(radius: 12, fill: AppTheme.surface)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(AppTheme.primary.opacity(0.28), lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                if e.simulateAllowed {
                    Text("当前为模拟支付环境，可在下单后使用模拟到账。")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.primary)
                }
            }
        }
    }

    private var supportCard: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(AppTheme.surfaceHighest)
                Image(systemName: "questionmark.circle.fill")
                    .font(.title3)
                    .foregroundStyle(AppTheme.primary)
            }
            .frame(width: 40, height: 40)
            VStack(alignment: .leading, spacing: 4) {
                Text("充值遇到问题？")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(AppTheme.onSurface)
                Text("若充值长时间未到账，请保留订单信息并联系客服协助处理。")
                    .font(.caption)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
        }
        .padding(14)
        .hgCard(fill: Color(red: 0.078, green: 0.078, blue: 0.094))
        .overlay(
            RoundedRectangle(cornerRadius: AppTheme.cardRadius)
                .stroke(Color.white.opacity(0.15), lineWidth: 1)
        )
    }

    private func reload() async {
        guard session.isLoggedIn else { return }
        do {
            balance = try await APIClient.shared.getWallet(token: session.token)
            env = try await APIClient.shared.getRechargePackages(token: session.token)
        } catch { message = error.localizedDescription }
    }

    private func rechargeConfirmMessage(_ pkg: RechargePackageItem) -> String {
        var msg = "\(pkg.name)\n基础 \(pkg.coins) 金币"
        if pkg.bonusCoins > 0 {
            msg += " + 赠送 \(pkg.bonusCoins)"
        }
        msg += "（共 \(pkg.coins + pkg.bonusCoins)）\n¥\(String(format: "%.2f", pkg.priceYuan))"
        return msg
    }

    private func buyConfirmed(_ pkg: RechargePackageItem, _ e: RechargePackagesEnvelope) async {
        var productId: String?
        if e.lubzfEnabled {
            let opts = e.payOptions.filter(\.enabled)
            if opts.isEmpty {
                message = "暂无可用支付方式"
                return
            }
            if opts.count > 1 {
                payChannelPkg = pkg
                payChannelEnv = e
                showPayChannel = true
                return
            }
            productId = opts[0].productId
        }
        await executeRechargeOrder(package: pkg, envelope: e, productId: productId)
    }

    private func executeRechargeOrder(
        package pkg: RechargePackageItem,
        envelope e: RechargePackagesEnvelope,
        productId: String?
    ) async {
        guard !recharging else {
            message = "订单提交中，请稍候"
            return
        }
        payChannelPkg = nil
        payChannelEnv = nil
        recharging = true
        defer { recharging = false }
        do {
            let r = try await APIClient.shared.createRechargeOrder(
                packageId: pkg.id,
                productId: productId,
                token: session.token
            )
            if let url = r.payUrl, !url.isEmpty, let u = URL(string: url) {
                payQueryMch = r.mchOrderNo ?? r.order?.mchOrderNo
                payQueryPayId = r.order?.payOrderId
                let opened = await UIApplication.shared.open(u)
                if opened {
                    message = "已跳转支付，返回本应用后将自动查单；也可稍后在流水中确认。"
                } else {
                    payQueryMch = nil
                    payQueryPayId = nil
                    message = "无法打开支付链接"
                }
            } else if let oid = r.order?.id, e.simulateAllowed {
                try await APIClient.shared.simulateRechargePay(orderId: oid, token: session.token)
                message = "订单已完成，金币已发放到您的账户。"
                await reload()
            } else {
                message = "订单已完成，金币已发放到您的账户。"
                await reload()
            }
        } catch {
            message = "无法发起支付，请稍后重试"
        }
    }

    private func queryPendingRechargeIfNeeded() async {
        guard session.isLoggedIn, payQueryMch != nil || payQueryPayId != nil else { return }
        payChecking = true
        defer { payChecking = false }
        do {
            let r = try await APIClient.shared.queryRechargeOrder(
                mchOrderNo: payQueryMch,
                payOrderId: payQueryPayId,
                token: session.token
            )
            if r.order?.status == "paid" {
                payQueryMch = nil
                payQueryPayId = nil
                message = "订单已完成，金币已发放到您的账户。"
                await reload()
            } else {
                message = "暂未获取到支付结果，请下拉页面刷新后查看。若已付款，也可稍等片刻后再次刷新。"
            }
        } catch {
            if payQueryMch != nil || payQueryPayId != nil {
                message = "服务繁忙，暂时无法完成校验，请稍后再试。已付款的订单可稍后在「钱包-流水」中确认。"
            }
        }
    }
}

private struct WalletTxDest: Hashable {}

struct WalletTransactionsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var page = 1
    @State private var items: [WalletTransaction] = []
    @State private var filterIndex = 0
    @State private var hasMore = true
    @State private var loading = false

    private let filterValues: [String?] = [nil, "recharge", "consume"]
    private let filterLabels = ["全部", "充值", "消费"]

    var body: some View {
        VStack {
            HStack(spacing: 0) {
                ForEach(0 ..< filterLabels.count, id: \.self) { i in
                    Button {
                        filterIndex = i
                    } label: {
                        VStack(spacing: 6) {
                            Text(filterLabels[i])
                                .font(.subheadline.weight(filterIndex == i ? .bold : .regular))
                                .foregroundStyle(filterIndex == i ? AppTheme.onSurface : AppTheme.onSurfaceVariant)
                            Rectangle()
                                .fill(filterIndex == i ? AppTheme.walletAccent : Color.clear)
                                .frame(height: 3)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal)
            .padding(.top, 8)
            .onChange(of: filterIndex) { _ in Task { await reset() } }

            List {
                ForEach(items) { tx in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(tx.title)
                            Text(tx.createdAt ?? "")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.onSurfaceVariant)
                        }
                        Spacer()
                        Text(tx.amount >= 0 ? "+\(tx.amount)" : "\(tx.amount)")
                            .foregroundStyle(tx.amount >= 0 ? AppTheme.primary : AppTheme.onSurfaceVariant)
                    }
                }
                if hasMore {
                    Color.clear.onAppear { Task { await loadMore() } }
                }
            }
            .scrollContentBackground(.hidden)
        }
        .background(AppTheme.background)
        .navigationTitle("余额流水")
        .task { await reset() }
    }

    private func reset() async {
        page = 1
        hasMore = true
        items = []
        await loadMore()
    }

    private func loadMore() async {
        guard session.isLoggedIn, hasMore, !loading else { return }
        loading = true
        defer { loading = false }
        do {
            let type = filterValues[filterIndex]
            let p = try await APIClient.shared.getWalletTransactions(
                page: page,
                pageSize: 20,
                type: type,
                token: session.token
            )
            if p.list.isEmpty { hasMore = false; return }
            if page == 1 { items = p.list } else { items.append(contentsOf: p.list) }
            page += 1
        } catch { hasMore = false }
    }
}
