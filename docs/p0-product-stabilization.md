# P0 product stabilization evidence

## Baseline

- Base branch: `main`
- Base commit: `e219a8e1da8c28627bb3a10effca7b54307aabff`
- Base state: clean and identical to `origin/main`
- Working branch: `fix/p0-product-stabilization`
- Recorded on: 2026-11-22 (Asia/Tehran)

The repository contains `gradle/wrapper/gradle-wrapper.properties`, pinned to Gradle 9.3.1,
but does not contain `gradlew` or `gradlew.bat`. The initial baseline commands therefore did
not start:

| Command | Exit/result | Evidence |
| --- | --- | --- |
| `.\gradlew.bat assembleDebug` | Not started | PowerShell: `gradlew.bat` was not recognized |
| `.\gradlew.bat testDebugUnitTest` | Not started | PowerShell: `gradlew.bat` was not recognized |

Gradle 9.3.1 was then downloaded outside the repository and verified against the official
SHA-256 (`b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`).
With that executable, project configuration failed before compilation because AGP 9.1.1
dependencies were not resolvable from the configured repositories. The missing artifacts
included `com.android:signflinger:9.1.1`, `tracker:32.1.1`, `manifest-merger:32.1.1`,
`zipflinger:9.1.1`, `apksig:9.1.1`, and `apkzlib:9.1.1`.

## Acceptance criteria and evidence

| Change | Acceptance criterion | Test/evidence | Result | Risk |
| --- | --- | --- | --- | --- |
| Failed microphone start | No file is published, `isRecording` stays `false`, and the recorder is released | `microphoneStartFailureDoesNotPublishAFileOrRecordingState` | Test present; execution blocked during Gradle project configuration | Device/vendor `MediaRecorder` failures still vary; failure is surfaced instead of hidden |
| Missing permission | Recorder does not start and no recordings directory/file is created when `RECORD_AUDIO` is denied | `missingPermissionDoesNotStartTheRecorderOrCreateAFile` | Test present; execution blocked during Gradle project configuration | UI retains the existing generic microphone-access message |
| Invalid output | Empty or unreadable audio is not returned or rewritten | `invalidOrEmptyOutputIsNotPublishedOrRewritten` | Test present; execution blocked during Gradle project configuration | `MediaExtractor` behavior can vary by Android image/vendor |
| Stop failure | State is cleared, recorder is released, partial data is preserved, and no failed recording is published | `stopFailureClearsStateAndDoesNotReturnTheFailedRecording` | Test present; execution blocked during Gradle project configuration | A partial failed artifact can remain on disk; this intentionally avoids deleting user data |
| Successful stop | A readable audio track is returned once; duration is preserved and another recording can start safely | `successfulStopReturnsTheRealOutputOnceAndAllowsRetry` | Test present; execution blocked during Gradle project configuration | Validation reads up to 64 KiB from the first audio track; unusual containers need device coverage |
| No fabricated audio | Production code contains no dummy-header or fallback write path | Source diff and search for `FileOutputStream`, `dummy`, and `ftyp` | Pass (static evidence) | Static evidence does not replace device/instrumentation coverage |

## Verification commands and actual outcomes

| Phase | Command | Actual outcome |
| --- | --- | --- |
| Focused test | `gradle.bat :app:testDebugUnitTest --tests com.example.service.AudioRecordManagerTest` | Exit 1 before compilation: configured repositories could not resolve required AGP artifacts |
| Mirror diagnostic (not committed) | Same command with temporary Google/plugin mirrors | Resolution progressed but remained without output for more than 10 minutes and was interrupted; mirror edits were reverted |
| Focused offline retry | `gradle.bat :app:testDebugUnitTest --tests com.example.service.AudioRecordManagerTest --offline` | Exit 1 in 57s: the six AGP artifacts were not present in cache |
| Source hygiene | `git diff --check` | Exit 0 |

Because test execution never reached compilation, neither focused tests nor the regression
suite are reported as passing. The dependency-resolution failure is a pre-existing build
infrastructure blocker and remains visible for reviewers.

## Data-safety note

No recording, database, preference, archive, or other user-data deletion was added or run.
The failure paths deliberately preserve any existing partial/invalid file while refusing to
publish it as a valid recording.
