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
        context.coordinator.scrollView = s
        return s
    }

    func updateUIView(_ scrollView: UIScrollView, context: Context) {
        context.coordinator.indexBinding = $currentIndex
        context.coordinator.pageBuilder = page
        context.coordinator.count = count
        context.coordinator.pageWidth = pageWidth
        context.coordinator.pageHeight = pageHeight
        context.coordinator.rebuildIfNeeded(scrollView: scrollView)
        context.coordinator.syncOffsetIfIndexChangedExternally()
    }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        var indexBinding: Binding<Int>!
        var pageBuilder: ((Int) -> AnyView)!
        var count: Int = 0
        var pageWidth: CGFloat = 0
        var pageHeight: CGFloat = 0
        weak var scrollView: UIScrollView?
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
            if n == lastCount, abs(w - lastW) < 0.5, abs(h - lastH) < 0.5 {
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

        func syncOffsetIfIndexChangedExternally() {
            guard let s = scrollView, lastH > 0, count > 0, indexBinding != nil else { return }
            let maxI = count - 1
            let target = min(max(0, indexBinding.wrappedValue), maxI)
            let y = CGFloat(target) * lastH
            if abs(s.contentOffset.y - y) > 1 {
                isSyncingFromCode = true
                s.setContentOffset(CGPoint(x: 0, y: y), animated: false)
                isSyncingFromCode = false
            }
        }

        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
            updateIndex(from: scrollView)
        }

        func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
            if !decelerate { updateIndex(from: scrollView) }
        }

        private func updateIndex(from scrollView: UIScrollView) {
            guard !isSyncingFromCode, lastH > 0, indexBinding != nil else { return }
            let p = Int(round(scrollView.contentOffset.y / lastH))
            let clamped = min(max(0, p), max(0, count - 1))
            if indexBinding.wrappedValue != clamped {
                indexBinding.wrappedValue = clamped
            }
        }
    }
}
