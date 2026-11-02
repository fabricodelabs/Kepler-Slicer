# Online / Developer Preferences — Android implementation boundary

Snapshot: 2026-11-02. This work does not modify JNI, libslic3r, or the packaged native `.so`.

Implemented consumers:

- `stealth_mode`: `NetworkPolicy` rejects public/cloud requests while permitting private/link-local/loopback LAN targets. LAN-looking DNS names are resolved on a worker; every returned address must be private. Every HTTP request checks the current policy before connecting and again after registration; changing offline/stealth cancels active HTTP operations. Offline remains a separate all-network gate.
- Network test: tests the selected printer's actual protocol. With no selected printer, an explicit action tests HTTPS to the official Orca GitHub repository. It never uploads G-code or starts a print.
- `check_stable_update_only`: chooses stable versus prerelease entries from the official Orca releases API. It checks desktop Orca releases; it neither installs a desktop binary on Android nor pretends to update built-in presets.
- `sync_user_preset`: explicitly enabled foreground synchronization performs real account-scoped repository transactions for user Printer/Filament/Process profiles, not only a disconnected cloud mirror. A five-minute debounce bounds attempts while the application is used. Manual synchronization in CloudSyncActivity explains both pull and push and asks for product confirmation before sending profiles. Existing system presets remain immutable.
- `developer_mode`: the Preferences UI owns its actual diagnostics visibility consumer. The Java diagnostic helper reports the real Android/upstream version and policy state; it does not claim native developer APIs.
- `log_severity_level`: filters structured, bounded local Java diagnostic events. Event data excludes endpoint URLs, account IDs, credentials, tokens and raw HTTP bodies. This is the Android application logger, not an unimplemented JNI setter for the desktop Boost logger.

Cloud repository behavior:

- Original JSON values, types and unknown fields remain in the preset source. Account/UUID/revision/baseline metadata is stored outside source JSON and is absent from preset exports.
- New profiles use their stable local UUID as the cloud resource ID. Imports link their actual `parent_id` / `inherits`; out-of-order parent/child batches resolve in one validated write.
- Clean server changes and deletions apply atomically to the real user repository. Dirty local changes, duplicate names, unsupported types, unresolved parents and cycles fail the local batch without partial writes or silent overwrites.
- Mapped-profile conflicts offer a frozen preview of both sources, export of both versions, and per-profile keep-local / keep-remote / duplicate actions. Keep-local uses the reviewed server revision (never an unconditional force push). Keep-remote and duplicate validate the reviewed local fingerprint, update one source in one repository transaction, and preserve unrelated profiles. The duplicate saves full local effective configuration against its stable system ancestor. Stale reviews, duplicate names and unresolved parent/deletion dependencies fail without partial writes. Unassociated name collisions still require the explicit manual export/rename steps shown in the UI.
- Clean upserts and preserved conflict versions never move to an older `updated_time`. Mirror deletion revisions prevent stale upserts from resurrecting deleted records; a delete with an older cursor cannot erase a newer acknowledged upload. Older batch cursors (including an unexpected reset to zero after a nonzero cursor) fail closed rather than prune data. A legitimate server cursor reset may therefore require an explicitly reviewed resynchronization; actual service behavior has not been tested. Manual legacy mirror uploads share the full synchronization request/commit lock.
- The complete server mirror and conflict responses remain available separately. A 304 with no known initial snapshot cannot remove local profiles. Sending an older snapshot while the local source changes does not mark the newer edit clean.
- Every outgoing repository upload first creates a durable account/UUID reservation. A deletion during a first POST therefore queues cleanup even when the server accepted the POST but the response timed out; later pulls cannot resurrect that source. An accepted response arriving after local deletion also persists a tombstone before reporting the race. Explicit local deletion of a mapped profile queues an account-scoped tombstone. Acknowledgment removes the queue item only after an accepted remote delete. Failed/ambiguous requests retain retry state.
- Stealth/offline and changed/logout sessions are checked before requests. No synchronization starts at application startup unless the user has explicitly enabled it; a valid signed-in account is also required. No real cloud session has been used in verification.

Still blocked: **eight** original preferences have not gained their promised desktop behavior. The UI must expose the capability reason rather than a working-looking switch:

| Preference | Dependency / reason |
|---|---|
| Login region | The original preference routes the Bambu network agent. Orca Cloud in this port uses one global API. Storing a region or rerouting the global API would not implement region selection. |
| Filament sync mode | Requires real Bambu/AMS device state and a compatible filament/color mapping path. |
| Enable Bambu network plug-in | No Android Bambu library or JNI provider is present. The generic Kepler HTTP transport is not this plug-in. |
| Network plug-in version | Requires compatible plug-in distributions, ABI/version validation and installation lifecycle. |
| Skip AMS blacklist check | Requires the original device/AMS check and material identity data. |
| Allow Abnormal Storage | Requires the printer's actual storage-state report and the corresponding upload gate. |
| Reload network plug-in | Requires the Bambu provider lifecycle; reloading preferences or HTTP clients is not equivalent. |
| Automatically update built-in presets | Requires an authenticated/verified compatible profile-pack source, index/catalog regeneration and staged rollback tied to the pinned engine/schema. Checking desktop release availability is not installation of a compatible profile pack. |

Bambu feasibility evidence:

- Pinned Orca `src/slic3r/GUI/Preferences.cpp`: 587–657 region; 2246–2251 filament sync mode; 2262–2268 plug-in enable/version; 2348 AMS blacklist; 2376 abnormal storage; 1325–1442 plug-in version/reload workflow.
- Pinned `src/slic3r/Utils/BBLNetworkPlugin.cpp`: 85–203 versioned native library loading, desktop platform names and `LoadLibrary`/`dlopen`; 658 onwards native symbol binding. `PresetUpdater.cpp:806–816` expects Windows DLL, macOS dylib or Linux `.so` plus Bambu media libraries. This is not a Java Android provider.
- The official [Bambu Studio README](https://github.com/bambulab/BambuStudio/blob/master/README.md) lists Windows/macOS/Linux build targets and describes the optional networking plug-in as based on non-free libraries. Its documentation does not provide an Android plug-in distribution. This search finding is not proof that a private/vendor Android SDK does not exist.
- An ARM64 Linux binary is not evidence of Android NDK/Bionic/C++ ABI compatibility. A supported Android build and its API/license/distribution contract must be established before a loader can advertise successful installation.

Concrete remaining implementation paths: obtain a vendor-supported Android distribution and add a separately reviewed provider/ABI lifecycle, or port the documented/open LAN wire-protocol portions into an Android provider with strict TLS/identity handling, simulator tests and eventual device verification. A LAN implementation alone would not establish Bambu Cloud, camera, all AMS/storage semantics or interoperability with the proprietary desktop plug-in. No third-party slicer application is proposed or required.

Verification: `tools/test_cloud_presets.ps1` exercises the real repository against supplied server snapshots; `tools/test_online_preferences.ps1` tests policy/channel selection and HTTP requests against loopback only. These tests do not execute JNI, authenticate with cloud accounts, use a physical printer, or establish desktop round-trip parity.

Latest host results: 66 cloud-repository checks, 40 online policy/release/HTTP checks and 25 cloud-mirror checks; the current existing preset suite has 85 assertions. Android AtomicFile is an explicit host boundary double in mirror tests. Actual cloud accounts and the conflict dialogs on a signed-in device remain unverified.
