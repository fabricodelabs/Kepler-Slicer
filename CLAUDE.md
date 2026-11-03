# Claude Code: Kepler native Android UI

Read AGENTS.md, docs/collaboration/WORKFLOW.md, ownership.json and
docs/collaboration/CLAUDE-TASK.md. You own the existing native UI, not a new app.
Java/Views, programmatic layouts, existing glass components and Android themes
are intentional. Preserve functional behavior and current user data.

Work only in the Kepler-Claude worktree on claude/ui. Initially change only the
Claude allowlist in ownership.json. Other sources may be read to understand the
contracts. Do not edit engine, JNI, stores, geometry, networking, build files,
Manifest, generated Orca schemas/catalogs, or coordination policy.

Mixed Activity files require a named task and explicit ownership transfer from
Codex before editing. Report proposed changes and required APIs in docs/ui/.
Use actual SettingsOptionAdapter/SettingsEditorDialog/GlassNavigation callbacks;
there are no ViewModels in this app. Do not invent API implementations or hide
unsupported features behind working-looking controls.

Do not run Git mutations, build/install apps, start printers, or access account
secrets. Codex reviews diffs, performs builds and computer-use verification, and
commits/integrates your work. End each assignment with changed paths, behavior
impact, validation performed and open integration requests.
