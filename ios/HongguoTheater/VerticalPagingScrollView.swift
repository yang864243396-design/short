import SwiftUI
import UIKit

/// 竖向分页 `UIScrollView`，对齐 Android `ViewPager2` 竖滑行为。
struct VerticalPagingScrollView: UIViewRepresentable {
    @Binding var currentIndex: Int
    var count: Int
    var pageWidth: CGFloat
    var pageHeight: CGFloat
    var page: (Int) -> AnyView

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIScrollView {
        let s = UIScrollView()
        s.isPagingEnabled = true
        s.showsVerticalScrollIndicator = false
        s.showsHorizontalScrollIndicator = false
        s.bounces = true
        s.alwaysBounceVertical = false
        s.delegate = context.coordinator
        s.backgroundColor = .clear
        return s
    }

    func updateUIView(_ scrollView: UIScrollView, context: Context) {
        context.coordinator.indexBinding = $currentIndex
        context.coordinator.pageBuilder = page
        context.coordinator.count = count
        context.coordinator.pageWidth = pageWidth
        context.coordinator.pageHeight = pageHeight
        context.coordinator.rebuildIfNeeded(scrollView: scrollView)
        context.coordinator.refreshHostedPages(scrollView: scrollView)
        context.coordinator.syncOffsetIfIndexChangedExternally(scrollView)
    }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        var indexBinding: Binding<Int>!
        var pageBuilder: ((Int) -> AnyView)!
        var count: Int = 0
        var pageWidth: CGFloat = 0
        var pageHeight: CGFloat = 0
        private var hosts: [UIHostingController<AnyView>] = []
        private var lastCount: Int = -1
        private var lastW: CGFloat = 0
        private var lastH: CGFloat = 0
        private var isSyncingFromCode = false

        func rebuildIfNeeded(scrollView: UIScrollView) {
            let w = pageWidth
            let h = pageHeight
            let n = count
            guard w > 0, h > 0, n > 0 else {
                hosts.forEach { $0.view.removeFromSuperview() }
                hosts.removeAll()
                scrollView.contentSize = .zero
                lastCount = 0
                return
            }
            // 仅看 (count, size) 相同就跳过会漏掉「count 没变但子宿主数量已不同步」的情况：
            // 此时 refreshHostedPages 会因 hosts.count != count 直接放弃，表现像「刷剧永远只有一条/滑不动」。
            let layoutUnchanged = n == lastCount
                && abs(w - lastW) < 0.5
                && abs(h - lastH) < 0.5
                && hosts.count == n
            if layoutUnchanged {
                return
            }
            lastCount = n
            lastW = w
            lastH = h

            hosts.forEach { $0.view.removeFromSuperview() }
            hosts.removeAll()

            scrollView.frame = CGRect(x: 0, y: 0, width: w, height: h)
            scrollView.contentSize = CGSize(width: w, height: h * CGFloat(n))

            for i in 0 ..< n {
                let hc = UIHostingController(rootView: pageBuilder(i))
                hc.view.backgroundColor = .clear
                hc.view.frame = CGRect(x: 0, y: CGFloat(i) * h, width: w, height: h)
                scrollView.addSubview(hc.view)
                hosts.append(hc)
            }
            isSyncingFromCode = true
            let idx = indexBinding.wrappedValue
            let y = CGFloat(min(max(0, idx), n - 1)) * h
            scrollView.contentOffset = CGPoint(x: 0, y: y)
            isSyncingFromCode = false
        }

        func refreshHostedPages(scrollView: UIScrollView) {
            guard pageBuilder != nil else { return }
            if hosts.count != count {
                rebuildIfNeeded(scrollView: scrollView)
                return
            }
            for i in 0 ..< hosts.count {
                hosts[i].rootView = pageBuilder(i)
            }
        }

        /// 仅在「非用户滑动过程」中把 `contentOffset` 对齐到绑定索引（例如代码里改 `currentIndex`）。
        /// 滑动/减速期间绝不能调用：父视图因进度条等 @State 高频刷新时也会走 `updateUIView`，
        /// 若此时强行对齐旧索引，会把用户正在浏览的下一页拽回上一页，导致 overlays 跟着滑走但底层单路视频仍停在第一条。
        func syncOffsetIfIndexChangedExternally(_ s: UIScrollView) {
            guard lastH > 0, count > 0, indexBinding != nil else { return }
            if s.isDragging || s.isDecelerating { return }
            let maxI = count - 1
            let target = min(max(0, indexBinding.wrappedValue), maxI)
            let y = CGFloat(target) * lastH
            if abs(s.contentOffset.y - y) > 1 {
                isSyncingFromCode = true
                s.setContentOffset(CGPoint(x: 0, y: y), animated: false)
                isSyncingFromCode = false
            }
        }

        /// 抖音式：随可见页中心变化立刻更新索引，使单路 `AVPlayer` 与当前页 overlay 同步（不只等减速结束）。
        func scrollViewDidScroll(_ scrollView: UIScrollView) {
            updateIndexFromOffset(scrollView, allowDuringUserScroll: true)
        }

        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
            updateIndexFromOffset(scrollView, allowDuringUserScroll: false)
        }

        func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
            if !decelerate { updateIndexFromOffset(scrollView, allowDuringUserScroll: false) }
        }

        private func updateIndexFromOffset(_ scrollView: UIScrollView, allowDuringUserScroll: Bool) {
            guard !isSyncingFromCode, lastH > 0, indexBinding != nil, count > 0 else { return }
            if !allowDuringUserScroll, scrollView.isDragging || scrollView.isDecelerating { return }
            let p = Int(round(scrollView.contentOffset.y / lastH))
            let clamped = min(max(0, p), max(0, count - 1))
            if indexBinding.wrappedValue != clamped {
                indexBinding.wrappedValue = clamped
            }
        }
    }
}
