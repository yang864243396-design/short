# 红果剧场 iOS（原生 SwiftUI）

与仓库内 `android_stitch` 对齐：**三 Tab（首页 / 播放 / 我的）**、搜索、排行榜、登录注册、个人中心（历史/收藏/点赞/钱包/免广购买）、竖屏播放页（40 集分组选集、点赞、收藏、评论楼中楼、金币解锁、Bearer 拉流）、刷剧 Feed 竖向分页、充值下单与模拟支付。

## 环境

- macOS + **Xcode 16.4**（Xcode 15+ 也可打开）
- **iOS 16.7+**
- 使用 [XcodeGen](https://github.com/yonaskolb/XcodeGen) 从 `project.yml` 生成工程（避免提交庞大的 `pbxproj`）

## 生成 Xcode 工程

```bash
brew install xcodegen
cd ios
xcodegen
open HongguoTheater.xcodeproj
```

## 接口地址

与 Android `build.gradle` 中 `BASE_URL` 一致，默认写在 `HongguoTheater/Info.plist` 的 `API_BASE_URL`（含结尾 `/`），可按环境修改，例如：

- 开发：`http://192.168.20.2:8080/api/v1/`
- 生产：`https://你的域名/api/v1/`

已开启 `NSAllowsArbitraryLoads` 以便 HTTP 调试；上架前请改为 HTTPS + ATS 白名单。

## 与 Android 的差异说明

| 项目 | 说明 |
|------|------|
| 刷剧 Tab | 已使用 `UIScrollView` 竖向分页，对齐 Android ViewPager2 的刷剧方向。 |
| 播放前广告 | 播放页已接入图片/视频贴片与免广告跳过；Feed 保持直接正片播放。 |
| 支付 | 外跳 `pay_url` 与 Android 一致；支付宝/微信是否唤起取决于系统是否安装客户端。 |
| 最终验证 | 当前仓库环境为 Windows，需在 macOS + Xcode 16.4 上执行最终 Build。 |

## App 图标

在 `Assets.xcassets/AppIcon` 中补充 1024×1024 及所需尺寸（或改用 Xcode 资产槽位）。
