# UI-006 handoff: settings search

Claude Code authored candidate32e59cd: hub result count, scrollable no-results
footers in hub/editor, clear action, polite live regions and tonal target ring
inside existing grouped-row clipping. Matching, callbacks, guards and read-only
behavior remain unchanged. SettingsActivity/SettingsOptionAdapter only.

The bounded Sonnet/medium run ended at configured12turnlimit (CLI reports13turns),
estimatedUSD0.3363753, error_max_turns. No automatic restart. Under sequential
UI-006-INTEGRATION-LEASE, Codex completed missing RU/default/EN string/plural
resources, renamed local searchCount to avoid TextWatcher count shadowing and
made display truncation Unicode-code-point safe. These repairs are Codex-authored.

Final combined build/lint passed offline (0 errors, 120 warnings). Final APK:
251F9F267DB6E30B145FF51D1AB1C49EAD0612AF02632DF97E76E694D29A36B5,
221,212,040 bytes; installed through Android Studio (533 ms). Earlier candidate
A154FD76F7F85B9E099382A3F56D40FFC9F3A78CFBEF658D5433DF8E614A967A
and pre-keyboard-fix combined 155AE7D49F3FBA28 have separate evidence in the report.

Actual CU found VIS-006 on enlarged portrait+IME: clear action clipped, footer
could not scroll into view. Under the stopped-run lease Codex repaired only
SettingsActivity.shell keyboard sizing: adjustResize and API35+ safe-inset union
including IME. Final APK passed enlarged portrait scroll/clear, enlarged landscape
empty/clear and highlighted search jump, default portrait empty/clear/IME-close,
default landscape scrolling/group joins. Medium Phone API37, Russian, font stops
2/7 and 5/7, display size 2/5. Original font 2/7 and portrait restored; existing
STEP model/profiles preserved. No saved model/profile edits or slicing during QA.

Integrator accepts this bounded presentation increment. Full Phase2 acceptance,
API26-34, complete RU/EN/TalkBack matrix, project read-only runtime regression,
all row targets and all profiles remain unverified. Target highlight is lost
after rotation (preexisting state limitation); selected tab survives. The large
font navigation caption ellipsizes within its separate slot. No desktop-pixel
parity or <=4dp claim is made.

Read-only UI review found no blocking semantic regressions. Expected short-screen
behavior: footer scrolls within ListView, never hides the header. Android's default
landscapeIME uses its system fullscreen editor; Done returns to app results.

Actual-device/font/orientation evidence and limitations are recorded by the
integrator in docs/collaboration/INCREMENT-2026-11-03-UI006.md.
