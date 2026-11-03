# Cloud preset name fallback

Orca Cloud can return a profile name in the response envelope while omitting
`content.name`. The pinned desktop source explicitly chooses `content.name`, then
the envelope `name`, then the profile UUID
(`OrcaCloudServiceAgent.cpp:1162`, revision
`6d2869c430284714f0a22570c38d3b359965a802`).

Kepler's protocol parser and mirror already retained that fallback, but the actual
preset repository required `content.name`. A valid profile therefore failed with
`Invalid preset name` after its mirror and cursor had been saved, preventing
subsequent synchronization from completing.

The repository now fills a missing or empty content name in a detached source
copy. Merge and conflict review use the same fallback; server acceptance consumes
the normalized reviewed source and retains the UUID fallback for unnamed source
content. A present content name takes precedence. Existing name validation,
account/revision checks, conflicts, inheritance and atomic writes still apply.
Caller JSON, unknown source values and original inheritance values remain intact.
No protocol, UI, JNI or printer-provider behavior changes.

Validation: the added regression failed against the previous implementation with
`Invalid preset name`. After the fix, `tools/test_cloud_presets.ps1` passed all
87 repository checks using the existing `work/process-settings-test/json.jar`.
Coverage includes fallback priority, empty/missing names, clean baselines and
restart, detached JSON, dirty conflicts, server/duplicate resolution, invalid-name
rollback and out-of-order parent/child linking by fallback name.

These are host tests against the production repository with a small system-preset
fixture. They do not exercise HTTP, Android AtomicFile, real cloud accounts or the
Android UI. Numeric protocol validation is outside this change.
