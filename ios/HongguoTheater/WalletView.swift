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

    var body: some View {
        List {
            if let b = balance {
                Section("我的钱包") {
                    Text("当前余额：\(b.coins) \(b.currencyName ?? "金币")")
                    if b.coinsPerYuan ?? 0 > 0 {
                        Text("规则：1 元 ≈ \(b.coinsPerYuan ?? 100) 金币")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            if let e = env {
                Section("充值套餐") {
                    ForEach(e.list, id: \.id) { pkg in
                        Button {
                            Task { await buy(pkg, e) }
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(pkg.name)
                                    Text("¥ \(String(format: "%.2f", pkg.priceYuan))")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text("\(pkg.coins + pkg.bonusCoins) 币")
                                    .foregroundStyle(AppTheme.primary)
                            }
                        }
                    }
                }
                if e.simulateAllowed {
                    Section {
                        Text("当前为模拟支付环境，可在下单后使用模拟到账。")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            Section {
                Button("余额流水") { path.append(WalletTxDest()) }
            }
        }
        .navigationTitle("我的钱包")
        .task { await reload() }
        .refreshable { await reload() }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                Task { await queryPendingRechargeIfNeeded() }
            }
        }
        .confirmationDialog("选择支付方式", isPresented: $showPayChannel, titleVisibility: .visible) {
            if let e = payChannelEnv {
                ForEach(e.payOptions.filter(\.enabled), id: \.id) { opt in
                    Button(opt.name) {
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

    private func reload() async {
        guard session.isLoggedIn else { return }
        do {
            balance = try await APIClient.shared.getWallet(token: session.token)
            env = try await APIClient.shared.getRechargePackages(token: session.token)
        } catch { message = error.localizedDescription }
    }

    private func buy(_ pkg: RechargePackageItem, _ e: RechargePackagesEnvelope) async {
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
        payChannelPkg = nil
        payChannelEnv = nil
        do {
            let r = try await APIClient.shared.createRechargeOrder(
                packageId: pkg.id,
                productId: productId,
                token: session.token
            )
            if let url = r.payUrl, !url.isEmpty, let u = URL(string: url) {
                payQueryMch = r.mchOrderNo ?? r.order?.mchOrderNo
                payQueryPayId = r.order?.payOrderId
                UIApplication.shared.open(u)
                message = "已跳转支付，返回本应用后将自动查单；也可稍后在流水中确认。"
            } else if let oid = r.order?.id, e.simulateAllowed {
                try await APIClient.shared.simulateRechargePay(orderId: oid, token: session.token)
                message = "模拟支付成功"
                await reload()
            } else {
                message = "充值成功"
                await reload()
            }
        } catch { message = error.localizedDescription }
    }

    private func queryPendingRechargeIfNeeded() async {
        guard session.isLoggedIn, payQueryMch != nil || payQueryPayId != nil else { return }
        do {
            let r = try await APIClient.shared.queryRechargeOrder(
                mchOrderNo: payQueryMch,
                payOrderId: payQueryPayId,
                token: session.token
            )
            if r.order?.status == "paid" {
                payQueryMch = nil
                payQueryPayId = nil
                if let c = r.coins {
                    message = "支付成功，当前约 \(c) 金币"
                } else {
                    message = "支付成功"
                }
                await reload()
            }
        } catch {
            if payQueryMch != nil || payQueryPayId != nil {
                message = "查单未成功，请下拉刷新重试，或稍后在流水中确认。"
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
            Picker("", selection: $filterIndex) {
                ForEach(0 ..< filterLabels.count, id: \.self) { i in
                    Text(filterLabels[i]).tag(i)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .onChange(of: filterIndex) { _ in Task { await reset() } }

            List {
                ForEach(items) { tx in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(tx.title)
                            Text(tx.createdAt)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
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
        }
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
