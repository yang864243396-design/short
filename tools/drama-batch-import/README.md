# drama-batch-import

剧集批量导入 CLI（Go），完整约定见同目录 **[DRAMA_BATCH_IMPORT_SPEC.md](./DRAMA_BATCH_IMPORT_SPEC.md)**（含 **§17** 方案补遗、**§18** 未实现清单）。

查看参数：`./drama-import -help`（或 `-h` / `--help`）。

## 未实现一览（权威：SPEC §18）

下列与规格 **字字对齐** 仍缺交付；此处仅速览，编号与规格一致：

| 编号 | 类别 | 内容 |
|------|------|------|
| **B1** | 后端 | 「仅注册路径」建剧建集（§10），替代 multipart |
| **B2** | 后端 / 运维 | 孤儿上传文件自动回收（规格接受人工清理） |
| **C1** | CLI | YAML/JSON `-config`，优先级 CLI > 配置文件 > env |
| **C3** | CLI（可选） | `max_dirs` 仅「全流程成功」才 +1 |
| **C4** | 平台 | Windows 单机锁（规格不要求；生产 Linux） |
| **O1** | 运维 | 无 mp4 目录反复命中时的黑名单等（§5） |
| **O2** | 运维 | URL/字段超长约束（§15） |

**已实现**：管理员口令支持 **`IMPORT_ADMIN_PASSWORD_FILE`** / **`-admin-password-file`**（§2.3）。

**语义差异**（已实现但与字面略有出入）见下文「与 DRAMA_BATCH_IMPORT_SPEC 的差异说明」。

## 构建

```bash
cd tools/drama-batch-import
go mod tidy
GOOS=linux GOARCH=amd64 go build -o drama-import .
```

须在 **Linux** 下运行（使用 `flock` 文件锁）。

## 常用参数

| 参数 | 环境变量 | 说明 |
|------|----------|------|
| `-max-dirs` | `IMPORT_MAX_DIRS_PER_RUN` | 本轮最多处理多少个剧集目录（可选，`1…10000`） |
| `-before-date` | `IMPORT_MOD_TIME_BEFORE` | 仅处理目录 `ModTime` 早于该日本地 0 点的剧集（可选，`YYYY-MM-DD` 推荐） |
| `-allow-future-before-date` | （无） | 跳过「`before-date` 不得晚于 now+24h」校验；远期截止日期专用 |
| `-source-root` | `IMPORT_SOURCE_ROOT` | 默认 `/mnt/DuanJu` |
| `-staging-root` | `IMPORT_STAGING_ROOT` | 默认 `…/uploads/staging` |
| `-state-file` | `IMPORT_STATE_FILE` | `daoru.txt` 路径 |
| `-admin-base-url` | `IMPORT_ADMIN_BASE_URL` | 如 `http://127.0.0.1:8080/api/v1/admin` |
| `-admin-user` | `IMPORT_ADMIN_USER` | 管理员用户名 |
| `-admin-password` | `IMPORT_ADMIN_PASSWORD` | 管理员密码（明文）；与下一项同时指定时 **优先本项** |
| `-admin-password-file` | `IMPORT_ADMIN_PASSWORD_FILE` | 密钥文件路径；读取全文 **`TrimSpace`** 作为口令 |

**配置优先级**：

- **一般参数**：命令行 **覆盖** 对应环境变量（无 YAML 配置文件层 → 对应 SPEC **§18 C1**）。
- **管理员口令**（四选一，先到者优先）：**CLI `-admin-password` > CLI `-admin-password-file` > `IMPORT_ADMIN_PASSWORD` > `IMPORT_ADMIN_PASSWORD_FILE`**。

示例：

```bash
./drama-import \
  -admin-base-url http://127.0.0.1:8080/api/v1/admin \
  -admin-user admin \
  -admin-password secret \
  -max-dirs 10 \
  -before-date 2026-05-01
```

使用密钥文件代替明文：

```bash
IMPORT_ADMIN_PASSWORD_FILE=/secure/admin.pass ./drama-import \
  -admin-base-url http://127.0.0.1:8080/api/v1/admin \
  -admin-user admin
```

---

## 与 DRAMA_BATCH_IMPORT_SPEC 的差异说明（未完成项 / 注意点）

下列条目便于核对：**规格文档写什么** vs **`tools/drama-batch-import` 里 Go CLI 实际做到哪里**。

### 一、规格有要求，本 CLI 尚未实现（若要与 SPEC 字字一致需后续开发）

对应 SPEC **§18**：**C1**（配置文件）、**B1**（仅注册路径，依赖后端）。

| 条目 | 规格出处（概要） | 当前状态 |
|------|------------------|----------|
| **YAML/JSON 配置文件** | §2：可选用配置文件；优先级 `CLI > 配置文件 > 环境变量` | **未实现**。仅 **命令行参数 + 环境变量**；无 `-config`。 |
| **「仅注册路径」上传替代** | §10：减少孤儿文件的后端能力 + 导入工具切换 | **未实现**（依赖后端新接口；当前仍每次 `UploadVideo`/`UploadImage`）。 |

### 二、行为已实现，但与规格字面或惯例略有出入（可接受；要严格对齐再改代码）

| 条目 | 说明 |
|------|------|
| **§7.1 源 → staging 排除 `1`/`2`/`4`/`5`** | 与 SPEC 一致：每个剧集根下这四个**直接子目录**（及后代）不拷贝；staging 侧同名树在 prune 时删除。见 `sync.go`。 |
| **「本轮推进」计数** | 每对一个 **非 `已完成`** 目录完整跑完一轮 `processDirectory`（无论成功、`拷贝部分`/报错、或无 mp4 skip），**都计入一次** `advanced`，直到 `max_dirs` 或队列结束。规格「取出并至少推进一步」当前解释为 **「对该目录调度尝试一轮」**；若要做成 **「仅完全成功才占用 N」**（§18 **C3**）须改计数逻辑。 |
| **运行平台** | 规格写 **Linux**；锁文件使用 **`unix` build tag**（Linux/macOS 等可编译）。生产仍以 Linux 为准（§18 **C4**）。 |
| **`recommend_sort` 显式 `null`** | 建剧请求 **省略字段**；一般与后端默认一致；若某版本强制 JSON `null` 再补。 |
| **`before-date` 格式** | 解析允许多种格式；**推荐只用 `YYYY-MM-DD`**，避免带时区字符串与「本地日历边界」不一致。 |
| **无 mp4 且已有 `daoru` 行** | SPEC §5：可不新增行；已有行可刷新 `last_error`。本实现：**仅在有历史行时**写入 `last_error`，**不改动 `status`**。 |

### 三、已知局限（规格已写或属运维）

对应 SPEC **§18 O1 / O2**、**B2**。

| 条目 | 说明 |
|------|------|
| **孤儿上传文件** | 上传成功、建集失败等会产生服务端残留 mp4；§10 已说明接受或运维清理。 |
| **无 mp4 目录不写 daoru（首见）** | 下次仍会命中同一目录；§5 已说明，需人工移目录或另做黑名单。 |

完整条文仍以 **[DRAMA_BATCH_IMPORT_SPEC.md](./DRAMA_BATCH_IMPORT_SPEC.md)** 为准；上表仅描述 **本参考实现的边界**。
