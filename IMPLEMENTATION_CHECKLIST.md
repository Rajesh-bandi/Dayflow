# Dayflow implementation checklist

Audit baseline: Kotlin/Compose, Navigation Compose, Room 2.6.1, min SDK 26, target/compile SDK 35. Stitch references are in `stitch_dayflow_focus_schedule_planner/`.

## Delivery status

- [x] Remove first-run fabricated tasks and user-specific fabricated greeting.
- [x] Replace Today quick-action examples with saved Quick Action tasks; remove false automatic wind-down claim.
- [x] Quick Actions can be created through the task editor, edited, opened through Android `ACTION_VIEW`, and manually confirmed after launch; link-launch failures are surfaced.
- [x] Replace fabricated focus timer, app lists, and kernel-level enforcement claims with a clear statement that those capabilities are unavailable.
- [x] Replace false permission-granted indicators with accurate disclosure that those features are not yet implemented.
- [x] Keep empty Today, Week, Actions, and Progress states grounded in Room records.
- [x] Add a migration that removes only known legacy demo rows with zero creation timestamps; verified the upgraded device now shows an empty schedule.
- [x] Manually create a temporary task on the connected device, restart the app to verify persistence, then delete the test task and confirm the empty state.
- [x] Add a versioned Room migration and persisted focus-session history with running, paused, completed, interrupted, cancelled, and expired states.
- [x] Add persisted occurrence status, focus profiles, routine templates, settings, and saved Quick Action tasks.
- [x] Add edit-one-occurrence by detaching a dated copy, edit-series behavior, computed missed/in-progress states, skipped occurrences with undo, archive/restore, device-local recurrence times, and duplicate-safe routine application.
- [x] Add a daily schedule timeline, free-time calculations, routine management, and conflict confirmation when applying a routine.
- [x] Add weekly/monthly/custom analytics, completed-session focus minutes, schedule-aware habit streaks, and previous-period comparisons.
- [x] Add a Room-backed focus-session lifecycle, pause/resume, process-restart recovery, and actual elapsed-time recording.
- [x] Add focus completion notifications and alarm-triggered background session reconciliation.
- [x] Add an opt-in notification permission, 10-minute task reminders, and reboot/date/time/time-zone alarm restoration.
- [x] Add launchable-app discovery and an explicitly user-enabled Accessibility focus guard that sends selected apps Home while a session runs.
- [x] Add an on-device neural voice (sherpa-onnx + Piper/Kokoro) so Lumi can speak naturally with no account, key, or server; the gateway voice and Android's built-in voice remain as fallbacks.
- [x] Replace the fixed handful of "thinking" lines with an intent-aware status library covering 34 kinds of request, rotated so a repeated request does not repeat the same sentence.
- [x] Make focus-guard setup self-diagnosing: report whether this install declares the service, whether the switch is on, and whether the service is bound, and link straight to the guard's own Accessibility toggle page.
- [x] Add pure unit tests for status-intent classification, the WAV writer, and voice-archive extraction including a path-traversal attempt.
- [x] Move the natural voice into first-run setup: the assistant asks for its name, offers feminine or masculine voices, downloads the chosen model with live progress, selects it as the speaking voice and pre-loads it so the first reply is not the slow one.
- [x] Let the user rename the assistant and change the voice gender at any time; the name is used in the chat header, thinking lines, Today, the sanctuary and the earbud notification, and it is passed to the model so it refers to itself correctly.
- [x] Reuse the loaded voice model between replies instead of rebuilding it per sentence, and drop a failed engine so speech cannot stay broken.
- [x] Add unit tests for the assistant name sanitiser, the gender-to-voice mapping (Kokoro speaker ids), and the installed-voice preference when the gender changes.
- [x] Send a blocked app back into the running focus session instead of the launcher, with a Home fallback if Android refuses the background activity start.
- [x] Let the user unlock an app deliberately from the focus screen for the rest of that session, cleared when the session changes.
- [x] Mark the linked task complete when a focus session runs its full length; ending early, cancelling, expiring, or a session without a task leaves the schedule untouched.
- [x] Give notifications their own ascending three-note chime (bundled, not a system sound) plus a matching vibration and a monochrome focus icon, on versioned channels so an installed app picks the sound up.
- [x] Make notification taps meaningful: a reminder opens the app, a session-finished notification opens the focus screen, and an action task's secure link opens directly. Insecure links still open the app so the task's own confirmation step can ask first.
- [x] Add a profile avatar on the home screen, showing the saved name's initial, that opens profile and settings.
- [x] Add unit tests for the focus-completion rules (including a session that crosses midnight) and for notification link targeting and reminder id stability.
- [x] Add persisted dark/light theme, week-start, time-format, reminder, and onboarding settings.
- [x] Bundle Stitch Inter across app and Material components; align dark tokens/system bars, compact page chrome, Today rhythm hero, four-tab dock, and floating add action.
- [x] Reshape Progress around the harmony index, real period comparison, completion/focus/streak stats, consistency heatmap, time allocation, observations, and habit anchors; add a 90-day quarter range.
- [x] Reshape active Focus around a live circular timer, profile goal, Lumi focus expression, and a truthful opt-in guard status; pin save/discard controls on the task editor and onboarding flow.
- [ ] Broaden accessibility coverage and add consistent database loading/error surfaces.
- [x] Add pure unit tests for timestamp-based focus timer calculations.
- [ ] Add Room migration/restart persistence tests, UI tests, and Android integration tests beyond current domain/repository tests.
- [x] Compile Kotlin and assemble the debug APK with Java 21.
- [ ] Run unit tests and lint after these changes.
- [x] Install and launch on a real Android 14 device (RMX3561); verified the process stayed alive and the migration completed.
- [ ] Run instrumentation tests and real-device enforcement checks.
- [ ] Download a neural voice model and confirm synthesised speech on a device; the download, unpacking, and native synthesis path is unit-tested only for the parts that run on the JVM.
- [ ] Confirm both generated voices on hardware: the feminine (Amy / Kokoro sid 2) and masculine (Lessac / Kokoro sid 6) mappings come from the models' published speaker lists, not from listening to them.
- [ ] Hear the notification chime and watch the focus guard return to Dayflow on a device. The chime's shape is verified by measurement (880 Hz then 1320 Hz then 1760 Hz, 0.95 s, peak 82% of full scale), not by ear, and the background-activity-start behaviour depends on the ROM's handling of accessibility services.

## Known platform and verification limits

The focus guard requires the user to activate Dayflow in Android Accessibility settings. It sends selected launchable apps back to Home during active sessions; it cannot guarantee a lock, Android can stop it, and selected apps can be reopened. Dayflow, system UI/settings, and the default launcher are excluded. No usage-history permission is requested; discovery is limited to launchable user apps.

Task and focus alarms use `setAndAllowWhileIdle`, not exact alarms; Android battery policies can delay them. If notification permission is revoked, alarms may still run but Android will not show the notification. Time-zone/date/time and boot broadcasts refresh task reminders.

The latest Kotlin compile and debug APK assembly passed with Java 21 and the existing Gradle cache. The Java 27 default runtime is incompatible with this project's Kotlin parser. The updated APK was installed and launched on RMX3561; the Today dashboard, Lumi state, schedule and bottom dock were visually reviewed.

The neural voice adds native libraries (sherpa-onnx, two ABIs), which takes the debug APK to about 70 MB, and the chosen voice model is a separate one-time download of 63 MB (Piper) or 320 MB (Kokoro) into app storage. On this turn no Android device was attached to adb, so the model download and native synthesis were not exercised on hardware: only the JVM-testable parts (catalogue, WAV writer, bzip2/tar extraction and its path-traversal guard) were run, and all 64 unit tests passed. The Accessibility switch still has to be turned on by the user; the app can now link directly to that switch and reports exactly which part of the chain is missing.
