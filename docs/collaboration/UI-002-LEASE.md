# Active sequential handoff: UI-002

2026-11-03: owner instructed both agents to continue their respective roles.
Claude owns SettingsActivity.java and PreferencesActivity.java presentation for
the nav/footer fix, plus the existing presentation allowlist. Their business
methods are frozen. Codex must not edit these files concurrently.

Codex owns SliceActivity.java only for extracting prepareResources into a private
technical resource service and wiring its callers; no visual redesign there.
Native library/JNI signatures stay unchanged for this increment. This lease does
not open the later product phases. Root integrates only after scope, build and
computer-use visual acceptance. Budget settings remain Sonnet/medium/USD1/12.
