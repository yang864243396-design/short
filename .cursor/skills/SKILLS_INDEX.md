# 项目 Skill 索引与路由（最小 token）

## 零、何时打开本文件

| 情况 | 是否读本文 |
|------|------------|
| 只动 **backend/** Go、与 **admin/Android 界面无关** | **否** — 用 `project.mdc` 架构表即可 |
| 动 **admin/**、**Android** UI，或用户提 **设计 / 图标 / 动效 / 品牌 / DESIGN.md** | **是** — 读完 **§半** 后最多再读 **1～2** 个 `SKILL.md` |

**硬规则**：勿批量 `Read` `.agents/skills/**`；勿在同一任务里把 **Impeccable** 与 **Taste** 各读一整份（主审美 **二选一**；可额外叠 **窄** skill，如仅 `full-output-enforcement`）。

---

## 半、10 秒决策树（按顺序命中即停）

1. **纯 API / 库表 / 与 UI 无关** → 不读任何 UI skill。  
2. **先定方案再写码（简报 / 流程 / 信息架构）** → `shape` **或** `impeccable`（只选其一作主线）。  
3. **「像 Vercel / Notion / Linear」等品牌风** → `.cursor/skills/awesome-design-md/SKILL.md` + **仅一份** getdesign DESIGN.md。  
4. **Vue 管理台要栈 / 色板 / 字体 / UX 清单 / 图表类型** → `ui-ux-pro-max`。  
5. **Iconify / 跨库搜图标 / better-icons CLI·MCP** → `better-icons`。  
6. **走 Taste 审美（不用 Impeccable 作主链）** → 下表 **「Taste」只选一行**。  
7. **界面已存在：查问题 / 改质感** → **§二** 里 **只选最贴的一项**（常见：`audit` → `polish`）。

安装记录见仓库根目录 **`skills-lock.json`**（与 `.agents/skills/` 目录名一致）。

---

## 一、已安装目录速查（相对仓库根）

**路径约定**：下列包除标注外均在 **`.agents/skills/<目录名>/SKILL.md`**。

### pbakaus/impeccable（17）

| 目录 | 用途（极简） |
|------|----------------|
| shape | 规划：UI/UX 简报，先想清楚再写码 |
| impeccable | 设计上下文、teach/craft、反 AI 默认脸 |
| audit | 技术质量：a11y / 性能 / 响应式 |
| critique | UX 评审：层级、情感 |
| polish | 上线前统一、润色 |
| layout | 布局与间距节奏 |
| typeset | 字体与层级 |
| colorize | 色彩策略 |
| animate | 动效 |
| adapt | 多设备适配 |
| distill | 简化信息结构 |
| clarify | 文案、空状态 |
| optimize | 性能优化 |
| bolder / quieter | 视觉加强 / 收敛 |
| delight | 微惊喜 |
| overdrive | 高阶视觉效果 |

### Leonxlnx/taste-skill（8）

| 目录 | 用途（极简） |
|------|----------------|
| design-taste-frontend | Taste 主 skill（通用高级前端） |
| gpt-taste | 高规格站、强动效 / GSAP 向 |
| redesign-existing-projects | 先审计再改旧界面 |
| high-end-visual-design | 柔和贵价、留白 |
| full-output-enforcement | 禁止半成品、占位输出 |
| minimalist-ui | 极简编辑风 |
| industrial-brutalist-ui | 粗野实验风 |
| stitch-design-taste | Stitch / DESIGN.md 语义 |

### better-auth/better-icons（1）

| 目录 | 用途（极简） |
|------|----------------|
| better-icons | 搜 200+ 图标集、取 SVG、MCP |

### `.cursor/skills/`（仓库手写）

| 路径 | 用途（极简） |
|------|----------------|
| `ui-ux-pro-max/SKILL.md` | 多栈设计数据、脚本查 CSV |
| `awesome-design-md/SKILL.md` | getdesign / 品牌 DESIGN.md 桥接 |
| `install-pbakaus-impeccable/SKILL.md` | `npx skills` 安装 Impeccable 说明 |

---

## 二、实现阶段：Impeccable 单选参考

**只读与当前子任务最相关的一个文件。**

| 关键词 | 目录 |
|--------|------|
| 质量扫描 / a11y / 性能 | audit |
| 设计点评 | critique |
| 定稿润色 | polish |
| 排版 | typeset |
| 栅格间距 | layout |
| 配色 | colorize |
| 动画 | animate |
| 响应式 | adapt |
| 做减法 | distill |
| 文案 | clarify |
| 性能 | optimize |
| 更抢眼 / 更克制 | bolder / quieter |
| 小惊喜 | delight |
| 炫技视觉 | overdrive |

---

## 三、管理台图标（Element vs better-icons）

- **默认**：`admin/` 内 **@element-plus/icons-vue**，与 Element 成套。  
- **better-icons**：缺隐喻、指定 Lucide/MDI 等、要原始 SVG / 批量搜 — 见 `better-icons` skill；**同一屏不混多种图标族**。

---

## 四、工具命令（备忘）

| 场景 | 命令或位置 |
|------|------------|
| 装 / 更新 Impeccable | 见 `install-pbakaus-impeccable/SKILL.md` |
| 装 better-icons | `npx skills add better-auth/better-icons --skill "*" -a cursor -y` |
| 同步 awesome-design-md 索引 | `git clone --depth 1 https://github.com/VoltAgent/awesome-design-md.git references/awesome-design-md`（或见 `awesome-design-md/SKILL.md`） |

---

## 五、维护

新增 skill：在 **§一** 加一行（目录名 + 一句话）；更新 **`skills-lock.json`**（若由 `npx skills` 安装会自动变）；**勿** 把长文贴进本文件。
