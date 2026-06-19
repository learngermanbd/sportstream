# SportStream — Master Todo (Redirect)

> The canonical, human-readable build checklist lives at **[`TODO.html`](TODO.html)** (dark glassmorphism, styled phase + step cards, status badges, premium budget table, cheat-sheet).
>
> This file is kept as a thin redirect so any downstream agent that reads `sportstream/TODO.md` still gets pointed to the source of truth. It is auto-synced to GitHub via the post-commit hook (`learngermanbd/sportstream:main`).

## Where to look

| File | Format | Audience |
|---|---|---|
| `TODO.html` | Rich dark-themed HTML | Humans, reviewers, screenshots, MiMo 2.5 multimodal |
| `TODO.md`  | This redirect              | Headless agents / grep |

## Quick phases

- **Phase 0** — Environment & Setup · ✓ DONE
- **Phase 1** — Project Setup & Foundation (Step 1.1 ✓ — rest pending)
- **Phase 2** — Core Architecture & Data Layer
- **Phase 3** — UI Screens
- **Phase 4** — Video Player
- **Phase 5** — Extra Features
- **Phase 6** — Polish, Ads & Launch
- **Phase 7** — Security & Protection
- **Phase 8** — Admin Panel (Web + Android + Backend)
- **Phase 9** — Testing & QA

Open **TODO.html** for the full step list with agents, prompts, and "Done when" gates.

## Strict plan mode (effective 2026-06-19)

🚨 **Effective immediately, every subsequent step in this project must follow the build plan strictly.** Authoritative source for each step is the `sportzfy_build_plan.html` `#phaseN` section — DO NOT defer or substitute features from the plan without an explicit sign-off recorded in the step's TODO.html entry.

Concretely:

- If a step's "Done when" lists a UI component, server endpoint, model field, or coroutine pattern — ship it AS SPECIFIED. Do not "defer to later."
- If the plan says 3 tabs and you built 4, that's a deviation. Either revert or get an explicit deviation-note recorded inline.
- If you find a deviation in the audit, fix it AND record the fix in TODO.html before moving on.
- Stricter than the previous TODO.html narrative: the audit summary at the bottom of Phase 3 in TODO.html is the new baseline. Anything not on that list is non-compliant.

Reviewed-by: Phase 3 audit (2026-06-19) — see `🚨 Phase 3 Audit` block at the end of Phase 3 in TODO.html for the specific deviations found and the fixes applied.
