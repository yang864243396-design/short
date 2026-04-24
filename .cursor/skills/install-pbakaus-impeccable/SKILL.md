---
name: install-pbakaus-impeccable
description: >-
  Installs Paul Bakaus’s Impeccable design skill bundle for Cursor via the Vercel
  skills CLI (pbakaus/impeccable), with non-interactive flags and path notes. Use
  when the user wants Impeccable, mentions npx skills add pbakaus/impeccable,
  impeccable.style, or needs audit/polish/critique-style UI steering commands
  installed from that repository.
---

# Install pbakaus/impeccable (Impeccable) for Cursor

## When this applies

Use this workflow when the repo should gain **Impeccable** (frontend design steering: `/audit`, `/polish`, `/critique`, typography/color/motion references, anti-“AI slop” rules) from **GitHub `pbakaus/impeccable`**, installed through the **`skills` CLI** (`npx skills …`), not by hand-copying folders unless the CLI is unavailable.

## One-shot install (project, Cursor only, no prompts)

From the **repository root**, run:

```bash
npx skills add pbakaus/impeccable --skill "*" -a cursor -y
```

**Why these flags**

- `--skill "*"` — install every skill discovered in that repo (Impeccable ships many command-skills).
- `-a cursor` — target Cursor only.
- `-y` — skip confirmation prompts (CI / automation friendly).

**Shorter interactive install** (will prompt for skill selection if `-y` / selection flags are omitted):

```bash
npx skills add pbakaus/impeccable
```

**Global install** (all projects for your user):

```bash
npx skills add pbakaus/impeccable --skill "*" -a cursor -y -g
```

## Where files land (important)

The `skills` CLI follows the [Agent Skills](https://agentskills.io/specification) layout. For **Cursor + project scope**, canonical copies are typically under:

```text
.agents/skills/<skill-name>/SKILL.md
```

The CLI reports the exact paths in its “Installation Summary”. After installing, verify with:

```bash
npx skills list
```

Do **not** assume `.cursor/skills/` unless your Cursor version and docs say so—this installer commonly uses `.agents/skills/` for the project copy.

## Cursor settings

Impeccable’s docs and Cursor’s skills feature evolve quickly. Ensure **Agent Skills** are enabled per [Cursor Skills](https://cursor.com/docs/context/skills). If commands like `/audit` are not recognized, check Beta/Nightly requirements in Cursor settings and restart the app after install.

## After install: how the agent should work

1. Prefer invoking the **installed** skills (`audit`, `polish`, `impeccable`, etc.) via the user’s slash commands or by reading those `SKILL.md` files under `.agents/skills/`.
2. For deep rules, open the **`impeccable`** skill and its `reference/` files (typography, color, spatial, motion, interaction, responsive, UX writing) when doing UI work.
3. **Cursor quirk**: optional “arguments” after a slash command are usually **appended as plain context**, not injected placeholders. Skills should behave sensibly with **no** trailing text or **specific** trailing text (e.g. `/audit checkout flow`).
4. One-time project design context: run the **teach / onboard** style command shipped with the bundle (name may vary by build; see `impeccable/SKILL.md` after install).

## Alternatives if `npx` is not an option

- Download a bundle from [impeccable.style](https://impeccable.style) and merge into the project per their instructions.
- From a clone of `pbakaus/impeccable`, copy the Cursor distribution: `dist/cursor/.cursor` (see upstream `README.md`).

## Optional: static anti-pattern scan (no agent)

```bash
npx impeccable detect path/to/ui
```

Use for quick regex/heuristic checks; it does not replace the skill bundle.

## Security note

`npx skills add` may run third-party install logic. Review the [skills.sh assessment](https://skills.sh/pbakaus/impeccable) link printed by the CLI and only proceed if acceptable for your environment.
