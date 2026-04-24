---
name: awesome-design-md
description: >-
  Uses Stitch-style DESIGN.md from VoltAgent awesome-design-md / getdesign.md
  when the user wants UI aligned with a named product (Vercel, Linear, Cursor,
  Notion, etc.). Local brand index under references/; full tokens live on
  getdesign.md. Use when the user mentions awesome-design-md, getdesign.md,
  DESIGN.md, or brand-inspired design systems.
---

# Awesome DESIGN.md（VoltAgent）

## 背景

- 上游仓库：[VoltAgent/awesome-design-md](https://github.com/VoltAgent/awesome-design-md)（MIT）。
- 浏览与下载入口：[getdesign.md](https://getdesign.md/)（含 [#find-skills](https://getdesign.md/#find-skills) 式检索）。
- **没有** 符合 Agent Skills 规范的 `SKILL.md`，因此 **`npx skills add VoltAgent/awesome-design-md` 会失败**；本仓库用 **本文件 + 本地索引目录** 代替。

## 本地索引路径

```
references/awesome-design-md/design-md/<品牌文件夹>/README.md
```

每个 `README.md` 会给出该品牌在 getdesign.md 上的 **DESIGN.md 链接**（例如 Cursor：`https://getdesign.md/cursor/design-md`）。文件夹名与 URL 段一致（含 `linear.app`、`x.ai` 带点号等情况）。

## Agent 工作流（省 token）

1. **只选一个品牌**；不要通读整个 `design-md/` 树。
2. 读对应目录下的 **`README.md`**，取出 getdesign.md 的 URL。
3. **只拉取这一份 DESIGN.md**（浏览器、或用户下载后放入项目；若网络可用再请求该 URL）。禁止把 60+ 套设计一次读入上下文。
4. 将 DESIGN.md 中的色板、字体层级、组件规则 **映射到当前栈**（本项目的 `admin/` 为 **Vue3 + Element Plus**），它是 **参考规范** 不是现成 CSS。

## 更新索引

若仍带 `.git` 的完整克隆：

```bash
git -C references/awesome-design-md pull
```

当前仓库若已 **去掉嵌套 `.git`**，可从上游重新同步：

```bash
rm -rf references/awesome-design-md
git clone --depth 1 https://github.com/VoltAgent/awesome-design-md.git references/awesome-design-md
Remove-Item -Recurse -Force references/awesome-design-md/.git   # Windows: 可选，便于主仓库纳入索引文件
```

## 与其它 skill 的关系

主审美流程仍以 **`SKILLS_INDEX.md`** 为准（Impeccable / Taste / `ui-ux-pro-max`）。**DESIGN.md 定「像谁」**；Element 组件与工程约束仍要服从项目规则。
