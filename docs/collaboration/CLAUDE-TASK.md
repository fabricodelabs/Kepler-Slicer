# UI-002: fix navigation/content collisions in existing Settings and Preferences

Owner: Claude Code, branch claude/ui. Technical reviewer: Codex.
Budget: Sonnet / medium reasoning, USD1 estimated and 12 agentic turns per run.
Keep this bounded: read only the two host files, GlassNavigation, and relevant
handoff notes. Do not repeat the whole-repository audit. Implement, then report.

## Evidence and exact assignment
VIS-001 is confirmed through computer-use on the existing APK: landscape Settings
has the Material name drawn beneath the translucent bottom navigation and icon.
See docs/collaboration/screenshots/VIS-001-settings-landscape.png. The existing
110/112dp scroll-content padding only affects the END of the list; it does not
stop intermediate rows rendering behind a floating nav. Fix the layout itself.

Sequential ownership is transferred to you for SettingsActivity.java and
PreferencesActivity.java PRESENTATION ONLY: shell()/render(), nav positioning,
page/scroll available height and inset/keyboard layout. Codex will not edit those
files during UI-002. Existing callbacks, workers, lifecycle/generation guards,
validation, preset/project semantics and preference actions are frozen.

1. Reserve a real footer region for navigation, so scroll/list content is clipped
   ABOVE its top and cannot visually overlap it. Preserve floating pill styling,
   current destinations, colors and callbacks. Prefer actual measured nav height
   (WRAP_CONTENT/minimumHeight) over fixed 64dp or a guessed content spacer.
2. Make the nav host tolerate enlarged font in portrait and landscape. Do not
   reduce system font scaling, auto-shrink text, or change reference tokens to
   conceal clipping. Keep title/back/actions from overlapping where touched.
3. Apply the same footer contract consistently on Settings hub/editor and
   Preferences, including after section rebuild/rotation. Preserve editor IME
   behavior and scroll restoration. No speculative unrelated API26 insets rewrite.
4. Keep the prior chip-width candidate. No other Activity or technical file may
   change. An additive helper in glass/ is allowed if it reduces duplication.
5. Write docs/ui/UI-002-HANDOFF.md with exact changes, acceptance matrix and
   remaining defects. Codex will compile/install and visually test the APK with
   computer-use before integration. Do not claim screenshots/tests you did not run.

No Git mutations, shell tools, devices, accounts or network calls. If budget is
running short, finish the small footer fix/report rather than scanning more files.
Future UI work will be delegated as subsequent bounded assignments. Phases 3-7
remain gated. Codex separately owns SliceActivity resource-service extraction.
